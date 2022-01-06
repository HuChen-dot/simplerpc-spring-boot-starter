package org.hu.rpc.proxy;

import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.core.execute.DefaultExecuter;
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

    public Object createProxy(Class clazz) {
        return Proxy.newProxyInstance(JdkProxy.class.getClassLoader(), new Class[]{clazz}, new DefaultExecuter(nettyClientConfig,routeStrategy));
    }
}
