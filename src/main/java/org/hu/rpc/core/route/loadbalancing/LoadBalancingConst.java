package org.hu.rpc.core.route.loadbalancing;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/27 9:17 PM
 **/
public class LoadBalancingConst {


    /**
     * 轮询的
     */
    public static final String POLLING="polling";
    /**
     * 随机的
     */
    public static final String RANDOM="random";

    /**
     * 根据响应时间进行负载均衡
     */
    public static final String RESPONSE_TIME="response_time";
}
