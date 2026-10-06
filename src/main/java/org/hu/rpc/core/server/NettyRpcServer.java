package org.hu.rpc.core.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.json.JsonObjectDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.hu.rpc.config.NettyServerConfig;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.register.zk.server.ZkRegisterInit;
import org.hu.rpc.register.redis.RedisRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@ConfigurationProperties(prefix = "simplerpc.server.thread-pool")
@Configuration
public class NettyRpcServer {
    private static final Logger log = LoggerFactory.getLogger(NettyRpcServer.class);
    private int bossGroupSize = 1;
    private int workerGroupSize = Math.min(4, Runtime.getRuntime().availableProcessors());
    private int backlog = 1024;
    @Autowired
    private NettyServerConfig nettyServerConfig;
    @Autowired
    private RpcServerHandler rpcServerHandler;
    @Autowired
    private ZkRegisterInit zkRegisterInit;
    @Autowired
    private RedisRegistry redisRegistry;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private DefaultEventExecutorGroup businessGroup;
    private Channel serverChannel;
    private final ChannelGroup connections = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private boolean destroyed;

    /** 绑定端口后返回；绑定失败直接使 Spring 启动失败。 */
    public synchronized void start() {
        if (!nettyServerConfig.isEnabled()) {
            return;
        }
        if (destroyed) {
            throw new SimpleRpcException("RPC 服务端已关闭");
        }
        if (serverChannel != null) { return; }
        try {
            bossGroup = new NioEventLoopGroup(bossGroupSize);
            workerGroup = new NioEventLoopGroup(workerGroupSize);
            businessGroup = new DefaultEventExecutorGroup(Math.max(2, workerGroupSize));
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, backlog)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new LoggingHandler(LogLevel.valueOf(nettyServerConfig.getLogLevel().toUpperCase(Locale.ROOT))))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            connections.add(channel);
                            channel.pipeline().addLast(new JsonObjectDecoder(16 * 1024 * 1024));
                            channel.pipeline().addLast(new StringDecoder(StandardCharsets.UTF_8));
                            channel.pipeline().addLast(new StringEncoder(StandardCharsets.UTF_8));
                            channel.pipeline().addLast(businessGroup, rpcServerHandler);
                        }
                    });
            ChannelFuture binding = bootstrap.bind(nettyServerConfig.getPort());
            serverChannel = binding.channel();
            binding.sync();
            int port = ((InetSocketAddress) serverChannel.localAddress()).getPort();
            if (redisRegistry.isEnabled()) {
                redisRegistry.register(port, rpcServerHandler.interfaceApi);
            } else {
                zkRegisterInit.init(port);
            }
            log.info("RPC 服务端启动：{}", serverChannel.localAddress());
        } catch (InterruptedException e) {
            destroy();
            Thread.currentThread().interrupt();
            throw new SimpleRpcException("RPC 服务端启动被中断", e);
        } catch (Exception e) {
            destroy();
            throw new SimpleRpcException("RPC 服务端启动失败", e);
        }
    }

    @PreDestroy
    public synchronized void destroy() {
        if (destroyed) { return; }
        destroyed = true;
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        connections.close().awaitUninterruptibly();
        try {
            zkRegisterInit.close();
        } catch (RuntimeException e) {
            log.warn("注销服务失败，继续关闭网络资源", e);
        }
        if (redisRegistry.isEnabled()) {
            try { redisRegistry.close(); }
            catch (RuntimeException e) { log.warn("注销 Redis 服务失败，继续关闭网络资源", e); }
        }
        if (businessGroup != null) {
            businessGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }

    public int getBossGroupSize() { return bossGroupSize; }
    public void setBossGroupSize(int bossGroupSize) {
        if (bossGroupSize <= 0) { throw new IllegalArgumentException("bossGroupSize 必须大于 0"); }
        this.bossGroupSize = bossGroupSize;
    }
    public int getWorkerGroupSize() { return workerGroupSize; }
    public void setWorkerGroupSize(int workerGroupSize) {
        if (workerGroupSize < 0) { throw new IllegalArgumentException("workerGroupSize 不能为负数"); }
        this.workerGroupSize = workerGroupSize;
    }
    public int getBacklog() { return backlog; }
    public void setBacklog(int backlog) {
        if (backlog <= 0) { throw new IllegalArgumentException("backlog 必须大于 0"); }
        this.backlog = backlog;
    }
}
