package org.hu.rpc.config;

import org.hu.rpc.register.zk.util.ZkClientService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: hu.chen
 * @Description: 注册中心配置
 * @DateTime: 2022/1/6 10:24 AM
 **/
@Configuration
@EnableConfigurationProperties(RegistryConfiguration.class)
@ConfigurationProperties(prefix = "simplerpc.registry")
public class RegistryConfiguration {

    private String address = "127.0.0.1:2181";
    /**
     * 是否使用zk注册中心,默认关闭
     */
    private boolean openzk = false;

    @Bean
    public ZkClientService zkClientService() {
        return new ZkClientService(address, openzk);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isOpenzk() {
        return openzk;
    }

    public void setOpenzk(boolean openzk) {
        this.openzk = openzk;
    }
}
