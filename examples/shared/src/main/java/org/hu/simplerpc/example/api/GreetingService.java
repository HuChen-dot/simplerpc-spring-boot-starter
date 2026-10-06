package org.hu.simplerpc.example.api;

/** 服务端和客户端使用相同的接口全名及方法签名。 */
public interface GreetingService {
    String greet(String name);
}
