package org.hu.rpc.core.server;

import com.alibaba.fastjson.JSON;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.hu.rpc.annotation.RpcAutowired;
import org.hu.rpc.annotation.RpcService;
import org.hu.rpc.common.RpcRequest;
import org.hu.rpc.common.RpcResponse;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.proxy.JdkProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: hu.chen
 * @Description: 服务端业务处理Handler；继承SimpleChannelInboundHandler时指定的
 * 范型为消息的类型，
 * @DateTime: 2021/12/25 3:05 PM
 **/
@Component
// @ChannelHandler.Sharable 这个注解代表，当前这个处理类，可以被多个通道共享
@ChannelHandler.Sharable
public class RpctServerHandler extends SimpleChannelInboundHandler<String> implements ApplicationContextAware {

    Logger log = LoggerFactory.getLogger(RpctServerHandler.class);

    /**
     * 用来存储 被标记了@RpcService 注解的bean
     */
    public static Map<String, Object> beans = new ConcurrentHashMap<>();

    public List<Class> interfaceApi=new ArrayList<>();

    @Autowired
    private JdkProxy jdkProxy;

    /**
     * 处理通道读取事件
     *
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {

        RpcRequest request = JSON.parseObject(msg, RpcRequest.class);
        RpcResponse response = new RpcResponse();
        response.setRequestId(request.getRequestId());

        // 校验参数
        if (check(request, response)) {
            try {
                doInvoke(request, response);
            } catch (Exception e) {
                log.error("请求参数校验失败：{}",e);
                response.setError(e.getMessage());
            }
        }

        // 写出响应信息
        ctx.writeAndFlush(JSON.toJSONString(response));
    }

    /**
     * 进行校验
     *
     * @return
     */
    private boolean check(RpcRequest request, RpcResponse response) {
        if (beans == null || beans.size() == 0) {
            response.setError("没有可以提供的服务!!!");
            return false;
        }
        if (beans.get(request.getClassName()) == null) {
            response.setError("查找不到你需要消费的服务，请查看服务是否提供!!!");
            return false;
        }
        return true;
    }

    /**
     * 执行方法
     *
     * @param request
     * @param response
     * @throws Exception
     */
    private void doInvoke(RpcRequest request, RpcResponse response) throws Exception {
        // 从容器中 获取 需要执行的 bean
        Object o = beans.get(request.getClassName());

        // 获取 class
        Class<?> aClass = o.getClass();
        // 根据class 反射获取需要执行的方法
        Method declaredMethod = aClass.getDeclaredMethod(request.getMethodName(), request.getParameterTypes());

        // 执行方法，并将返回值进行封装
        response.setResult(declaredMethod.invoke(o, request.getParameters()));
    }


    /**
     * 通道发生异常
     *
     * @param ctx   上下文
     * @param cause 异常对象
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 关闭通道
        ctx.close();
    }


    /**
     * @param applicationContext
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, Object> serviceMap = applicationContext.getBeansWithAnnotation(RpcService.class);

        Set<Map.Entry<String, Object>> entries = serviceMap.entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            Object serviceBean = entry.getValue();

            Class<?>[] interfaces = serviceBean.getClass().getInterfaces();

            if (interfaces.length == 0) {
                throw new SimpleRpcException("类：" + serviceBean.getClass().getName() + " 必须实现接口");
            }

            // 默认获取第一个接口作为key 的名称
            Class<?> className = interfaces[0];
            interfaceApi.add(className);
            beans.put(className.getName(), serviceBean);
        }




        // 处理创建客户端的代理对象
        String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();

        Set<Object> onlySet = new HashSet<>();

        for (String beanDefinitionName : beanDefinitionNames) {
            Object bean = applicationContext.getBean(beanDefinitionName);
            if (!onlySet.add(bean)) {
                continue;
            }
            Field[] declaredFields = bean.getClass().getDeclaredFields();

            for (Field declaredField : declaredFields) {
                if (declaredField.isAnnotationPresent(RpcAutowired.class)) {


                    // 生成代理对象
                    Object proxy = jdkProxy.createProxy(declaredField.getType());

                    // 开启暴力反射
                    declaredField.setAccessible(true);
                    try {
                        declaredField.set(bean, proxy);
                    } catch (IllegalAccessException e) {
                        log.error("设置bean属性失败：{}",e);
                    }
                }
            }

        }

    }
}
