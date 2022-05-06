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
     * @param services 服务提供者列表
     * @param path 路径信息
     * @return
     */
    String[] load(List<String[]> services,String path);
}
