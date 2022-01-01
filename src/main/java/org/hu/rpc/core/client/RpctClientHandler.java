package org.hu.rpc.core.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.concurrent.Callable;

/**
 * @Author: hu.chen
 * @Description: 服务端业务处理Handler；继承SimpleChannelInboundHandler时指定的
 * 范型为消息的类型，
 * @DateTime: 2021/12/25 3:05 PM
 **/
public class RpctClientHandler extends SimpleChannelInboundHandler<String> implements Callable {


    private ChannelHandlerContext ctx;

    /**
     * 发送的消息
     */
    private String request;

    /**
     * 服务端返回的消息
     */
    private String response;

    public void setRequest(String request) {
        this.request = request;
    }

    /**
     * 通道就绪事件（给服务端发送消息）
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    /**
     * 处理通道读取事件
     *
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    protected synchronized void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {

        this.response=msg;

        // 唤醒等待的线程
        notifyAll();

    }

    @Override
    public synchronized String call() throws Exception {

        ctx.writeAndFlush(request);
        wait();

        return response;
    }
}
