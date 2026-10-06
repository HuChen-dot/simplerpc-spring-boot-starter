package org.hu.rpc.proxy;

import org.hu.rpc.annotation.RpcAutowired;
import org.hu.rpc.exception.SimpleRpcException;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Modifier;

/** 在 Bean 初始化前注入代理，支持父类字段且不提前遍历整个容器。 */
@Component
public class RpcAutowiredBeanPostProcessor implements InstantiationAwareBeanPostProcessor, BeanFactoryAware {
    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public PropertyValues postProcessProperties(PropertyValues properties, Object bean, String beanName) {
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            if (!field.isAnnotationPresent(RpcAutowired.class)) {
                return;
            }
            if (!field.getType().isInterface() || Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                throw new SimpleRpcException("@RpcAutowired 必须标记非 static/final 的接口字段：" + field);
            }
            ReflectionUtils.makeAccessible(field);
            ReflectionUtils.setField(field, bean, beanFactory.getBean(JdkProxy.class).createProxy(field.getType()));
        });
        return properties;
    }
}
