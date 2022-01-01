package org.hu.rpc.core.route.loadbalancing;

import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.util.DateUtil;
import org.hu.rpc.zk.util.ZkClientUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: hu.chen
 * @Description: 根据响应时间进行负载均衡
 * @DateTime: 2021/12/30 4:37 PM
 **/
@Component
public class ResponseTimeRpcLoadBalancing implements RpcLoadBalancing {


    @Autowired
    private ZkClientUtils zkClientUtils;

    /**
     * 定义相差多长时间，忽略
     */
    private static final long time = 5000;

    @Override
    public String[] load(List<String[]> services, String path) {
        if (!zkClientUtils.isOpenzk()) {
            throw new SimpleRpcException("使用响应时间进行负载均衡，必须使用zk作为注册中心");
        }
        int i = 1000000;

        Map<Integer, List<String[]>> map = new ConcurrentHashMap<>();

        // 获取当前时间
        Date date = new Date();

        for (String[] service : services) {
            String dataStr = zkClientUtils.readNode(zkClientUtils.getNameSpace() + "/" + path + "/" + service[0] + ":" + service[1]);
            // 为空，代表是新机器加入集群，先让其执行一次
            if (dataStr == null || dataStr.indexOf("&")==-1) {
                return service;
            }
            // dataStr == 21&2021-12-28 12:33:45
            String[] split = dataStr.split("&");
            int i1 = Integer.parseInt(split[0]);
            if (i1 <= i) {
                long l = DateUtil.dateMinusDate(date, split[1]);

                // 判断当前时间和当前时间的差值，如果大于阀值，则清空，循环下一个
                if (l > time) {
                    // 使用redis 懒删除策略
                    zkClientUtils.updataNode(zkClientUtils.getNameSpace() + "/" + path + "/" + service[0] + ":" + service[1], "");
                    continue;
                }
                i = i1;
                List<String[]> list = map.get(i);
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(i, list);
                }
                list.add(service);
            }
        }

        List<String[]> strings = map.get(i);

        // 等于null 则代表，所有机器都已经超过阈值
        if (strings == null) {
            strings = services;
        }
        // 随机选择一个
        Random random = new Random();
        // 如果响应最短的有多个会从这个多个里面选择一个，只有一个的话，一致性哈希就会选择那一个
        return strings.get(Math.abs(random.nextInt()) % strings.size());

    }
}
