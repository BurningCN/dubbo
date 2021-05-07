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
package org.apache.dubbo.registry.client.event.listener;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.event.ConditionalEventListener;
import org.apache.dubbo.event.EventListener;
import org.apache.dubbo.metadata.MetadataInfo;
import org.apache.dubbo.metadata.MetadataInfo.ServiceInfo;
import org.apache.dubbo.metadata.MetadataService;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.client.DefaultServiceInstance;
import org.apache.dubbo.registry.client.RegistryClusterIdentifier;
import org.apache.dubbo.registry.client.ServiceDiscovery;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.registry.client.event.ServiceInstancesChangedEvent;
import org.apache.dubbo.registry.client.metadata.MetadataUtils;
import org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils;
import org.apache.dubbo.registry.client.metadata.store.RemoteMetadataServiceImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import static org.apache.dubbo.common.constants.CommonConstants.REMOTE_METADATA_STORAGE_TYPE;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_CLUSTER_KEY;
import static org.apache.dubbo.metadata.MetadataInfo.DEFAULT_REVISION;
import static org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils.getExportedServicesRevision;

/**
 * The Service Discovery Changed {@link EventListener Event Listener}
 *
 * @see ServiceInstancesChangedEvent
 * @since 2.7.5
 */
// OK
public class ServiceInstancesChangedListener implements ConditionalEventListener<ServiceInstancesChangedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ServiceInstancesChangedListener.class);

    private final Set<String> serviceNames;
    private final ServiceDiscovery serviceDiscovery;
    private URL url;
    private Map<String, NotifyListener> listeners;

    private Map<String, List<ServiceInstance>> allInstances;

    private Map<String, List<URL>> serviceUrls;

    private Map<String, MetadataInfo> revisionToMetadata;

    // gx
    public ServiceInstancesChangedListener(Set<String> serviceNames, ServiceDiscovery serviceDiscovery) {
        //serviceNames = {HashSet@3669}  size = 2
        // 0 = "demo-provider"
        // 1 = "demo-provider-test1"
        this.serviceNames = serviceNames;
        //serviceDiscovery = {EventPublishingServiceDiscovery@3510} "org.apache.dubbo.registry.zookeeper.ZookeeperServiceDiscovery@1f2d2181"
        // serviceDiscovery = {ZookeeperServiceDiscovery@3712}
        this.serviceDiscovery = serviceDiscovery;
        this.listeners = new HashMap<>();
        this.allInstances = new HashMap<>();
        this.serviceUrls = new HashMap<>();
        this.revisionToMetadata = new HashMap<>();
    }

    /**
     * On {@link ServiceInstancesChangedEvent the service instances change event}
     *
     * @param event {@link ServiceInstancesChangedEvent}
     */
    // 加锁了，很多涉及到事件的都加了锁
    public synchronized void onEvent(ServiceInstancesChangedEvent event) {
        logger.info("Received instance notification, serviceName: " + event.getServiceName() + ", instances: " + event.getServiceInstances().size());
        String appName = event.getServiceName();
        allInstances.put(appName, event.getServiceInstances());

        Map<String, List<ServiceInstance>> revisionToInstances = new HashMap<>();
        Map<String, Set<String>> localServiceToRevisions = new HashMap<>();
        Map<Set<String>, List<URL>> revisionsToUrls = new HashMap();

        // 遍历所有app的serviceInstance
        for (Map.Entry<String, List<ServiceInstance>> entry : allInstances.entrySet()) {
            List<ServiceInstance> instances = entry.getValue();
            for (ServiceInstance instance : instances) {
                // 进去 n. [印刷] 修正；复习；修订本 获取 dubbo.metadata.revision的值，一般都是有值的（提供者先前写到zk的）
                String revision = getExportedServicesRevision(instance);
                if (DEFAULT_REVISION.equals(revision)) {
                    logger.info("Find instance without valid service metadata: " + instance.getAddress());
                    continue;
                }
                // revisionToInstances , 一个revision代表一个app，一个app下的所有service，以ServiceInstance表示，都会存放这个reversion，表示这是同一app下的
                List<ServiceInstance> subInstances = revisionToInstances.computeIfAbsent(revision, r -> new LinkedList<>());
                subInstances.add(instance);

                // 同上面的逻辑。前面是reversion - List<ServiceInstance> 映射，下面是 reversion - MetadataInfo进行映射
                MetadataInfo metadata = revisionToMetadata.get(revision);
                if (metadata == null) {
                    // 进去
                    metadata = getMetadataInfo(instance);
                    logger.info("MetadataInfo for instance " + instance.getAddress() + "?revision=" + revision + " is " + metadata);
                    if (metadata != null) {
                        revisionToMetadata.put(revision, metadata);
                    }
                }

                if (metadata != null) {
                    // 进去
                    parseMetadata(revision, metadata, localServiceToRevisions);
                    ((DefaultServiceInstance) instance).setServiceMetadata(metadata);
                }
//                else {
//                    logger.error("Failed to load service metadata for instance " + instance);
//                    Set<String> set = localServiceToRevisions.computeIfAbsent(url.getServiceKey(), k -> new TreeSet<>());
//                    set.add(revision);
//                }

                localServiceToRevisions.forEach((serviceKey, revisions) -> {
                    List<URL> urls = revisionsToUrls.get(revisions);
                    if (urls != null) {
                        serviceUrls.put(serviceKey, urls);
                    } else {
                        urls = new ArrayList<>();
                        for (String r : revisions) {
                            for (ServiceInstance i : revisionToInstances.get(r)) {
                                urls.add(i.toURL());
                            }
                        }
                        revisionsToUrls.put(revisions, urls);
                        serviceUrls.put(serviceKey, urls);
                    }
                });
            }
        }

        this.notifyAddressChanged();
    }

    private Map<String, Set<String>> parseMetadata(String revision, MetadataInfo metadata, Map<String, Set<String>> localServiceToRevisions) {
        Map<String, ServiceInfo> serviceInfos = metadata.getServices();
        for (Map.Entry<String, ServiceInfo> entry : serviceInfos.entrySet()) {
            Set<String> set = localServiceToRevisions.computeIfAbsent(entry.getKey(), k -> new TreeSet<>());
            set.add(revision);
        }

        //localServiceToRevisions = {HashMap@3840}  size = 2
        //   "demo-provider/org.apache.dubbo.metadata.MetadataService:1.0.0:dubbo"
        //   "AB6F0B7C2429C8828F640F853B65E1E1"

        //   "samples.servicediscovery.demo.DemoService:dubbo"
        //   "AB6F0B7C2429C8828F640F853B65E1E1"
        return localServiceToRevisions;
    }

    private MetadataInfo getMetadataInfo(ServiceInstance instance) {
        // 进去 获取存储类型，是local还是remote，分别对应InMemoryWritableMetadataService 和 RemoteMetadataServiceImpl
        String metadataType = ServiceInstanceMetadataUtils.getMetadataStorageType(instance);
        // FIXME, check "REGISTRY_CLUSTER_KEY" must be set by every registry implementation.
        // extendParams添加了这个 REGISTRY_CLUSTER -> org.apache.dubbo.config.RegistryConfig
        instance.getExtendParams().putIfAbsent(REGISTRY_CLUSTER_KEY, RegistryClusterIdentifier.getExtension(url).consumerKey(url));
        MetadataInfo metadataInfo;
        try {
            if (logger.isDebugEnabled()) {
                // Instance 30.25.58.166:20880 is using metadata type remote
                logger.info("Instance " + instance.getAddress() + " is using metadata type " + metadataType);
            }
            // remote
            if (REMOTE_METADATA_STORAGE_TYPE.equals(metadataType)) {
                // 进去
                RemoteMetadataServiceImpl remoteMetadataService = MetadataUtils.getRemoteMetadataService();
                // 进去 从zk获取值
                metadataInfo = remoteMetadataService.getMetadata(instance);
                // local
            } else {
                MetadataService metadataServiceProxy = MetadataUtils.getMetadataServiceProxy(instance, serviceDiscovery);
                metadataInfo = metadataServiceProxy.getMetadataInfo(ServiceInstanceMetadataUtils.getExportedServicesRevision(instance));
            }
        } catch (Exception e) {
            logger.error("Failed to load service metadata, metadta type is " + metadataType, e);
            metadataInfo = null;
            // TODO, load metadata backup. Stop getting metadata after x times of failure for one revision?
        }
        return metadataInfo;
    }

    private void notifyAddressChanged() {
        listeners.forEach((key, notifyListener) -> {
            //FIXME, group wildcard match
            notifyListener.notify(toUrlsWithEmpty(serviceUrls.get(key)));
        });
    }

    private List<URL> toUrlsWithEmpty(List<URL> urls) {
        if (urls == null) {
            urls = Collections.emptyList();
        }
        return urls;
    }

    public void addListener(String serviceKey, NotifyListener listener) {
        this.listeners.put(serviceKey, listener);
    }

    public List<URL> getUrls(String serviceKey) {
        return toUrlsWithEmpty(serviceUrls.get(serviceKey));
    }

    /**
     * Get the correlative service name
     *
     * @return the correlative service name
     */
    public final Set<String> getServiceNames() {
        return serviceNames;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public URL getUrl() {
        return url;
    }

    /**
     * @param event {@link ServiceInstancesChangedEvent event}
     * @return If service name matches, return <code>true</code>, or <code>false</code>
     */
    public final boolean accept(ServiceInstancesChangedEvent event) {
        return serviceNames.contains(event.getServiceName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceInstancesChangedListener)) return false;
        ServiceInstancesChangedListener that = (ServiceInstancesChangedListener) o;
        return Objects.equals(getServiceNames(), that.getServiceNames());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), getServiceNames());
    }
}
