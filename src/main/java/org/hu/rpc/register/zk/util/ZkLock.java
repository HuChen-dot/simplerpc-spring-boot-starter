package org.hu.rpc.register.zk.util;

import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.hu.rpc.exception.SimpleRpcException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 使用 Curator 的可重入分布式锁；必须在获取锁的线程释放。 */
@Component
public class ZkLock {
    @Autowired
    private ZkClientService zkClientService;
    private volatile InterProcessMutex mutex;

    private InterProcessMutex mutex() {
        if (mutex == null) {
            synchronized (this) {
                if (mutex == null) {
                    mutex = new InterProcessMutex(zkClientService.getClient(),
                            zkClientService.getNamespace() + "/zklock/lock");
                }
            }
        }
        return mutex;
    }

    public void lock() {
        try {
            mutex().acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SimpleRpcException("获取 ZooKeeper 锁被中断", e);
        } catch (Exception e) {
            throw new SimpleRpcException("获取 ZooKeeper 锁失败", e);
        }
    }

    public void unlock() {
        if (mutex == null) { throw new SimpleRpcException("当前线程未持有 ZooKeeper 锁"); }
        try {
            mutex.release();
        } catch (Exception e) {
            throw new SimpleRpcException("释放 ZooKeeper 锁失败", e);
        }
    }
}
