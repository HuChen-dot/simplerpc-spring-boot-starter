package org.hu.rpc.config;

import org.hu.rpc.register.zk.util.ZkClientService;
import org.hu.rpc.register.redis.RedisRegistry;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: hu.chen
 * @Description: 注册中心配置
 * @DateTime: 2022/1/6 10:24 AM
 **/
@Configuration
@ConfigurationProperties(prefix = "simplerpc.registry", ignoreUnknownFields = false)
public class RegistryConfiguration {

    private String address;
    private String type = "none";
    private long redisLeaseMillis = 15000;

    @Bean
    public ZkClientService zkClientService() {
        return new ZkClientService(getAddress(), isZk());
    }

    @Bean
    public RedisRegistry redisRegistry() {
        return new RedisRegistry(this);
    }

    public String getAddress() {
        if (address != null) { return address; }
        if (isRedis()) { return "redis://127.0.0.1:6379/0"; }
        return isZk() ? "127.0.0.1:2181" : null;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isZk() {
        return "zk".equals(getType());
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        String value = type == null ? null : type.trim().toLowerCase(Locale.ROOT);
        if (!"none".equals(value) && !"zk".equals(value) && !"redis".equals(value)) {
            throw new IllegalArgumentException("registry.type 必须为 none、zk 或 redis");
        }
        this.type = value;
    }

    public boolean isRedis() { return "redis".equals(getType()); }
    public long getRedisLeaseMillis() { return redisLeaseMillis; }
    public void setRedisLeaseMillis(long redisLeaseMillis) {
        if (redisLeaseMillis < 300 || redisLeaseMillis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("redisLeaseMillis 必须在 300 到 2147483647 之间");
        }
        this.redisLeaseMillis = redisLeaseMillis;
    }
}
