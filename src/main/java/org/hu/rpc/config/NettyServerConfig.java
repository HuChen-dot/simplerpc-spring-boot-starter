package org.hu.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;


/**
 * @Author: hu.chen
 * @Description: Netty 配置类
 * @DateTime: 2021/12/26 6:39 PM
 **/
@EnableConfigurationProperties(NettyServerConfig.class)
@ConfigurationProperties(prefix = "simplerpc.server")
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
