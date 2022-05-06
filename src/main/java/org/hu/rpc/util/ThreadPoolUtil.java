package org.hu.rpc.util;

import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.*;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/26 10:13 PM
 **/
public class ThreadPoolUtil {

    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(60, 80, 3, TimeUnit.SECONDS
            , new ArrayBlockingQueue<>(200), new DefaultThreadFactory("worker"));

    public static ThreadPoolExecutor poolExecutor(){

        Executors.newCachedThreadPool();
        return threadPoolExecutor;
    }
}
