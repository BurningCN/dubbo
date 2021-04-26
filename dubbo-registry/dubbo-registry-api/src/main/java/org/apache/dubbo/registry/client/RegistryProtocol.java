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
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.timer.HashedWheelTimer;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;
import org.apache.dubbo.registry.RegistryService;
import org.apache.dubbo.registry.integration.AbstractConfiguratorListener;
import org.apache.dubbo.registry.integration.DynamicDirectory;
import org.apache.dubbo.registry.integration.InterfaceCompatibleRegistryProtocol;
import org.apache.dubbo.registry.integration.RegistryProtocolListener;
import org.apache.dubbo.registry.retry.ReExportTask;
import org.apache.dubbo.registry.support.SkipFailbackWrapperException;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProtocolServer;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Cluster;
import org.apache.dubbo.rpc.cluster.ClusterInvoker;
import org.apache.dubbo.rpc.cluster.Configurator;
import org.apache.dubbo.rpc.cluster.governance.GovernanceRuleRepository;
import org.apache.dubbo.rpc.cluster.support.MergeableCluster;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ProviderModel;
import org.apache.dubbo.rpc.protocol.InvokerWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CLUSTER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_VERSION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.EXTRA_KEYS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.HIDE_KEY_PREFIX;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.LOADBALANCE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METHODS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.MONITOR_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.RELEASE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMESTAMP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.common.constants.FilterConstants.VALIDATION_KEY;
import static org.apache.dubbo.common.constants.QosConstants.ACCEPT_FOREIGN_IP;
import static org.apache.dubbo.common.constants.QosConstants.QOS_ENABLE;
import static org.apache.dubbo.common.constants.QosConstants.QOS_HOST;
import static org.apache.dubbo.common.constants.QosConstants.QOS_PORT;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.OVERRIDE_PROTOCOL;
import static org.apache.dubbo.common.constants.RegistryConstants.PROVIDERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_PROTOCOL;
import static org.apache.dubbo.common.constants.RegistryConstants.ROUTERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.SERVICE_REGISTRY_PROTOCOL;
import static org.apache.dubbo.common.utils.UrlUtils.classifyUrls;
import static org.apache.dubbo.registry.Constants.CONFIGURATORS_SUFFIX;
import static org.apache.dubbo.registry.Constants.CONSUMER_PROTOCOL;
import static org.apache.dubbo.registry.Constants.DEFAULT_REGISTRY_RETRY_PERIOD;
import static org.apache.dubbo.registry.Constants.PROVIDER_PROTOCOL;
import static org.apache.dubbo.registry.Constants.REGISTER_IP_KEY;
import static org.apache.dubbo.registry.Constants.REGISTER_KEY;
import static org.apache.dubbo.registry.Constants.REGISTRY_RETRY_PERIOD_KEY;
import static org.apache.dubbo.registry.Constants.SIMPLIFIED_KEY;
import static org.apache.dubbo.remoting.Constants.BIND_IP_KEY;
import static org.apache.dubbo.remoting.Constants.BIND_PORT_KEY;
import static org.apache.dubbo.remoting.Constants.CHECK_KEY;
import static org.apache.dubbo.remoting.Constants.CODEC_KEY;
import static org.apache.dubbo.remoting.Constants.CONNECTIONS_KEY;
import static org.apache.dubbo.remoting.Constants.EXCHANGER_KEY;
import static org.apache.dubbo.remoting.Constants.SERIALIZATION_KEY;
import static org.apache.dubbo.rpc.Constants.DEPRECATED_KEY;
import static org.apache.dubbo.rpc.Constants.INTERFACES;
import static org.apache.dubbo.rpc.Constants.MOCK_KEY;
import static org.apache.dubbo.rpc.Constants.TOKEN_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.EXPORT_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.WARMUP_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.WEIGHT_KEY;

/**
 * TODO, replace RegistryProtocol completely in the future.
 */
public class RegistryProtocol implements Protocol {
    public static final String[] DEFAULT_REGISTER_PROVIDER_KEYS = {
            APPLICATION_KEY, CODEC_KEY, EXCHANGER_KEY, SERIALIZATION_KEY, CLUSTER_KEY, CONNECTIONS_KEY, DEPRECATED_KEY,
            GROUP_KEY, LOADBALANCE_KEY, MOCK_KEY, PATH_KEY, TIMEOUT_KEY, TOKEN_KEY, VERSION_KEY, WARMUP_KEY,
            WEIGHT_KEY, TIMESTAMP_KEY, DUBBO_VERSION_KEY, RELEASE_KEY
    };

    public static final String[] DEFAULT_REGISTER_CONSUMER_KEYS = {
            APPLICATION_KEY, VERSION_KEY, GROUP_KEY, DUBBO_VERSION_KEY, RELEASE_KEY
    };

