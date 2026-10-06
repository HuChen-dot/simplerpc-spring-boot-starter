package org.hu.rpcintegration;

import org.hu.rpc.config.BeanConfiguration;
import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.config.RegistryConfiguration;
import org.hu.rpc.core.route.RouteStrategy;
import org.hu.rpc.RpcStartMain;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.register.redis.RedisRegistry;
import org.hu.rpc.register.zk.util.ZkClientService;
import org.hu.rpc.util.IpUtils;
import org.junit.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.Jedis;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;

public class RedisRegistryIntegrationTest {
    private static RedisTestServer server;
    private final List<RedisRegistry> registries = new ArrayList<>();
    private static final String SERVICE = RpcIntegrationTest.EchoApi.class.getName();

    @BeforeClass public static void startServer() throws Exception { server = new RedisTestServer(); }
    @AfterClass public static void stopServer() throws Exception { if (server != null) { server.close(); } }
    @Before public void clearTestDatabase() { try (Jedis client = server.client()) { client.flushDB(); } }
    @After public void closeRegistries() { registries.forEach(RedisRegistry::close); }

    private RedisRegistry registry() {
        RegistryConfiguration config = new RegistryConfiguration();
        config.setType("redis");
        config.setAddress(server.address());
        config.setRedisLeaseMillis(600);
        RedisRegistry registry = new RedisRegistry(config);
        registries.add(registry);
        registry.init();
        return registry;
    }

    private void stopHeartbeat(RedisRegistry registry) {
        ((ScheduledExecutorService) ReflectionTestUtils.getField(registry, "heartbeat")).shutdownNow();
    }

