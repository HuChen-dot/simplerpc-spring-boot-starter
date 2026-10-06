package org.hu.rpc.core.route;

import org.hu.rpc.core.route.loadbalancing.DefaultRpcLoadBalancing;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class LoadBalancingTest {
    @Test
    public void roundRobinSurvivesShrinkingServiceList() {
        DefaultRpcLoadBalancing loadBalancing = new DefaultRpcLoadBalancing();
        List<String[]> services = Arrays.asList(new String[]{"a", "1"}, new String[]{"b", "2"}, new String[]{"c", "3"});
        loadBalancing.load(services, "shrink");
        loadBalancing.load(services, "shrink");
        assertSame(services.get(0), loadBalancing.load(Collections.singletonList(services.get(0)), "shrink"));
    }

    @Test
    public void roundRobinIsIndependentForEachService() {
        DefaultRpcLoadBalancing loadBalancing = new DefaultRpcLoadBalancing();
        List<String[]> services = Arrays.asList(new String[]{"a", "1"}, new String[]{"b", "2"});
        assertSame(services.get(0), loadBalancing.load(services, "first"));
        assertSame(services.get(0), loadBalancing.load(services, "second"));
        assertSame(services.get(1), loadBalancing.load(services, "first"));
    }

    @Test(timeout = 10000)
    public void concurrentRoundRobinDistributesEveryCall() throws Exception {
        DefaultRpcLoadBalancing loadBalancing = new DefaultRpcLoadBalancing();
        List<String[]> services = Arrays.asList(new String[]{"a", "1"}, new String[]{"b", "2"});
        AtomicInteger first = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> {
                    for (int n = 0; n < 1000; n++) {
                        if (loadBalancing.load(services, "concurrent") == services.get(0)) {
                            first.incrementAndGet();
                        }
                    }
                    return null;
                });
            }
            for (Future<Void> result : executor.invokeAll(tasks)) {
                result.get();
            }
            assertEquals(4000, first.get());
        } finally {
            executor.shutdownNow();
        }
    }
}
