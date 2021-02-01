/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.registry.zookeeper;

import org.apache.dubbo.registry.client.ServiceDiscovery;
import org.apache.dubbo.registry.client.event.ServiceInstancesChangedEvent;
import org.apache.dubbo.registry.client.event.listener.ServiceInstancesChangedListener;

import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import static org.apache.zookeeper.Watcher.Event.EventType.NodeChildrenChanged;
import static org.apache.zookeeper.Watcher.Event.EventType.NodeDataChanged;

/**
 * Zookeeper {@link ServiceDiscovery} Change {@link CuratorWatcher watcher} only interests in
 * {@link Watcher.Event.EventType#NodeChildrenChanged} and {@link Watcher.Event.EventType#NodeDataChanged} event types,
 * which will multicast a {@link ServiceInstancesChangedEvent} when the service node has been changed.
 *
 * @since 2.7.5
 */
// OK
// 实现 CuratorWatcher
public class ZookeeperServiceDiscoveryChangeWatcher implements CuratorWatcher {
    private ServiceInstancesChangedListener listener;

    private final ZookeeperServiceDiscovery zookeeperServiceDiscovery;

    private final String serviceName;

    // gx
    public ZookeeperServiceDiscoveryChangeWatcher(ZookeeperServiceDiscovery zookeeperServiceDiscovery,
                                                  String serviceName,
                                                  ServiceInstancesChangedListener listener) {
        this.zookeeperServiceDiscovery = zookeeperServiceDiscovery;
        this.serviceName = serviceName;
        this.listener = listener;
    }

    @Override
    public void process(WatchedEvent event) throws Exception {

        Watcher.Event.EventType eventType = event.getType();

        if (NodeChildrenChanged.equals(eventType) || NodeDataChanged.equals(eventType)) {
            // zookeeperServiceDiscovery.getInstances(serviceName) 获取该serviceName节点下的所有子节点
            listener.onEvent(new ServiceInstancesChangedEvent(serviceName, zookeeperServiceDiscovery.getInstances(serviceName)));
            // watcher使用一次就没了，这里重复注册，进去
            zookeeperServiceDiscovery.registerServiceWatcher(serviceName, listener);
            //
            zookeeperServiceDiscovery.dispatchServiceInstancesChangedEvent(serviceName);
        }
    }
}
