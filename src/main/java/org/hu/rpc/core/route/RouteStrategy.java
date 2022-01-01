package org.hu.rpc.core.route;

import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.core.route.loadbalancing.*;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.util.BeanUtils;
import org.hu.rpc.zk.util.ZkClientUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: hu.chen
 * @Description: 路由策略
 * @DateTime: 2021/12/31 5:22 PM
 **/
@Component
public class RouteStrategy {


    @Autowired
    private NettyClientConfig nettyClientConfig;

    private Map<String, List<String[]>> mapAddress = new ConcurrentHashMap<>();


    @Autowired
    private ZkClientUtils zkClientUtils;

    public String[] getHostAndPort(String path) {
        if (mapAddress.size() == 0) {
            // 如果为空，并且zk注册中心是启用状态，则代表还没有可以使用的服务
            if (zkClientUtils.isOpenzk()) {
                throw new SimpleRpcException("没有可以使用的服务");
            }
            synchronized (this) {
                if (mapAddress.size() == 0) {

                    // 获取用户的配置
                    Map<String, String> address = nettyClientConfig.getAddress();

                    Set<Map.Entry<String, String>> entries = address.entrySet();

                    for (Map.Entry<String, String> entry : entries) {
                        String value = entry.getValue();
                        String[] split = value.split("&");

                        List<String[]> list = new ArrayList<>();
                        for (String server : split) {
                            list.add(server.split(":"));
                        }
                        mapAddress.put(entry.getKey(), list);
                    }
                }
            }
        }
        List<String[]> services = mapAddress.get(path);
        if (services == null || services.size() == 0) {
            throw new SimpleRpcException("没有可以提供服务的服务者");
        }
        RpcLoadBalancing rpcLoadBalancing = getRpcLoadBalancing();
        return rpcLoadBalancing.load(services, path);
    }


    /**
     * 简单工厂模式获取负载均衡实现
     *
     * @return
     */
    private RpcLoadBalancing getRpcLoadBalancing() {
        switch (nettyClientConfig.getLoadbalancing()) {
            case LoadBalancingConst.POLLING: {

                return BeanUtils.getBean(DefaultRpcLoadBalancing.class);
            }
            case LoadBalancingConst.RANDOM: {

                return BeanUtils.getBean(RandomRpcLoadBalancing.class);
            }
            case LoadBalancingConst.RESPONSE_TIME: {
                return BeanUtils.getBean(ResponseTimeRpcLoadBalancing.class);
            }
            default: {
                return BeanUtils.getBean(DefaultRpcLoadBalancing.class);

            }
        }
    }

    public Map<String, List<String[]>> getMapAddress() {
        return mapAddress;
    }

    public void setMapAddress(Map<String, List<String[]>> mapAddress) {
        this.mapAddress = mapAddress;
    }


}
