package org.hu.rpc.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/26 7:03 PM
 **/
@Configuration
@ComponentScan({"org.hu.rpc"})
public class BeanStart{



    @Bean
    public NettyServerConfig nettyServerConfig() {
        return new NettyServerConfig();
    }

    @Bean
    public NettyClientConfig nettyClientConfig() {
        return new NettyClientConfig();
    }


}
