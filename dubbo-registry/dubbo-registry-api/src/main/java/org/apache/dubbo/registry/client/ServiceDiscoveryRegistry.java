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
package org.apache.dubbo.registry.client;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.metadata.MappingChangedEvent;
import org.apache.dubbo.metadata.MappingListener;
import org.apache.dubbo.metadata.ServiceNameMapping;
import org.apache.dubbo.metadata.WritableMetadataService;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.client.event.ServiceInstancesChangedEvent;
import org.apache.dubbo.registry.client.event.listener.ServiceInstancesChangedListener;
import org.apache.dubbo.registry.client.metadata.SubscribedURLsSynthesizer;
import org.apache.dubbo.registry.support.AbstractRegistryFactory;
import org.apache.dubbo.registry.support.FailbackRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.of;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_CHAR_SEPARATOR;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.MAPPING_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROTOCOL_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.PROVIDED_BY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_CLUSTER_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_TYPE_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.SERVICE_REGISTRY_TYPE;
import static org.apache.dubbo.common.constants.RegistryConstants.SUBSCRIBED_SERVICE_NAMES_KEY;
import static org.apache.dubbo.common.function.ThrowableAction.execute;
import static org.apache.dubbo.common.utils.CollectionUtils.isEmpty;
import static org.apache.dubbo.common.utils.StringUtils.isBlank;
import static org.apache.dubbo.registry.client.ServiceDiscoveryFactory.getExtension;
import static org.apache.dubbo.rpc.Constants.ID_KEY;

/**
 * Being different to the traditional registry, {@link ServiceDiscoveryRegistry} that is a new service-oriented
 * {@link Registry} based on {@link ServiceDiscovery}, it will not interact in the external registry directly,
 * but store the {@link URL urls} that Dubbo services exported and referenced into {@link WritableMetadataService}
 * when {@link #register(URL)} and {@link #subscribe(URL, NotifyListener)} methods are executed. After that the exported
 * {@link URL urls} can be get from {@link WritableMetadataService#getExportedURLs()} and its variant methods. In contrast,
 * {@link WritableMetadataService#getSubscribedURLs()} method offers the subscribed {@link URL URLs}.
 * <p>
 * Every {@link ServiceDiscoveryRegistry} object has its own {@link ServiceDiscovery} instance that was initialized
 * under {@link #ServiceDiscoveryRegistry(URL) the construction}. As the primary argument of constructor , the
 * {@link URL} of connection the registry decides what the kind of ServiceDiscovery is. Generally, each
 * protocol associates with a kind of {@link ServiceDiscovery}'s implementation if present, or the
 * {@link FileSystemServiceDiscovery} will be the default one. Obviously, it's also allowed to extend
 * {@link ServiceDiscovery} using {@link SPI the Dubbo SPI}.
 * In contrast, current {@link ServiceInstance service instance} will not be registered to the registry whether any
 * Dubbo service is exported or not.
 * <p>
 *
 * @see ServiceDiscovery
 * @see FailbackRegistry
 * @see WritableMetadataService
 * @since 2.7.5
 */
public class ServiceDiscoveryRegistry implements Registry {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final ServiceDiscovery serviceDiscovery;

    private final Set<String> subscribedServices;

    private final ServiceNameMapping serviceNameMapping;

    private final WritableMetadataService writableMetadataService;

    private final Set<String> registeredListeners = new LinkedHashSet<>();

    /* apps - listener */
    private final Map<String, ServiceInstancesChangedListener> serviceListeners = new HashMap<>();

    private final Map<String, String> serviceToAppsMapping = new HashMap<>();

    private URL registryURL;

    /**
     * A cache for all URLs of services that the subscribed services exported
     * The key is the service name
     * The value is a nested {@link Map} whose key is the revision and value is all URLs of services
     */
    private final Map<String, Map<String, List<URL>>> serviceRevisionExportedURLsCache = new LinkedHashMap<>();

