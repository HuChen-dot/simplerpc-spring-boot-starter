package org.hu.rpc.annotation;

import java.lang.annotation.*;
import org.springframework.stereotype.Component;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/26 7:46 PM
 **/
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface RpcService {

}
