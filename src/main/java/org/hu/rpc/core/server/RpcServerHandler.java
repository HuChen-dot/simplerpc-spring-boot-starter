package org.hu.rpc.core.server;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.hu.rpc.annotation.RpcService;
import org.hu.rpc.common.entity.RpcResponse;
import org.hu.rpc.exception.SimpleRpcException;
import org.hu.rpc.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.GenericTypeResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ChannelHandler.Sharable
public class RpcServerHandler extends SimpleChannelInboundHandler<String>
        implements ApplicationContextAware, SmartInitializingSingleton {
    private static final Logger log = LoggerFactory.getLogger(RpcServerHandler.class);
    public final Map<String, Object> beans = new ConcurrentHashMap<>();
    public final List<Class<?>> interfaceApi = new ArrayList<>();
    private final Map<String, Class<?>> interfaces = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
        RpcResponse response = new RpcResponse();
        try {
            // 参数类型来自本地已注册接口，不加载网络请求指定的任意 Class。
            JSONObject request = JsonUtils.parseObject(msg);
            if (request == null) {
                throw new SimpleRpcException("RPC 请求不能为空");
            }
            response.setRequestId(request.getString("requestId"));
            if (response.getRequestId() == null || response.getRequestId().isEmpty()) {
                throw new SimpleRpcException("请求 ID 不能为空");
            }
            String className = request.getString("className");
            Class<?> api = className == null ? null : interfaces.get(className);
            if (api == null) {
                throw new SimpleRpcException("查找不到你需要消费的服务：" + className);
            }
            JSONArray parameterTypes = request.getJSONArray("parameterTypes");
            JSONArray parameters = request.getJSONArray("parameters");
            int count = parameterTypes == null ? 0 : parameterTypes.size();
            if (count != (parameters == null ? 0 : parameters.size())) {
                throw new SimpleRpcException("RPC 参数数量与类型数量不一致");
            }
            Method method = null;
            for (Method candidate : api.getMethods()) {
                if (!candidate.getName().equals(request.getString("methodName")) || candidate.getParameterCount() != count) {
                    continue;
                }
                boolean matches = true;
                for (int i = 0; i < count; i++) {
                    if (!candidate.getParameterTypes()[i].getName().equals(parameterTypes.getString(i))) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    method = candidate;
                    break;
                }
            }
            if (method == null) {
                throw new SimpleRpcException("接口中不存在方法：" + request.getString("methodName"));
            }
            Object[] args = new Object[count];
            for (int i = 0; i < count; i++) {
                args[i] = JsonUtils.convert(parameters.get(i), GenericTypeResolver.resolveType(method.getGenericParameterTypes()[i], api));
            }
            response.setResult(method.invoke(beans.get(className), args));
        } catch (Exception e) {
            Throwable cause = e instanceof InvocationTargetException ? ((InvocationTargetException) e).getTargetException() : e;
            response.setError(cause.getMessage() == null ? cause.getClass().getName() : cause.getMessage());
            log.warn("RPC 请求处理失败，requestId={}", response.getRequestId(), cause);
        }
        ctx.writeAndFlush(JSON.toJSONString(response));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("RPC 通道异常", cause);
        ctx.close();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (Object service : applicationContext.getBeansWithAnnotation(RpcService.class).values()) {
            Class<?> targetClass = AopUtils.getTargetClass(service);
            Set<Class<?>> serviceInterfaces = ClassUtils.getAllInterfacesForClassAsSet(targetClass);
            if (serviceInterfaces.isEmpty()) {
                throw new SimpleRpcException("类：" + targetClass.getName() + " 必须实现接口");
            }
            for (Class<?> api : serviceInterfaces) {
                registerInterface(api, service);
            }
        }
    }

    private void registerInterface(Class<?> api, Object service) {
        Object existing = beans.putIfAbsent(api.getName(), service);
        if (existing != null && existing != service) {
            throw new SimpleRpcException("接口存在多个服务实现：" + api.getName());
        }
        if (interfaces.putIfAbsent(api.getName(), api) == null) {
            interfaceApi.add(api);
        }
        for (Class<?> parent : api.getInterfaces()) {
            registerInterface(parent, service);
        }
    }
}
