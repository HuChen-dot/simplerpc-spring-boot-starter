package org.hu.rpc;

import org.hu.rpc.core.server.NettyRpcServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: hu.chen
 * @Description:
 * @DateTime: 2021/12/26 7:21 PM
 **/
@Configuration
public class RpcStartMain implements CommandLineRunner {

    @Autowired
    private NettyRpcServer nettyRpcServer;

    @Override
    public void run(String... args) throws Exception {
        // 启动 Netty 服务端
        new Thread(nettyRpcServer).start();
    }
}
