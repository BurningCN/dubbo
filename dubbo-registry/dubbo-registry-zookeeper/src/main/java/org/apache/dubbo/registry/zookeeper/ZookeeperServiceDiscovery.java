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
import org.apache.dubbo.common.function.ThrowableConsumer;
import org.apache.dubbo.common.function.ThrowableFunction;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.DefaultPage;
import org.apache.dubbo.common.utils.Page;
import org.apache.dubbo.event.EventDispatcher;
import org.apache.dubbo.registry.client.ServiceDiscovery;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.registry.client.event.listener.ServiceInstancesChangedListener;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.KeeperException;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.dubbo.common.function.ThrowableFunction.execute;
import static org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils.isInstanceUpdated;
import static org.apache.dubbo.registry.zookeeper.util.CuratorFrameworkParams.ROOT_PATH;
import static org.apache.dubbo.registry.zookeeper.util.CuratorFrameworkUtils.build;
import static org.apache.dubbo.registry.zookeeper.util.CuratorFrameworkUtils.buildCuratorFramework;
import static org.apache.dubbo.registry.zookeeper.util.CuratorFrameworkUtils.buildServiceDiscovery;

/**
 * Zookeeper {@link ServiceDiscovery} implementation based on
 * <a href="https://curator.apache.org/curator-x-discovery/index.html">Apache Curator X Discovery</a>
 */

// OK 可以看下curator discovery的相关入门文章，简单了解下
public class ZookeeperServiceDiscovery implements ServiceDiscovery {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private URL registryURL;

    private CuratorFramework curatorFramework;

    private String rootPath;

    private org.apache.curator.x.discovery.ServiceDiscovery<ZookeeperInstance> serviceDiscovery;

    private ServiceInstance serviceInstance;

    /**
     * The Key is watched Zookeeper path, the value is an instance of {@link CuratorWatcher}
     */
    private final Map<String, CuratorWatcher> watcherCaches = new ConcurrentHashMap<>();

    // 主要被 EventPublishingServiceDiscovery 调用
    @Override
    public void initialize(URL registryURL) throws Exception {
        // eg zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=demo-provider&dubbo=2.0.2&id=org.apache.dubbo.config.RegistryConfig&interface=org.apache.dubbo.registry.client.ServiceDiscovery&metadata-type=remote&pid=67248&timestamp=1619165300619
        this.registryURL = registryURL;
        this.curatorFramework = buildCuratorFramework(registryURL); // 进去
        // 进去 默认值 /services
        this.rootPath = ROOT_PATH.getParameterValue(registryURL);
        this.serviceDiscovery = buildServiceDiscovery(curatorFramework, rootPath);// 进去
        // 和buildCuratorFramework一样，建立完成都需要start
        this.serviceDiscovery.start();
    }

    @Override
    public URL getUrl() {
        return registryURL;
    }

    public void destroy() throws Exception {
        serviceDiscovery.close();
    }

    @Override
    public ServiceInstance getLocalInstance() {
        return serviceInstance;
    }

    public void register(ServiceInstance serviceInstance) throws RuntimeException {
        this.serviceInstance = serviceInstance;
        // doInServiceRegistry、build进去  registerService是ServiceDiscovery的api
        doInServiceRegistry(serviceDiscovery -> {
            serviceDiscovery.registerService(build(serviceInstance));
        });
    }

    public void update(ServiceInstance serviceInstance) throws RuntimeException {
        this.serviceInstance = serviceInstance;
        // 进去
        if (isInstanceUpdated(serviceInstance)) {
            doInServiceRegistry(serviceDiscovery -> {
                // build进去 updateService是ServiceDiscovery的api
                serviceDiscovery.updateService(build(serviceInstance));
            });
        }
    }

    public void unregister(ServiceInstance serviceInstance) throws RuntimeException {
        doInServiceRegistry(serviceDiscovery -> {
            serviceDiscovery.unregisterService(build(serviceInstance));
        });
    }

