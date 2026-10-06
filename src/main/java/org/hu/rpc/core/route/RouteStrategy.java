package org.hu.rpc.core.route;

import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.core.route.loadbalancing.*;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.register.zk.util.ZkClientService;
import org.hu.rpc.register.redis.RedisRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RouteStrategy {
    @Autowired
    private NettyClientConfig nettyClientConfig;
    @Autowired
    private ZkClientService zkClientService;
    @Autowired
    private RedisRegistry redisRegistry;
    @Autowired
    private DefaultRpcLoadBalancing polling;
    @Autowired
    private RandomRpcLoadBalancing random;
    @Autowired
    private ResponseTimeRpcLoadBalancing responseTime;
    private final Map<String, List<String[]>> mapAddress = new ConcurrentHashMap<>();

    public String[] getHostAndPort(String path) {
        List<String[]> services = mapAddress.get(path);
        if (redisRegistry.isEnabled()) {
            services = redisRegistry.discover(path);
            mapAddress.put(path, services);
        } else if ((services == null || services.isEmpty()) && !zkClientService.isEnabled()) {
            String address = nettyClientConfig.getAddress().get(path);
            if (address != null) {
                services = new ArrayList<>();
                for (String server : address.split("&", -1)) {
                    services.add(parseAddress(server));
                }
                services = Collections.unmodifiableList(services);
                mapAddress.put(path, services);
            }
        }
        if (services == null || services.isEmpty()) {
            throw new SimpleRpcException("没有可以提供服务的服务者：" + path);
        }
        RpcLoadBalancing strategy = polling;
        if (LoadBalancingConst.RANDOM.equals(nettyClientConfig.getLoadBalancing())) {
            strategy = random;
        } else if (LoadBalancingConst.RESPONSE_TIME.equals(nettyClientConfig.getLoadBalancing())) {
            strategy = responseTime;
        }
        return strategy.load(services, path);
    }

    /** 支持 host:port 和 [IPv6]:port，拒绝残缺地址。 */
    public static String[] parseAddress(String address) {
        String value = address == null ? "" : address.trim();
        int separator = value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new SimpleRpcException("无效的 RPC 服务地址：" + address);
        }
        String host = value.substring(0, separator);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        } else if (host.indexOf(':') >= 0) {
            throw new SimpleRpcException("IPv6 地址必须使用 [host]:port：" + address);
        }
        try {
            int port = Integer.parseInt(value.substring(separator + 1));
            if (host.isEmpty() || port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
            return new String[]{host, String.valueOf(port)};
        } catch (NumberFormatException e) {
            throw new SimpleRpcException("无效的 RPC 服务地址：" + address, e);
        }
    }

    public Map<String, List<String[]>> getMapAddress() { return mapAddress; }

    public void recordResponseTime(String path, String[] address, long elapsedMillis) {
        if (LoadBalancingConst.RESPONSE_TIME.equals(nettyClientConfig.getLoadBalancing())
                && (zkClientService.isEnabled() || (redisRegistry.isEnabled()))) {
            responseTime.record(path, address, elapsedMillis);
        }
    }

}
