package org.hu.rpc.zk.client;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.hu.rpc.core.route.RouteStrategy;
import org.hu.rpc.zk.util.ZkClientUtils;
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
public class CilentInit {

    @Autowired
    private ZkClientUtils zkClientUtils;

    @Autowired
    private  RouteStrategy routeStrategy;

    private Set<String> nodeSet=new HashSet<>();

    @PostConstruct
    public void init() {
        // 是否使用了 zk
        if (!zkClientUtils.isOpenzk()) {
            return;
        }

        // 获取根路径
        String namespace = zkClientUtils.getNameSpace();

        // 判断根路径是否存在
        if (!zkClientUtils.exists(namespace)) {
            // 如果不存在 则创建节点
            zkClientUtils.createPersistent(namespace);
        }
        Map<String, List<String[]>> mapAddress = routeStrategy.getMapAddress();

        zkClientUtils.addNodeListener(namespace, new PathChildrenCacheListener() {
            @Override
            public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent pathChildrenCacheEvent) throws Exception {

                if(CHILD_ADDED==pathChildrenCacheEvent.getType()||CHILD_REMOVED==pathChildrenCacheEvent.getType()){

                    List<String> nodes = zkClientUtils.getNodes(namespace);

                    // 给根节点的新添加的子节点创建监听
                    addNodeListener(nodes, namespace, mapAddress);

                }
            }
        });



        // 获取根路径下的子节点
        List<String> childNodes = zkClientUtils.getNodes(namespace);

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

            List<String> ipNodes = zkClientUtils.getNodes(path);

            zkClientUtils.addNodeListener(path, new PathChildrenCacheListener() {
                @Override
                public void childEvent(CuratorFramework curatorFramework, PathChildrenCacheEvent pathChildrenCacheEvent) throws Exception {

                    if(CHILD_ADDED==pathChildrenCacheEvent.getType()||CHILD_REMOVED==pathChildrenCacheEvent.getType()){
                        List<String> list = zkClientUtils.getNodes(path);

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
