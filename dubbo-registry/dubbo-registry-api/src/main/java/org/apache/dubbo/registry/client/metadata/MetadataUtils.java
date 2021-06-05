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
package org.apache.dubbo.registry.client.metadata;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.metadata.MetadataService;
import org.apache.dubbo.metadata.WritableMetadataService;
import org.apache.dubbo.registry.client.ServiceDiscovery;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.registry.client.metadata.store.RemoteMetadataServiceImpl;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils.METADATA_SERVICE_URLS_PROPERTY_NAME;

public class MetadataUtils {

    private static final Object REMOTE_LOCK = new Object();

    public static ConcurrentMap<String, MetadataService> metadataServiceProxies = new ConcurrentHashMap<>();

    private static final ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    private static final Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

    public static RemoteMetadataServiceImpl remoteMetadataService;

    public static WritableMetadataService localMetadataService;

    public static RemoteMetadataServiceImpl getRemoteMetadataService() {
        if (remoteMetadataService == null) {
            synchronized (REMOTE_LOCK) {
                if (remoteMetadataService == null) {
                    remoteMetadataService = new RemoteMetadataServiceImpl(WritableMetadataService.getDefaultExtension());
                }
            }
        }
        return remoteMetadataService;
    }

    // gx
    public static void publishServiceDefinition(URL url) {
        // store in local 将服务定义信息存到本地内存，
        WritableMetadataService.getDefaultExtension().publishServiceDefinition(url);
        // 在发送/存储到远端（比如zk），getRemoteMetadataService、publishServiceDefinition进去
        getRemoteMetadataService().publishServiceDefinition(url);
    }

    public static MetadataService getMetadataServiceProxy(ServiceInstance instance, ServiceDiscovery serviceDiscovery) {
        // demo-provider##AB6F0B7C2429C8828F640F853B65E1E1
        String key = instance.getServiceName() + "##" + instance.getId() + "##" +
                ServiceInstanceMetadataUtils.getExportedServicesRevision(instance);
        return metadataServiceProxies.computeIfAbsent(key, k -> {
            MetadataServiceURLBuilder builder;
            // MetadataServiceURLBuilder 两个扩展实例
            ExtensionLoader<MetadataServiceURLBuilder> loader
                    = ExtensionLoader.getExtensionLoader(MetadataServiceURLBuilder.class);

            //metadata = {LinkedHashMap@4574}  size = 4
            // "dubbo.metadata-service.url-params" -> "{"dubbo":{"version":"1.0.0","dubbo":"2.0.2","port":"20881"}}"
            // "dubbo.endpoints" -> "[{"port":20880,"protocol":"dubbo"}]"
            // "dubbo.metadata.revision" -> "AB6F0B7C2429C8828F640F853B65E1E1"
            // "dubbo.metadata.storage-type" -> "remote"
            Map<String, String> metadata = instance.getMetadata();

            // METADATA_SERVICE_URLS_PROPERTY_NAME is a unique key exists only on instances of spring-cloud-alibaba.
            // 默认取出来为null
            String dubboURLsJSON = metadata.get(METADATA_SERVICE_URLS_PROPERTY_NAME);
            if (StringUtils.isNotEmpty(dubboURLsJSON)) {
                builder = loader.getExtension(SpringCloudMetadataServiceURLBuilder.NAME);
            } else {
                // 默认这个
                builder = loader.getExtension(StandardMetadataServiceURLBuilder.NAME);
            }

            // 进去
            List<URL> urls = builder.build(instance);
            if (CollectionUtils.isEmpty(urls)) {
                throw new IllegalStateException("You have enabled introspection service discovery mode for instance "
                        + instance + ", but no metadata service can build from it.");
            }

            // urls里面一般只有一项，eg dubbo://30.25.58.166:20881/org.apache.dubbo.metadata.MetadataService?dubbo=2.0.2&group=demo-provider&port=20881&side=consumer&timeout=5000&version=1.0.0
            // Simply rely on the first metadata url, as stated in MetadataServiceURLBuilder.
            // 只需依赖第一个元数据url，如MetadataServiceURLBuilder中所述。
            Invoker<MetadataService> invoker = protocol.refer(MetadataService.class, urls.get(0));
            // 和远端的MetadataService提供者建立了连接，其实一会要想远端的 InMemoryWritableMetadataService 发起调用，调用其getMetadataInfo方法
            return proxyFactory.getProxy(invoker);
        });
    }
}
