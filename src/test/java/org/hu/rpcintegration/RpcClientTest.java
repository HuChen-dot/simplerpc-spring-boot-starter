package org.hu.rpcintegration;

import com.alibaba.fastjson.JSON;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.json.JsonObjectDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.hu.rpc.common.entity.RpcResponse;
import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.core.client.NettyRpcClient;
import org.hu.rpc.exception.SimpleRpcException;
import org.junit.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.junit.Assert.*;

public class RpcClientTest {
    private EventLoopGroup group;
    private Channel server;
    private NettyRpcClient client;
    private volatile BiConsumer<ChannelHandlerContext, String> onRequest;

    @Before
    public void setUp() throws Exception {
        group = new NioEventLoopGroup(1);
        server = new ServerBootstrap().group(group).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(new JsonObjectDecoder(), new StringDecoder(StandardCharsets.UTF_8), new StringEncoder(StandardCharsets.UTF_8),
                                new SimpleChannelInboundHandler<String>() {
                                    protected void channelRead0(ChannelHandlerContext ctx, String msg) { onRequest.accept(ctx, msg); }
                                });
                    }
                }).bind(0).sync().channel();
        NettyClientConfig config = new NettyClientConfig();
        config.setRequestTimeout(500);
        client = new NettyRpcClient(config, new String[]{"127.0.0.1", String.valueOf(((InetSocketAddress) server.localAddress()).getPort())}, group);
    }

    @After
    public void close() {
        if (client != null) { client.close(); }
        if (server != null) { server.close().syncUninterruptibly(); }
        if (group != null) { group.shutdownGracefully(0, 2, TimeUnit.SECONDS).syncUninterruptibly(); }
    }

    private String request(String id) { return "{\"requestId\":\"" + id + "\"}"; }
    private String response(String id) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(id);
        response.setResult(id);
        return JSON.toJSONString(response);
    }

    @Test(timeout = 5000)
    public void concurrentRequestsReceiveOutOfOrderResponsesById() throws Exception {
        AtomicReference<String> first = new AtomicReference<>();
        CountDownLatch receivedFirst = new CountDownLatch(1);
        onRequest = (ctx, message) -> {
            String id = JSON.parseObject(message).getString("requestId");
            if (first.compareAndSet(null, id)) {
                receivedFirst.countDown();
            } else {
                // 一次写出两个 JSON，响应顺序与请求相反。
                ctx.writeAndFlush(response(id) + response(first.get()));
            }
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<RpcResponse> one = executor.submit(() -> client.send(request("one")));
            assertTrue(receivedFirst.await(1, TimeUnit.SECONDS));
            Future<RpcResponse> two = executor.submit(() -> client.send(request("two")));
            assertEquals("one", one.get(2, TimeUnit.SECONDS).getResult());
            assertEquals("two", two.get(2, TimeUnit.SECONDS).getResult());
        } finally { executor.shutdownNow(); }
    }

    @Test(timeout = 3000)
    public void responseSplitAcrossBytesIsReassembled() throws Exception {
        onRequest = (ctx, message) -> {
            byte[] response = response("split").getBytes(StandardCharsets.UTF_8);
            for (byte value : response) {
                ctx.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(new byte[]{value}));
            }
        };
        assertEquals("split", client.send(request("split")).getResult());
    }

    @Test(timeout = 3000)
    public void disconnectFailsPendingCall() {
        onRequest = (ctx, message) -> ctx.close();
        ExecutionException failure = assertThrows(ExecutionException.class, () -> client.send(request("disconnect")));
        assertTrue(failure.getCause().getMessage().contains("连接已关闭"));
    }

    @Test(timeout = 3000)
    public void unrelatedResponseCannotCompleteCall() {
        onRequest = (ctx, message) -> ctx.writeAndFlush(response("wrong-id"));
        assertThrows(SimpleRpcException.class, () -> client.send(request("right-id")));
    }

    @Test(timeout = 3000)
    public void duplicateInflightRequestIdIsRejected() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        onRequest = (ctx, message) -> received.countDown();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<RpcResponse> pending = executor.submit(() -> client.send(request("same")));
            assertTrue(received.await(1, TimeUnit.SECONDS));
            SimpleRpcException failure = assertThrows(SimpleRpcException.class, () -> client.send(request("same")));
            assertTrue(failure.getMessage().contains("重复"));
            client.close();
            assertThrows(ExecutionException.class, () -> pending.get(1, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
}
