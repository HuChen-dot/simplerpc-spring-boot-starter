package org.hu.rpc.annotation;

import java.lang.annotation.*;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/26 9:48 PM
 **/
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcAutowired {
}
