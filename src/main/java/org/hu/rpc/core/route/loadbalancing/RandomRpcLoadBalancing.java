package org.hu.rpc.core.route.loadbalancing;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.hu.rpc.exception.SimpleRpcException;

/**
 * @Author: hu.chen
 * @Description: 随机的负载均衡算法
 * @DateTime: 2021/12/27 9:12 PM
 **/
@Component
public class RandomRpcLoadBalancing implements RpcLoadBalancing{

    /**
     * 随机的负载均衡
     * @param services
     * @return
     */
    @Override
    public String[] load(List<String[]> services,String path) {
        // 此处负载均衡策略为随机
        if (services == null || services.isEmpty()) {
            throw new SimpleRpcException("没有可以提供服务的服务者：" + path);
        }
        int value = ThreadLocalRandom.current().nextInt(services.size());
        return services.get(value);
    }
}
