package org.hu.rpc.common.service;

import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.*;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/26 10:13 PM
 **/
public class ThreadPoolService {

    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(60, 80, 3, TimeUnit.SECONDS
            , new ArrayBlockingQueue<>(200), new DefaultThreadFactory("worker"));

    public static ThreadPoolExecutor poolExecutor(){

        Executors.newCachedThreadPool();
        return threadPoolExecutor;
    }
}
