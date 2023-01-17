package org.hu.rpc.core.execute;

import com.alibaba.fastjson.JSON;
import org.hu.rpc.common.entity.RpcRequest;
import org.hu.rpc.common.entity.RpcResponse;
import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.core.client.NettyRpcClient;
import org.hu.rpc.core.route.RouteStrategy;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2022/1/6 10:30 AM
 **/
public class DefaultExecuter implements InvocationHandler {
    private static Logger log = LoggerFactory.getLogger(DefaultExecuter.class);

    private NettyClientConfig nettyClientConfig;

    private RouteStrategy routeStrategy;

    public DefaultExecuter(NettyClientConfig nettyClientConfig, RouteStrategy routeStrategy) {
        this.nettyClientConfig = nettyClientConfig;
        this.routeStrategy = routeStrategy;
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        String requestData = getRequestStr(method, args);
        String tag = method.getDeclaringClass().getName();

        // 获取提供服务的机器的ip和端口
        String[] hostAndPort = routeStrategy.getHostAndPort(tag);
        // 发送消息
        NettyRpcClient nettyRpcClient = new NettyRpcClient(nettyClientConfig, hostAndPort);
        try {

            // 发出请求
            RpcResponse send = nettyRpcClient.send(requestData);

            if (send.getError() != null) {
                throw new SimpleRpcException("调用失败：" + send.getError());
            }

            Object result = send.getResult();

            if (result == null || !JsonUtils.isJsonType(result.toString())) {
                return result;
            } else {
                return JSON.parseObject(result.toString(), method.getReturnType());
            }
        } finally {
            // 关闭资源
            nettyRpcClient.close();
        }
    }


    public String getRequestStr(Method method, Object[] args) {
        RpcRequest request = new RpcRequest();
        Class<?> declaringClass = method.getDeclaringClass();
        // 设置请求标识
        request.setRequestId(UUID.randomUUID().toString());
        // 设置接口名称
        request.setClassName(declaringClass.getName());
        // 设置方法名
        request.setMethodName(method.getName());

        // 设置方法参数类型
        request.setParameterTypes(method.getParameterTypes());
        // 设置参数
        request.setParameters(args);

        return JSON.toJSONString(request);
    }

}

