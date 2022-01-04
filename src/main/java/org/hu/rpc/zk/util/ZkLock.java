package org.hu.rpc.zk.util;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;

import static org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type.CHILD_ADDED;
import static org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type.CHILD_REMOVED;

/**
 * @Author: hu.chen
 * @Description: zk实现分布式锁-（排他锁）
 * @DateTime: 2021/12/30 10:05 AM
 **/
@Component
public class ZkLock {

  private  Logger log = LoggerFactory.getLogger(ZkLock.class);

    private static final String ROOT_NODE = "/zklock";

    private static final String LOCK_NODE = "/lock";

    private static CountDownLatch countDownLatch = new CountDownLatch(1);

    @Autowired
    private ZkClientUtils zkClientUtils;

    /**
     * 是否第一次运行
     */
    private static volatile boolean isOenRun = true;

    private static volatile boolean isRun = true;


    public void lock() {


        // 在zk的一个固定根节点，创建一个临时子节点
        // 如果根节点不存在，则创建根节点
        check();
        while (true) {
            try {
                // 如果程序不是运行状态，则抛异常
                if (!isRun) {
                    throw new Exception();
                }
                // 创建锁的临时节点
                zkClientUtils.createEphemeral(zkClientUtils.getNameSpace() + ROOT_NODE + LOCK_NODE);
                return;
            } catch (Exception e) {
                zkClientUtils.addNodeListener(zkClientUtils.getNameSpace() + ROOT_NODE, new PathChildrenCacheListener() {
                    @Override
                    public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent pathChildrenCacheEvent) throws Exception {

                        if(CHILD_ADDED==pathChildrenCacheEvent.getType()||CHILD_REMOVED==pathChildrenCacheEvent.getType()){
                            //锁被释放了
                            countDownLatch.countDown();

                        }
                    }
                });

                //如果没有获取到锁,需要重新设置同步资源值
                if (countDownLatch.getCount() <= 0) {
                    countDownLatch = new CountDownLatch(1);
                }

                // 进行休眠等待
                try {
                    countDownLatch.await();
                } catch (InterruptedException ex) {
                    log.error("获取锁失败，休眠时异常：{}",e);
                }
            }
        }

    }


    /**
     * 检验参数，并设置虚拟机关闭的回调
     */
    private void check() {
        if (isOenRun) {
            synchronized (ZkLock.class) {
                if (isOenRun) {
                    if (!zkClientUtils.exists(zkClientUtils.getNameSpace() + ROOT_NODE)) {
                        zkClientUtils.createPersistent(zkClientUtils.getNameSpace() + ROOT_NODE);
                    }
                    // 注册在虚拟机关闭时的回调
                    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                        @Override
                        public void run() {
                            log.info("程序关闭，执行回调删除锁.....");
                            isRun = false;
                            //释放锁
                            zkClientUtils.delete(zkClientUtils.getNameSpace() + ROOT_NODE + LOCK_NODE);
                        }
                    }));
                    // 状态修改
                    isOenRun = false;
                }
            }
        }
    }


    /**
     * 释放锁
     */
    public void unLock() {
        //释放锁
        zkClientUtils.delete(zkClientUtils.getNameSpace() + ROOT_NODE + LOCK_NODE);
    }

}