    private final static Logger logger = LoggerFactory.getLogger(InterfaceCompatibleRegistryProtocol.class);
    private final Map<URL, NotifyListener> overrideListeners = new ConcurrentHashMap<>();
    private final Map<String, ServiceConfigurationListener> serviceConfigurationListeners = new ConcurrentHashMap<>();
    // 构造方法进去
    private final ProviderConfigurationListener providerConfigurationListener = new ProviderConfigurationListener();
    // To solve the problem of RMI repeated exposure port conflicts, the services that have been exposed are no longer exposed.
    // 为了解决RMI反复暴露/公开端口冲突的问题，已经公开的服务不再公开。
    // providerurl <--> exporter
    private final ConcurrentMap<String, ExporterChangeableWrapper<?>> bounds = new ConcurrentHashMap<>();
    protected Protocol protocol;
    protected RegistryFactory registryFactory;
    protected ProxyFactory proxyFactory;

    private ConcurrentMap<URL, ReExportTask> reExportFailedTasks = new ConcurrentHashMap<>();
    private HashedWheelTimer retryTimer = new HashedWheelTimer(new NamedThreadFactory("DubboReexportTimer", true), DEFAULT_REGISTRY_RETRY_PERIOD, TimeUnit.MILLISECONDS, 128);

    //Filter the parameters that do not need to be output in url(Starting with .)
    private static String[] getFilteredKeys(URL url) {
        Map<String, String> params = url.getParameters();
        if (CollectionUtils.isNotEmptyMap(params)) {
            return params.keySet().stream()
                    .filter(k -> k.startsWith(HIDE_KEY_PREFIX))
                    .toArray(String[]::new);
        } else {
            return new String[0];
        }
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public void setRegistryFactory(RegistryFactory registryFactory) {
        this.registryFactory = registryFactory;
    }

    public void setProxyFactory(ProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    @Override
    public int getDefaultPort() {
        return 9090;
    }

    public Map<URL, NotifyListener> getOverrideListeners() {
        return overrideListeners;
    }

    private void register(URL registryUrl, URL registeredProviderUrl) {
        // 获取 Registry，由 AbstractRegistryFactory 实现 进去
        Registry registry = registryFactory.getRegistry(registryUrl); // 第一个参数用于找到注册中心
        // 注册服务
        registry.register(registeredProviderUrl);// 第二个参数就是把provider的信息注册到注册中心上
    }

    private void registerStatedUrl(URL registryUrl, URL registeredProviderUrl, boolean registered) {
        // 进去
        ProviderModel model = ApplicationModel.getProviderModel(registeredProviderUrl.getServiceKey());
        // 构建RegisterStatedURL，添加到model的urls容器（三个参数表示registeredProviderUrl注册到了registryUrl，registered表示是否注册成功）
        model.addStatedUrl(new ProviderModel.RegisterStatedURL(
                registeredProviderUrl,
                registryUrl,
                registered));
    }

    @Override
    public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {
        // 获取注册中心 URL，以 zookeeper 注册中心为例，下面一行执行后得到的示例 registryUrl 如下：
        // zookeeper://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=demo-provider&dubbo=2.0.2&export=dubbo%3A%2F%2F172.17.48.52%3A20880%2Fcom.alibaba.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddemo-provider
        // 注意步进重写的是getRegistryUrl方法，因为this是InterfaceCompatibleRegistryProtocol实例
        URL registryUrl = getRegistryUrl(originInvoker);
        // url to export locally
        // 获取url中export参数的值并解码返回（填充+编码是在ServiceConfig的****标记处），进去
        URL providerUrl = getProviderUrl(originInvoker);

        // FIXME 当提供者订阅时，它将影响场景:某个JVM公开服务并调用相同的服务。由于已订阅的键与服务的名称一起被缓存，因此它会覆盖订阅信息。
        // 获取订阅 URL，内部会根据providerUrl生成overrideSubscribeUrl，进去。比如：provider://172.17.48.52:20880/com.alibaba.dubbo.demo.DemoService?category=configurators&check=false&anyhost=true&application=demo-provider&dubbo=2.0.2&generic=false&interface=com.alibaba.dubbo.demo.DemoService&methods=sayHello
        final URL overrideSubscribeUrl = getSubscribedOverrideUrl(providerUrl);
        // 构建一个覆盖监听器（该类是RegistryProtocol本类的内部类，其实NotifyListener的子类），进去
        final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl, originInvoker);
        // 添加到map
        overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);

        // 注意下，到现在涉及到三个url了，registryUrl、providerUrl、overrideSubscribeUrl

        // 使用监听器覆盖providerUrl（的一些参数）? 进去
        providerUrl = overrideUrlWithConfig(providerUrl, overrideSubscribeListener);

        // export invoker 内部主要是创建了NettyServer，进去
        final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker, providerUrl);// < ------核心1：服务导出

        // url to registry（eg ZookeeperRegistry、ZookeeperServiceDiscovery）
        final Registry registry = getRegistry(originInvoker);
        // 获取将注册的服务提供者URL，比如：dubbo://172.17.48.52:20880/com.alibaba.dubbo.demo.DemoService?anyhost=true&application=demo-provider&dubbo=2.0.2&generic=false&interface=com.alibaba.dubbo.demo.DemoService&methods=sayHello
        final URL registeredProviderUrl = getUrlToRegistry(providerUrl, registryUrl);

