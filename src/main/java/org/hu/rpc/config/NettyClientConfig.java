package org.hu.rpc.config;

import org.hu.rpc.core.route.loadbalancing.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: hu.chen
 * @Description: Netty 配置类
 * @DateTime: 2021/12/26 6:39 PM
 **/
//此注解用来开启下面的@ConfigurationProperties注解，
@EnableConfigurationProperties(NettyClientConfig.class)
//用来读取配置文件中的值，给类的属性自动赋值
@ConfigurationProperties(prefix = "simplerpc.netty.client")
public class NettyClientConfig {

    /**
     * netty 默认连接地址
     * 下面这段配置代表：人事部:订单服务//ip:端口号和ip:端口号|人事部下的用户服务//ip:端口号和ip:端口号,电商部:订单服务//ip:端口号和ip:端口号|电商部下的用户服务//ip:端口号和ip:端口号
     * ministry_of_personne:order//127.0.0.1:8091&127.0.0.1:8092|user//127.0.0.1:8091&127.0.0.1:8092,electronic:order//127.0.0.1:8091&127.0.0.1:8092|user//127.0.0.1:8091&127.0.0.1:8092
     */
    private Map<String,String> address=new ConcurrentHashMap<>();

    /**
     * 客户端连接超时时间
     */
    private Integer connecttimeout = 3000;

    /**
     * 负载均衡策略
     */
    private String loadbalancing = LoadBalancingConst.POLLING;


    private List<String[]> arrayAddress = null;


    public Integer getConnecttimeout() {
        return connecttimeout;
    }

    public void setConnecttimeout(Integer connecttimeout) {
        this.connecttimeout = connecttimeout;
    }


    public Map<String, String> getAddress() {
        return address;
    }

    public void setAddress(Map<String, String> address) {
        this.address = address;
    }

    public String getLoadbalancing() {
        return loadbalancing;
    }

    public void setLoadbalancing(String loadbalancing) {
        this.loadbalancing = loadbalancing;
    }

    public List<String[]> getArrayAddress() {
        return arrayAddress;
    }

    public void setArrayAddress(List<String[]> arrayAddress) {
        this.arrayAddress = arrayAddress;
    }


}
