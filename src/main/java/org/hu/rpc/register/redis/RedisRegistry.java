package org.hu.rpc.register.redis;

import org.hu.rpc.config.RegistryConfiguration;
import org.hu.rpc.core.route.RouteStrategy;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.util.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

/** 使用带所有者标识的 TTL 租约发布地址；Lua 保证续期和注销原子性。 */
public class RedisRegistry {
    private static final Logger log = LoggerFactory.getLogger(RedisRegistry.class);
    private static final String PREFIX = "simplerpc:registry:";
    private static final String REGISTER =
            "local owner = redis.call('GET', KEYS[1]); " +
            "if owner and owner ~= ARGV[1] then return 0 end; " +
            "if not owner then redis.call('DEL', KEYS[3]) end; " +
            "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[3]); " +
            "redis.call('SADD', KEYS[2], ARGV[2]); " +
            "redis.call('PEXPIRE', KEYS[2], ARGV[3] * 2); return 1";
    private static final String UNREGISTER =
            "if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end; " +
            "redis.call('DEL', KEYS[1], KEYS[3]); " +
            "redis.call('SREM', KEYS[2], ARGV[2]); return 1";
    private static final String DISCOVER =
            "local result = {}; for i = 2, #KEYS do " +
            "if redis.call('EXISTS', KEYS[i]) == 1 then table.insert(result, ARGV[i-1]) " +
            "else redis.call('SREM', KEYS[1], ARGV[i-1]) end end; return result";
    private static final String RECORD =
            "if redis.call('EXISTS', KEYS[1]) == 1 then " +
            "redis.call('SET', KEYS[2], ARGV[1], 'PX', 5000); return 1 end; return 0";

    private final RegistryConfiguration config;
    private final String owner = UUID.randomUUID().toString();
    private final Set<String> services = new LinkedHashSet<>();
    private volatile JedisPool pool;
    private ScheduledExecutorService heartbeat;
    private String address;
    private volatile boolean closed;

    public RedisRegistry(RegistryConfiguration config) { this.config = config; }
    public boolean isEnabled() { return config.isRedis(); }

    @PostConstruct
    public synchronized void init() {
        if (!isEnabled() || pool != null) { return; }
        if (closed) { throw new SimpleRpcException("Redis 注册中心已关闭"); }
        try {
            String configured = config.getAddress();
            URI uri = URI.create(configured.contains("://") ? configured : "redis://" + configured);
            if ((!"redis".equals(uri.getScheme()) && !"rediss".equals(uri.getScheme())) || uri.getHost() == null) {
                throw new IllegalArgumentException("Redis 地址必须为 host:port 或 redis[s]://host:port/db");
            }
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxWaitMillis(2000);
            poolConfig.setTestOnBorrow(true);
            pool = new JedisPool(poolConfig, uri, 2000, 2000);
            try (Jedis client = pool.getResource()) { client.ping(); }
        } catch (RuntimeException e) {
            close();
            throw new SimpleRpcException("Redis 注册中心初始化失败", e);
        }
    }

    private Jedis client() {
        JedisPool current = pool;
        if (!isEnabled() || closed || current == null) {
            throw new SimpleRpcException("Redis 注册中心未启用或已关闭");
        }
        return current.getResource();
    }

    private String leasePrefix(String service) { return PREFIX + service + ":lease:"; }
    private String index(String service) { return PREFIX + service + ":addresses"; }
    private String metric(String service, String address) { return PREFIX + service + ":response:" + address; }
    private List<String> keys(String service) {
        return Arrays.asList(leasePrefix(service) + address, index(service), metric(service, address));
    }

    public synchronized void register(int port, Collection<? extends Class<?>> interfaces) {
        if (!isEnabled()) { return; }
        if (closed) { throw new SimpleRpcException("Redis 注册中心已关闭"); }
        if (heartbeat != null) { throw new SimpleRpcException("Redis 服务已经注册"); }
        address = IpUtils.getLocalIpAddr() + ":" + port;
        RouteStrategy.parseAddress(address);
        try {
            for (Class<?> api : interfaces) {
                publish(api.getName());
                services.add(api.getName());
            }
            if (!services.isEmpty()) {
                heartbeat = Executors.newSingleThreadScheduledExecutor(task -> {
                    Thread thread = new Thread(task, "simplerpc-redis-heartbeat");
                    thread.setDaemon(true);
                    return thread;
                });
                long interval = config.getRedisLeaseMillis() / 3;
                heartbeat.scheduleWithFixedDelay(this::renew, interval, interval, TimeUnit.MILLISECONDS);
            }
        } catch (RuntimeException e) {
            close();
            throw new SimpleRpcException("Redis 服务注册失败", e);
        }
    }

    private void publish(String service) {
        try (Jedis client = client()) {
            Object result = client.eval(REGISTER, keys(service),
                    Arrays.asList(owner, address, String.valueOf(config.getRedisLeaseMillis())));
            if (!Long.valueOf(1).equals(result)) {
                throw new SimpleRpcException("服务地址已被其他进程注册：" + service + "/" + address);
            }
        }
    }

    private synchronized void renew() {
        if (closed) { return; }
        for (String service : services) {
            try { publish(service); }
            catch (RuntimeException e) { log.warn("Redis 服务续期失败：" + service, e); }
        }
    }

    public List<String[]> discover(String service) {
        try (Jedis client = client()) {
            List<String> members = new ArrayList<>(client.smembers(index(service)));
            List<String> keys = new ArrayList<>();
            keys.add(index(service));
            for (String member : members) { keys.add(leasePrefix(service) + member); }
            @SuppressWarnings("unchecked")
            List<String> addresses = (List<String>) client.eval(DISCOVER, keys, members);
            Collections.sort(addresses);
            List<String[]> result = new ArrayList<>();
            for (String address : addresses) { result.add(RouteStrategy.parseAddress(address)); }
            return Collections.unmodifiableList(result);
        } catch (RuntimeException e) {
            throw new SimpleRpcException("Redis 服务发现失败：" + service, e);
        }
    }

    public String readResponseTime(String service, String[] address) {
        try (Jedis client = client()) { return client.get(metric(service, address[0] + ":" + address[1])); }
        catch (RuntimeException e) { throw new SimpleRpcException("Redis 响应时间读取失败：" + service, e); }
    }

    public void recordResponseTime(String service, String[] address, long elapsedMillis) {
        String endpoint = address[0] + ":" + address[1];
        try (Jedis client = client()) {
            client.eval(RECORD, Arrays.asList(leasePrefix(service) + endpoint, metric(service, endpoint)),
                    Collections.singletonList(elapsedMillis + "&" + System.currentTimeMillis()));
        }
    }

    @PreDestroy
    public synchronized void close() {
        if (closed) { return; }
        closed = true;
        if (heartbeat != null) { heartbeat.shutdownNow(); }
        if (pool != null) {
            for (String service : services) {
                try (Jedis client = pool.getResource()) {
                    client.eval(UNREGISTER, keys(service), Arrays.asList(owner, address));
                } catch (RuntimeException e) { log.warn("Redis 服务注销失败，将等待租约过期：" + service, e); }
            }
            services.clear();
            pool.close();
        }
    }
}
