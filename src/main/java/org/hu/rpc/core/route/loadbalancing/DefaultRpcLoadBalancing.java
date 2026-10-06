package org.hu.rpc.core.route.loadbalancing;

import org.hu.rpc.exception.SimpleRpcException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 每个服务独立进行线程安全的轮询。 */
@Component
public class DefaultRpcLoadBalancing implements RpcLoadBalancing {
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    @Override
    public String[] load(List<String[]> services, String path) {
        if (services == null || services.isEmpty()) {
            throw new SimpleRpcException("没有可以提供服务的服务者：" + path);
        }
        int count = counters.computeIfAbsent(path, key -> new AtomicInteger()).getAndIncrement();
        return services.get(Math.floorMod(count, services.size()));
    }
}
