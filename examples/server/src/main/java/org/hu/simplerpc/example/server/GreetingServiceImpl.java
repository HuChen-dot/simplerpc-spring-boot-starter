package org.hu.simplerpc.example.server;

import org.hu.rpc.annotation.RpcService;
import org.hu.simplerpc.example.api.GreetingService;

@RpcService
public class GreetingServiceImpl implements GreetingService {
    @Override
    public String greet(String name) {
        return "你好，" + name + "！来自 SimpleRPC 服务端。";
    }
}
