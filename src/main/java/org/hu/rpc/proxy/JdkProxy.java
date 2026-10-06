package org.hu.rpc.proxy;

import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.core.execute.RpcInvocationHandler;
import org.hu.rpc.core.client.RpcClientTransport;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.core.route.RouteStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;


/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/26 9:50 PM
 **/
@Component
public class JdkProxy {

    @Autowired
    private NettyClientConfig nettyClientConfig;
    @Autowired
    private RouteStrategy routeStrategy;

    @Autowired
    private RpcClientTransport transport;

    public Object createProxy(Class<?> clazz) {
        if (!clazz.isInterface()) {
            throw new SimpleRpcException("RPC 代理类型必须是接口：" + clazz.getName());
        }
        return Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz},
                new RpcInvocationHandler(nettyClientConfig, routeStrategy, clazz, transport));
    }
}