    public ServiceDiscoveryRegistry(URL registryURL) {
        // eg zookeeper://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=demo-provider&dubbo=2.0.2&id=org.apache.dubbo.config.RegistryConfig&interface=org.apache.dubbo.registry.RegistryService&metadata-type=remote&pid=67248&registry-type=service&timestamp=1619165300619
        this.registryURL = registryURL;
        // 创建sd进去
        this.serviceDiscovery = createServiceDiscovery(registryURL);
        // 获取 "subscribed-services"参数值,按照逗号分割填充到set集合，这就是appNames
        this.subscribedServices = parseServices(registryURL.getParameter(SUBSCRIBED_SERVICE_NAMES_KEY));
        // 获取"mapping-type"参数值，ServiceNameMapping#getExtension进去。有两种，基于config-center的和基于MetadataReport的
        // debug可以临时加参数，如果想跟踪MetadataServiceNameMapping的逻辑 --- > registryURL = registryURL.addParameter("mapping-type","metadata")
        this.serviceNameMapping = ServiceNameMapping.getExtension(registryURL.getParameter(MAPPING_KEY));
        // 进去 默认InMemoryWritableMetadataService
        this.writableMetadataService = WritableMetadataService.getDefaultExtension();
    }

    public ServiceDiscovery getServiceDiscovery() {
        return serviceDiscovery;
    }

    /**
     * Create the {@link ServiceDiscovery} from the registry {@link URL}
     *
     * @param registryURL the {@link URL} to connect the registry
     * @return non-null
     */
    protected ServiceDiscovery createServiceDiscovery(URL registryURL) {
        // 先创建目标sd，比如测试程序的InMemoryServiceDiscovery（实际场景比如zkSD），进去

        // RegistryFactory -- AbstractRegistryFactory -- ServiceDiscoveryRegistry
        // ServiceDiscoveryFactory -- AbstractServiceDiscoveryFactory  -- ZookeeperServiceDiscoveryFactory
        // ServiceDiscovery -- AbstractServiceDiscovery -- ZookeeperServiceDiscovery

        // 这里是 ZookeeperServiceDiscovery
        ServiceDiscovery originalServiceDiscovery = getServiceDiscovery(registryURL);
        // origin 目标sd用 EventPublishingServiceDiscovery 包装下。这里enhance名字用得好
        ServiceDiscovery serviceDiscovery = enhanceEventPublishing(originalServiceDiscovery);
        execute(() -> {
            // 进去 EventPublishingServiceDiscovery#initialize
            // 这里将interface参数值从org.apache.dubbo.registry.RegistryService变成
            // org.apache.dubbo.registry.client.ServiceDiscovery，并且把 registry-type=service 参数去掉了
            serviceDiscovery.initialize(registryURL.addParameter(INTERFACE_KEY, ServiceDiscovery.class.getName())
                    .removeParameter(REGISTRY_TYPE_KEY));
        });
        return serviceDiscovery; // 返回EventPublishingServiceDiscovery
    }

    private List<SubscribedURLsSynthesizer> initSubscribedURLsSynthesizers() {
        ExtensionLoader<SubscribedURLsSynthesizer> loader = ExtensionLoader.getExtensionLoader(SubscribedURLsSynthesizer.class);
        return Collections.unmodifiableList(new ArrayList<>(loader.getSupportedExtensionInstances()));
    }

    /**
     * Get the instance {@link ServiceDiscovery} from the registry {@link URL} using
     * {@link ServiceDiscoveryFactory} SPI
     *
     * @param registryURL the {@link URL} to connect the registry
     * @return
     */
    private ServiceDiscovery getServiceDiscovery(URL registryURL) {
        // ServiceDiscoveryFactory#getExtension的方法，获取对应扩展实例，如果没有则使用DefaultServiceDiscoveryFactory
        // 如果是实际场景一般比如返回类型为ZookeeperServiceDiscoveryFactory
        ServiceDiscoveryFactory factory = getExtension(registryURL);
        // 通过工厂获得ServiceDiscovery。进去，看AbstractServiceDiscoveryFactory
        return factory.getServiceDiscovery(registryURL);
    }

    /**
     * Enhance the original {@link ServiceDiscovery} with event publishing feature
     *
     * @param original the original {@link ServiceDiscovery}
     * @return {@link EventPublishingServiceDiscovery} instance
     */
    private ServiceDiscovery enhanceEventPublishing(ServiceDiscovery original) {
        return new EventPublishingServiceDiscovery(original);
    }

