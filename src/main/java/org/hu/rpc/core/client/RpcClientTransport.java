package org.hu.rpc.core.client;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.exception.SimpleRpcException;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

/** Spring 容器内共享网络线程，容器关闭时统一释放。 */
@Component
public class RpcClientTransport {
    private EventLoopGroup group;
    private boolean closed;

    private synchronized EventLoopGroup eventLoopGroup() {
        if (closed) {
            throw new SimpleRpcException("RPC 网络资源已关闭");
        }
        if (group == null) {
            group = new NioEventLoopGroup(Math.min(4, Runtime.getRuntime().availableProcessors()));
        }
        return group;
    }

    public NettyRpcClient connect(NettyClientConfig config, String[] hostAndPort) {
        return new NettyRpcClient(config, hostAndPort, eventLoopGroup());
    }

    @PreDestroy
    public synchronized void close() {
        closed = true;
        if (group != null) {
            group.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}
