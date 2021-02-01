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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.Page;
import org.apache.dubbo.event.EventDispatcher;
import org.apache.dubbo.registry.client.DefaultServiceInstance;
import org.apache.dubbo.registry.client.ServiceInstance;

import org.apache.curator.test.TestingServer;
import org.apache.dubbo.registry.client.event.ServiceInstancesChangedEvent;
import org.apache.dubbo.registry.client.event.listener.ServiceInstancesChangedListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;

import static java.util.Arrays.asList;
import static org.apache.dubbo.common.utils.NetUtils.getAvailablePort;
import static org.apache.dubbo.registry.zookeeper.util.CuratorFrameworkUtils.generateId;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ZookeeperServiceDiscovery} Test
 *
 * @since 2.7.5
 */
// OK
public class ZookeeperServiceDiscoveryTest {

    private static final String SERVICE_NAME = "A";

    private static final String LOCALHOST = "127.0.0.1";

    private TestingServer zkServer;
    private int zkServerPort;
    private URL registryUrl;

    private ZookeeperServiceDiscovery discovery;

    @BeforeEach
    public void init() throws Exception {
        EventDispatcher.getDefaultExtension().removeAllEventListeners();
//        zkServerPort = getAvailablePort();
//        zkServer = new TestingServer(zkServerPort, true);
//        zkServer.start();

        zkServerPort = 2181;

        this.registryUrl = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort);
        this.discovery = new ZookeeperServiceDiscovery();
        this.discovery.initialize(registryUrl); // 进去
    }

    //@AfterEach
    public void close() throws Exception {
        discovery.destroy();
        zkServer.stop();
    }

    @Test
    public void testRegistration() {
        // 进去
        DefaultServiceInstance serviceInstance = createServiceInstance(SERVICE_NAME, LOCALHOST, NetUtils.getAvailablePort());

        discovery.register(serviceInstance);// 进去
        // 此时 有节点/services/A ，还有一个子节点[127.0.0.1:54461] （A是serviceName，ip:port是id）（当然要快速看，晚一会就没了）

        List<ServiceInstance> serviceInstances = discovery.getInstances(SERVICE_NAME);// 进去

        assertTrue(serviceInstances.contains(serviceInstance));
        assertEquals(asList(serviceInstance), serviceInstances);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("message", "Hello,World");
        serviceInstance.setMetadata(metadata);

        // 进去
        discovery.update(serviceInstance);
        // get /services/A/127.0.0.1:55073
        //{"name":"A","id":"127.0.0.1:55073","address":"127.0.0.1","port":55073,"sslPort":null,"payload":{"@class":"org.apache.dubbo.registry.zookeeper.ZookeeperInstance","id":null,"name":"A","metadata":{}},"registrationTimeUTC":1610526105860,"serviceType":"DYNAMIC","uriSpec":null}

        serviceInstances = discovery.getInstances(SERVICE_NAME);

        assertEquals(serviceInstance, serviceInstances.get(0));

        // 进去
        discovery.unregister(serviceInstance);

        serviceInstances = discovery.getInstances(SERVICE_NAME);

        assertTrue(serviceInstances.isEmpty());

    }

    private DefaultServiceInstance createServiceInstance(String serviceName, String host, int port) {
        return new DefaultServiceInstance(generateId(host, port), serviceName, host, port);
    }

    @Test
    public void testGetInstances() throws InterruptedException {

        List<ServiceInstance> instances = asList(
                createServiceInstance(SERVICE_NAME, LOCALHOST, 8080),
                createServiceInstance(SERVICE_NAME, LOCALHOST, 8081),
                createServiceInstance(SERVICE_NAME, LOCALHOST, 8082)
        );
        //0 = {DefaultServiceInstance@2328} "DefaultServiceInstance{id='127.0.0.1:8080', serviceName='A', host='127.0.0.1', port=8080, enabled=true, healthy=true, metadata={}}"
        //1 = {DefaultServiceInstance@2329} "DefaultServiceInstance{id='127.0.0.1:8081', serviceName='A', host='127.0.0.1', port=8081, enabled=true, healthy=true, metadata={}}"
        //2 = {DefaultServiceInstance@2330} "DefaultServiceInstance{id='127.0.0.1:8082', serviceName='A', host='127.0.0.1', port=8082, enabled=true, healthy=true, metadata={}}"

        // 进去，
        instances.forEach(discovery::register);
        // 终端连zk看下
        //[zk: localhost:2181(CONNECTED) 19] ls /services/A
        //[127.0.0.1:8080, 127.0.0.1:8081, 127.0.0.1:8082]

        List<ServiceInstance> serviceInstances = new LinkedList<>();

        CountDownLatch latch = new CountDownLatch(1);

        Set<String> serviceNames = new HashSet<>();
        serviceNames.add(SERVICE_NAME);
        // Add Listener，两个方法都进去
        discovery.addServiceInstancesChangedListener(new ServiceInstancesChangedListener(serviceNames,discovery) {
            @Override
            public void onEvent(ServiceInstancesChangedEvent event) {
                serviceInstances.addAll(event.getServiceInstances());
                latch.countDown();
            }
        });

        discovery.register(createServiceInstance(SERVICE_NAME, LOCALHOST, 8082));// 这个会触发上面的监听
        discovery.update(createServiceInstance(SERVICE_NAME, LOCALHOST, 8082));

        latch.await();

        assertFalse(serviceInstances.isEmpty());

        // offset starts 0
        int offset = 0;
        // requestSize > total elements
        int requestSize = 5;

        // 进去
        Page<ServiceInstance> page = discovery.getInstances(SERVICE_NAME, offset, requestSize);
        assertEquals(0, page.getOffset());
        assertEquals(5, page.getPageSize());
        assertEquals(3, page.getTotalSize());
        assertEquals(3, page.getData().size());
        assertTrue(page.hasData());

        for (ServiceInstance instance : page.getData()) {
            assertTrue(instances.contains(instance));
        }

        // requestSize < total elements
        requestSize = 2;

        page = discovery.getInstances(SERVICE_NAME, offset, requestSize);
        assertEquals(0, page.getOffset());
        assertEquals(2, page.getPageSize());
        assertEquals(3, page.getTotalSize());
        assertEquals(2, page.getData().size());
        assertTrue(page.hasData());

        for (ServiceInstance instance : page.getData()) {
            assertTrue(instances.contains(instance));
        }

        offset = 1;
        page = discovery.getInstances(SERVICE_NAME, offset, requestSize);
        assertEquals(1, page.getOffset());
        assertEquals(2, page.getPageSize());
        assertEquals(3, page.getTotalSize());
        assertEquals(2, page.getData().size());
        assertTrue(page.hasData());

        for (ServiceInstance instance : page.getData()) {
            assertTrue(instances.contains(instance));
        }

        offset = 2;
        page = discovery.getInstances(SERVICE_NAME, offset, requestSize);
        assertEquals(2, page.getOffset());
        assertEquals(2, page.getPageSize());
        assertEquals(3, page.getTotalSize());
        assertEquals(1, page.getData().size());
        assertTrue(page.hasData());

        offset = 3;
        page = discovery.getInstances(SERVICE_NAME, offset, requestSize);
        assertEquals(3, page.getOffset());
        assertEquals(2, page.getPageSize());
        assertEquals(3, page.getTotalSize());
        assertEquals(0, page.getData().size());
        assertFalse(page.hasData());

        offset = 5;
        page = discovery.getInstances(SERVICE_NAME, offset, requestSize);
        assertEquals(5, page.getOffset());
        assertEquals(2, page.getPageSize());
        assertEquals(3, page.getTotalSize());
        assertEquals(0, page.getData().size());
        assertFalse(page.hasData());

    }
}
