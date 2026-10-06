package org.hu.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * @Author: hu.chen
 * @Description: Netty 配置类
 * @DateTime: 2021/12/26 6:39 PM
 **/
@ConfigurationProperties(prefix = "simplerpc.server")
public class NettyServerConfig {

    /**
     * 端口号
     */
    private int port=9091;

    /**
     * 是否运行服务端
     */
    private boolean enabled=true;


    /**
     * 设置日志打印级别
     */
    private String logLevel="info";


    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }
}
