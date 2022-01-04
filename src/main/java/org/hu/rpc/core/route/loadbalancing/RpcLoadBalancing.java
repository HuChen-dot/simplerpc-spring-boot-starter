package org.hu.rpc.core.route.loadbalancing;

import java.util.List;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/27 9:07 PM
 **/
public interface RpcLoadBalancing {


    /**
     * 负载均衡方法
     */
    String[] load(List<String[]> services,String path);
}
