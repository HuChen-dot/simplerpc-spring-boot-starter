package org.hu.rpc.register.zk.client;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.hu.rpc.core.route.RouteStrategy;
import org.hu.rpc.register.zk.util.ZkClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

import static org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type.CHILD_ADDED;
import static org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent.Type.CHILD_REMOVED;

/**
 * @Author: hu.chen
 * @Description: 服务消费者启动初始化
 * @DateTime: 2021/12/29 1:36 PM
 **/
@Component
public class ZkClientInit {

    @Autowired
    private ZkClientService zkClientService;

    @Autowired
    private  RouteStrategy routeStrategy;

    private Set<String> nodeSet=new HashSet<>();

    @PostConstruct
    public void init() {
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
        Map<String, List<String[]>> mapAddress = routeStrategy.getMapAddress();

        zkClientService.addNodeListener(namespace, new PathChildrenCacheListener() {
            @Override
            public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent pathChildrenCacheEvent) throws Exception {

                if(CHILD_ADDED==pathChildrenCacheEvent.getType()||CHILD_REMOVED==pathChildrenCacheEvent.getType()){

                    List<String> nodes = zkClientService.getNodes(namespace);

                    // 给根节点的新添加的子节点创建监听
                    addNodeListener(nodes, namespace, mapAddress);

                }
            }
        });

        // 获取根路径下的子节点
        List<String> childNodes = zkClientService.getNodes(namespace);

        addNodeListener(childNodes, namespace, mapAddress);
    }


    /**
     * 给子节点创建监听
     * @param childNodes
     * @param namespace
     * @param mapAddress
     */
    private void addNodeListener(List<String> childNodes,String namespace,Map<String, List<String[]>> mapAddress){
        //遍历子节点，并给子节点建立监听
        for (String childNode : childNodes) {
            if (!nodeSet.add(childNode)) {
                continue;
            }
            String path = namespace + "/" + childNode;

            List<String> ipNodes = zkClientService.getNodes(path);

            zkClientService.addNodeListener(path, new PathChildrenCacheListener() {
                @Override
                public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent pathChildrenCacheEvent) throws Exception {

                    if(CHILD_ADDED==pathChildrenCacheEvent.getType()||CHILD_REMOVED==pathChildrenCacheEvent.getType()){
                        List<String> list = zkClientService.getNodes(path);

                        Map<String, List<String[]>> mapAddress1 = routeStrategy.getMapAddress();
                        List<String[]> ipArray = new ArrayList<>();
                        for (String ipNode : list) {
                            ipArray.add(ipNode.split(":"));
                        }
                        mapAddress1.put(childNode, ipArray);

                    }
                }
            });


            List<String[]> ipArray = new ArrayList<>();
            for (String ipNode : ipNodes) {
                ipArray.add(ipNode.split(":"));
            }
            mapAddress.put(childNode, ipArray);
        }
    }
}
