package org.hu.rpc.core.execute;

import com.alibaba.fastjson.JSON;
import org.hu.rpc.common.entity.RpcRequest;
import org.hu.rpc.common.entity.RpcResponse;
import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.core.client.NettyRpcClient;
import org.hu.rpc.core.client.RpcClientTransport;
import org.hu.rpc.core.route.RouteStrategy;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.util.JsonUtils;
import org.springframework.core.GenericTypeResolver;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class RpcInvocationHandler implements InvocationHandler {
    private final NettyClientConfig nettyClientConfig;
    private final RouteStrategy routeStrategy;
    private final Class<?> serviceInterface;
    private final RpcClientTransport transport;

    public RpcInvocationHandler(NettyClientConfig config, RouteStrategy routeStrategy, Class<?> serviceInterface, RpcClientTransport transport) {
        this.nettyClientConfig = config;
        this.routeStrategy = routeStrategy;
        this.serviceInterface = serviceInterface;
        this.transport = transport;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            switch (method.getName()) {
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "SimpleRpcProxy(" + proxy.getClass().getInterfaces()[0].getName() + ")";
                default: throw new SimpleRpcException("不支持的 Object 方法：" + method.getName());
            }
        }
        String tag = serviceInterface.getName();
        String[] hostAndPort = routeStrategy.getHostAndPort(tag);
        try (NettyRpcClient client = transport.connect(nettyClientConfig, hostAndPort)) {
            long started = System.nanoTime();
            RpcResponse response = client.send(getRequestStr(method, args));
            routeStrategy.recordResponseTime(tag, hostAndPort, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            if (response.getError() != null) {
                throw new SimpleRpcException("调用失败：" + response.getError());
            }
            if (method.getReturnType() == void.class) {
                return null;
            }
            if (response.getResult() == null && method.getReturnType().isPrimitive()) {
                throw new SimpleRpcException("RPC 返回 null，无法转换为 " + method.getReturnType().getName());
            }
            return JsonUtils.convert(response.getResult(), GenericTypeResolver.resolveType(method.getGenericReturnType(),
                    serviceInterface));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SimpleRpcException("RPC 调用被中断", e);
        } catch (ExecutionException e) {
            throw new SimpleRpcException("RPC 通信失败", e.getCause());
        }
    }

    private String getRequestStr(Method method, Object[] args) {
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClassName(serviceInterface.getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args == null ? new Object[0] : args);
        return JSON.toJSONString(request);
    }
}
