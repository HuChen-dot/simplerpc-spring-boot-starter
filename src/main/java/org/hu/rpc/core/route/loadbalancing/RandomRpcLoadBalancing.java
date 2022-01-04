package org.hu.rpc.core.route.loadbalancing;

import org.springframework.stereotype.Component;

import java.util.List;

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
        int i = (int) (System.currentTimeMillis() % services.size());
        return services.get(i);
    }
}
