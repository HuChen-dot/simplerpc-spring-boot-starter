package org.hu.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


/**
 * @Author: hu.chen
 * @Description: Netty 配置类
 * @DateTime: 2021/12/26 6:39 PM
 **/
//此注解用来开启下面的@ConfigurationProperties注解，
@EnableConfigurationProperties(NettyServerConfig.class)
//用来读取配置文件中的值，给类的属性自动赋值
@ConfigurationProperties(prefix = "simplerpc.netty.server")
public class NettyServerConfig {

    /**
     * 端口号
     */
    private int port=9091;

    /**
     * 是否运行服务端
     */
    private boolean isrun=true;


    /**
     * 设置日志打印级别
     */
    private String loglevel="info";


    public boolean isIsrun() {
        return isrun;
    }

    public void setIsrun(boolean isrun) {
        this.isrun = isrun;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getLoglevel() {
        return loglevel;
    }

    public void setLoglevel(String loglevel) {
        this.loglevel = loglevel;
    }
}
