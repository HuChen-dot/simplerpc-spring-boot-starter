package org.hu.rpcintegration;

import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.config.RegistryConfiguration;
import org.hu.rpc.register.redis.RedisRegistry;
import org.hu.rpc.core.route.RouteStrategy;
import org.hu.rpc.core.route.loadbalancing.*;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.register.zk.util.ZkClientService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.Assert.*;

public class RouteStrategyTest {
    @Test
    public void staticRoutesLoadEachServiceSeparately() {
        NettyClientConfig config = new NettyClientConfig();
        config.getAddress().put("one", "127.0.0.1:9001&127.0.0.1:9002");
        config.getAddress().put("two", "127.0.0.1:9003");
        RouteStrategy route = new RouteStrategy();
        ReflectionTestUtils.setField(route, "nettyClientConfig", config);
        ReflectionTestUtils.setField(route, "zkClientService", new ZkClientService("", false));
        ReflectionTestUtils.setField(route, "redisRegistry", new RedisRegistry(new RegistryConfiguration()));
        ReflectionTestUtils.setField(route, "polling", new DefaultRpcLoadBalancing());
        assertEquals("9001", route.getHostAndPort("one")[1]);
        assertEquals("9003", route.getHostAndPort("two")[1]);
        assertEquals("9002", route.getHostAndPort("one")[1]);
        assertThrows(SimpleRpcException.class, () -> route.getHostAndPort("missing"));
    }

    @Test
    public void addressesAndTimeoutsAreValidatedAtBoundary() {
        assertArrayEquals(new String[]{"::1", "9001"}, RouteStrategy.parseAddress("[::1]:9001"));
        for (String address : Arrays.asList("host", "host:", ":80", "host:0", "host:65536", "host:abc", "::1:80")) {
            assertThrows(address, SimpleRpcException.class, () -> RouteStrategy.parseAddress(address));
        }
        NettyClientConfig config = new NettyClientConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setConnectTimeout(0));
        assertThrows(IllegalArgumentException.class, () -> config.setRequestTimeout(-1));
        assertThrows(SimpleRpcException.class, () -> new RandomRpcLoadBalancing().load(Collections.emptyList(), "empty"));
    }

    private static class Registry extends ZkClientService {
        final Map<String, String> samples = new HashMap<>();
        Registry() { super("", true); }
        @Override public String readNode(String path) { return samples.get(path); }
        @Override public void updateNode(String path, String data) { samples.put(path, data); }
    }

    @Test
    public void responseTimeChoosesMinimumWithoutOneMillionLimit() {
        Registry registry = new Registry();
        String now = String.valueOf(System.currentTimeMillis());
        registry.samples.put("/simplerpc/api/a:1", "2000000&" + now);
        registry.samples.put("/simplerpc/api/b:2", "1500000&" + now);
        ResponseTimeRpcLoadBalancing balancing = new ResponseTimeRpcLoadBalancing();
        ReflectionTestUtils.setField(balancing, "zkClientService", registry);
        ReflectionTestUtils.setField(balancing, "redisRegistry", new RedisRegistry(new RegistryConfiguration()));
        assertEquals("b", balancing.load(Arrays.asList(new String[]{"a", "1"}, new String[]{"b", "2"}), "api")[0]);
        balancing.record("api", new String[]{"a", "1"}, 3);
        assertTrue(registry.samples.get("/simplerpc/api/a:1").startsWith("3&"));
    }

    @Test
    public void staleAndMalformedSamplesCanBeProbedWithoutMutatingRegistry() {
        Registry registry = new Registry();
        String stale = "2&" + (System.currentTimeMillis() - 10000);
        registry.samples.put("/simplerpc/api/a:1", stale);
        registry.samples.put("/simplerpc/api/b:2", "bad&sample");
        ResponseTimeRpcLoadBalancing balancing = new ResponseTimeRpcLoadBalancing();
        ReflectionTestUtils.setField(balancing, "zkClientService", registry);
        ReflectionTestUtils.setField(balancing, "redisRegistry", new RedisRegistry(new RegistryConfiguration()));
        List<String[]> services = Arrays.asList(new String[]{"a", "1"}, new String[]{"b", "2"});
        for (int i = 0; i < 20; i++) { assertTrue(services.contains(balancing.load(services, "api"))); }
        assertEquals(stale, registry.samples.get("/simplerpc/api/a:1"));
    }
}
