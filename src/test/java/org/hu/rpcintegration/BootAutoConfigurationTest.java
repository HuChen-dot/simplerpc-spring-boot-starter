package org.hu.rpcintegration;

import org.hu.rpc.annotation.RpcAutowired;
import org.hu.rpc.config.NettyServerConfig;
import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.core.server.NettyRpcServer;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class BootAutoConfigurationTest {
    public static class Consumer {
        @RpcAutowired RpcIntegrationTest.EchoApi service;
        boolean ready;
        @PostConstruct public void init() { ready = service != null; }
    }

    @Configuration
    @EnableAutoConfiguration
    public static class App {
        @Bean public Consumer consumer() { return new Consumer(); }
    }

    @Test
    public void starterLoadsThroughSpringFactoriesWithoutCircularReferences() {
        SpringApplication application = new SpringApplication(App.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(org.springframework.boot.Banner.Mode.OFF);
        Map<String, Object> properties = new HashMap<>();
        properties.put("simplerpc.server.enabled", "false");
        properties.put("simplerpc.server.log-level", "warn");
        properties.put("simplerpc.server.thread-pool.boss-group-size", "2");
        properties.put("simplerpc.server.thread-pool.worker-group-size", "3");
        properties.put("simplerpc.server.thread-pool.backlog", "256");
        properties.put("simplerpc.consumer.connect-timeout", "1234");
        properties.put("simplerpc.consumer.request-timeout", "2345");
        properties.put("simplerpc.consumer.load-balancing", "random");
        properties.put("spring.main.allow-circular-references", "false");
        application.setDefaultProperties(properties);
        try (ConfigurableApplicationContext context = application.run()) {
            assertTrue(context.getBean(Consumer.class).ready);
            assertFalse(context.getBean(NettyServerConfig.class).isEnabled());
            assertEquals("warn", context.getBean(NettyServerConfig.class).getLogLevel());
            NettyRpcServer server = context.getBean(NettyRpcServer.class);
            assertEquals(2, server.getBossGroupSize());
            assertEquals(3, server.getWorkerGroupSize());
            assertEquals(256, server.getBacklog());
            NettyClientConfig client = context.getBean(NettyClientConfig.class);
            assertEquals(Integer.valueOf(1234), client.getConnectTimeout());
            assertEquals(Integer.valueOf(2345), client.getRequestTimeout());
            assertEquals("random", client.getLoadBalancing());
            assertNull(ReflectionTestUtils.getField(context.getBean(NettyRpcServer.class), "serverChannel"));
        }
    }
}
