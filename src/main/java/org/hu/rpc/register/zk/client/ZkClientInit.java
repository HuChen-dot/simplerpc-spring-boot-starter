package org.hu.rpc.register.zk.client;

import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.hu.rpc.core.route.RouteStrategy;
import org.hu.rpc.register.zk.util.ZkClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

@Component
public class ZkClientInit {
    @Autowired
    private ZkClientService zkClientService;
    @Autowired
    private RouteStrategy routeStrategy;
    private final Set<String> watchedServices = new HashSet<>();

    @PostConstruct
    public void init() {
        if (!zkClientService.isEnabled()) { return; }
        String namespace = zkClientService.getNamespace();
        zkClientService.createPersistent(namespace);
        zkClientService.addNodeListener(namespace, (client, event) -> {
            if (addressesChanged(event)) { refreshServices(); }
        });
        refreshServices();
    }

    private boolean addressesChanged(PathChildrenCacheEvent event) {
        switch (event.getType()) {
            case CHILD_ADDED:
            case CHILD_REMOVED:
            case INITIALIZED:
            case CONNECTION_RECONNECTED:
                return true;
            default:
                return false;
        }
    }

    private synchronized void refreshServices() {
        String namespace = zkClientService.getNamespace();
        List<String> services = zkClientService.getNodes(namespace);
        routeStrategy.getMapAddress().keySet().retainAll(services);
        for (String service : services) {
            if ("zklock".equals(service)) { continue; }
            if (watchedServices.add(service)) {
                try {
                    zkClientService.addNodeListener(namespace + "/" + service, (client, event) -> {
                        if (addressesChanged(event)) { refreshAddresses(service); }
                    });
                } catch (RuntimeException e) {
                    watchedServices.remove(service);
                    throw e;
                }
            }
            refreshAddresses(service);
        }
    }

    private synchronized void refreshAddresses(String service) {
        List<String[]> addresses = new ArrayList<>();
        for (String address : zkClientService.getNodes(zkClientService.getNamespace() + "/" + service)) {
            addresses.add(RouteStrategy.parseAddress(address));
        }
        routeStrategy.getMapAddress().put(service, Collections.unmodifiableList(addresses));
    }
}
