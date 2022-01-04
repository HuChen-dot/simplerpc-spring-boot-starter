package org.hu.rpc.core.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.logging.LoggingHandler;
import org.hu.rpc.config.NettyServerConfig;
import org.hu.rpc.zk.server.ServerInit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/26 6:43 PM
 **/
//此注解用来开启下面的@ConfigurationProperties注解，
@EnableConfigurationProperties(NettyRpcServer.class)
//用来读取配置文件中的值，给类的属性自动赋值
@ConfigurationProperties(prefix = "simplerpc.netty.server.threadpoll")
@Configuration
public class NettyRpcServer implements Runnable {
    Logger log = LoggerFactory.getLogger(NettyRpcServer.class);


    /**
     * 连接处理线程数
     */
    private int bossGroupSize = 1;

    /**
     * 读写处理线程数
     */
    private int workerGroupSize = 200;

    /**
     * 服务端bossGroup 的等待队列大小
     */
    private int bossGroupQueueSize = 1024;

    @Autowired
    private NettyServerConfig nettyServerConfig;

    @Autowired
    private RpctServerHandler rpctServerHandler;

    @Autowired
    private ServerInit serverInit;


    private NioEventLoopGroup bossGroup = null;

    private NioEventLoopGroup workerGroup = null;

    private static Object lock = new Object();

    @Override
    public void run() {
        if (!nettyServerConfig.isIsrun()) {
            return;
        }
        synchronized (lock) {
            if (bossGroup == null) {
                try {
                    // 创建连接线程组
                    bossGroup = new NioEventLoopGroup(bossGroupSize);
                    // 创建 工作线程组
                    workerGroup = new NioEventLoopGroup(workerGroupSize);

                    // 创建服务端启动助手
                    ServerBootstrap serverBootstrap = new ServerBootstrap();

                    // 关联线程组
                    serverBootstrap.group(bossGroup, workerGroup);
                    // 设置服务端通道为Nio
                    serverBootstrap.channel(NioServerSocketChannel.class);

                    //6：设置相应的参数--设置bossGroup 等待队列的大小
                    serverBootstrap.option(ChannelOption.SO_BACKLOG, bossGroupQueueSize);

                    //6：设置相应的参数--设置workerGroup 开启连接的探活
                    serverBootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);

                    // 设置 日志打印级别
                    serverBootstrap.handler(new LoggingHandler(nettyServerConfig.getLoglevel()));

                    // 绑定通道初始化对象
                    serverBootstrap.childHandler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel channel) throws Exception {
                            // 获取 ChannelPipeline 用于注册编码器和自定义业务处理类
                            ChannelPipeline pipeline = channel.pipeline();

                            // 设置编解码器
                            channel.pipeline().addLast(new StringDecoder());
                            channel.pipeline().addLast(new StringEncoder());

                            // 添加自定义处理 hangler
                            pipeline.addLast(rpctServerHandler);
                        }
                    });

                    // 绑定端口,同时将异步修改成同步
                    ChannelFuture channelFuture = serverBootstrap.bind(nettyServerConfig.getPort()).sync();
                    // 启动 zk 注册中心
                    serverInit.init(nettyServerConfig.getPort());

                    log.info("Netty server running.....");

                    //关闭通道--(并不是真正意义上的关闭，而是监听通道关闭的状态）
                    channelFuture.channel().closeFuture().sync();
                } catch (InterruptedException e) {
                    log.error("发生异常，关闭资源：{}",e);
                    //11: 关闭连接池
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                } finally {
                    log.info("最终关闭资源");
                    //11: 关闭连接池
                    bossGroup.shutdownGracefully();
                    workerGroup.shutdownGracefully();
                }
            }
        }
    }


    @PreDestroy
    public void destroy() {
        if (bossGroup != null) {
            log.info("bossGroup销毁");
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            log.info("workerGroup销毁");
            workerGroup.shutdownGracefully();
        }
    }


    public int getBossGroupSize() {
        return bossGroupSize;
    }

    public void setBossGroupSize(int bossGroupSize) {
        this.bossGroupSize = bossGroupSize;
    }

    public int getWorkerGroupSize() {
        return workerGroupSize;
    }

    public void setWorkerGroupSize(int workerGroupSize) {
        this.workerGroupSize = workerGroupSize;
    }

    public int getBossGroupQueueSize() {
        return bossGroupQueueSize;
    }

    public void setBossGroupQueueSize(int bossGroupQueueSize) {
        this.bossGroupQueueSize = bossGroupQueueSize;
    }


}
