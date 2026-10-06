package org.hu.simplerpc.example.client;

import org.hu.rpc.annotation.RpcAutowired;
import org.hu.simplerpc.example.api.GreetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
public class GreetingController {
    @RpcAutowired
    private GreetingService greetingService;

    @GetMapping("/greet")
    public Map<String, String> greet(@RequestParam(value = "name", defaultValue = "世界") String name) {
        return Collections.singletonMap("message", greetingService.greet(name));
    }
}
