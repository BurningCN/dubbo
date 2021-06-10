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
package org.apache.dubbo.rpc.model;

import org.apache.dubbo.common.context.FrameworkExt;
import org.apache.dubbo.common.context.LifecycleAdapter;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.config.ReferenceConfigBase;
import org.apache.dubbo.config.ServiceConfigBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.apache.dubbo.common.BaseServiceMetadata.interfaceFromServiceKey;
import static org.apache.dubbo.common.BaseServiceMetadata.versionFromServiceKey;

// OK
public class ServiceRepository extends LifecycleAdapter implements FrameworkExt {

    public static final String NAME = "repository";

    // services
    private ConcurrentMap<String, ServiceDescriptor> services = new ConcurrentHashMap<>();

    // consumers
    private ConcurrentMap<String, ConsumerModel> consumers = new ConcurrentHashMap<>();

    // providers
    private ConcurrentMap<String, ProviderModel> providers = new ConcurrentHashMap<>();

    // useful to find a provider model quickly with serviceInterfaceName:version
    private ConcurrentMap<String, ProviderModel> providersWithoutGroup = new ConcurrentHashMap<>();

    // 在第一次getExtension获取该类实例的时候，会触发如下方法，实现填充几个service
    public ServiceRepository() {
        // BuiltinServiceDetector ：返内装式服务探测器，有四个子类
        Set<BuiltinServiceDetector> builtinServices
                = ExtensionLoader.getExtensionLoader(BuiltinServiceDetector.class).getSupportedExtensionInstances();
        if (CollectionUtils.isNotEmpty(builtinServices)) {
            for (BuiltinServiceDetector service : builtinServices) {
                // 挨个注册下，getService方法实现和registerService进去
                registerService(service.getService());
            }
        }
    }


    // gx 除了上面的，重点关注ServiceConfig的调用处
    public ServiceDescriptor registerService(Class<?> interfaceClazz) {

        return services.computeIfAbsent(interfaceClazz.getName(),
                // 进去
                _k -> new ServiceDescriptor(interfaceClazz));
    }


    /**
     * See {@link #registerService(Class)}
     * <p>
     * we assume:
     * 1. services with different interfaces are not allowed to have the same path.
     * 2. services share the same interface but has different group/version can share the same path.
     * 3. path's default value is the name of the interface.
     * * 1。不同接口的服务不允许有相同的路径。
     * * 2。服务共享相同的接口，但不同的组/版本可以共享相同的路径。
     * * 3。path的默认值是接口名。
     * @param path
     * @param interfaceClass
     * @return
     */
    // gx
    public ServiceDescriptor registerService(String path, Class<?> interfaceClass) {
        ServiceDescriptor serviceDescriptor = registerService(interfaceClass);
        // if path is different with interface name, add extra（额外的） path mapping
        if (!interfaceClass.getName().equals(path)) {
            services.putIfAbsent(path, serviceDescriptor);
        }
        return serviceDescriptor;
    }

    public void unregisterService(Class<?> interfaceClazz) {
        unregisterService(interfaceClazz.getName());
    }

    public void unregisterService(String path) {
        services.remove(path);
    }

    public void registerConsumer(String serviceKey,
                                 ServiceDescriptor serviceDescriptor,
                                 ReferenceConfigBase<?> rc,
                                 Object proxy,
                                 ServiceMetadata serviceMetadata) {
        ConsumerModel consumerModel = new ConsumerModel(serviceMetadata.getServiceKey(), proxy, serviceDescriptor, rc,
                serviceMetadata);
        consumers.putIfAbsent(serviceKey, consumerModel);
    }

    public void reRegisterConsumer(String newServiceKey, String serviceKey) {
        ConsumerModel consumerModel = consumers.get(serviceKey);
        consumerModel.setServiceKey(newServiceKey);
        consumers.putIfAbsent(newServiceKey, consumerModel);
        consumers.remove(serviceKey);

    }

    // gx
    public void registerProvider(String serviceKey, // URL.buildKey(interfaceName, getGroup(), getVersion());
                                 Object serviceInstance,// 接口实现类
                                 ServiceDescriptor serviceModel,// 服务描述，先前调用register返回的
                                 ServiceConfigBase<?> serviceConfig,// ServiceConfig this 对象
                                 ServiceMetadata serviceMetadata) {// 元数据
        // 构建ProviderModel
        ProviderModel providerModel = new ProviderModel(serviceKey, serviceInstance, serviceModel, serviceConfig,
                serviceMetadata);
        // 存到下两个容器
        providers.putIfAbsent(serviceKey, providerModel);
        providersWithoutGroup.putIfAbsent(keyWithoutGroup(serviceKey), providerModel);// eg:serviceKey = org.apache.dubbo.demo.DemoService:null，那么 keyWithoutGroup为org.apache.dubbo.demo.DemoService:null
    }

    private static String keyWithoutGroup(String serviceKey) {
        return interfaceFromServiceKey(serviceKey) + ":" + versionFromServiceKey(serviceKey);
    }

    public void reRegisterProvider(String newServiceKey, String serviceKey) {
        ProviderModel providerModel = providers.get(serviceKey);
        providerModel.setServiceKey(newServiceKey);
        providers.putIfAbsent(newServiceKey, providerModel);
        providers.remove(serviceKey);
    }

    public List<ServiceDescriptor> getAllServices() {
        return Collections.unmodifiableList(new ArrayList<>(services.values()));
    }

    // 前面注册到了容器，这个就是从容器查，其实就是做了缓存作用
    public ServiceDescriptor lookupService(String interfaceName) {
        return services.get(interfaceName);
    }

    public MethodDescriptor lookupMethod(String interfaceName, String methodName) {
        ServiceDescriptor serviceDescriptor = lookupService(interfaceName);
        if (serviceDescriptor == null) {
            return null;
        }

        List<MethodDescriptor> methods = serviceDescriptor.getMethods(methodName);
        if (CollectionUtils.isEmpty(methods)) {
            return null;
        }
        return methods.iterator().next();
    }

    public List<ProviderModel> getExportedServices() {
        return Collections.unmodifiableList(new ArrayList<>(providers.values()));
    }

    public ProviderModel lookupExportedService(String serviceKey) {
        // providers的填充处在registerService的调用，而registerService的调用在ServiceConfig的doExportUrls方法
        return providers.get(serviceKey);
    }

    public ProviderModel lookupExportedServiceWithoutGroup(String key) {
        return providersWithoutGroup.get(key);
    }

    public List<ConsumerModel> getReferredServices() {
        return Collections.unmodifiableList(new ArrayList<>(consumers.values()));
    }

    public ConsumerModel lookupReferredService(String serviceKey) {
        return consumers.get(serviceKey);
    }

    @Override
    public void destroy() throws IllegalStateException {
        // currently works for unit test
        services.clear();
        consumers.clear();
        providers.clear();
        providersWithoutGroup.clear();
    }
}