    protected boolean shouldRegister(URL providerURL) {

        String side = providerURL.getParameter(SIDE_KEY);

        boolean should = PROVIDER_SIDE.equals(side); // Only register the Provider. --- 注意 只注册provider

        if (!should) {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("The URL[%s] should not be registered.", providerURL.toString()));
            }
        }

        return should;
    }

    protected boolean shouldSubscribe(URL subscribedURL) {
        return !shouldRegister(subscribedURL);// shouldRegister限定只有提供者可以注册，这里取反表示可以订阅，说明只有consumer可以订阅
    }

    @Override
    public final void register(URL url) {
        if (!shouldRegister(url)) { // Should Not Register // 进去
            return;
        }
        doRegister(url);// 进去
    }

    public void doRegister(URL url) {
        // 获取"id"参数值，比如 id -> org.apache.dubbo.config.RegistryConfig#0
        // 注意参数url是provider url，serviceDiscovery.getUrl()是sd的url，一个是dubbo://，一个是zookeeper://127.0.0.1:2181/。。。
        String registryCluster = serviceDiscovery.getUrl().getParameter(ID_KEY);
        if (registryCluster != null && url.getParameter(REGISTRY_CLUSTER_KEY) == null) {
            url = url.addParameter(REGISTRY_CLUSTER_KEY, registryCluster);// 如果"id"参数值不空，但是"REGISTRY_CLUSTER"参数为空，则将"id"参数值赋值给"REGISTRY_CLUSTER"参数值
        }
        // 暴露提供者url（一般是InMemory），进去
        if (writableMetadataService.exportURL(url)) {
            if (logger.isInfoEnabled()) {
                logger.info(format("The URL[%s] registered successfully.", url.toString()));
            }
        } else {
            if (logger.isWarnEnabled()) {
                logger.info(format("The URL[%s] has been registered.", url.toString()));
            }
        }
        // 这里发现仅注册到了内存，还没有注册到远端
    }

    @Override
    public final void unregister(URL url) {
        if (!shouldRegister(url)) {
            return;
        }
        doUnregister(url);
    }

    public void doUnregister(URL url) {
        String registryCluster = serviceDiscovery.getUrl().getParameter(ID_KEY);
        if (registryCluster != null && url.getParameter(REGISTRY_CLUSTER_KEY) == null) {
            url = url.addParameter(REGISTRY_CLUSTER_KEY, registryCluster);
        }
        if (writableMetadataService.unexportURL(url)) {
            if (logger.isInfoEnabled()) {
                logger.info(format("The URL[%s] deregistered successfully.", url.toString()));
            }
        } else {
            if (logger.isWarnEnabled()) {
                logger.info(format("The URL[%s] has been deregistered.", url.toString()));
            }
        }
    }

    // url为consumer://30.25.58.166/samples.servicediscovery.demo.DemoService?application=demo-consumer&category=providers,configurators,routers&check=false&dubbo=2.0.2&init=false&interface=samples.servicediscovery.demo.DemoService&mapping-type=metadata&mapping.type=metadata&metadata-type=remote&methods=sayHello&pid=41340&provided-by=demo-provider&side=consumer&sticky=false&timestamp=1620447023760
    // listener 为 ServiceDiscoveryRegistryDirectory
    @Override
    public final void subscribe(URL url, NotifyListener listener) {
        if (!shouldSubscribe(url)) { // Should Not Subscribe // 进去
            return;
        }
        // eg org.apache.dubbo.config.RegistryConfig
        String registryCluster = serviceDiscovery.getUrl().getParameter(ID_KEY);
        if (registryCluster != null && url.getParameter(REGISTRY_CLUSTER_KEY) == null) {
            url = url.addParameter(REGISTRY_CLUSTER_KEY, registryCluster);
        }
        // 进去
        doSubscribe(url, listener);
    }

    public void doSubscribe(URL url, NotifyListener listener) {
        // 将url填充到 InMemoryWritableMetadataService#subscribedServiceURLs 容器中 进去
        writableMetadataService.subscribeURL(url);

        // 获取url对应的服务列表（如果有的话），进去，非常重要。返回的是appNames
        Set<String> serviceNames = getServices(url, listener);
        if (CollectionUtils.isEmpty(serviceNames)) {
            throw new IllegalStateException("Should has at least one way to know which services this interface belongs to, subscription url: " + url);
        }

        // 这里才是真实的订阅，进去，这里会根据前面的serviceNames（appName）获取app下支持的service信息，比如ip port
        subscribeURLs(url, listener, serviceNames);
    }

    @Override
    public final void unsubscribe(URL url, NotifyListener listener) {
        if (!shouldSubscribe(url)) { // Should Not Subscribe
            return;
        }
        String registryCluster = serviceDiscovery.getUrl().getParameter(ID_KEY);
        if (registryCluster != null && url.getParameter(REGISTRY_CLUSTER_KEY) == null) {
            url = url.addParameter(REGISTRY_CLUSTER_KEY, registryCluster);
        }
        doUnsubscribe(url, listener);
    }

    public void doUnsubscribe(URL url, NotifyListener listener) {
        writableMetadataService.unsubscribeURL(url);
    }

    @Override
    public List<URL> lookup(URL url) {
        throw new UnsupportedOperationException("");
    }

    @Override
    public URL getUrl() {
        return registryURL;
    }

    @Override
    public boolean isAvailable() {
        return !serviceDiscovery.getServices().isEmpty();
    }

    @Override
    public void destroy() {
        AbstractRegistryFactory.removeDestroyedRegistry(this);
        execute(() -> {
            // stop ServiceDiscovery
            serviceDiscovery.destroy();
        });
    }

    // 主要被该类的doSubscribe主动调用。或者DefaultMappingListener调用，用于在收到新的一批appName的时候进行相关订阅操作
    // listener为ServiceDiscoveryDirectory
    protected void subscribeURLs(URL url, NotifyListener listener, Set<String> serviceNames) {
        String serviceNamesKey = serviceNames.toString();
        String protocolServiceKey = url.getServiceKey() + GROUP_CHAR_SEPARATOR + url.getParameter(PROTOCOL_KEY, DUBBO);
        // eg samples.servicediscovery.demo.DemoService:dubbo -> [demo-provider, demo-provider-test1]
        serviceToAppsMapping.put(protocolServiceKey, serviceNamesKey);

        // register ServiceInstancesChangedListener
        ServiceInstancesChangedListener serviceListener = serviceListeners.computeIfAbsent(serviceNamesKey,
                k -> new ServiceInstancesChangedListener(serviceNames, serviceDiscovery));
        serviceListener.setUrl(url);

        // serviceToAppsMapping 和 serviceListeners 感觉没啥用处，就是做了一个缓存

        // listener为ServiceDiscoveryRegistryDirectory进去 ，这个也感觉没啥用处，设置进去后也没有调用
        listener.addServiceListener(serviceListener);

        // 将 listener 即ServiceDiscoveryDirectory保存到ServiceInstancesChangedListener（和前面步骤相反）
        // 这样后者得到通知的时候回调用listener#notify方法
        serviceListener.addListener(protocolServiceKey, listener);


        // 核心点注册到sd，进去
        registerServiceInstancesChangedListener(url, serviceListener);

        serviceNames.forEach(serviceName -> {
            // 进去 serviceName demo-provider appname
            // 内部会获取path为/services/demo-provider/30.25.58.39:20880的值并反序列化为DefaultServiceInstance（是SD#register注册d）
              List<ServiceInstance> serviceInstances = serviceDiscovery.getInstances(serviceName);
            //0 = {DefaultServiceInstance@3779} "DefaultServiceInstance{id='30.25.58.39:20880', serviceName='demo-provider', host='30.25.58.39', port=20880, enabled=true, healthy=true, metadata={dubbo.metadata-service.url-params={"dubbo":{"version":"1.0.0","dubbo":"2.0.2","port":"20881"}}, dubbo.endpoints=[{"port":20880,"protocol":"dubbo"}], dubbo.metadata.revision=AB6F0B7C2429C8828F640F853B65E1E1, dubbo.metadata.storage-type=remote}}"
            // id = "30.25.58.39:20880"
            // serviceName = "demo-provider"
            // host = "30.25.58.39"
            // port = {Integer@3784} 20880
            // enabled = true
            // healthy = true
            // metadata = {LinkedHashMap@3785}  size = 4
            // address = null
            // serviceMetadata = null
            // extendParams = {HashMap@3786}  size = 0

            if (CollectionUtils.isNotEmpty(serviceInstances)) {
                // 进去
                serviceListener.onEvent(new ServiceInstancesChangedEvent(serviceName, serviceInstances));
            } else {
                logger.info("getInstances by serviceName=" + serviceName + " is empty, waiting for serviceListener callback. url=" + url);
            }
        });

        // 前面onEvent内部已经会调用下面的方法了，这里再一次了
        listener.notify(serviceListener.getUrls(protocolServiceKey));
    }

    /**
     * Register the {@link ServiceInstancesChangedListener} If absent
     *
     * @param url      {@link URL}
     * @param listener the {@link ServiceInstancesChangedListener}
     */
    private void registerServiceInstancesChangedListener(URL url, ServiceInstancesChangedListener listener) {
        // eg [demo-provider, demo-provider-test1]:consumer://30.25.58.39/samples.servicediscovery.demo.DemoService
        String listenerId = createListenerId(url, listener);// 进去
        // 添加到容器，返回值为t f，如果返回t表示之前没有，返回f表示重复添加了
        if (registeredListeners.add(listenerId)) {
            // 进去。进去是EventPublishingServiceDiscovery
            serviceDiscovery.addServiceInstancesChangedListener(listener);
        }
    }

    private String createListenerId(URL url, ServiceInstancesChangedListener listener) {
        return listener.getServiceNames() + ":" + url.toString(VERSION_KEY, GROUP_KEY, PROTOCOL_KEY);
    }

    /**
     * 1.developer explicitly specifies the application name this interface belongs to
     * 2.check Interface-App mapping
     * 3.use the services specified in registry url.
     *
     * 1.开发者显式指定该接口所属的应用程序名称
     * 2.检查Interface-App映射
     * 3.使用注册表url中指定的服务。 这三个步骤是获取appNames的顺序
     * @param subscribedURL
     * @return
     */
    // 两个参数 eg
    // consumer://30.25.58.39/samples.servicediscovery.demo.DemoService?REGISTRY_CLUSTER=org.apache.dubbo.config.RegistryConfig&application=demo-consumer&category=providers,configurators,routers&check=false&dubbo=2.0.2&init=false&interface=samples.servicediscovery.demo.DemoService&metadata-type=remote&methods=sayHello&pid=3520&provided-by=demo-provider&side=consumer&sticky=false&timestamp=1619495578337
    // ServiceDiscoveryRegistryDirectory
    protected Set<String> getServices(URL subscribedURL, final NotifyListener listener) {
        Set<String> subscribedServices = new TreeSet<>();

        // 获取 "provided-by" 参数值 很重要，消费者在xml必须配置。eg demo-provider，这个是应用级别的服务名称
        String serviceNames = subscribedURL.getParameter(PROVIDED_BY);
        if (StringUtils.isNotEmpty(serviceNames)) {
            logger.info(subscribedURL.getServiceInterface() + " mapping to " + serviceNames + " instructed by provided-by set by user.");
            // samples.servicediscovery.demo.DemoService mapping to demo-provider instructed by provided-by set by user.
            subscribedServices.addAll(parseServices(serviceNames));
            // subscribedServices = {TreeSet@5025}  size = 1
            // 0 = "demo-provider"
        }

        // 如果为空，即消费业务方没有指定 "provided-by" 参数值
        if (isEmpty(subscribedServices)) {
            // findMappedServices、DefaultMappingListener 进去
            Set<String> mappedServices = findMappedServices(subscribedURL, new DefaultMappingListener(subscribedURL, subscribedServices, listener));
            // instructed by remote metadata center（前面是instructed by provided-by set by user.）
            logger.info(subscribedURL.getServiceInterface() + " mapping to " + serviceNames + " instructed by remote metadata center.");
            subscribedServices.addAll(mappedServices);
            // 如果还是空，则直接获取getSubscribedServices填充到返回结果，subscribedServices属性的内容是在构造函数里面处理填充的，主要是获取url的"subscribed-services"参数值（按照逗号分割）
            if (isEmpty(subscribedServices)) {
                subscribedServices.addAll(getSubscribedServices());
            }
        }
        return subscribedServices;
    }

    public static Set<String> parseServices(String literalServices) {
        return isBlank(literalServices) ? emptySet() :
                unmodifiableSet(of(literalServices.split(","))// Stream.of。根据","分割
                        .map(String::trim)
                        .filter(StringUtils::isNotEmpty)
                        .collect(toSet()));
    }

    /**
     * Get the subscribed service names
     *
     * @return non-null
     */
    public Set<String> getSubscribedServices() {
        return subscribedServices;
    }

    /**
     * Get the mapped services name by the specified {@link URL}
     *
     * @param subscribedURL
     * @return
     */
    protected Set<String> findMappedServices(URL subscribedURL, MappingListener listener) {
        // 默认为DynamicConfigurationServiceNameMapping，listener为DefaultMappingListener
        // 进去。这里是找到所有appName（应用级别的服务名称）
        return serviceNameMapping.getAndListen(subscribedURL, listener);
    }

    /**
     * Create an instance of {@link ServiceDiscoveryRegistry} if supported
     *
     * @param registryURL the {@link URL url} of registry
     * @return <code>null</code> if not supported
     */
    public static ServiceDiscoveryRegistry create(URL registryURL) { // 两个都进去
        return supports(registryURL) ? new ServiceDiscoveryRegistry(registryURL) : null;
    }

    /**
     * Supports or not ?
     *
     * @param registryURL the {@link URL url} of registry
     * @return if supported, return <code>true</code>, or <code>false</code>
     */
    public static boolean supports(URL registryURL) { // url含有"registry-type"参数，且值为"service"
        return SERVICE_REGISTRY_TYPE.equalsIgnoreCase(registryURL.getParameter(REGISTRY_TYPE_KEY));
    }

    private static List<URL> filterSubscribedURLs(URL subscribedURL, List<URL> exportedURLs) {
        return exportedURLs.stream()
                .filter(url -> isSameServiceInterface(subscribedURL, url))
                .filter(url -> isSameParameter(subscribedURL, url, VERSION_KEY))
                .filter(url -> isSameParameter(subscribedURL, url, GROUP_KEY))
                .filter(url -> isCompatibleProtocol(subscribedURL, url))
                .collect(Collectors.toList());
    }

    private static boolean isSameServiceInterface(URL one, URL another) {
        return Objects.equals(one.getServiceInterface(), another.getServiceInterface());
    }

    private static boolean isSameParameter(URL one, URL another, String key) {
        return Objects.equals(one.getParameter(key), another.getParameter(key));
    }

    private static boolean isCompatibleProtocol(URL one, URL another) {
        String protocol = one.getParameter(PROTOCOL_KEY);
        return isCompatibleProtocol(protocol, another);
    }

    private static boolean isCompatibleProtocol(String protocol, URL targetURL) {
        return protocol == null || Objects.equals(protocol, targetURL.getParameter(PROTOCOL_KEY))
                || Objects.equals(protocol, targetURL.getProtocol());
    }

    private class DefaultMappingListener implements MappingListener {
        private URL url;
        private Set<String> oldApps;
        private NotifyListener listener;

        // 这个最终会跑到 ZookeeperMetadataReport#addServiceMappingListener方法的参数中
        public DefaultMappingListener(URL subscribedURL, Set<String> serviceNames, NotifyListener listener) {
            // url = {URL@4360} "consumer://30.25.58.39/samples.servicediscovery.demo.DemoService?REGISTRY_CLUSTER=org.apache.dubbo.config.RegistryConfig&application=demo-consumer&category=providers,configurators,routers&check=false&dubbo=2.0.2&init=false&interface=samples.servicediscovery.demo.DemoService&metadata-type=remote&methods=sayHello&pid=8079&provided-by=demo-provider&side=consumer&sticky=false&timestamp=1619507109916"
            // oldApps = {TreeSet@4370}  size = 0
            // listener = {ServiceDiscoveryRegistryDirectory@4350}
            this.url = subscribedURL;
            this.oldApps = serviceNames;
            this.listener = listener;
        }

        @Override
        public void onEvent(MappingChangedEvent event) {
            // event的两个属性填充处看 ZookeeperMetadataReport#addServiceMappingListener
            Set<String> newApps = event.getApps();
            Set<String> tempOldApps = oldApps;
            oldApps = newApps;

            if (CollectionUtils.isEmpty(newApps)) {
                return;
            }

            // 这里表示全部是新的appName，全都需要subscribeURLs
            if (CollectionUtils.isEmpty(tempOldApps) && newApps.size() > 0) {
                // // 进去
                subscribeURLs(url, listener, newApps);
                return;
            }

            for (String newAppName : newApps) {
                // 这是表示只对新的appName进行subscribeURLs的调用，旧的之前已经掉过了subscribeURLs了
                if (!tempOldApps.contains(newAppName)) {
                    subscribeURLs(url, listener, newApps);
                    return;
                }
            }
        }
    }
}