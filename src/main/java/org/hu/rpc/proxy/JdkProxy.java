package org.hu.rpc.proxy;

import com.alibaba.fastjson.JSON;
import org.hu.rpc.common.RpcRequest;
import org.hu.rpc.common.RpcResponse;
import org.hu.rpc.config.NettyClientConfig;
import org.hu.rpc.core.client.NettyRpcClient;
import org.hu.rpc.core.route.RouteStrategy;
import org.hu.rpc.core.route.loadbalancing.LoadBalancingConst;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.util.DateUtil;
import org.hu.rpc.util.JsonUtils;
import org.hu.rpc.zk.util.ZkClientUtils;
import org.hu.rpc.zk.util.ZkLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.UUID;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/26 9:50 PM
 **/
@Component
//此注解用来开启下面的@ConfigurationProperties注解，
@EnableConfigurationProperties(JdkProxy.class)
//用来读取配置文件中的值，给类的属性自动赋值
@ConfigurationProperties(prefix = "simplerpc.netty.client.threadpoll")
public class JdkProxy {

    private static Logger log = LoggerFactory.getLogger(JdkProxy.class);

    @Autowired
    private NettyClientConfig nettyClientConfig;

    @Autowired
    private RouteStrategy routeStrategy;

    @Autowired
    private ZkLock zkLock;


    @Autowired
    private ZkClientUtils zkClientUtils;

    public Object createProxy(Class clazz) {

        return Proxy.newProxyInstance(JdkProxy.class.getClassLoader(), new Class[]{clazz}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

                RpcRequest request = new RpcRequest();
                Class<?> declaringClass = method.getDeclaringClass();

                String tag = declaringClass.getName();

                // 获取提供服务的机器的ip和端口
                String[] hostAndPort = routeStrategy.getHostAndPort(tag);

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
                // 发送消息
                NettyRpcClient nettyRpcClient = new NettyRpcClient(nettyClientConfig, hostAndPort);
                try {

                    String requestData = JSON.toJSONString(request);

                    // 记录rpc请求时间
                    long start = System.currentTimeMillis();

                    RpcResponse send = nettyRpcClient.send(requestData);

                    long end = System.currentTimeMillis();

                    if (send.getError() != null) {
                        throw new SimpleRpcException(send.getError());
                    }

                    Object result = send.getResult();

                    if (zkClientUtils.isOpenzk() && LoadBalancingConst.RESPONSE_TIME.equals(nettyClientConfig.getLoadbalancing())) {
                        setNodeContent(start, end, tag, hostAndPort);
                    }

                    if (result == null) {
                        return result;
                    } else {
                        if (JsonUtils.isJsonType(result.toString())) {
                            return JSON.parseObject(result.toString(), method.getReturnType());
                        }
                        return result;
                    }

                } catch (Exception e) {
                    log.error("远程调用失败：{}", e);
                } finally {
                    // 关闭资源
                    nettyRpcClient.close();
                }
                return null;
            }
        });
    }

    /**
     * 给节点设置内容
     */
    private void setNodeContent(long start, long end, String tag, String[] hostAndPort) {
        String s1 = DateUtil.dateToStr(new Date());
        s1 = (end - start) + "&" + s1;
        StringBuffer buffer = new StringBuffer(zkClientUtils.getNameSpace())
                .append("/").append(tag).append("/").append(hostAndPort[0])
                .append(":").append(hostAndPort[1]);
        zkLock.lock();
        try {
            zkClientUtils.updataNode(buffer.toString(), s1);
        } finally {
            zkLock.unLock();
        }

    }
}
