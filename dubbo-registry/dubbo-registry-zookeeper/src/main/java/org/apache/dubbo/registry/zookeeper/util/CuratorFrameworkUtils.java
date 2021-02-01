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
package org.apache.dubbo.registry.zookeeper.util;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.registry.client.DefaultServiceInstance;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.registry.zookeeper.ZookeeperInstance;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstanceBuilder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.curator.x.discovery.ServiceInstance.builder;
import static org.apache.dubbo.registry.zookeeper.util.CuratorFrameworkParams.BASE_SLEEP_TIME;
import static org.apache.dubbo.registry.zookeeper.util.CuratorFrameworkParams.BLOCK_UNTIL_CONNECTED_UNIT;
import static org.apache.dubbo.registry.zookeeper.util.CuratorFrameworkParams.BLOCK_UNTIL_CONNECTED_WAIT;
import static org.apache.dubbo.registry.zookeeper.util.CuratorFrameworkParams.MAX_RETRIES;
import static org.apache.dubbo.registry.zookeeper.util.CuratorFrameworkParams.MAX_SLEEP;

/**
 * Curator Framework Utilities Class
 *
 * @since 2.7.5
 */
// OK
public abstract class CuratorFrameworkUtils {

    // gx
    public static ServiceDiscovery<ZookeeperInstance> buildServiceDiscovery(CuratorFramework curatorFramework,
                                                                            String basePath) {
        return ServiceDiscoveryBuilder.builder(ZookeeperInstance.class)
                .client(curatorFramework)
                .basePath(basePath)
                .build();
    }

    // gx
    public static CuratorFramework buildCuratorFramework(URL connectionURL) throws Exception {
        CuratorFramework curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(connectionURL.getIp() + ":" + connectionURL.getPort())
                .retryPolicy(buildRetryPolicy(connectionURL))
                .build();
        curatorFramework.start();
        curatorFramework.blockUntilConnected(BLOCK_UNTIL_CONNECTED_WAIT.getParameterValue(connectionURL),
                BLOCK_UNTIL_CONNECTED_UNIT.getParameterValue(connectionURL));
        // 这个就是zkClient
        return curatorFramework;
    }

    public static RetryPolicy buildRetryPolicy(URL connectionURL) {
        int baseSleepTimeMs = BASE_SLEEP_TIME.getParameterValue(connectionURL);
        int maxRetries = MAX_RETRIES.getParameterValue(connectionURL);
        int getMaxSleepMs = MAX_SLEEP.getParameterValue(connectionURL);
        // 指数退避重试
        return new ExponentialBackoffRetry(baseSleepTimeMs, maxRetries, getMaxSleepMs);
    }


    // gx
    public static List<ServiceInstance> build(Collection<org.apache.curator.x.discovery.ServiceInstance<ZookeeperInstance>>
                                                      instances) {
        // build方法将Curator的ServiceInstance转化为项目自己的ServiceInstance，build进去
        return instances.stream().map(CuratorFrameworkUtils::build).collect(Collectors.toList());
    }

    // gx
    public static ServiceInstance build(org.apache.curator.x.discovery.ServiceInstance<ZookeeperInstance> instance) {
        String name = instance.getName();
        String host = instance.getAddress();
        int port = instance.getPort();
        ZookeeperInstance zookeeperInstance = instance.getPayload();
        DefaultServiceInstance serviceInstance = new DefaultServiceInstance(instance.getId(), name, host, port);
        // zk的属性塞到serviceInstance中（和zk交互信息载体永远都是我们自定义zookeeperInstance类，明面上注册和取值虽然是用的
        // DefaultServiceInstance，但是到最后一步都是用的ZookeeperInstance，详见下面的build方法）
        serviceInstance.setMetadata(zookeeperInstance.getMetadata());
        return serviceInstance;
    }

    public static org.apache.curator.x.discovery.ServiceInstance<ZookeeperInstance> build(ServiceInstance serviceInstance) {
        ServiceInstanceBuilder builder = null;
        String serviceName = serviceInstance.getServiceName();
        String host = serviceInstance.getHost();
        int port = serviceInstance.getPort();
        Map<String, String> metadata = serviceInstance.getMetadata();
        String id = generateId(host, port);
        ZookeeperInstance zookeeperInstance = new ZookeeperInstance(null, serviceName, metadata);
        try {
            builder = builder()
                    .id(id)//
                    .name(serviceName) // (看下面zk输出例子，A就是这里的serviceName，其三个子节点就是上面的id)
                    .address(host)
                    .port(port)
                    // ZookeeperInstance是我们自己的类，也是数据载体
                    .payload(zookeeperInstance);
            // [zk: localhost:2181(CONNECTED) 29] ls /services/A
            // [127.0.0.1:8080, 127.0.0.1:8081, 127.0.0.1:8082]
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return builder.build();
    }

    public static final String generateId(String host, int port) {
        return host + ":" + port;
    }
}
