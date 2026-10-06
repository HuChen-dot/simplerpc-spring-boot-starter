package org.hu.rpc.core.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.hu.rpc.common.entity.RpcResponse;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.util.JsonUtils;

import java.util.Map;
import java.util.concurrent.*;

/** 按请求 ID 关联响应，不阻塞 Netty 事件线程。 */
public class RpcClientHandler extends SimpleChannelInboundHandler<String> {
    private volatile ChannelHandlerContext ctx;
    private final Map<String, CompletableFuture<RpcResponse>> pending = new ConcurrentHashMap<>();

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public RpcResponse send(String request, int timeoutMillis) throws ExecutionException, InterruptedException {
        String requestId = JsonUtils.parseObject(request).getString("requestId");
        if (requestId == null || requestId.isEmpty()) {
            throw new SimpleRpcException("请求 ID 不能为空");
        }
        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        if (pending.putIfAbsent(requestId, future) != null) {
            throw new SimpleRpcException("请求 ID 重复：" + requestId);
        }
        try {
            ChannelHandlerContext context = ctx;
            if (context == null || !context.channel().isActive()) {
                throw new SimpleRpcException("RPC 连接已关闭");
            }
            context.writeAndFlush(request).addListener(result -> {
                if (!result.isSuccess()) {
                    future.completeExceptionally(result.cause());
                }
            });
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new SimpleRpcException("RPC 响应超时：" + timeoutMillis + "ms，requestId=" + requestId, e);
        } finally {
            pending.remove(requestId, future);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        RpcResponse response = JsonUtils.parse(msg, RpcResponse.class);
        if (response == null || response.getRequestId() == null) {
            throw new SimpleRpcException("RPC 响应缺少请求 ID");
        }
        CompletableFuture<RpcResponse> future = pending.get(response.getRequestId());
        if (future != null) {
            future.complete(response);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        failPending(new SimpleRpcException("RPC 连接已关闭"));
        ctx.fireChannelInactive();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        failPending(cause);
        ctx.close();
    }

    private void failPending(Throwable cause) {
        pending.values().forEach(future -> future.completeExceptionally(cause));
    }

}
