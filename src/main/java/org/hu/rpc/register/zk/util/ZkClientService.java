package org.hu.rpc.register.zk.util;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.hu.rpc.exception.SimpleRpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ZkClientService {
    private static final Logger log = LoggerFactory.getLogger(ZkClientService.class);
    private CuratorFramework client;
    private final String address;
    private final boolean enabled;
    private final String namespace = "/simplerpc";
    private final Map<String, PathChildrenCache> caches = new HashMap<>();
    private volatile boolean closed;

    public ZkClientService(String address, boolean enabled) {
        this.address = address;
        this.enabled = enabled;
    }

    @PostConstruct
    public synchronized void init() {
        if (!enabled || client != null) { return; }
        if (closed) { throw new SimpleRpcException("ZooKeeper 客户端已关闭"); }
        client = CuratorFrameworkFactory.builder().connectString(address)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3)).build();
        client.start();
        try {
            if (!client.blockUntilConnected(5, TimeUnit.SECONDS)) {
                throw new SimpleRpcException("ZooKeeper 连接超时：" + address);
            }
        } catch (InterruptedException e) {
            close();
            Thread.currentThread().interrupt();
            throw new SimpleRpcException("ZooKeeper 连接被中断", e);
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    public CuratorFramework getClient() {
        if (!enabled || client == null || closed) {
            throw new SimpleRpcException("ZooKeeper 注册中心未启用或已关闭");
        }
        return client;
    }

    private SimpleRpcException failure(String operation, String path, Exception e) {
        if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); }
        return new SimpleRpcException(operation + "失败：" + path, e);
    }

    private void create(String path, String data, CreateMode mode) {
        try {
            getClient().create().creatingParentsIfNeeded().withMode(mode)
                    .forPath(path, data.getBytes(StandardCharsets.UTF_8));
        } catch (KeeperException.NodeExistsException e) {
            if (mode != CreateMode.PERSISTENT) { throw failure("创建节点", path, e); }
        } catch (Exception e) {
            throw failure("创建节点", path, e);
        }
    }

    public void createPersistent(String path) { createPersistent(path, ""); }
    public void createPersistent(String path, String data) { create(path, data, CreateMode.PERSISTENT); }
    public void createEphemeral(String path) { createEphemeral(path, ""); }
    public void createEphemeral(String path, String data) { create(path, data, CreateMode.EPHEMERAL); }

    public void delete(String path) { deleteNode(path, false); }
    public void deleteRecursive(String path) { deleteNode(path, true); }

    private void deleteNode(String path, boolean recursive) {
        try {
            if (recursive) {
                getClient().delete().deletingChildrenIfNeeded().forPath(path);
            } else {
                getClient().delete().forPath(path);
            }
        } catch (KeeperException.NoNodeException ignored) {
            // 删除一个已经离线的节点是幂等操作。
        } catch (Exception e) {
            throw failure("删除节点", path, e);
        }
    }

    public List<String> getNodes(String path) {
        try {
            return getClient().getChildren().forPath(path);
        } catch (KeeperException.NoNodeException ignored) {
            return Collections.emptyList();
        } catch (Exception e) {
            throw failure("获取子节点", path, e);
        }
    }

    public synchronized void addNodeListener(String path, PathChildrenCacheListener listener) {
        PathChildrenCache existing = caches.get(path);
        if (existing != null) {
            existing.getListenable().addListener(listener);
            return;
        }
        PathChildrenCache cache = new PathChildrenCache(getClient(), path, true);
        cache.getListenable().addListener(listener);
        try {
            cache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
            caches.put(path, cache);
        } catch (Exception e) {
            try { cache.close(); } catch (IOException closeError) { e.addSuppressed(closeError); }
            throw failure("添加节点监听", path, e);
        }
    }

    public boolean exists(String path) {
        try { return getClient().checkExists().forPath(path) != null; }
        catch (Exception e) { throw failure("检查节点", path, e); }
    }

    public String readNode(String path) {
        try { return new String(getClient().getData().forPath(path), StandardCharsets.UTF_8); }
        catch (KeeperException.NoNodeException ignored) { return null; }
        catch (Exception e) { throw failure("读取节点", path, e); }
    }

    public void updateNode(String path, String data) {
        try { getClient().setData().forPath(path, data.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw failure("更新节点", path, e); }
    }

    @PreDestroy
    public synchronized void close() {
        if (closed) { return; }
        closed = true;
        for (PathChildrenCache cache : caches.values()) {
            try { cache.close(); } catch (IOException e) { log.warn("关闭 ZooKeeper 缓存失败", e); }
        }
        caches.clear();
        if (client != null) { client.close(); }
    }

    public String getNamespace() { return namespace; }
    public boolean isEnabled() { return enabled; }
}
