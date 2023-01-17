package org.hu.rpc.register.zk.server;

import org.hu.rpc.core.server.RpcServerHandler;
import org.hu.rpc.util.IpUtils;
import org.hu.rpc.register.zk.util.ZkClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.List;

/**
 * @Author: hu.chen
 * @Description: 服务提供者初始化
 * @DateTime: 2021/12/29 1:15 PM
 **/
@Component
public class ZkRegisterInit {


    @Autowired
    private ZkClientService zkClientService;

    @Autowired
    private RpcServerHandler rpcServerHandler;


    public void init(int port) {
        // 是否使用了 zk
        if (!zkClientService.isOpenzk()) {
            return;
        }

        // 获取根路径
        String namespace = zkClientService.getNameSpace();

        // 判断根路径是否存在
        if (!zkClientService.exists(namespace)) {
            // 如果不存在 则创建节点
            zkClientService.createPersistent(namespace);
        }

        //获取当前服务的 ip
        String localIpAddr = IpUtils.getLocalIpAddr();

        List<Class> interfaceApi = rpcServerHandler.interfaceApi;


        for (Class aClass : interfaceApi) {

            //拼装当前节点微服务的节点路径
            String servicePath=namespace+"/"+aClass.getName();

            // 判断当前节点是否存在
            if(!zkClientService.exists(servicePath)) {
                // 在zk上创建当前微服务的节点信息
                zkClientService.createPersistent(servicePath);
            }
            String ipPath=servicePath + "/" + localIpAddr + ":" + port;

            //将当前服务的ip:端口，注册到根节点/当前微服务节点下,创建成临时节点
            zkClientService.createEphemeral(ipPath);
        }

    }

    /**
     * 容器关闭时，关闭和zk服务器的连接
     */
    @PreDestroy
    public void close(){
        if (zkClientService.isOpenzk()) {
            zkClientService.close();
        }
    }


}
