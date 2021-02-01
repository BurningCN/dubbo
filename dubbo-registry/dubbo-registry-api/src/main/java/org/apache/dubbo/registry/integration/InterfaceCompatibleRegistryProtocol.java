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
package org.apache.dubbo.registry.integration;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.client.RegistryProtocol;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.cluster.Directory;

import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.RegistryConstants.ENABLE_REGISTRY_DIRECTORY_AUTO_MIGRATION;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_PROTOCOL;
import static org.apache.dubbo.registry.Constants.CONSUMER_PROTOCOL;
import static org.apache.dubbo.registry.Constants.DEFAULT_REGISTRY;
import static org.apache.dubbo.registry.Constants.REGISTER_IP_KEY;

/**
 * RegistryProtocol
 */
public class InterfaceCompatibleRegistryProtocol extends RegistryProtocol {

    @Override
    protected URL getRegistryUrl(Invoker<?> originInvoker) {
        URL registryUrl = originInvoker.getUrl();
        if (REGISTRY_PROTOCOL.equals(registryUrl.getProtocol())) {
            // 获取registry参数的值，一般都是有值的，比如zookeeper（url比如registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&export=dubbo%3A%2F%2F30.25.58.102%3A20880%2Forg.apache.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddubbo-demo-api-provider%26bind.ip%3D30.25.58.102%26bind.port%3D20880%26default%3Dtrue%26deprecated%3Dfalse%26dubbo%3D2.0.2%26dynamic%3Dtrue%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26metadata-type%3Dremote%26methods%3DsayHello%2CsayHelloAsync%26pid%3D11828%26release%3D%26side%3Dprovider%26timestamp%3D1609920795524&pid=11828&registry=zookeeper&timestamp=1609920790513）
            String protocol = registryUrl.getParameter(REGISTRY_KEY, DEFAULT_REGISTRY);
            // 将registry://这部分转化为具体的协议，比如zookeeper://，并去掉registry参数
            registryUrl = registryUrl.setProtocol(protocol).removeParameter(REGISTRY_KEY);
        }
        return registryUrl;
    }

    @Override
    protected URL getRegistryUrl(URL url) {
        return URLBuilder.from(url)
                .setProtocol(url.getParameter(REGISTRY_KEY, DEFAULT_REGISTRY))
                .removeParameter(REGISTRY_KEY)
                .build();
    }

    @Override
    protected <T> DynamicDirectory<T> createDirectory(Class<T> type, URL url) {
        return new RegistryDirectory<>(type, url);
    }

    protected <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        ClusterInvoker<T> invoker = getInvoker(cluster, registry, type, url);
        ClusterInvoker<T> serviceDiscoveryInvoker = getServiceDiscoveryInvoker(cluster, type, url);
        ClusterInvoker<T> migrationInvoker = new MigrationInvoker<>(invoker, serviceDiscoveryInvoker);

        return interceptInvoker(migrationInvoker, url);
    }

    protected <T> ClusterInvoker<T> getServiceDiscoveryInvoker(Cluster cluster, Class<T> type, URL url) {
        Registry registry = registryFactory.getRegistry(super.getRegistryUrl(url));
        ClusterInvoker<T> serviceDiscoveryInvoker = null;
        // enable auto migration from interface address pool to instance address pool
        boolean autoMigration = url.getParameter(ENABLE_REGISTRY_DIRECTORY_AUTO_MIGRATION, false);
        if (autoMigration) {
            DynamicDirectory<T> serviceDiscoveryDirectory = super.createDirectory(type, url);
            serviceDiscoveryDirectory.setRegistry(registry);
            serviceDiscoveryDirectory.setProtocol(protocol);
            Map<String, String> parameters = new HashMap<String, String>(serviceDiscoveryDirectory.getConsumerUrl().getParameters());
            URL urlToRegistry = new URL(CONSUMER_PROTOCOL, parameters.remove(REGISTER_IP_KEY), 0, type.getName(), parameters);
            if (serviceDiscoveryDirectory.isShouldRegister()) {
                serviceDiscoveryDirectory.setRegisteredConsumerUrl(urlToRegistry);
                registry.register(serviceDiscoveryDirectory.getRegisteredConsumerUrl());
            }
            serviceDiscoveryDirectory.buildRouterChain(urlToRegistry);
            serviceDiscoveryDirectory.subscribe(toSubscribeUrl(urlToRegistry));
            serviceDiscoveryInvoker = (ClusterInvoker<T>) cluster.join(serviceDiscoveryDirectory);
        }
        return serviceDiscoveryInvoker;
    }

    private static class MigrationInvoker<T> implements ClusterInvoker<T> {
        private ClusterInvoker<T> invoker;
        private ClusterInvoker<T> serviceDiscoveryInvoker;

        public MigrationInvoker(ClusterInvoker<T> invoker, ClusterInvoker<T> serviceDiscoveryInvoker) {
            this.invoker = invoker;
            this.serviceDiscoveryInvoker = serviceDiscoveryInvoker;
        }

        public ClusterInvoker<T> getInvoker() {
            return invoker;
        }

        public void setInvoker(ClusterInvoker<T> invoker) {
            this.invoker = invoker;
        }

        public ClusterInvoker<T> getServiceDiscoveryInvoker() {
            return serviceDiscoveryInvoker;
        }

        public void setServiceDiscoveryInvoker(ClusterInvoker<T> serviceDiscoveryInvoker) {
            this.serviceDiscoveryInvoker = serviceDiscoveryInvoker;
        }

        @Override
        public Class<T> getInterface() {
            return invoker.getInterface();
        }

        @Override
        public Result invoke(Invocation invocation) throws RpcException {
            if (serviceDiscoveryInvoker == null) {
                return invoker.invoke(invocation);
            }

            if (invoker.isDestroyed()) {
                return serviceDiscoveryInvoker.invoke(invocation);
            }
            if (serviceDiscoveryInvoker.isAvailable()) {
                invoker.destroy(); // can be destroyed asynchronously
                return serviceDiscoveryInvoker.invoke(invocation);
            }
            return invoker.invoke(invocation);
        }

        @Override
        public URL getUrl() {
            return invoker.getUrl();
        }

        @Override
        public boolean isAvailable() {
            return invoker.isAvailable() || serviceDiscoveryInvoker.isAvailable();
        }

        @Override
        public void destroy() {
            if (invoker != null) {
                invoker.destroy();
            }
            if (serviceDiscoveryInvoker != null) {
                serviceDiscoveryInvoker.destroy();
            }
        }

        @Override
        public URL getRegistryUrl() {
            return invoker.getRegistryUrl();
        }

        @Override
        public Directory<T> getDirectory() {
            return invoker.getDirectory();
        }

        @Override
        public boolean isDestroyed() {
            return invoker.isDestroyed() && serviceDiscoveryInvoker.isDestroyed();
        }
    }

}