    private void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < end) { Thread.sleep(20); }
        assertTrue("Redis state did not converge", condition.getAsBoolean());
    }

    @Test(timeout = 10000)
    public void multipleProvidersRenewAndUnregisterIndependently() throws Exception {
        RedisRegistry one = registry();
        RedisRegistry two = registry();
        RedisRegistry consumer = registry();
        one.register(9001, Arrays.asList(RpcIntegrationTest.EchoApi.class, RpcServerHandlerTest.OtherApi.class));
        two.register(9002, Collections.singletonList(RpcIntegrationTest.EchoApi.class));
        assertEquals(2, consumer.discover(SERVICE).size());
        assertEquals(1, consumer.discover(RpcServerHandlerTest.OtherApi.class.getName()).size());
        Thread.sleep(1300); // 超过两个完整租约周期，验证续期而非初始 TTL。
        assertEquals(2, consumer.discover(SERVICE).size());
        one.close();
        assertEquals("9002", consumer.discover(SERVICE).get(0)[1]);
        two.close();
        assertTrue(consumer.discover(SERVICE).isEmpty());
    }

    @Test(timeout = 10000)
    public void expiredProvidersAreRemovedFromDiscoveryAndIndex() throws Exception {
        RedisRegistry live = registry();
        RedisRegistry expired = registry();
        live.register(9001, Collections.singletonList(RpcIntegrationTest.EchoApi.class));
        expired.register(9002, Collections.singletonList(RpcIntegrationTest.EchoApi.class));
        stopHeartbeat(expired);
        await(() -> live.discover(SERVICE).size() == 1);
        assertEquals("9001", live.discover(SERVICE).get(0)[1]);
        try (Jedis client = server.client()) {
            assertFalse(client.sismember("simplerpc:registry:" + SERVICE + ":addresses", IpUtils.getLocalIpAddr() + ":9002"));
        }
    }

    @Test(timeout = 10000)
    public void oldOwnerCannotDeleteReplacementAfterLeaseExpires() throws Exception {
        RedisRegistry old = registry();
        old.register(9001, Collections.singletonList(RpcIntegrationTest.EchoApi.class));
        stopHeartbeat(old);
        RedisRegistry replacement = registry();
        await(() -> replacement.discover(SERVICE).isEmpty());
        replacement.register(9001, Collections.singletonList(RpcIntegrationTest.EchoApi.class));
        ReflectionTestUtils.invokeMethod(old, "renew");
        old.close();
        assertEquals(1, replacement.discover(SERVICE).size());
    }

    @Test(timeout = 10000)
    public void addressIndexExpiresEvenWithoutAnyConsumer() throws Exception {
        RedisRegistry provider = registry();
        provider.register(9001, Collections.singletonList(RpcIntegrationTest.EchoApi.class));
        stopHeartbeat(provider);
        String index = "simplerpc:registry:" + SERVICE + ":addresses";
        await(() -> {
            try (Jedis client = server.client()) { return !client.exists(index); }
        });
    }

    @Test
    public void duplicateLiveProviderIsRejectedWithoutDeletingOriginal() {
        RedisRegistry first = registry();
        RedisRegistry duplicate = registry();
        first.register(9001, Collections.singletonList(RpcIntegrationTest.EchoApi.class));
        assertThrows(SimpleRpcException.class, () -> duplicate.register(9001, Collections.singletonList(RpcIntegrationTest.EchoApi.class)));
        assertEquals(1, first.discover(SERVICE).size());
    }

    @Test(timeout = 10000)
    public void providerRecoversAfterRedisRestart() throws Exception {
        RedisRegistry provider = registry();
        provider.register(9001, Collections.singletonList(RpcIntegrationTest.EchoApi.class));
        assertEquals(1, provider.discover(SERVICE).size());
        server.restart();
        await(() -> provider.discover(SERVICE).size() == 1);
    }

    @Test
    public void responseMetricsAreRecordedOnlyForLiveProviders() {
        RedisRegistry provider = registry();
        provider.register(9001, Collections.singletonList(RpcIntegrationTest.EchoApi.class));
        String[] endpoint = provider.discover(SERVICE).get(0);
        provider.recordResponseTime(SERVICE, endpoint, 8);
        assertTrue(provider.readResponseTime(SERVICE, endpoint).startsWith("8&"));
        provider.recordResponseTime("missing", endpoint, 8);
        assertNull(provider.readResponseTime("missing", endpoint));
    }

    private AnnotationConfigApplicationContext context(boolean provider) throws Exception {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
        Map<String, Object> properties = new HashMap<>();
        properties.put("simplerpc.registry.type", "redis");
        properties.put("simplerpc.registry.address", server.address());
        properties.put("simplerpc.registry.redis-lease-millis", "600");
        properties.put("simplerpc.server.port", "0");
        properties.put("simplerpc.server.enabled", String.valueOf(provider));
        properties.put("simplerpc.consumer.load-balancing", "response-time");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(BeanConfiguration.class);
        if (provider) { context.registerBean("echoService", RpcIntegrationTest.EchoService.class); }
        else { context.registerBean("consumer", RpcIntegrationTest.Consumer.class); }
        try {
            context.refresh();
            context.getBean(RpcStartMain.class).run();
            return context;
        } catch (Exception e) { context.close(); throw e; }
    }

    @Test(timeout = 15000)
    public void springRpcUsesRedisDiscoveryAndResponseTimeWithoutStaticFallback() throws Exception {
        try (AnnotationConfigApplicationContext provider = context(true);
             AnnotationConfigApplicationContext consumer = context(false)) {
            assertFalse(provider.getBean(ZkClientService.class).isEnabled());
            RpcIntegrationTest.EchoApi proxy = consumer.getBean(RpcIntegrationTest.Consumer.class).api;
            assertEquals("redis rpc", proxy.echo("redis rpc"));
            RouteStrategy route = consumer.getBean(RouteStrategy.class);
            String[] address = route.getHostAndPort(SERVICE);
            assertNotNull(consumer.getBean(RedisRegistry.class).readResponseTime(SERVICE, address));
            NettyClientConfig config = consumer.getBean(NettyClientConfig.class);
            config.setLoadBalancing("polling");
            assertEquals("polling", proxy.echo("polling"));
            config.setLoadBalancing("random");
            assertEquals("random", proxy.echo("random"));
            config.getAddress().put(SERVICE, "127.0.0.1:1");
            provider.close();
            SimpleRpcException failure = assertThrows(SimpleRpcException.class, () -> proxy.echo("offline"));
            assertTrue(failure.getMessage().contains("没有可以提供服务"));
        }
    }
}
