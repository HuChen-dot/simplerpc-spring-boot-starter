package org.hu.rpcintegration;

import org.apache.curator.test.TestingServer;
import org.apache.curator.test.KillSession;
import org.hu.rpc.core.server.RpcServerHandler;
import org.hu.rpc.register.zk.server.ZkRegisterInit;
import org.hu.rpc.util.IpUtils;
import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.core.route.RouteStrategy;
import org.hu.rpc.register.zk.client.ZkClientInit;
import org.hu.rpc.register.zk.util.ZkClientService;
import org.hu.rpc.register.zk.util.ZkLock;
import org.hu.rpc.exception.SimpleRpcException;
import org.junit.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;

public class ZkIntegrationTest {
    private TestingServer server;
    private ZkClientService first;
    private ZkClientService second;

    @Before
    public void setUp() throws Exception {
        server = new TestingServer();
        first = new ZkClientService(server.getConnectString(), true);
        second = new ZkClientService(server.getConnectString(), true);
        first.init();
        second.init();
    }

    @After
    public void close() throws Exception {
        if (first != null) { first.close(); }
        if (second != null) { second.close(); }
        if (server != null) { server.close(); }
    }

    private ZkLock lock(ZkClientService client) {
        ZkLock lock = new ZkLock();
        ReflectionTestUtils.setField(lock, "zkClientService", client);
        return lock;
    }
    private void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < end) { Thread.sleep(10); }
        assertTrue("ZooKeeper state did not converge", condition.getAsBoolean());
    }

    @Test(timeout = 10000)
    public void distributedLockExcludesOtherClientAndIsReentrant() throws Exception {
        ZkLock one = lock(first);
        ZkLock two = lock(second);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch acquired = new CountDownLatch(1);
        one.lock();
        one.lock();
        try {
            Future<?> task = executor.submit(() -> { two.lock(); try { acquired.countDown(); } finally { two.unlock(); } });
            assertFalse(acquired.await(100, TimeUnit.MILLISECONDS));
            one.unlock();
            assertFalse(acquired.await(100, TimeUnit.MILLISECONDS));
            one.unlock();
            assertTrue(acquired.await(3, TimeUnit.SECONDS));
            task.get(3, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }

    @Test(timeout = 10000)
    public void discoveryTracksInitialNodesRemovalAndRecreation() throws Exception {
        String path = "/simplerpc/example.Api";
        first.createEphemeral(path + "/127.0.0.1:9001");
        RouteStrategy routes = new RouteStrategy();
        ReflectionTestUtils.setField(routes, "zkClientService", second);
        ReflectionTestUtils.setField(routes, "nettyClientConfig", new NettyClientConfig());
        ZkClientInit discovery = new ZkClientInit();
        ReflectionTestUtils.setField(discovery, "zkClientService", second);
        ReflectionTestUtils.setField(discovery, "routeStrategy", routes);
        discovery.init();
        await(() -> routes.getMapAddress().containsKey("example.Api") && routes.getMapAddress().get("example.Api").size() == 1);
        first.deleteRecursive(path);
        await(() -> !routes.getMapAddress().containsKey("example.Api") || routes.getMapAddress().get("example.Api").isEmpty());
        first.createEphemeral(path + "/127.0.0.1:9002");
        await(() -> routes.getMapAddress().containsKey("example.Api") && routes.getMapAddress().get("example.Api").size() == 1
                && "9002".equals(routes.getMapAddress().get("example.Api").get(0)[1]));
    }

    @Test
    public void createFailureIsNotReportedAsSuccessfulLock() {
        first.createEphemeral("/collision");
        assertThrows(SimpleRpcException.class, () -> second.createEphemeral("/collision"));
        first.createPersistent("/persistent");
        second.createPersistent("/persistent");
        assertEquals(Collections.emptyList(), second.getNodes("/missing"));
        first.createPersistent("/utf8", "中文");
        assertEquals("中文", second.readNode("/utf8"));
        second.updateNode("/utf8", "修改");
        assertEquals("修改", first.readNode("/utf8"));
    }

    @Test
    public void disabledRegistryCanBeClosedAndCannotAcquireLock() {
        ZkClientService disabled = new ZkClientService("", false);
        assertThrows(SimpleRpcException.class, () -> lock(disabled).lock());
        disabled.close();
        disabled.close();
    }

    @Test(timeout = 15000)
    public void registrationRecoversAfterSessionExpirationAndUnregistersOnClose() throws Exception {
        RpcServerHandler handler = new RpcServerHandler();
        handler.interfaceApi.add(RpcServerHandlerTest.Api.class);
        ZkRegisterInit registration = new ZkRegisterInit();
        ReflectionTestUtils.setField(registration, "zkClientService", first);
        ReflectionTestUtils.setField(registration, "rpcServerHandler", handler);
        String path = "/simplerpc/" + RpcServerHandlerTest.Api.class.getName() + "/" + IpUtils.getLocalIpAddr() + ":9001";
        try {
            registration.init(9001);
            assertTrue(second.exists(path));
            long originalSession = first.getClient().getZookeeperClient().getZooKeeper().getSessionId();
            KillSession.kill(first.getClient().getZookeeperClient().getZooKeeper(), server.getConnectString());
            await(() -> {
                try {
                    return first.getClient().getZookeeperClient().getZooKeeper().getSessionId() != originalSession && second.exists(path);
                } catch (Exception e) { return false; }
            });
        } finally { registration.close(); }
        assertFalse(second.exists(path));
        registration.close();
    }

    @Test(timeout = 10000)
    public void interruptedLockWaitPreservesInterruptFlag() throws Exception {
        ZkLock held = lock(first);
        ZkLock waiting = lock(second);
        CountDownLatch finished = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean interrupted = new java.util.concurrent.atomic.AtomicBoolean();
        Thread waiter = new Thread(() -> {
            try {
                waiting.lock();
                waiting.unlock();
            } catch (SimpleRpcException e) {
                interrupted.set(Thread.currentThread().isInterrupted());
            } finally { finished.countDown(); }
        });
        held.lock();
        try {
            waiter.start();
            await(() -> first.getNodes("/simplerpc/zklock/lock").size() == 2);
            waiter.interrupt();
            assertTrue(finished.await(3, TimeUnit.SECONDS));
            assertTrue(interrupted.get());
        } finally {
            held.unlock();
            waiter.interrupt();
            waiter.join(3000);
        }
    }
}