    @Override
    public Set<String> getServices() {
        // 查全部的
        return doInServiceDiscovery(s -> new LinkedHashSet<>(s.queryForNames()));
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceName) throws NullPointerException {
        // build进去  queryForInstances是api，serviceName是appName
        // 看下面register的逻辑，会发起注册，完成的path为 /services/demo-provider/30.25.58.39:20880
        return doInServiceDiscovery(s -> build(s.queryForInstances(serviceName)));
    }

    // 分页查询
    @Override
    public Page<ServiceInstance> getInstances(String serviceName, int offset, int pageSize, boolean healthyOnly) {
        // 进去
        String path = buildServicePath(serviceName);// eg /services/A

        // execute进去
        return execute(path, p -> {

            List<ServiceInstance> serviceInstances = new LinkedList<>();

            // 写操作比较多的话使用LinkedList
            // 这里直接使用curatorFramework（zkClient）进行zk交互，其实前面的ServiceDiscovery在构造的时候传入了 curatorFramework，然后我们很方便的
            // 的进行注册、取消注册、查询内部逻辑实际还是利用curatorFramework！
            // 这个是取出p下的所有子节点
            List<String> serviceIds = new LinkedList<>(curatorFramework.getChildren().forPath(p));// eg p = /services/A

            int totalSize = serviceIds.size();

            Iterator<String> iterator = serviceIds.iterator();

            // sd本身是不支持分页的，这是在内存级别做分页，实际还是将serviceName下的所有的数据查出来了（上面getChildren）
            for (int i = 0; i < offset; i++) {
                if (iterator.hasNext()) { // remove the elements from 0 to offset
                    iterator.next();
                    iterator.remove();
                }
            }

            for (int i = 0; i < pageSize; i++) {
                if (iterator.hasNext()) {
                    String serviceId = iterator.next();
                    // serviceName是父节点，serviceId是其子节点。这里是取子节点的值。build进去
                    ServiceInstance serviceInstance = build(serviceDiscovery.queryForInstance(serviceName, serviceId));
                    serviceInstances.add(serviceInstance);
                }
            }

            return new DefaultPage<>(offset, pageSize, serviceInstances, totalSize);
        });
    }

    @Override
    public void addServiceInstancesChangedListener(ServiceInstancesChangedListener listener)
            throws NullPointerException, IllegalArgumentException {
        //每一个服务/节点（appName）绑定一个watcher  ， registerServiceWatcher  进去，
        listener.getServiceNames().forEach(serviceName -> registerServiceWatcher(serviceName, listener));
    }

    private void doInServiceRegistry(ThrowableConsumer<org.apache.curator.x.discovery.ServiceDiscovery> consumer) {
        ThrowableConsumer.execute(serviceDiscovery, s -> {
            consumer.accept(s);
        });
    }

    private <R> R doInServiceDiscovery(ThrowableFunction<org.apache.curator.x.discovery.ServiceDiscovery, R> function) {
        return execute(serviceDiscovery, function);
    }

    protected void registerServiceWatcher(String serviceName, ServiceInstancesChangedListener listener) {
        //  eg /services/demo-provider
        String path = buildServicePath(serviceName);

        try {
            // 递归创建节点创建节点
            curatorFramework.create().creatingParentsIfNeeded().forPath(path);
        } catch (KeeperException.NodeExistsException e) {
            // ignored 前面create、创建节点已存在，会进这个NodeExistsException异常，这里直接忽略即可
            if (logger.isDebugEnabled()) {

                logger.debug(e);
            }
        } catch (Exception e) {
            throw new IllegalStateException("registerServiceWatcher create path=" + path + " fail.", e);
        }

        CuratorWatcher watcher = watcherCaches.computeIfAbsent(path, key ->
                // 进去
                new ZookeeperServiceDiscoveryChangeWatcher(this, serviceName, listener));
        try {
            // 注册watcher getChildren有 /services/demo-provider/30.25.58.39:20880
            curatorFramework.getChildren().usingWatcher(watcher).forPath(path);
        } catch (KeeperException.NoNodeException e) {
            // ignored
            if (logger.isErrorEnabled()) {
                logger.error(e.getMessage());
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private String buildServicePath(String serviceName) {
        return rootPath + "/" + serviceName;
    }
}
