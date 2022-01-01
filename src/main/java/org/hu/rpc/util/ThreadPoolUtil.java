package org.hu.rpc.util;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/26 10:13 PM
 **/
public class ThreadPoolUtil {

    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(60, 80, 3, TimeUnit.SECONDS
            , new ArrayBlockingQueue<>(200));

    public static ThreadPoolExecutor poolExecutor(){
        return threadPoolExecutor;
    }
}
