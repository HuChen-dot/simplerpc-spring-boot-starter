package org.hu.rpc.core.route.loadbalancing;

import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.register.zk.util.ZkClientService;
import org.hu.rpc.register.redis.RedisRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class ResponseTimeRpcLoadBalancing implements RpcLoadBalancing {
    private static final Logger log = LoggerFactory.getLogger(ResponseTimeRpcLoadBalancing.class);
    @Autowired
    private ZkClientService zkClientService;
    @Autowired
    private RedisRegistry redisRegistry;
    private static final long TIME = 5000;

    public void record(String path, String[] address, long elapsedMillis) {
        try {
            if (redisRegistry.isEnabled()) {
                redisRegistry.recordResponseTime(path, address, elapsedMillis);
                return;
            }
            zkClientService.updateNode(zkClientService.getNamespace() + "/" + path + "/" + address[0] + ":" + address[1],
                    elapsedMillis + "&" + System.currentTimeMillis());
        } catch (RuntimeException e) {
            log.debug("记录响应时间失败，保留 RPC 调用结果", e);
        }
    }

    @Override
    public String[] load(List<String[]> services, String path) {
        boolean redis = redisRegistry.isEnabled();
        if (!zkClientService.isEnabled() && !redis) {
            throw new SimpleRpcException("使用响应时间进行负载均衡，必须使用 zk 或 Redis 注册中心");
        }
        if (services == null || services.isEmpty()) {
            throw new SimpleRpcException("没有可以提供服务的服务者：" + path);
        }
        long shortest = Long.MAX_VALUE;
        List<String[]> fastest = new ArrayList<>();
        List<String[]> unknown = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String[] service : services) {
            String data = redis ? redisRegistry.readResponseTime(path, service)
                    : zkClientService.readNode(zkClientService.getNamespace() + "/" + path + "/" + service[0] + ":" + service[1]);
            try {
                String[] parts = data == null ? new String[0] : data.split("&", 2);
                long time = parts.length == 2 ? Long.parseLong(parts[0]) : -1;
                long timestamp = parts.length == 2 ? Long.parseLong(parts[1]) : -1;
                if (time < 0 || timestamp < 0 || timestamp > now || now - timestamp > TIME) {
                    unknown.add(service);
                    continue;
                }
                if (time < shortest) {
                    shortest = time;
                    fastest.clear();
                }
                if (time == shortest) {
                    fastest.add(service);
                }
            } catch (NumberFormatException e) {
                unknown.add(service);
            }
        }
        List<String[]> choices = unknown.isEmpty() ? fastest : unknown;
        return choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
    }
}
