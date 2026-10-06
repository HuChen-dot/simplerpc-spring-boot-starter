package org.hu.rpcintegration;

import org.hu.rpc.core.server.RpcServerHandler;

import com.alibaba.fastjson.JSON;
import io.netty.channel.embedded.EmbeddedChannel;
import org.hu.rpc.annotation.RpcService;
import org.hu.rpc.common.entity.RpcRequest;
import org.hu.rpc.common.entity.RpcResponse;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class RpcServerHandlerTest {
    private RpcServerHandler handler;

    public interface ParentApi { String inherited(); }
    public interface Api extends ParentApi {
        String greet(Person person);
        String names(List<Person> people);
        String fail();
    }
    public interface OtherApi { String other(); }
    public static class Person {
        private String name;
        public Person() { }
        public Person(String name) { this.name = name; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
    public static class BaseService { public String inherited() { return "parent"; } }
    @RpcService
    public static class Service extends BaseService implements Api, OtherApi {
        public String greet(Person person) { return person.getName(); }
        public String names(List<Person> people) { return people.get(0).getName(); }
        public String fail() { throw new IllegalStateException("business failure"); }
        public String other() { return "other"; }
        public String internal() { return "must not be exposed"; }
    }

    @Before
    public void setUp() {
        handler = new RpcServerHandler();
        handler.beans.clear();
        StaticApplicationContext context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("service", new Service());
        handler.setApplicationContext(context);
        handler.afterSingletonsInstantiated();
    }

    private RpcResponse invoke(Class<?> api, String method, Class<?>[] types, Object... args) {
        RpcRequest request = new RpcRequest();
        request.setRequestId("test-id");
        request.setClassName(api.getName());
        request.setMethodName(method);
        request.setParameterTypes(types);
        request.setParameters(args);
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.writeInbound(JSON.toJSONString(request));
            String response = channel.readOutbound();
            assertNotNull("server must return a response", response);
            return JSON.parseObject(response, RpcResponse.class);
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void inheritedImplementationCanBeCalled() {
        RpcResponse response = invoke(Api.class, "inherited", new Class<?>[0]);
        assertNull(response.getError());
        assertEquals("parent", response.getResult());
    }

    @Test
    public void dtoArgumentsAreConvertedToDeclaredTypes() {
        RpcResponse response = invoke(Api.class, "greet", new Class<?>[]{Person.class}, new Person("中文"));
        assertNull(response.getError());
        assertEquals("中文", response.getResult());
    }

    @Test
    public void genericArgumentsAreConvertedToDeclaredTypes() {
        RpcResponse response = invoke(Api.class, "names", new Class<?>[]{List.class}, Collections.singletonList(new Person("list")));
        assertNull(response.getError());
        assertEquals("list", response.getResult());
    }

    @Test
    public void businessExceptionIsReturnedToConsumer() {
        assertEquals("business failure", invoke(Api.class, "fail", new Class<?>[0]).getError());
    }

    @Test
    public void allServiceInterfacesAreRegistered() {
        assertEquals("other", invoke(OtherApi.class, "other", new Class<?>[0]).getResult());
        assertEquals("parent", invoke(ParentApi.class, "inherited", new Class<?>[0]).getResult());
    }

    @Test
    public void malformedRequestReturnsErrorAndRequestClassesAreNotLoaded() {
        EmbeddedChannel channel = new EmbeddedChannel(handler);
        try {
            channel.writeInbound("{}");
            assertNotNull(JSON.parseObject((String) channel.readOutbound(), RpcResponse.class).getError());
            channel.writeInbound("{\"requestId\":\"unsafe\",\"className\":\"" + Api.class.getName()
                    + "\",\"methodName\":\"greet\",\"parameterTypes\":[\"http://invalid!/Class\"],\"parameters\":[{}]}");
            RpcResponse response = JSON.parseObject((String) channel.readOutbound(), RpcResponse.class);
            assertEquals("unsafe", response.getRequestId());
            assertNotNull(response.getError());
        } finally { channel.finishAndReleaseAll(); }
    }

    @Test
    public void serviceTablesDoNotLeakBetweenContainers() {
        RpcServerHandler another = new RpcServerHandler();
        StaticApplicationContext empty = new StaticApplicationContext();
        another.setApplicationContext(empty);
        another.afterSingletonsInstantiated();
        assertTrue(another.beans.isEmpty());
        assertFalse(handler.beans.isEmpty());
    }

    @Test
    public void implementationOnlyMethodsAreNotExposed() {
        assertNotNull(invoke(Api.class, "internal", new Class<?>[0]).getError());
    }
}
