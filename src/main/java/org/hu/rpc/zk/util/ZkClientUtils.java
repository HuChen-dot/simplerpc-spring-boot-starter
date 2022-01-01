package org.hu.rpc.zk.util;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/29 10:15 AM
 **/
@Component
@EnableConfigurationProperties(ZkClientUtils.class)
@ConfigurationProperties(prefix = "simplerpc.netty.zk")
public class ZkClientUtils {

    private static Logger log = LoggerFactory.getLogger(ZkClientUtils.class);


    private  CuratorFramework client;

    private String address ="127.0.0.1:2181";

    /**
     * 是否使用zk注册中心,默认关闭
     */
    private boolean openzk=false;

    /**
     * 服务注册的根节点
     */
    private String nameSpace="/simplerpc";


    public String getNameSpace() {
        return nameSpace;
    }

    public void setNamespace(String namespace) {
        this.nameSpace = namespace;
    }

    public boolean isOpenzk() {
        return openzk;
    }

    public void setOpenzk(boolean openzk) {
        this.openzk = openzk;
    }

    @PostConstruct
    public void init(){
        // 如果为false代表不使用zk注册中心
        if(!openzk){
            return;
        }
        /**
         * 连接会话
         * 创建一个zkClient实例就可以完成会话的连接
         * serverstring:连接的地址：ip:端口号
         */
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
        client = CuratorFrameworkFactory.builder()
                .connectString(address)
                // 连接失败重连策略
                .retryPolicy(retryPolicy)
                .build();
        client.start();
    }

    /**
     * 创建持久节点，同时递归创建子节点
     */
    public  void createPersistent(String path) {
        // creatingParentsIfNeeded 代表递归创建节点
        try {
            client.create().creatingParentsIfNeeded().forPath(path);
        } catch (Exception e) {
            log.error("创建持久节点失败：{}",e);
        }
    }

    /**
     * 创建持久节点，并给节点添加内容
     *
     * @param path
     * @param data
     */
    public  void createPersistent(String path, String data) {
        // creatingParentsIfNeeded 代表递归创建节点
        try {
            client.create().creatingParentsIfNeeded().forPath(path, data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("创建持久节点并添加节点内容失败：{}",e);
        }
    }

    /**
     * 创建持久顺序节点，并给节点添加内容
     *
     * @param path
     * @param data
     */
    public  void createPersistentSequential(String path, String data){
        try {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT_SEQUENTIAL).forPath(path, data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("创建持久顺序节点并添加节点内容失败：{}",e);
        }
    }


    /**
     * 创建临时节点
     *
     * @param path 节点名称
     */
    public  void createEphemeral(String path) {
        try {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path);
        } catch (Exception e) {
            log.error("创建临时节点失败：{}",e);
        }
    }

    /**
     * 创建临时节点，并给节点添加内容
     *
     * @param path
     * @param data
     */
    public  void createEphemeral(String path, String data) {
        try {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(path, data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("创建临时节点并给节点添加内容失败：{}",e);
        }
    }

    /**
     * 创建临时顺序节点，并给节点添加内容
     *
     * @param path
     * @param data
     */
    public  void createEphemeralSequential(String path, String data) {
        try {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(path, data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("创建临时顺序节点并添加节点内容失败：{}",e);
        }
    }

    /**
     * 删除节点：删除节点
     */
    public  void delete(String path) {
        try {
            client.delete().forPath(path);
        } catch (Exception e) {
            log.error("删除节点失败：{}",e);
        }
    }

    /**
     * 删除节点：递归删除节点，先删除该节点下的子节点，然后在删除该节点
     */
    public  void deleteRecursive(String path) {
        try {
            client.delete().deletingChildrenIfNeeded().forPath(path);
        } catch (Exception e) {
            log.error("递归删除节点失败：{}",e);
        }
    }

    /**
     * 删除节点并指定版本：递归删除节点，先删除该节点下的子节点，然后在删除该节点
     */
    public  void deleteRecursive(String path, int version) {
        try {
            client.delete().deletingChildrenIfNeeded().withVersion(version).forPath(path);
        } catch (Exception e) {
            log.error("删除节点并指定版本失败：{}",e);
        }
    }

    /**
     * 获取某节点的子节点列表
     */
    public  List<String> getNodes(String path) {
        List<String> nodes = null;
        try {
            nodes = client.getChildren().forPath(path);
        } catch (Exception e) {
            log.error("获取某节点的子节点列表失败：{}",e);
        }
        return nodes;
    }

    /**
     * 给某个节点添加事件监听，当此节点的子节点列表发生变化时，会触发里面的 childEvent() 方法
     * 注意：原生的zkAPI的监听是一次性的,在监听触发后之前注册的监听就会失效，所以需要重新注册
     * 但是 ZkClient 实现了 反复注册监听的功能，所以再触发监听后不需要在重新注册

     */
    public  void addNodeListener(String path, PathChildrenCacheListener listener) {

        // PathChildrenCache
        PathChildrenCache cache = new PathChildrenCache(client, path, true);

        /**
         * Normal 初始化为空
         * BUILD_INITIAL_CACHE 方法 return 之前 调用一个 rebuild 操作
         * POST_INITIALIZED_EVENT cache 初始化后发出一个事件
         */
        try {
            cache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);
        } catch (Exception e) {
            log.error("给节点添加事件失败：{}",e);
        }
        // 添加事件监听器
        cache.getListenable().addListener(listener);
    }

    /**
     * 判断节点是否存在
     *
     * @param path
     * @return
     */
    public  boolean exists(String path) {

        Stat stat = null;
        try {
            stat = client.checkExists().forPath(path);
        } catch (Exception e) {
            log.error("判断节点是否存在失败：{}",e);
        }

        return stat != null;
    }

    /**
     * 读取节点内容
     *
     * @return
     */
    public  String readNode(String path) {
        byte[] bytes = new byte[0];
        try {
            bytes = client.getData().forPath(path);
        } catch (Exception e) {
            log.error("读取节点内容失败：{}",e);
        }
        return new String(bytes);
    }

    /**
     * 更改节点内容
     *
     * @param path
     * @param data
     * @throws Exception
     */
    public  void updataNode(String path, String data) {

        try {
            client.setData().forPath(path, data.getBytes());
        } catch (Exception e) {
            log.error("更改节点内容失败：{}",e);
        }
    }

    /**
     * 关闭客户端连接
     */
    public void close(){
        client.close();
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

}
