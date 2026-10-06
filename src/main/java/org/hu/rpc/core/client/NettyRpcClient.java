package org.hu.rpc.core.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.json.JsonObjectDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.hu.rpc.common.entity.RpcResponse;
import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.exception.SimpleRpcException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

/** 单个连接；Spring 代理调用共享事件线程组。 */
public class NettyRpcClient implements AutoCloseable {
    private final NettyClientConfig nettyClientConfig;
    private final String[] hostAndPort;
    private final EventLoopGroup eventExecutors;
    private final RpcClientHandler rpcClientHandler = new RpcClientHandler();
    private Channel channel;

    public NettyRpcClient(NettyClientConfig config, String[] hostAndPort, EventLoopGroup group) {
        this.nettyClientConfig = config;
        this.hostAndPort = hostAndPort;
        this.eventExecutors = group;
        start();
    }

    private void start() {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(eventExecutors).channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, nettyClientConfig.getConnectTimeout())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            channel.pipeline().addLast(new JsonObjectDecoder(16 * 1024 * 1024));
                            channel.pipeline().addLast(new StringDecoder(StandardCharsets.UTF_8));
                            channel.pipeline().addLast(new StringEncoder(StandardCharsets.UTF_8));
                            channel.pipeline().addLast(rpcClientHandler);
                        }
                    });
            ChannelFuture connection = bootstrap.connect(hostAndPort[0], Integer.parseInt(hostAndPort[1]));
            channel = connection.channel();
            connection.sync();
        } catch (InterruptedException e) {
            close();
            Thread.currentThread().interrupt();
            throw new SimpleRpcException("RPC 连接被中断", e);
        } catch (Exception e) {
            close();
            throw new SimpleRpcException("RPC 连接失败：" + java.util.Arrays.toString(hostAndPort), e);
        }
    }

    @Override
    public synchronized void close() {
        if (channel != null) {
            channel.close();
        }
    }

    public RpcResponse send(String request) throws ExecutionException, InterruptedException {
        return rpcClientHandler.send(request, nettyClientConfig.getRequestTimeout());
    }
}