        // 获取 register 参数
        boolean register = providerUrl.getParameter(REGISTER_KEY, true);
        // 根据 register 的值决定是否注册服务
        if (register) {
            // 向注册中心注册服务，进去 ，实例为ListenerRegistryWrapper（Registry是spi接口，被ListenerRegistryWrapper包装了ZooKeeperRegistry或者ServiceDiscoveryRegistry）
            register(registryUrl, registeredProviderUrl); // < ------核心2：服务注册
        }

        // register stated url on provider model 进去
        registerStatedUrl(registryUrl, registeredProviderUrl, register);


        // 给exporter设置两个属性值
        exporter.setRegisterUrl(registeredProviderUrl);
        exporter.setSubscribeUrl(overrideSubscribeUrl);

        // Deprecated! Subscribe to override rules in 2.6.x or before. 弃用!订阅以覆盖2.6中的规则。x或之前。
        // 向注册中心进行订阅 override 数据 进去 ，这里的registry为ListenerRegistryWrapper
        registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);

        // "暴露"事件完毕，通知"RegistryProtocolListener"监听器，
        notifyExport(exporter);

        // Ensure that a new exporter instance is returned every time export 确保每次导出时都返回一个新的导出器实例
        // 创建并返回 DestroyableExporter（内部类），进去
        return new DestroyableExporter<>(exporter);
    }

    private <T> void notifyExport(ExporterChangeableWrapper<T> exporter) {
        // 获取满足激活条件的RegistryProtocolListener扩展实例（目前有唯一扩展子类，激活条件是满足的）
        List<RegistryProtocolListener> listeners = ExtensionLoader.getExtensionLoader(RegistryProtocolListener.class)
                // 进去看下key的处理（其实就是激活扩展名为registry、protocol、listener的扩展实例，但是看了spi文件，没有满足的，所以返回的list为空）
                .getActivateExtension(exporter.getOriginInvoker().getUrl(), "registry.protocol.listener");
        if (CollectionUtils.isNotEmpty(listeners)) {
            for (RegistryProtocolListener listener : listeners) {
                // 挨个调用onExport
                listener.onExport(this, exporter);
            }
        }
    }

    private URL overrideUrlWithConfig(URL providerUrl, OverrideListener listener) {

        // ProviderConfigurationListener是该类的内部类，去看下属性的赋值处，然后overrideUrl进去
        providerUrl = providerConfigurationListener.overrideUrl(providerUrl);

        // ServiceConfigurationListener、ProviderConfigurationListener都是该类的内部类，构造方法进去
        ServiceConfigurationListener serviceConfigurationListener = new ServiceConfigurationListener(providerUrl, listener);
        // 添加到map
        serviceConfigurationListeners.put(providerUrl.getServiceKey(), serviceConfigurationListener);
        // 再次覆盖，进去
        return serviceConfigurationListener.overrideUrl(providerUrl);

    }

    @SuppressWarnings("unchecked")
    private <T> ExporterChangeableWrapper<T> doLocalExport(final Invoker<T> originInvoker, URL providerUrl) {
        // 访问缓存
        String key = getCacheKey(originInvoker);
        // 写缓存，去看下bounds属性的上面的注释（ providerurl <--> exporter）
        return (ExporterChangeableWrapper<T>) bounds.computeIfAbsent(key, s -> {
            // 创建 Invoker 为委托类对象
            Invoker<?> invokerDelegate = new InvokerDelegate<>(originInvoker, providerUrl);
            // 调用 protocol 的 export 方法导出服务，protocol是Protocol$Adaptive，内部export方法会调用invoker.getUrl(),
            // 其实就是参数providerUrl，而providerUrl一般就是dubbo://开头的，所以最后会进 DubboProtocol
            // export之后创建ExporterChangeableWrapper对象，进去（该类是RegistryProtocol的内部类）
            return new ExporterChangeableWrapper<>((Exporter<T>) protocol.export(invokerDelegate), originInvoker);
        });
        // 上面的代码是典型的双重检查锁，大家在阅读 Dubbo 的源码中，会多次见到。接下来，我们把重点放在 Protocol 的 export 方法上。假设运行时协
        // 议为 dubbo，此处的 protocol 变量会在运行时加载 DubboProtocol，并调用 DubboProtocol 的 export 方法。所以，接下来我们目光转移
        // 到 DubboProtocol 的 export 方法上，相关分析如下：
    }

    public <T> void reExport(Exporter<T> exporter, URL newInvokerUrl) {
        if (exporter instanceof ExporterChangeableWrapper) {
            ExporterChangeableWrapper<T> exporterWrapper = (ExporterChangeableWrapper<T>) exporter;
            Invoker<T> originInvoker = exporterWrapper.getOriginInvoker();
            reExport(originInvoker, newInvokerUrl);
        }
    }

    /**
     * Reexport the invoker of the modified url
     *
     * @param originInvoker
     * @param newInvokerUrl
     * @param <T>
     */
    @SuppressWarnings("unchecked")
    public <T> void reExport(final Invoker<T> originInvoker, URL newInvokerUrl) {
        String key = getCacheKey(originInvoker);
        ExporterChangeableWrapper<T> exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
        URL registeredUrl = exporter.getRegisterUrl();

        URL registryUrl = getRegistryUrl(originInvoker);
        URL newProviderUrl = getUrlToRegistry(newInvokerUrl, registryUrl);

        // update local exporter
        Invoker<T> invokerDelegate = new InvokerDelegate<T>(originInvoker, newInvokerUrl);
        exporter.setExporter(protocol.export(invokerDelegate));

        // update registry
        if (!newProviderUrl.equals(registeredUrl)) {
            try {
                doReExport(originInvoker, exporter, registryUrl, registeredUrl, newProviderUrl);
            } catch (Exception e) {
                // 下面大体逻辑可以参考FailbackRegistry的addFailedRegistered方法

                ReExportTask oldTask = reExportFailedTasks.get(registeredUrl);
                if (oldTask != null) {
                    return;
                }
                ReExportTask task = new ReExportTask(
                        () -> doReExport(originInvoker, exporter, registryUrl, registeredUrl, newProviderUrl),
                        registeredUrl,
                        null
                );
                oldTask = reExportFailedTasks.putIfAbsent(registeredUrl, task);
                if (oldTask == null) {
                    // never has a retry task. then start a new task for retry.
                    retryTimer.newTimeout(task, registryUrl.getParameter(REGISTRY_RETRY_PERIOD_KEY, DEFAULT_REGISTRY_RETRY_PERIOD), TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    private <T> void doReExport(final Invoker<T> originInvoker, ExporterChangeableWrapper<T> exporter,
                                URL registryUrl, URL oldProviderUrl, URL newProviderUrl) {
        if (getProviderUrl(originInvoker).getParameter(REGISTER_KEY, true)) {
            Registry registry = null;
            try {
                registry = getRegistry(originInvoker);
            } catch (Exception e) {
                //  忽略Failback异常
                throw new SkipFailbackWrapperException(e);
            }

            logger.info("Try to unregister old url: " + oldProviderUrl);
            registry.reExportUnregister(oldProviderUrl);

            logger.info("Try to register new url: " + newProviderUrl);
            registry.reExportRegister(newProviderUrl);
        }
        try {
            ProviderModel.RegisterStatedURL statedUrl = getStatedUrl(registryUrl, newProviderUrl);
            statedUrl.setProviderUrl(newProviderUrl);
            exporter.setRegisterUrl(newProviderUrl);
        } catch (Exception e) {
            throw new SkipFailbackWrapperException(e);
        }
    }

    private ProviderModel.RegisterStatedURL getStatedUrl(URL registryUrl, URL providerUrl) {
        ProviderModel providerModel = ApplicationModel.getServiceRepository()
                .lookupExportedService(providerUrl.getServiceKey());

        List<ProviderModel.RegisterStatedURL> statedUrls = providerModel.getStatedUrl();
        return statedUrls.stream()
                .filter(u -> u.getRegistryUrl().equals(registryUrl)
                        && u.getProviderUrl().getProtocol().equals(providerUrl.getProtocol()))
                .findFirst().orElseThrow(() -> new IllegalStateException("There should have at least one registered url."));
    }

    /**
     * Get an instance of registry based on the address of invoker
     *
     * @param originInvoker
     * @return
     */
    protected Registry getRegistry(final Invoker<?> originInvoker) {
        // 进去
        URL registryUrl = getRegistryUrl(originInvoker);
        // registryFactory 为 RegistryFactory$Adaptive ，其赋值处是在setRegistryFactory的调用，而setXX是怎么调用的，不用猜就是ioc(具体是谁取决于registryUrl protocol值，是registry://还是service-discovery-registry://)
        // 在RegistryProtocol实例化的时候（ServiceConfig的PROTOCOL.export(wrapperInvoker)处），会inject相关依赖的扩展实例（通过调用set方法）
        return registryFactory.getRegistry(registryUrl);
    }

    // 这个是服务端提供者专用的（export方法），注意看子类重写的
    protected URL getRegistryUrl(Invoker<?> originInvoker) {
        return originInvoker.getUrl();
    }

    // 这个是消费者专用的（refer方法），注意看子类重写的
    protected URL getRegistryUrl(URL url) {
        if (SERVICE_REGISTRY_PROTOCOL.equals(url.getProtocol())) {
            return url;
        }
        return url.addParameter(REGISTRY_KEY, url.getProtocol()).setProtocol(SERVICE_REGISTRY_PROTOCOL);
    }

    /**
     * Return the url that is registered to the registry and filter the url parameter once
     * 返回注册到注册中心的url并过滤url参数一次 。提供者专用，export方法使用的，即provider注册到Registry需要做一下处理，将处理后的url注册到Registry
     *
     * @param providerUrl
     * @return url to registry.
     */
    private URL getUrlToRegistry(final URL providerUrl, final URL registryUrl) {
        //The address you see at the registry。是否需要简化，即去掉某些参数，默认不需要
        if (!registryUrl.getParameter(SIMPLIFIED_KEY, false)) {
            return providerUrl.removeParameters(getFilteredKeys(providerUrl)).removeParameters(
                    MONITOR_KEY, BIND_IP_KEY, BIND_PORT_KEY, QOS_ENABLE, QOS_HOST, QOS_PORT, ACCEPT_FOREIGN_IP, VALIDATION_KEY,
                    INTERFACES);
        } else {
            String extraKeys = registryUrl.getParameter(EXTRA_KEYS_KEY, "");
            // if path is not the same as interface name then we should keep INTERFACE_KEY,
            // otherwise, the registry structure of zookeeper would be '/dubbo/path/providers',
            // but what we expect is '/dubbo/interface/providers'
            if (!providerUrl.getPath().equals(providerUrl.getParameter(INTERFACE_KEY))) {
                if (StringUtils.isNotEmpty(extraKeys)) {
                    extraKeys += ",";
                }
                extraKeys += INTERFACE_KEY;
            }
            String[] paramsToRegistry = getParamsToRegistry(DEFAULT_REGISTER_PROVIDER_KEYS
                    , COMMA_SPLIT_PATTERN.split(extraKeys));
            return URL.valueOf(providerUrl, paramsToRegistry, providerUrl.getParameter(METHODS_KEY, (String[]) null));
        }

    }
    // 方法作用将参数 的dubbo:// 变成 provider://，且加了&category=configurators&check=false参数对
    // eg : dubbo://30.25.58.102:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=30.25.58.102&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&metadata-type=remote&methods=sayHello,sayHelloAsync&pid=11828&release=&side=provider&timestamp=1609920795524 ---> 变成 provider://30.25.58.102:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=30.25.58.102&bind.port=20880&category=configurators&check=false&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&metadata-type=remote&methods=sayHello,sayHelloAsync&pid=11828&release=&side=provider&timestamp=1609920795524
    private URL getSubscribedOverrideUrl(URL registeredProviderUrl) {
        return registeredProviderUrl.setProtocol(PROVIDER_PROTOCOL)
                .addParameters(CATEGORY_KEY, CONFIGURATORS_CATEGORY, CHECK_KEY, String.valueOf(false));
    }

    /**
     * Get the address of the providerUrl through the url of the invoker
     *
     * @param originInvoker
     * @return
     */
    private URL getProviderUrl(final Invoker<?> originInvoker) {
        // 获取export参数的值，并进行解码解码，比如 registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&export=dubbo%3A%2F%2F30.25.58.102%3A20880%2Forg.apache.dubbo.demo.DemoService%3Fanyhost%3Dtrue%26application%3Ddubbo-demo-api-provider%26bind.ip%3D30.25.58.102%26bind.port%3D20880%26default%3Dtrue%26deprecated%3Dfalse%26dubbo%3D2.0.2%26dynamic%3Dtrue%26generic%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26metadata-type%3Dremote%26methods%3DsayHello%2CsayHelloAsync%26pid%3D11828%26release%3D%26side%3Dprovider%26timestamp%3D1609920795524&pid=11828&registry=zookeeper&timestamp=1609920790513 解码为dubbo://30.25.58.102:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=30.25.58.102&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&metadata-type=remote&methods=sayHello,sayHelloAsync&pid=11828&release=&side=provider&timestamp=1609920795524
        String export = originInvoker.getUrl().getParameterAndDecoded(EXPORT_KEY);
        if (export == null || export.length() == 0) {
            throw new IllegalArgumentException("The registry export url is null! registry: " + originInvoker.getUrl());
        }
        // string - > URL
        return URL.valueOf(export);
    }

    /**
     * Get the key cached in bounds by invoker
     *
     * @param originInvoker
     * @return
     */
    private String getCacheKey(final Invoker<?> originInvoker) {
        // 进去
        URL providerUrl = getProviderUrl(originInvoker);
        // 移除dynamic、enabled参数对并转化为string返回
        String key = providerUrl.removeParameters("dynamic", "enabled").toFullString();
        return key;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        // 取 registry 参数值，并将其设置为协议头
        url = getRegistryUrl(url);
        // 获取注册中心实例
        Registry registry = registryFactory.getRegistry(url);
        if (RegistryService.class.equals(type)) {
            return proxyFactory.getInvoker((T) registry, type, url);
        }

        // group="a,b" or group="*"
        // 将 url 查询字符串转为 Map
        Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(REFER_KEY));
        // 获取 group 配置
        String group = qs.get(GROUP_KEY);
        if (group != null && group.length() > 0) {
            if ((COMMA_SPLIT_PATTERN.split(group)).length > 1 || "*".equals(group)) {
                // 通过 SPI 加载 MergeableCluster 实例，并调用 doRefer 继续执行服务引用逻辑
                return doRefer(Cluster.getCluster(MergeableCluster.NAME), registry, type, url);
            }
        }

        Cluster cluster = Cluster.getCluster(qs.get(CLUSTER_KEY));
        // 调用 doRefer 继续执行服务引用逻辑
        return doRefer(cluster, registry, type, url);
        // 上面代码首先为 url 设置协议头，然后根据 url 参数加载注册中心实例。然后获取 group 配置，根据 group 配置决定 doRefer 第一个参数的类型。这里的重点是 doRefer 方法
    }

    protected <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
        return interceptInvoker(getInvoker(cluster, registry, type, url), url);
    }

    protected <T> Invoker<T> interceptInvoker(ClusterInvoker<T> invoker, URL url) {
        List<RegistryProtocolListener> listeners = findRegistryProtocolListeners(url);
        if (CollectionUtils.isEmpty(listeners)) {
            return invoker;
        }

        for (RegistryProtocolListener listener : listeners) {
            listener.onRefer(this, invoker);
        }
        return invoker;
    }

    protected <T> ClusterInvoker<T> getInvoker(Cluster cluster, Registry registry, Class<T> type, URL url) {
        DynamicDirectory<T> directory = createDirectory(type, url);
        directory.setRegistry(registry);
        directory.setProtocol(protocol);
        // all attributes of REFER_KEY
        Map<String, String> parameters = new HashMap<String, String>(directory.getConsumerUrl().getParameters());
        URL urlToRegistry = new URL(CONSUMER_PROTOCOL, parameters.remove(REGISTER_IP_KEY), 0, type.getName(), parameters);
        if (directory.isShouldRegister()) {
            directory.setRegisteredConsumerUrl(urlToRegistry);
            registry.register(directory.getRegisteredConsumerUrl());
        }
        directory.buildRouterChain(urlToRegistry);
        directory.subscribe(toSubscribeUrl(urlToRegistry));

        return (ClusterInvoker<T>) cluster.join(directory);
    }

    protected <T> DynamicDirectory<T> createDirectory(Class<T> type, URL url) {
        return new ServiceDiscoveryRegistryDirectory<>(type, url);
    }

    public <T> void reRefer(DynamicDirectory<T> directory, URL newSubscribeUrl) {
        URL oldSubscribeUrl = directory.getRegisteredConsumerUrl();
        Registry registry = directory.getRegistry();
        registry.unregister(directory.getRegisteredConsumerUrl());
        directory.unSubscribe(toSubscribeUrl(oldSubscribeUrl));
        registry.register(directory.getRegisteredConsumerUrl());

        directory.setRegisteredConsumerUrl(newSubscribeUrl);
        directory.buildRouterChain(newSubscribeUrl);
        directory.subscribe(toSubscribeUrl(newSubscribeUrl));
    }

    protected static URL toSubscribeUrl(URL url) {
        return url.addParameter(CATEGORY_KEY, PROVIDERS_CATEGORY + "," + CONFIGURATORS_CATEGORY + "," + ROUTERS_CATEGORY);
    }

    protected List<RegistryProtocolListener> findRegistryProtocolListeners(URL url) {
        return ExtensionLoader.getExtensionLoader(RegistryProtocolListener.class)
                .getActivateExtension(url, "registry.protocol.listener");
    }

    // available to test
    public String[] getParamsToRegistry(String[] defaultKeys, String[] additionalParameterKeys) {
        int additionalLen = additionalParameterKeys.length;
        String[] registryParams = new String[defaultKeys.length + additionalLen];
        System.arraycopy(defaultKeys, 0, registryParams, 0, defaultKeys.length);
        System.arraycopy(additionalParameterKeys, 0, registryParams, defaultKeys.length, additionalLen);
        return registryParams;
    }

    @Override
    public void destroy() {
        List<RegistryProtocolListener> listeners = ExtensionLoader.getExtensionLoader(RegistryProtocolListener.class)
                .getLoadedExtensionInstances();
        if (CollectionUtils.isNotEmpty(listeners)) {
            for (RegistryProtocolListener listener : listeners) {
                listener.onDestroy();
            }
        }

        List<Exporter<?>> exporters = new ArrayList<>(bounds.values());
        for (Exporter<?> exporter : exporters) {
            exporter.unexport();
        }
        bounds.clear();

        ExtensionLoader.getExtensionLoader(GovernanceRuleRepository.class).getDefaultExtension()
                .removeListener(ApplicationModel.getApplication() + CONFIGURATORS_SUFFIX, providerConfigurationListener);
    }

    @Override
    public List<ProtocolServer> getServers() {
        return protocol.getServers();
    }

    //Merge the urls of configurators
    private static URL getConfigedInvokerUrl(List<Configurator> configurators, URL url) {
        if (configurators != null && configurators.size() > 0) {
            for (Configurator configurator : configurators) {
                // 进去 待看下相关的测试程序
                url = configurator.configure(url);
            }
        }
        return url;
    }

    public static class InvokerDelegate<T> extends InvokerWrapper<T> {
        private final Invoker<T> invoker;

        /**
         * @param invoker
         * @param url     invoker.getUrl return this value
         */
        public InvokerDelegate(Invoker<T> invoker, URL url) {
            super(invoker, url);
            // 父类有一个invoker，这里还来一个，完全没必要。可以把父类的访问级别变成protected
            this.invoker = invoker;
        }

        public Invoker<T> getInvoker() {
            if (invoker instanceof InvokerDelegate) {
                return ((InvokerDelegate<T>) invoker).getInvoker();
            } else {
                return invoker;
            }
        }
    }


    // 下面的代码太向静态代理模式了，代理类和目标类都实现接口
    private static class DestroyableExporter<T> implements Exporter<T> {

        private Exporter<T> exporter;

        // gx
        // 传进来的是 ExporterChangeableWrapper 实例
        public DestroyableExporter(Exporter<T> exporter) {
            this.exporter = exporter;
        }

        @Override
        public Invoker<T> getInvoker() {
            // 调用目标对象的方法
            return exporter.getInvoker();
        }

        @Override
        public void unexport() {
            // 调用目标对象的方法
            exporter.unexport();
        }
    }

    /**
     * Reexport: the exporter destroy problem in protocol
     * 1.Ensure that the exporter returned by registryprotocol can be normal destroyed
     * 2.No need to re-register to the registry after notify
     * 3.The invoker passed by the export method , would better to be the invoker of exporter
     */
    private class OverrideListener implements NotifyListener {
        private final URL subscribeUrl;
        private final Invoker originInvoker;


        private List<Configurator> configurators;

        public OverrideListener(URL subscribeUrl, Invoker originalInvoker) {
            this.subscribeUrl = subscribeUrl;
            this.originInvoker = originalInvoker;
        }

        /**
         * @param urls The list of registered information, is always not empty, The meaning is the same as the
         *             return value of {@link org.apache.dubbo.registry.RegistryService#lookup(URL)}.
         */
        @Override
        public synchronized void notify(List<URL> urls) {
            logger.debug("original override urls: " + urls);

            List<URL> matchedUrls = getMatchedUrls(urls, subscribeUrl.addParameter(CATEGORY_KEY,
                    CONFIGURATORS_CATEGORY));
            logger.debug("subscribe url: " + subscribeUrl + ", override urls: " + matchedUrls);

            // No matching results
            if (matchedUrls.isEmpty()) {
                return;
            }

            this.configurators = Configurator.toConfigurators(classifyUrls(matchedUrls, UrlUtils::isConfigurator))
                    .orElse(configurators);

            doOverrideIfNecessary();
        }

        public synchronized void doOverrideIfNecessary() {
            final Invoker<?> invoker;
            if (originInvoker instanceof InvokerDelegate) {
                invoker = ((InvokerDelegate<?>) originInvoker).getInvoker();
            } else {
                invoker = originInvoker;
            }
            //The origin invoker
            URL originUrl = RegistryProtocol.this.getProviderUrl(invoker);
            String key = getCacheKey(originInvoker);
            ExporterChangeableWrapper<?> exporter = bounds.get(key);
            if (exporter == null) {
                logger.warn(new IllegalStateException("error state, exporter should not be null"));
                return;
            }
            //The current, may have been merged many times
            URL currentUrl = exporter.getInvoker().getUrl();
            //Merged with this configuration
            URL newUrl = getConfigedInvokerUrl(configurators, currentUrl);
            newUrl = getConfigedInvokerUrl(providerConfigurationListener.getConfigurators(), newUrl);
            newUrl = getConfigedInvokerUrl(serviceConfigurationListeners.get(originUrl.getServiceKey())
                    .getConfigurators(), newUrl);
            if (!currentUrl.equals(newUrl)) {
                RegistryProtocol.this.reExport(originInvoker, newUrl);
                logger.info("exported provider url changed, origin url: " + originUrl +
                        ", old export url: " + currentUrl + ", new export url: " + newUrl);
            }
        }

        private List<URL> getMatchedUrls(List<URL> configuratorUrls, URL currentSubscribe) {
            List<URL> result = new ArrayList<URL>();
            for (URL url : configuratorUrls) {
                URL overrideUrl = url;
                // Compatible with the old version
                if (url.getParameter(CATEGORY_KEY) == null && OVERRIDE_PROTOCOL.equals(url.getProtocol())) {
                    overrideUrl = url.addParameter(CATEGORY_KEY, CONFIGURATORS_CATEGORY);
                }

                // Check whether url is to be applied to the current service
                if (UrlUtils.isMatch(currentSubscribe, overrideUrl)) {
                    result.add(url);
                }
            }
            return result;
        }
    }

    // OK
    private class ServiceConfigurationListener extends AbstractConfiguratorListener {
        private URL providerUrl;
        private OverrideListener notifyListener;

        public ServiceConfigurationListener(URL providerUrl, OverrideListener notifyListener) {
            this.providerUrl = providerUrl;
            this.notifyListener = notifyListener;
            // 进去
            this.initWith(DynamicConfiguration.getRuleKey(providerUrl) + CONFIGURATORS_SUFFIX);
        }

        private <T> URL overrideUrl(URL providerUrl) {
            return RegistryProtocol.getConfigedInvokerUrl(configurators, providerUrl);
        }

        @Override
        protected void notifyOverrides() {
            notifyListener.doOverrideIfNecessary();
        }
    }

    private class ProviderConfigurationListener extends AbstractConfiguratorListener {

        public ProviderConfigurationListener() {
            // ApplicationName + ".configurators"，initWith进去
            this.initWith(ApplicationModel.getApplication() + CONFIGURATORS_SUFFIX);
        }

        /**
         * Get existing configuration rule and override provider url before exporting.
         *
         * @param providerUrl
         * @param <T>
         * @return
         */
        // 看上面注释
        private <T> URL overrideUrl(URL providerUrl) {
            // 进去
            return RegistryProtocol.getConfigedInvokerUrl(configurators, providerUrl);
        }

        @Override
        protected void notifyOverrides() {
            overrideListeners.values().forEach(listener -> ((OverrideListener) listener).doOverrideIfNecessary());
        }
    }

    /**
     * exporter proxy, establish the corresponding relationship between the returned exporter and the exporter
     * exported by the protocol, and can modify the relationship at the time of override.
     *
     *   exporter的代理，在 returned exporter 和 被协议导出的exporter之间建立相应的关系，并可在重写时修改关系。
     * @param <T>
     */
    private class ExporterChangeableWrapper<T> implements Exporter<T> {

        // 单线程
        private final ExecutorService executor = newSingleThreadExecutor(new NamedThreadFactory("Exporter-Unexport", true));

        private final Invoker<T> originInvoker;
        private Exporter<T> exporter;
        private URL subscribeUrl;
        private URL registerUrl;

        // gx
        // 传进来的一般是DubboExporter
        public ExporterChangeableWrapper(Exporter<T> exporter, Invoker<T> originInvoker) {
            this.exporter = exporter;
            this.originInvoker = originInvoker;
        }

        public Invoker<T> getOriginInvoker() {
            return originInvoker;
        }

        @Override
        public Invoker<T> getInvoker() {
            return exporter.getInvoker();
        }

        public void setExporter(Exporter<T> exporter) {
            this.exporter = exporter;
        }

        @Override
        public void unexport() {
            // 和doLocalExport的逻辑第一步逻辑一样 ，getCacheKey内部根据originInvoker找到ProviderUrl（并 移除dynamic、enabled参数）
            String key = getCacheKey(this.originInvoker);
            // 从内存容器移除
            bounds.remove(key);

            // 和该类的export内某一步骤一样，getRegistry方法内部根据originInvoker找到registryUrl，根据此url找到Registry
            // originInvoker是Provider+Registry的url融合--->ServiceConfig的****标记处
            Registry registry = RegistryProtocol.this.getRegistry(originInvoker);
            try {
                // 该类的export内有一步骤是register，这里是unregister，进去
                // registerUrl看下setXX的调用处
                registry.unregister(registerUrl);
            } catch (Throwable t) {
                logger.warn(t.getMessage(), t);
            }
            try {
                // subscribeUrl去看下其setXX的调用处（一个subscribeUrl对应一个OverrideListener）
                NotifyListener listener = RegistryProtocol.this.overrideListeners.remove(subscribeUrl);
                // 取消注册，去进
                registry.unsubscribe(subscribeUrl, listener);
                // 治理规则仓库
                ExtensionLoader.getExtensionLoader(GovernanceRuleRepository.class).getDefaultExtension()
                        .removeListener(subscribeUrl.getServiceKey() + CONFIGURATORS_SUFFIX,
                                serviceConfigurationListeners.get(subscribeUrl.getServiceKey()));
            } catch (Throwable t) {
                logger.warn(t.getMessage(), t);
            }

            executor.submit(() -> {
                try {
                    // 获取服务关闭超时时间，进去
                    int timeout = ConfigurationUtils.getServerShutdownTimeout();
                    if (timeout > 0) {
                        // 日志
                        logger.info("Waiting " + timeout + "ms for registry to notify all consumers before unexport. " +
                                "Usually, this is called when you use dubbo API");
                        Thread.sleep(timeout);
                    }
                    // 到这里才调用目标对象的unexport（在线程类，异步的）
                    exporter.unexport();
                } catch (Throwable t) {
                    logger.warn(t.getMessage(), t);
                }
            });
        }

        // gx
        public void setSubscribeUrl(URL subscribeUrl) {
            this.subscribeUrl = subscribeUrl;
        }

        // gx
        public void setRegisterUrl(URL registerUrl) {
            this.registerUrl = registerUrl;
        }

        public URL getRegisterUrl() {
            return registerUrl;
        }
    }

    // for unit test
    private static RegistryProtocol INSTANCE;

    // for unit test
    public RegistryProtocol() {
        INSTANCE = this;
    }

    // for unit test
    public static RegistryProtocol getRegistryProtocol() {
        if (INSTANCE == null) {
            ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(REGISTRY_PROTOCOL); // load
        }
        return INSTANCE;
    }
}
