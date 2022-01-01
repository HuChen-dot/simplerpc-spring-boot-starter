package org.hu.rpc.core.client;

import com.alibaba.fastjson.JSON;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.hu.rpc.util.ThreadPoolUtil;
import org.hu.rpc.common.RpcResponse;
import org.hu.rpc.config.NettyClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * @Author: hu.chen
 * @Description: RPC客户端
 * 1：连接 Netty服务器
 * 2：暴露关闭连接方法给调用者
 * 3：提供发送消息的方法给调用者
 * @DateTime: 2021/12/26 8:44 PM
 **/
public class NettyRpcClient {

    private static Logger log = LoggerFactory.getLogger(NettyRpcClient.class);


    private NettyClientConfig nettyClientConfig;

    private String[] hostAndPort;


    public NettyRpcClient(NettyClientConfig nettyClientConfig,String[] hostAndPort) {
        this.nettyClientConfig = nettyClientConfig;
        this.hostAndPort=hostAndPort;
        start();
    }

    /**
     * 读写处理线程数
     */
    private int threadSize = 5;


    private EventLoopGroup eventExecutors = null;

    private Channel channel = null;

    private RpctClientHandler rpctClientHandler = new RpctClientHandler();


    public void start() {
        //1：创建线程组
        eventExecutors = new NioEventLoopGroup(threadSize);
        try {
            //2：创建客户端启动助手
            Bootstrap bootstrap = new Bootstrap();

            //3：设置线程组
            bootstrap.group(eventExecutors);

            //5： 设置客户端通道为：NIO
            bootstrap.channel(NioSocketChannel.class);

            bootstrap.option(ChannelOption.SO_KEEPALIVE, true);

            // 客户端连接超时时间
            bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, nettyClientConfig.getConnecttimeout());

            //6：创建一个通道初始化对象
            bootstrap.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel channel) throws Exception {
                    // 设置编解码器
                    channel.pipeline().addLast(new StringDecoder());
                    channel.pipeline().addLast(new StringEncoder());

                    //8：向pipeline中添加自定义业务处理handier
                    channel.pipeline().addLast(rpctClientHandler);
                }
            });
            //7: 启动客户端绑定端口，同时将异步改为同步
            channel = bootstrap.connect(hostAndPort[0], Integer.parseInt(hostAndPort[1])).sync().channel();
        } catch (InterruptedException e) {
            log.error("Netty 启动失败：{}",e);
            // 关闭资源
            close();
        }
    }


    /**
     * 提供给调用者关闭资源的方法
     */
    public void close() {
        if (channel != null) {
            channel.close();
        }
        //9：关闭连接池
        if (eventExecutors != null) {
            eventExecutors.shutdownGracefully();
        }
    }

    /**
     * 提供给调用者发送消息的方法
     */
    public RpcResponse send(String request) throws ExecutionException, InterruptedException {
        rpctClientHandler.setRequest(request);
        Future<String> submit = ThreadPoolUtil.poolExecutor().submit(rpctClientHandler);
        String response = submit.get();

        return JSON.parseObject(response, RpcResponse.class);
    }
}
