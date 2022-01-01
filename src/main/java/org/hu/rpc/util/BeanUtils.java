package org.hu.rpc.util;


import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @Author: hu.chen
 * @Description: 实体类之间属性拷贝（深拷贝）
 * @DateTime: 2021/9/28 3:40 下午
 **/
@Component
public class BeanUtils implements ApplicationContextAware {

    private static ApplicationContext ctx;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ctx = applicationContext;
    }


    public static ApplicationContext getCtx() {
        return ctx;
    }


    /**
     * 通过bean的名称获取bean
     *
     * @param beanName
     * @param <T>
     * @return
     */
    public static <T> T getBean(String beanName) {
        return (T) ctx.getBean(beanName);
    }

    /**
     * 通过bean的类型获取bean
     *
     * @param beanClazz
     * @param <T>
     * @return
     */
    public static <T> T getBean(Class<T> beanClazz) {
        return ctx.getBean(beanClazz);
    }

}