package org.hu.rpc.register.zk.server;

import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.zookeeper.data.Stat;
import org.hu.rpc.core.server.RpcServerHandler;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.util.IpUtils;
import org.hu.rpc.register.zk.util.ZkClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Component
public class ZkRegisterInit {
    private static final Logger log = LoggerFactory.getLogger(ZkRegisterInit.class);
    @Autowired
    private ZkClientService zkClientService;
    @Autowired
    private RpcServerHandler rpcServerHandler;
    private final List<String> registeredPaths = new ArrayList<>();
    private int port;
    private boolean closed;
    private ConnectionStateListener reconnectListener;

    public synchronized void init(int port) {
        if (!zkClientService.isEnabled() || closed) { return; }
        this.port = port;
        if (reconnectListener == null) {
            reconnectListener = (client, state) -> {
                if (state == ConnectionState.RECONNECTED) {
                    try { register(); } catch (RuntimeException e) { log.error("恢复 RPC 服务注册失败", e); }
                }
            };
            zkClientService.getClient().getConnectionStateListenable().addListener(reconnectListener);
        }
        register();
    }

    private synchronized void register() {
        if (closed) { return; }
        String ip = IpUtils.getLocalIpAddr();
        if (ip.isEmpty()) { throw new SimpleRpcException("无法获取服务注册 IP"); }
        for (Class<?> api : rpcServerHandler.interfaceApi) {
            String path = zkClientService.getNamespace() + "/" + api.getName() + "/" + ip + ":" + port;
            try {
                Stat existing = zkClientService.getClient().checkExists().forPath(path);
                if (existing == null) {
                    zkClientService.createEphemeral(path);
                } else if (existing.getEphemeralOwner() != zkClientService.getClient().getZookeeperClient().getZooKeeper().getSessionId()) {
                    throw new SimpleRpcException("服务地址已被其他进程注册：" + path);
                }
                if (!registeredPaths.contains(path)) { registeredPaths.add(path); }
            } catch (Exception e) {
                if (e instanceof InterruptedException) { Thread.currentThread().interrupt(); }
                throw new SimpleRpcException("服务注册失败：" + path, e);
            }
        }
    }

    @PreDestroy
    public synchronized void close() {
        if (closed) { return; }
        closed = true;
        if (!zkClientService.isEnabled()) { return; }
        if (reconnectListener != null) {
            zkClientService.getClient().getConnectionStateListenable().removeListener(reconnectListener);
        }
        for (String path : registeredPaths) {
            try { zkClientService.delete(path); } catch (RuntimeException e) { log.warn("注销 RPC 服务失败：" + path, e); }
        }
        registeredPaths.clear();
    }
}
