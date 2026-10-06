package org.hu.rpcintegration;

import org.hu.rpc.config.RegistryConfiguration;
import org.hu.rpc.config.BeanConfiguration;
import org.hu.rpc.register.redis.RedisRegistry;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.properties.bind.UnboundConfigurationPropertiesException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Collections;

import static org.junit.Assert.*;

public class RegistryConfigurationTest {
    @Test
    public void defaultTypeDisablesAllRegistries() {
        RegistryConfiguration config = new RegistryConfiguration();
        assertEquals("none", config.getType());
        assertFalse(config.isZk());
        assertFalse(config.isRedis());
        assertNull(config.getAddress());
        assertFalse(config.zkClientService().isEnabled());
    }

    @Test
    public void typeSelectsOneRegistryAndItsDefaultAddress() {
        RegistryConfiguration config = new RegistryConfiguration();
        config.setType("REDIS");
        assertEquals("redis", config.getType());
        assertFalse(config.zkClientService().isEnabled());
        assertEquals("redis://127.0.0.1:6379/0", config.getAddress());
        config.setType("none");
        assertFalse(config.isZk());
        assertFalse(config.isRedis());
        config.setType("zk");
        assertTrue(config.isZk());
        assertTrue(config.zkClientService().isEnabled());
        assertFalse(config.isRedis());
        assertEquals("127.0.0.1:2181", config.getAddress());
    }

    @Test
    public void disabledRedisCreatesNoConnectionOrHeartbeat() {
        RedisRegistry registry = new RedisRegistry(new RegistryConfiguration());
        registry.init();
        assertNull(ReflectionTestUtils.getField(registry, "pool"));
        assertNull(ReflectionTestUtils.getField(registry, "heartbeat"));
        registry.close();
    }

    @Test
    public void invalidRegistryTypeAndLeaseAreRejected() {
        RegistryConfiguration config = new RegistryConfiguration();
        assertThrows(IllegalArgumentException.class, () -> config.setType("unknown"));
        assertThrows(IllegalArgumentException.class, () -> config.setType(null));
        assertThrows(IllegalArgumentException.class, () -> config.setRedisLeaseMillis(299));
        config.setRedisLeaseMillis(300);
        assertEquals(300, config.getRedisLeaseMillis());
    }

    @Test
    public void unsupportedRegistryPropertiesFailBinding() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("unsupported",
                    Collections.singletonMap("simplerpc.registry.unsupported", "true")));
            context.register(BeanConfiguration.class);
            BeansException failure = assertThrows(BeansException.class, context::refresh);
            Throwable cause = failure;
            while (cause.getCause() != null) { cause = cause.getCause(); }
            assertTrue(cause instanceof UnboundConfigurationPropertiesException);
        }
    }
}
