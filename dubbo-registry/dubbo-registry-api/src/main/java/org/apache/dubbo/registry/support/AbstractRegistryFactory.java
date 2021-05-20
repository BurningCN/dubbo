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
package org.apache.dubbo.registry.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;
import org.apache.dubbo.registry.RegistryService;
import org.apache.dubbo.registry.client.ServiceDiscovery;
import org.apache.dubbo.registry.client.ServiceDiscoveryRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.EXPORT_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;

/**
 * AbstractRegistryFactory. (SPI, Singleton, ThreadSafe)
 *
 * @see org.apache.dubbo.registry.RegistryFactory
 */
// OK
// 设计到抽象工厂模式（不是严格的，多了一个AbstractXX，做一些模板方法设计）
public abstract class AbstractRegistryFactory implements RegistryFactory {

    // Log output
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRegistryFactory.class);

    // The lock for the acquisition process of the registry
    // 在创建 注册中心对象或者 销毁注册中心对象的时候会用到 锁
    protected static final ReentrantLock LOCK = new ReentrantLock();

    // Registry Collection Map<RegistryAddress, Registry>
    // 缓存 注册中心对象使用  key就是注册中心url的一个toServiceString， value是注册中心对象
    protected static final Map<String, Registry> REGISTRIES = new HashMap<>();

    // todo need pr 这里大写比较好
    private static final AtomicBoolean destroyed = new AtomicBoolean(false);

    /**
     * Get all registries
     *
     * @return all registries
     */
    public static Collection<Registry> getRegistries() {
        return Collections.unmodifiableCollection(new LinkedList<>(REGISTRIES.values()));
    }

    public static Registry getRegistry(String key) {
        return REGISTRIES.get(key);
    }

    // easy
    public static List<ServiceDiscovery> getServiceDiscoveries() {
        // AbstractRegistryFactory.getRegistries() 先前缓存了相关实例
        return AbstractRegistryFactory.getRegistries()
                .stream()
                .filter(registry -> registry instanceof ServiceDiscoveryRegistry)
                .map(registry -> (ServiceDiscoveryRegistry) registry)
                // 每个ServiceDiscoveryRegistry里面有一个serviceDiscovery属性，在其构造函数得到初始化的
                .map(ServiceDiscoveryRegistry::getServiceDiscovery)
                .collect(Collectors.toList());
    }

    /**
     * Close all created registries
     */
    public static void destroyAll() {
        if (!destroyed.compareAndSet(false, true)) {
            return;
        }

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Close all registries " + getRegistries());
        }
        // Lock up the registry shutdown process
        // 设计的精巧，当前线程已经走到这步骤，但是别的线程有可能还在后面getRegistry的LOCK.lock()这步骤前，而这俩是用的一把锁
        LOCK.lock();
        try {
            // 一般提供者只有一个元素，常规的ZookeeperRegistry，而如果是消费者（新版本）除了前者外，还有一个 ServiceDiscoverRegistry
            for (Registry registry : getRegistries()) {
                try {
                    registry.destroy();
                } catch (Throwable e) {
                    LOGGER.error(e.getMessage(), e);
                }
            }
            REGISTRIES.clear();
        } finally {
            // Release the lock
            LOCK.unlock();
        }
    }

    // 这个不应该叫这个名字，应该叫getOrCreateRegistry
    @Override
    public Registry getRegistry(URL url) {
        if (destroyed.get()) {
            LOGGER.warn("All registry instances have been destroyed, failed to fetch any instance. " +
                    "Usually, this means no need to try to do unnecessary redundant resource clearance, all registries has been taken care of.");
            return DEFAULT_NOP_REGISTRY;
        }

        url = URLBuilder.from(url)
                .setPath(RegistryService.class.getName())
                .addParameter(INTERFACE_KEY, RegistryService.class.getName())// interface
                .removeParameters(EXPORT_KEY, REFER_KEY) // export  refer
                .build();

        // eg : zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService
        // eg : service-discovery-registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService
        String key = createRegistryCacheKey(url);
        // Lock the registry access process to ensure a single instance of the registry
        LOCK.lock();
        try {
            // 访问缓存
            Registry registry = REGISTRIES.get(key);
            if (registry != null) {
                return registry;
            }
            //create registry by spi/ioc
            // 缓存未命中，创建 Registry 实例，进去
            // 这里是进Registry派系或者ServiceDiscovery派系，比如ZookeeperRegistryFactory 或者 ServiceDiscoveryRegistryFactory
            registry = createRegistry(url);
            if (registry == null) {
                throw new IllegalStateException("Can not create registry " + url);
            }
            // 写入缓存
            REGISTRIES.put(key, registry);
            return registry;
        } finally {
            // Release the lock
            LOCK.unlock();
        }
        // 如上，getRegistry 方法先访问缓存，缓存未命中则调用 createRegistry 创建 Registry，然后写入缓存。这里的 createRegistry 是一个
        // 模板方法，由具体的子类实现。因此，下面我们到 ZookeeperRegistryFactory 中探究一番。
    }

    /**
     * Create the key for the registries cache.
     * This method may be override by the sub-class.
     *
     * @param url the registration {@link URL url}
     * @return non-null
     */
    protected String createRegistryCacheKey(URL url) {
        // 进去
        return url.toServiceStringWithoutResolving();
    }

    // 由子类实现的 创建方法， 模板方法设计模式
    protected abstract Registry createRegistry(URL url);


    private static Registry DEFAULT_NOP_REGISTRY = new Registry() {
        @Override
        public URL getUrl() {
            return null;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void destroy() {

        }

        @Override
        public void register(URL url) {

        }

        @Override
        public void unregister(URL url) {

        }

        @Override
        public void subscribe(URL url, NotifyListener listener) {

        }

        @Override
        public void unsubscribe(URL url, NotifyListener listener) {

        }

        @Override
        public List<URL> lookup(URL url) {
            return null;
        }
    };

    public static void removeDestroyedRegistry(Registry toRm) {
        LOCK.lock();
        try {
            REGISTRIES.entrySet().removeIf(entry -> entry.getValue().equals(toRm));
        } finally {
            LOCK.unlock();
        }
    }

    // for unit test
    public static void clearRegistryNotDestroy() {
        REGISTRIES.clear();
    }

}
