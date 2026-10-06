package org.hu.rpc.config;

import org.hu.rpc.core.route.loadbalancing.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: hu.chen
 * @Description: Netty 配置类
 * @DateTime: 2021/12/26 6:39 PM
 **/
@ConfigurationProperties(prefix = "simplerpc.consumer")
public class NettyClientConfig {

    /**
     * netty 默认连接地址
     */
    private Map<String,String> address=new ConcurrentHashMap<>();

    /**
     * 客户端连接超时时间
     */
    private Integer connectTimeout = 3000;

    /** RPC 响应超时时间（毫秒）。 */
    private Integer requestTimeout = 3000;

    /**
     * 负载均衡策略
     */
    private String loadBalancing = LoadBalancingConst.POLLING;

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Integer connectTimeout) {
        if (connectTimeout == null || connectTimeout <= 0) {
            throw new IllegalArgumentException("connectTimeout 必须大于 0");
        }
        this.connectTimeout = connectTimeout;
    }

    public Integer getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Integer requestTimeout) {
        if (requestTimeout == null || requestTimeout <= 0) {
            throw new IllegalArgumentException("requestTimeout 必须大于 0");
        }
        this.requestTimeout = requestTimeout;
    }

    public Map<String, String> getAddress() {
        return address;
    }

    public void setAddress(Map<String, String> address) {
        this.address = address;
    }

    public String getLoadBalancing() {
        return loadBalancing;
    }

    public void setLoadBalancing(String loadBalancing) {
        this.loadBalancing = loadBalancing;
    }

}
