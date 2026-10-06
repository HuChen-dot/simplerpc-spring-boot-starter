package org.hu.rpcintegration;

import io.netty.channel.Channel;
import org.hu.rpc.RpcStartMain;
import org.hu.rpc.annotation.RpcAutowired;
import org.hu.rpc.annotation.RpcService;
import org.hu.rpc.config.BeanConfiguration;
import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.config.NettyServerConfig;
import org.hu.rpc.core.server.NettyRpcServer;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.proxy.JdkProxy;
import org.junit.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class RpcIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private EchoApi proxy;
    public interface GenericApi<T> { T generic(T value); }
    public interface EchoApi extends GenericApi<RpcServerHandlerTest.Person> {
        String echo(String value);
        List<RpcServerHandlerTest.Person> people();
        long number();
        String json();
        void nothing();
        String slow();
        String fail();
    }
    @RpcService
    public static class EchoService implements EchoApi {
        public String echo(String value) { return value; }
        public RpcServerHandlerTest.Person generic(RpcServerHandlerTest.Person value) { return value; }
        public List<RpcServerHandlerTest.Person> people() { return Collections.singletonList(new RpcServerHandlerTest.Person("person")); }
        public long number() { return 7L; }
        public String json() { return "{\"still\":\"a string\"}"; }
        public void nothing() { }
        public String slow() {
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "slow";
        }
        public String fail() { throw new IllegalStateException("expected failure"); }
    }
    public static class ParentConsumer {
        @RpcAutowired protected EchoApi api;
    }
    public static class Consumer extends ParentConsumer {
        boolean initialized;
        @PostConstruct public void init() { initialized = api != null && Proxy.isProxyClass(api.getClass()); }
    }

    @Before
    public void setUp() throws Exception {
        context = new AnnotationConfigApplicationContext();
        context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
        Map<String, Object> properties = new HashMap<>();
        properties.put("simplerpc.server.port", "0");
        properties.put("simplerpc.server.thread-pool.worker-group-size", "2");
        properties.put("simplerpc.consumer.request-timeout", "3000");
        properties.put("simplerpc.registry.type", "none");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(BeanConfiguration.class);
        context.registerBean("echoService", EchoService.class);
        context.registerBean("consumer", Consumer.class);
        context.refresh();
        context.getBean(RpcStartMain.class).run();
        Channel channel = (Channel) ReflectionTestUtils.getField(context.getBean(NettyRpcServer.class), "serverChannel");
        int port = ((java.net.InetSocketAddress) channel.localAddress()).getPort();
        context.getBean(NettyClientConfig.class).getAddress().put(EchoApi.class.getName(), "127.0.0.1:" + port);
        proxy = context.getBean(Consumer.class).api;
    }

    @After
    public void tearDown() { if (context != null) { context.close(); } }

    @Test
    public void springBindsOneConfigBeanAndInjectsBeforePostConstruct() {
        assertEquals(1, context.getBeansOfType(NettyClientConfig.class).size());
        assertEquals(1, context.getBeansOfType(NettyServerConfig.class).size());
        assertTrue(context.getBean(Consumer.class).initialized);
        assertEquals(2, context.getBean(NettyRpcServer.class).getWorkerGroupSize());
        assertEquals("hello", proxy.echo("hello"));
    }

    @Test
    public void inheritedGenericTypesPrimitiveVoidAndJsonStringsRoundTrip() {
        assertEquals("person", proxy.generic(new RpcServerHandlerTest.Person("person")).getName());
        assertEquals("person", proxy.people().get(0).getName());
        assertEquals(7L, proxy.number());
        assertEquals("{\"still\":\"a string\"}", proxy.json());
        proxy.nothing();
        assertNull(proxy.echo(null));
    }

    @Test
    public void objectMethodsAreLocalEvenWithoutServiceAddress() {
        context.getBean(NettyClientConfig.class).getAddress().clear();
        assertTrue(proxy.equals(proxy));
        assertFalse(proxy.equals(context.getBean(JdkProxy.class).createProxy(EchoApi.class)));
        assertEquals(System.identityHashCode(proxy), proxy.hashCode());
        assertTrue(proxy.toString().contains(EchoApi.class.getName()));
    }

    @Test(timeout = 10000)
    public void largeUtf8MessagesAndConcurrentCallsRoundTrip() throws Exception {
        char[] chars = new char[400000];
        Arrays.fill(chars, '中');
        String large = new String(chars);
        assertEquals(large, proxy.echo(large));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                String value = "call-" + i;
                results.add(executor.submit(() -> proxy.echo(value)));
            }
            for (int i = 0; i < results.size(); i++) { assertEquals("call-" + i, results.get(i).get(3, TimeUnit.SECONDS)); }
        } finally { executor.shutdownNow(); }
    }

    @Test
    public void remoteBusinessErrorsReachCaller() {
        SimpleRpcException failure = assertThrows(SimpleRpcException.class, () -> proxy.fail());
        assertTrue(failure.getMessage().contains("expected failure"));
    }

    @Test(timeout = 3000)
    public void slowResponseTimesOut() {
        context.getBean(NettyClientConfig.class).setRequestTimeout(50);
        SimpleRpcException failure = assertThrows(SimpleRpcException.class, () -> proxy.slow());
        assertTrue(failure.getMessage().contains("响应超时"));
    }

    @Test
    public void bindFailureIsReportedAndPortIsReleasedOnClose() throws Exception {
        Channel channel = (Channel) ReflectionTestUtils.getField(context.getBean(NettyRpcServer.class), "serverChannel");
        int port = ((java.net.InetSocketAddress) channel.localAddress()).getPort();
        NettyRpcServer another = new NettyRpcServer();
        NettyServerConfig config = new NettyServerConfig();
        config.setPort(port);
        ReflectionTestUtils.setField(another, "nettyServerConfig", config);
        ReflectionTestUtils.setField(another, "rpcServerHandler", context.getBean(org.hu.rpc.core.server.RpcServerHandler.class));
        ReflectionTestUtils.setField(another, "zkRegisterInit", context.getBean(org.hu.rpc.register.zk.server.ZkRegisterInit.class));
        ReflectionTestUtils.setField(another, "redisRegistry", context.getBean(org.hu.rpc.register.redis.RedisRegistry.class));
        assertThrows(SimpleRpcException.class, another::start);
        context.close();
        try (java.net.ServerSocket rebound = new java.net.ServerSocket(port)) { assertEquals(port, rebound.getLocalPort()); }
    }
}
