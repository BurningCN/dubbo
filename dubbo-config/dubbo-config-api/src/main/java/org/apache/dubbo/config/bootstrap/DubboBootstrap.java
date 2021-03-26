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
package org.apache.dubbo.config.bootstrap;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.config.Environment;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.config.configcenter.DynamicConfigurationFactory;
import org.apache.dubbo.common.config.configcenter.wrapper.CompositeDynamicConfiguration;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.lang.ShutdownHookCallback;
import org.apache.dubbo.common.lang.ShutdownHookCallbacks;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.concurrent.ScheduledCompletableFuture;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConfigCenterConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.DubboShutdownHook;
import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.config.MetricsConfig;
import org.apache.dubbo.config.ModuleConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.ServiceConfigBase;
import org.apache.dubbo.config.SslConfig;
import org.apache.dubbo.config.bootstrap.builders.ApplicationBuilder;
import org.apache.dubbo.config.bootstrap.builders.ConsumerBuilder;
import org.apache.dubbo.config.bootstrap.builders.ProtocolBuilder;
import org.apache.dubbo.config.bootstrap.builders.ProviderBuilder;
import org.apache.dubbo.config.bootstrap.builders.ReferenceBuilder;
import org.apache.dubbo.config.bootstrap.builders.RegistryBuilder;
import org.apache.dubbo.config.bootstrap.builders.ServiceBuilder;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.config.metadata.ConfigurableMetadataServiceExporter;
import org.apache.dubbo.config.utils.ConfigValidationUtils;
import org.apache.dubbo.config.utils.ReferenceConfigCache;
import org.apache.dubbo.event.EventDispatcher;
import org.apache.dubbo.event.EventListener;
import org.apache.dubbo.event.GenericEventListener;
import org.apache.dubbo.metadata.MetadataService;
import org.apache.dubbo.metadata.MetadataServiceExporter;
import org.apache.dubbo.metadata.WritableMetadataService;
import org.apache.dubbo.metadata.report.MetadataReportFactory;
import org.apache.dubbo.metadata.report.MetadataReportInstance;
import org.apache.dubbo.registry.client.DefaultServiceInstance;
import org.apache.dubbo.registry.client.ServiceInstance;
import org.apache.dubbo.registry.client.ServiceInstanceCustomizer;
import org.apache.dubbo.registry.client.metadata.MetadataUtils;
import org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils;
import org.apache.dubbo.registry.client.metadata.store.InMemoryWritableMetadataService;
import org.apache.dubbo.registry.client.metadata.store.RemoteMetadataServiceImpl;
import org.apache.dubbo.registry.support.AbstractRegistryFactory;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.apache.dubbo.common.config.ConfigurationUtils.parseProperties;
import static org.apache.dubbo.common.config.configcenter.DynamicConfiguration.getDynamicConfiguration;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_METADATA_STORAGE_TYPE;
import static org.apache.dubbo.common.constants.CommonConstants.REGISTRY_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.REMOTE_METADATA_STORAGE_TYPE;
import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.apache.dubbo.common.function.ThrowableAction.execute;
import static org.apache.dubbo.common.utils.StringUtils.isEmpty;
import static org.apache.dubbo.common.utils.StringUtils.isNotEmpty;
import static org.apache.dubbo.metadata.MetadataConstants.DEFAULT_METADATA_PUBLISH_DELAY;
import static org.apache.dubbo.metadata.MetadataConstants.METADATA_PUBLISH_DELAY_KEY;
import static org.apache.dubbo.metadata.WritableMetadataService.getDefaultExtension;
import static org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils.calInstanceRevision;
import static org.apache.dubbo.registry.client.metadata.ServiceInstanceMetadataUtils.setMetadataStorageType;
import static org.apache.dubbo.registry.support.AbstractRegistryFactory.getServiceDiscoveries;
import static org.apache.dubbo.remoting.Constants.CLIENT_KEY;

/**
 * See {@link ApplicationModel} and {@link ExtensionLoader} for why this class is designed to be singleton.
 * <p>
 * The bootstrap class of Dubbo
 * <p>
 * Get singleton instance by calling static method {@link #getInstance()}.
 * Designed as singleton because some classes inside Dubbo, such as ExtensionLoader, are designed only for one instance per process.
 *
 * @since 2.7.5
 */
public class DubboBootstrap extends GenericEventListener {

    public static final String DEFAULT_REGISTRY_ID = "REGISTRY#DEFAULT";

    public static final String DEFAULT_PROTOCOL_ID = "PROTOCOL#DEFAULT";

    public static final String DEFAULT_SERVICE_ID = "SERVICE#DEFAULT";

    public static final String DEFAULT_REFERENCE_ID = "REFERENCE#DEFAULT";

    public static final String DEFAULT_PROVIDER_ID = "PROVIDER#DEFAULT";

    public static final String DEFAULT_CONSUMER_ID = "CONSUMER#DEFAULT";

    private static final String NAME = DubboBootstrap.class.getSimpleName();

    // 进去
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static volatile DubboBootstrap instance;

    private final AtomicBoolean awaited = new AtomicBoolean(false);

    private final Lock lock = new ReentrantLock();

    private final Condition condition = lock.newCondition();

    private final Lock destroyLock = new ReentrantLock();

    private final ExecutorService executorService = newSingleThreadExecutor();
    // getDefaultExtension 进去
    private final EventDispatcher eventDispatcher = EventDispatcher.getDefaultExtension();

    // 去看下DefaultExecutorRepository的空参构造
    private final ExecutorRepository executorRepository = getExtensionLoader(ExecutorRepository.class).getDefaultExtension();

    private final ConfigManager configManager;

    private final Environment environment;

    private ReferenceConfigCache cache;

    private volatile boolean exportAsync;

    private volatile boolean referAsync;

    private AtomicBoolean initialized = new AtomicBoolean(false);

    private AtomicBoolean started = new AtomicBoolean(false);

    private AtomicBoolean ready = new AtomicBoolean(true);

    private AtomicBoolean destroyed = new AtomicBoolean(false);

    private volatile ServiceInstance serviceInstance;

    private volatile MetadataService metadataService;

    private volatile MetadataServiceExporter metadataServiceExporter;

    private List<ServiceConfigBase<?>> exportedServices = new ArrayList<>();

    private List<Future<?>> asyncExportingFutures = new ArrayList<>();

    private List<CompletableFuture<Object>> asyncReferringFutures = new ArrayList<>();

    /**
     * See {@link ApplicationModel} and {@link ExtensionLoader} for why DubboBootstrap is designed to be singleton.
     */
    // 双重检查
    public static DubboBootstrap getInstance() {
        // instance是被volatile修饰的
        if (instance == null) {
            synchronized (DubboBootstrap.class) {
                if (instance == null) {
                    // 进去
                    instance = new DubboBootstrap();
                }
            }
        }
        return instance;
    }

    // 私有的，防止外部直接调用
    private DubboBootstrap() {
        // 隐式的调用了父类的空参构造，GenericEventListener 去看下

        // 进去
        configManager = ApplicationModel.getConfigManager();
        environment = ApplicationModel.getEnvironment();

        // DubboShutdownHook是一个线程，register进去
        DubboShutdownHook.getDubboShutdownHook().register();
        // 再给ShutdownHookCallbacks添加一个callback
        ShutdownHookCallbacks.INSTANCE.addCallback(new ShutdownHookCallback() {
            @Override
            public void callback() throws Throwable {
                // 进去
                DubboBootstrap.this.destroy();
            }
        });
    }

    public void unRegisterShutdownHook() {
        // 进去
        DubboShutdownHook.getDubboShutdownHook().unregister();
    }

    // gx
    private boolean isOnlyRegisterProvider() {

        // 看Application是c还是p，两个都进去
        Boolean registerConsumer = getApplication().getRegisterConsumer();
        // 为空，或者为false 表示仅仅注册provider信息
        return registerConsumer == null || !registerConsumer;
    }

    private String getMetadataType() {
        String type = getApplication().getMetadataType();
        if (StringUtils.isEmpty(type)) {
            type = DEFAULT_METADATA_STORAGE_TYPE;
        }
        return type;
    }

    public DubboBootstrap metadataReport(MetadataReportConfig metadataReportConfig) {
        configManager.addMetadataReport(metadataReportConfig);
        return this;
    }

    public DubboBootstrap metadataReports(List<MetadataReportConfig> metadataReportConfigs) {
        if (CollectionUtils.isEmpty(metadataReportConfigs)) {
            return this;
        }

        configManager.addMetadataReports(metadataReportConfigs);
        return this;
    }

    // {@link ApplicationConfig} correlative methods

    /**
     * Set the name of application
     *
     * @param name the name of application
     * @return current {@link DubboBootstrap} instance
     */
    public DubboBootstrap application(String name) {
        return application(name, builder -> {
            // DO NOTHING
        });
    }

    /**
     * Set the name of application and it's future build
     *
     * @param name            the name of application
     * @param consumerBuilder {@link ApplicationBuilder}
     * @return current {@link DubboBootstrap} instance
     */
    public DubboBootstrap application(String name, Consumer<ApplicationBuilder> consumerBuilder) {
        ApplicationBuilder builder = createApplicationBuilder(name);
        consumerBuilder.accept(builder);
        return application(builder.build());
    }

    /**
     * Set the {@link ApplicationConfig}
     *
     * @param applicationConfig the {@link ApplicationConfig}
     * @return current {@link DubboBootstrap} instance
     */
    public DubboBootstrap application(ApplicationConfig applicationConfig) {
        // 存到configManager
        configManager.setApplication(applicationConfig);
        return this;
    }


    // {@link RegistryConfig} correlative methods

    /**
     * Add an instance of {@link RegistryConfig} with {@link #DEFAULT_REGISTRY_ID default ID}
     *
     * @param consumerBuilder the {@link Consumer} of {@link RegistryBuilder}
     * @return current {@link DubboBootstrap} instance
     */
    public DubboBootstrap registry(Consumer<RegistryBuilder> consumerBuilder) {
        return registry(DEFAULT_REGISTRY_ID, consumerBuilder);
    }

    /**
     * Add an instance of {@link RegistryConfig} with the specified ID
     *
     * @param id              the {@link RegistryConfig#getId() id}  of {@link RegistryConfig}
     * @param consumerBuilder the {@link Consumer} of {@link RegistryBuilder}
     * @return current {@link DubboBootstrap} instance
     */
    public DubboBootstrap registry(String id, Consumer<RegistryBuilder> consumerBuilder) {
        RegistryBuilder builder = createRegistryBuilder(id);
        consumerBuilder.accept(builder);
        return registry(builder.build());
    }

    /**
     * Add an instance of {@link RegistryConfig}
     *
     * @param registryConfig an instance of {@link RegistryConfig}
     * @return current {@link DubboBootstrap} instance
     */
    public DubboBootstrap registry(RegistryConfig registryConfig) {
        configManager.addRegistry(registryConfig);
        return this;
    }

    /**
     * Add an instance of {@link RegistryConfig}
     *
     * @param registryConfigs the multiple instances of {@link RegistryConfig}
     * @return current {@link DubboBootstrap} instance
     */
    public DubboBootstrap registries(List<RegistryConfig> registryConfigs) {
        if (CollectionUtils.isEmpty(registryConfigs)) {
            return this;
        }
        registryConfigs.forEach(this::registry);
        return this;
    }


    // {@link ProtocolConfig} correlative methods
    public DubboBootstrap protocol(Consumer<ProtocolBuilder> consumerBuilder) {
        return protocol(DEFAULT_PROTOCOL_ID, consumerBuilder);
    }

    public DubboBootstrap protocol(String id, Consumer<ProtocolBuilder> consumerBuilder) {
        ProtocolBuilder builder = createProtocolBuilder(id);
        consumerBuilder.accept(builder);
        return protocol(builder.build());
    }

    public DubboBootstrap protocol(ProtocolConfig protocolConfig) {
        return protocols(asList(protocolConfig));
    }

    public DubboBootstrap protocols(List<ProtocolConfig> protocolConfigs) {
        if (CollectionUtils.isEmpty(protocolConfigs)) {
            return this;
        }
        configManager.addProtocols(protocolConfigs);
        return this;
    }

    // {@link ServiceConfig} correlative methods
    public <S> DubboBootstrap service(Consumer<ServiceBuilder<S>> consumerBuilder) {
        return service(DEFAULT_SERVICE_ID, consumerBuilder);
    }

    public <S> DubboBootstrap service(String id, Consumer<ServiceBuilder<S>> consumerBuilder) {
        ServiceBuilder builder = createServiceBuilder(id);
        consumerBuilder.accept(builder);
        return service(builder.build());
    }

    public DubboBootstrap service(ServiceConfig<?> serviceConfig) {
        // serviceConfig添加到configManager
        configManager.addService(serviceConfig);
        return this;
    }

    public DubboBootstrap services(List<ServiceConfig> serviceConfigs) {
        if (CollectionUtils.isEmpty(serviceConfigs)) {
            return this;
        }
        serviceConfigs.forEach(configManager::addService);
        return this;
    }

    // {@link Reference} correlative methods
    public <S> DubboBootstrap reference(Consumer<ReferenceBuilder<S>> consumerBuilder) {
        return reference(DEFAULT_REFERENCE_ID, consumerBuilder);
    }

    public <S> DubboBootstrap reference(String id, Consumer<ReferenceBuilder<S>> consumerBuilder) {
        ReferenceBuilder builder = createReferenceBuilder(id);
        consumerBuilder.accept(builder);
        return reference(builder.build());
    }

    public DubboBootstrap reference(ReferenceConfig<?> referenceConfig) {
        configManager.addReference(referenceConfig);
        return this;
    }

    public DubboBootstrap references(List<ReferenceConfig> referenceConfigs) {
        if (CollectionUtils.isEmpty(referenceConfigs)) {
            return this;
        }

        referenceConfigs.forEach(configManager::addReference);
        return this;
    }

    // {@link ProviderConfig} correlative methods
    public DubboBootstrap provider(Consumer<ProviderBuilder> builderConsumer) {
        return provider(DEFAULT_PROVIDER_ID, builderConsumer);
    }

    public DubboBootstrap provider(String id, Consumer<ProviderBuilder> builderConsumer) {
        ProviderBuilder builder = createProviderBuilder(id);
        builderConsumer.accept(builder);
        return provider(builder.build());
    }

    public DubboBootstrap provider(ProviderConfig providerConfig) {
        return providers(asList(providerConfig));
    }

    public DubboBootstrap providers(List<ProviderConfig> providerConfigs) {
        if (CollectionUtils.isEmpty(providerConfigs)) {
            return this;
        }

        providerConfigs.forEach(configManager::addProvider);
        return this;
    }

    // {@link ConsumerConfig} correlative methods
    public DubboBootstrap consumer(Consumer<ConsumerBuilder> builderConsumer) {
        return consumer(DEFAULT_CONSUMER_ID, builderConsumer);
    }

    public DubboBootstrap consumer(String id, Consumer<ConsumerBuilder> builderConsumer) {
        ConsumerBuilder builder = createConsumerBuilder(id);
        builderConsumer.accept(builder);
        return consumer(builder.build());
    }

    public DubboBootstrap consumer(ConsumerConfig consumerConfig) {
        return consumers(asList(consumerConfig));
    }

    public DubboBootstrap consumers(List<ConsumerConfig> consumerConfigs) {
        if (CollectionUtils.isEmpty(consumerConfigs)) {
            return this;
        }

        consumerConfigs.forEach(configManager::addConsumer);
        return this;
    }

    // {@link ConfigCenterConfig} correlative methods
    public DubboBootstrap configCenter(ConfigCenterConfig configCenterConfig) {
        return configCenters(asList(configCenterConfig));
    }

    public DubboBootstrap configCenters(List<ConfigCenterConfig> configCenterConfigs) {
        if (CollectionUtils.isEmpty(configCenterConfigs)) {
            return this;
        }
        configManager.addConfigCenters(configCenterConfigs);
        return this;
    }

    public DubboBootstrap monitor(MonitorConfig monitor) {
        configManager.setMonitor(monitor);
        return this;
    }

    public DubboBootstrap metrics(MetricsConfig metrics) {
        configManager.setMetrics(metrics);
        return this;
    }

    public DubboBootstrap module(ModuleConfig module) {
        configManager.setModule(module);
        return this;
    }

    public DubboBootstrap ssl(SslConfig sslConfig) {
        configManager.setSsl(sslConfig);
        return this;
    }

    public DubboBootstrap cache(ReferenceConfigCache cache) {
        this.cache = cache;
        return this;
    }

    public ReferenceConfigCache getCache() {
        if (cache == null) {
            cache = ReferenceConfigCache.getCache();
        }
        return cache;
    }

    public DubboBootstrap exportAsync() {
        this.exportAsync = true;
        return this;
    }

    public DubboBootstrap referAsync() {
        this.referAsync = true;
        return this;
    }

    @Deprecated
    public void init() {
        initialize();
    }

    /**
     * Initialize
     */
    public void initialize() {
        // initialize方法只能初始化一次
        if (!initialized.compareAndSet(false, true)) {
            return;
        }

        // 初始化FrameworkExt实现类，进去
        ApplicationModel.initFrameworkExts();

        // 启动配置中心，进去
        startConfigCenter();

        // 加载远端配置 进去
        loadRemoteConfigs();

        // 检查全局配置 进去
        checkGlobalConfigs();

        // 启动元数据中心 进去
        startMetadataCenter();

        // 初始化元数据中心 进去
        initMetadataService();

        // 初始化事件监听器 进去
        initEventListener();

        if (logger.isInfoEnabled()) {
            // DubboBootstrap has been initialized!
            logger.info(NAME + " has been initialized!");
        }
    }


    // 下面又生成了一些xxConfig，并添加到了configManager的缓存中，以demo-api-provider为例，其缓存的内容见文件：demo-api-provider注册到configManger的内存数据.md
    private void checkGlobalConfigs() {
        // check Application
        ConfigValidationUtils.validateApplicationConfig(getApplication());

        // check Metadata
        Collection<MetadataReportConfig> metadatas = configManager.getMetadataConfigs();
        if (CollectionUtils.isEmpty(metadatas)) {
            MetadataReportConfig metadataReportConfig = new MetadataReportConfig();
            metadataReportConfig.refresh();
            if (metadataReportConfig.isValid()) {
                configManager.addMetadataReport(metadataReportConfig);
                metadatas = configManager.getMetadataConfigs();
            }
        }
        if (CollectionUtils.isNotEmpty(metadatas)) {
            for (MetadataReportConfig metadataReportConfig : metadatas) {
                metadataReportConfig.refresh();
                ConfigValidationUtils.validateMetadataConfig(metadataReportConfig);
            }
        }

        // check Provider
        Collection<ProviderConfig> providers = configManager.getProviders();
        if (CollectionUtils.isEmpty(providers)) {
            configManager.getDefaultProvider().orElseGet(() -> {
                ProviderConfig providerConfig = new ProviderConfig();
                configManager.addProvider(providerConfig); // 添加到configManager
                providerConfig.refresh();
                return providerConfig;
            });
        }
        for (ProviderConfig providerConfig : configManager.getProviders()) {
            ConfigValidationUtils.validateProviderConfig(providerConfig);
        }
        // check Consumer
        Collection<ConsumerConfig> consumers = configManager.getConsumers();
        if (CollectionUtils.isEmpty(consumers)) {
            configManager.getDefaultConsumer().orElseGet(() -> {
                ConsumerConfig consumerConfig = new ConsumerConfig();
                configManager.addConsumer(consumerConfig);// 添加到configManager
                consumerConfig.refresh();
                return consumerConfig;
            });
        }
        for (ConsumerConfig consumerConfig : configManager.getConsumers()) {
            ConfigValidationUtils.validateConsumerConfig(consumerConfig);
        }

        // check Monitor
        ConfigValidationUtils.validateMonitorConfig(getMonitor());// getMonitor进去，其实例会 添加到configManager，下面几个同理
        // check Metrics
        ConfigValidationUtils.validateMetricsConfig(getMetrics());
        // check Module
        ConfigValidationUtils.validateModuleConfig(getModule());
        // check Ssl
        ConfigValidationUtils.validateSslConfig(getSsl());
    }

    private void startConfigCenter() {

        // 使用注册作为配置中心，如果有必要的话，进去
        useRegistryAsConfigCenterIfNecessary();

        // 从ConfigManager中获得的所有的ConfigCenterConfig对象
        Collection<ConfigCenterConfig> configCenters = configManager.getConfigCenters();

        // check Config Center
        if (CollectionUtils.isEmpty(configCenters)) {
            // 如果集合为空，创建一个
            ConfigCenterConfig configCenterConfig = new ConfigCenterConfig();
            // 刷新下
            configCenterConfig.refresh();
            // 进去
            if (configCenterConfig.isValid()) {
                // 有效的话添加到configManager
                configManager.addConfigCenter(configCenterConfig);
                configCenters = configManager.getConfigCenters();
            }
        } else {
            for (ConfigCenterConfig configCenterConfig : configCenters) {
                // 和前面一样，刷新和检查
                configCenterConfig.refresh();
                ConfigValidationUtils.validateConfigCenterConfig(configCenterConfig);
            }
        }
        // 到这里configCenters一般是不为空的（会进下面的分支），因为前面有 useRegistryAsConfigCenterIfNecessary 方法做保障。
        if (CollectionUtils.isNotEmpty(configCenters)) {
            // 创建CompositeDynamicConfiguration
            CompositeDynamicConfiguration compositeDynamicConfiguration = new CompositeDynamicConfiguration();

            for (ConfigCenterConfig configCenter : configCenters) {
                // 将configCenter转化为DynamicConfiguration，填充到compositeDynamicConfiguration
                // 比如ZookeeperDynamicConfiguration就是是config center模块的）， prepareEnvironment 进去
                compositeDynamicConfiguration.addConfiguration(prepareEnvironment(configCenter));
            }
            // 存到environment
            environment.setDynamicConfiguration(compositeDynamicConfiguration);
        }
        configManager.refreshAll();
    }

    private void startMetadataCenter() {

        // 进去
        useRegistryAsMetadataCenterIfNecessary();

        ApplicationConfig applicationConfig = getApplication();

        String metadataType = applicationConfig.getMetadataType();
        // FIXME, multiple metadata config support.
        Collection<MetadataReportConfig> metadataReportConfigs = configManager.getMetadataConfigs();
        if (CollectionUtils.isEmpty(metadataReportConfigs)) {
            if (REMOTE_METADATA_STORAGE_TYPE.equals(metadataType)) {
                throw new IllegalStateException("No MetadataConfig found, Metadata Center address is required when 'metadata=remote' is enabled.");
            }
            return;
        }

        for (MetadataReportConfig metadataReportConfig : metadataReportConfigs) {
            ConfigValidationUtils.validateMetadataConfig(metadataReportConfig);
            if (!metadataReportConfig.isValid()) {
                return;
            }
            MetadataReportInstance.init(metadataReportConfig);
        }
    }

    /**
     * For compatibility purpose, use registry as the default config center when
     * there's no config center specified explicitly and
     * useAsConfigCenter of registryConfig is null or true
     * <p>
     * 为了兼容，当没有明确指定配置中心，并且registryConfig的useAsConfigCenter为null或true时，使用registry作为默认的配置中心
     */
    private void useRegistryAsConfigCenterIfNecessary() {
        // we use the loading status of DynamicConfiguration to decide whether ConfigCenter has been initiated.
        // 我们使用DynamicConfiguration的加载状态来决定ConfigCenter是否已经启动。
        if (environment.getDynamicConfiguration().isPresent()) {
            return;
        }

        // 如果有专门添加过配置中心，则返回
        if (CollectionUtils.isNotEmpty(configManager.getConfigCenters())) {
            return;
        }

        configManager
                .getDefaultRegistries()
                .stream()
                // 找出符合能作为配置中心条件的Registry项，进去
                .filter(this::isUsedRegistryAsConfigCenter)
                // registryAsConfigCenter见名知意，map将Registry映射成ConfigCenter，进去
                .map(this::registryAsConfigCenter)
                // 添加到configManager
                .forEach(configManager::addConfigCenter);
    }

    private boolean isUsedRegistryAsConfigCenter(RegistryConfig registryConfig) {
        // 进去，第二个参数是supplier
        return isUsedRegistryAsCenter(registryConfig, registryConfig::getUseAsConfigCenter, "config",
                DynamicConfigurationFactory.class);
    }

    private ConfigCenterConfig registryAsConfigCenter(RegistryConfig registryConfig) {
        // 下面就是根据registryConfig的信息生成ConfigCenterConfig对象

        // registryConfig的toString 比如 <dubbo:registry address="zookeeper://127.0.0.1:2181" protocol="zookeeper" port="2181" />
        // ConfigCenterConfig下面填充各种参数之后，最后其toString 比如如下：
        // <dubbo:config-center timeout="3000" group="dubbo" address="zookeeper://127.0.0.1:2181" protocol="zookeeper"
        //                      port="2181" configFile="dubbo.properties" highestPriority="false" check="true" />
        String protocol = registryConfig.getProtocol();
        Integer port = registryConfig.getPort();
        String id = "config-center-" + protocol + "-" + port;
        ConfigCenterConfig cc = new ConfigCenterConfig();
        cc.setId(id); // 这个id很重要，返回的ConfigCenterConfig实例会填充到configManager，格式为：{config-center:{config-center-zookeeper-2181:cc}}
        if (cc.getParameters() == null) {
            cc.setParameters(new HashMap<>());
        }
        if (registryConfig.getParameters() != null) {
            cc.getParameters().putAll(registryConfig.getParameters()); // copy the parameters
        }
        cc.getParameters().put(CLIENT_KEY, registryConfig.getClient());
        cc.setProtocol(protocol);
        cc.setPort(port);
        if (StringUtils.isNotEmpty(registryConfig.getGroup())) {
            cc.setGroup(registryConfig.getGroup());
        }
        cc.setAddress(getRegistryCompatibleAddress(registryConfig));
        cc.setNamespace(registryConfig.getGroup());
        cc.setUsername(registryConfig.getUsername());
        cc.setPassword(registryConfig.getPassword());
        if (registryConfig.getTimeout() != null) {
            cc.setTimeout(registryConfig.getTimeout().longValue());
        }
        cc.setHighestPriority(false);
        //
        return cc;
    }

    // 如下方法基本可以参考useRegistryAsConfigCenterIfNecessary
    private void useRegistryAsMetadataCenterIfNecessary() {

        Collection<MetadataReportConfig> metadataConfigs = configManager.getMetadataConfigs();

        if (CollectionUtils.isNotEmpty(metadataConfigs)) {
            return;
        }

        configManager
                .getDefaultRegistries()
                .stream()
                // 进去
                .filter(this::isUsedRegistryAsMetadataCenter)
                // 进去
                .map(this::registryAsMetadataCenter)
                .forEach(configManager::addMetadataReport);

    }

    private boolean isUsedRegistryAsMetadataCenter(RegistryConfig registryConfig) {
        return isUsedRegistryAsCenter(registryConfig, registryConfig::getUseAsMetadataCenter, "metadata",
                MetadataReportFactory.class);
    }

    /**
     * Is used the specified registry as a center infrastructure
     * <p>
     * 是否使用指定的注册表作为中心基础设施
     *
     * @param registryConfig       the {@link RegistryConfig}
     * @param usedRegistryAsCenter the configured value on
     * @param centerType           the type name of center
     * @param extensionClass       an extension class of a center infrastructure
     * @return
     * @since 2.7.8
     */
    private boolean isUsedRegistryAsCenter(RegistryConfig registryConfig,
                                           Supplier<Boolean> usedRegistryAsCenter, // 这个用法很好
                                           String centerType,
                                           Class<?> extensionClass) {
        final boolean supported;
        Boolean configuredValue = usedRegistryAsCenter.get();
        // If configured, take its value.
        if (configuredValue != null) {
            // api(和intValue一样)
            supported = configuredValue.booleanValue();

            // Or check the extension existence
        } else {
            // 比如registry配置的是zookeeper://ip:port , 那么getProtocol = zookeeper
            String protocol = registryConfig.getProtocol();
            // eg:两个参数为 DynamicConfigurationFactory.class , Zookeeper ，而正好是满足的  ， 进去
            // eg:两个参数为 MetadataReportFactory.class , Zookeeper，也是满足的
            supported = supportsExtension(extensionClass, protocol);
            if (logger.isInfoEnabled()) {
                // eg:No value is configured in the registry, the DynamicConfigurationFactory extension[name : zookeeper] supports as the config center
                // info方法进去，因为logger是FailsafeLogger，其info方法会拼dubbo version:xx 和 current host:xx
                logger.info(format("No value is configured in the registry, the %s extension[name : %s] %s as the %s center"
                        , extensionClass.getSimpleName(), protocol, supported ? "supports" : "does not support", centerType));
            }
        }

        if (logger.isInfoEnabled()) {
            // eg :The registry[<dubbo:registry address="zookeeper://127.0.0.1:2181" protocol="zookeeper" port="2181" />] will be used as the config center (或者 metadata center)
            logger.info(format("The registry[%s] will be %s as the %s center", registryConfig,
                    supported ? "used" : "not used", centerType));
        }
        return supported;
    }

    /**
     * Supports the extension with the specified class and name
     *
     * @param extensionClass the {@link Class} of extension
     * @param name           the name of extension
     * @return if supports, return <code>true</code>, or <code>false</code>
     * @since 2.7.8
     */
    private boolean supportsExtension(Class<?> extensionClass, String name) {
        if (isNotEmpty(name)) {
            ExtensionLoader extensionLoader = getExtensionLoader(extensionClass);
            return extensionLoader.hasExtension(name);
        }
        return false;
    }

    private MetadataReportConfig registryAsMetadataCenter(RegistryConfig registryConfig) {
        String protocol = registryConfig.getProtocol();
        Integer port = registryConfig.getPort();
        String id = "metadata-center-" + protocol + "-" + port;
        MetadataReportConfig metadataReportConfig = new MetadataReportConfig();
        metadataReportConfig.setId(id);
        if (metadataReportConfig.getParameters() == null) {
            metadataReportConfig.setParameters(new HashMap<>());
        }
        if (registryConfig.getParameters() != null) {
            metadataReportConfig.getParameters().putAll(registryConfig.getParameters()); // copy the parameters
        }
        metadataReportConfig.getParameters().put(CLIENT_KEY, registryConfig.getClient());
        metadataReportConfig.setGroup(registryConfig.getGroup());
        metadataReportConfig.setAddress(getRegistryCompatibleAddress(registryConfig));
        metadataReportConfig.setUsername(registryConfig.getUsername());
        metadataReportConfig.setPassword(registryConfig.getPassword());
        metadataReportConfig.setTimeout(registryConfig.getTimeout());
        return metadataReportConfig;
    }

    private String getRegistryCompatibleAddress(RegistryConfig registryConfig) {
        String registryAddress = registryConfig.getAddress();
        String[] addresses = REGISTRY_SPLIT_PATTERN.split(registryAddress);
        if (ArrayUtils.isEmpty(addresses)) {
            throw new IllegalStateException("Invalid registry address found.");
        }
        String address = addresses[0];
        // since 2.7.8
        // Issue : https://github.com/apache/dubbo/issues/6476
        StringBuilder metadataAddressBuilder = new StringBuilder();
        URL url = URL.valueOf(address);
        String protocolFromAddress = url.getProtocol();
        if (isEmpty(protocolFromAddress)) {
            // If the protocol from address is missing, is like :
            // "dubbo.registry.address = 127.0.0.1:2181"
            String protocolFromConfig = registryConfig.getProtocol();
            metadataAddressBuilder.append(protocolFromConfig).append("://");
        }
        metadataAddressBuilder.append(address);
        return metadataAddressBuilder.toString();
    }

    // add registry + protocol  to configManager
    private void loadRemoteConfigs() {
        // registry ids to registry configs
        List<RegistryConfig> tmpRegistries = new ArrayList<>();
        Set<String> registryIds = configManager.getRegistryIds();
        registryIds.forEach(id -> {
            if (tmpRegistries.stream().noneMatch(reg -> reg.getId().equals(id))) {
                tmpRegistries.add(configManager.getRegistry(id).orElseGet(() -> {
                    RegistryConfig registryConfig = new RegistryConfig();
                    registryConfig.setId(id);
                    registryConfig.refresh();
                    return registryConfig;
                }));
            }
        });

        configManager.addRegistries(tmpRegistries);

        // protocol ids to protocol configs
        List<ProtocolConfig> tmpProtocols = new ArrayList<>();
        Set<String> protocolIds = configManager.getProtocolIds();
        protocolIds.forEach(id -> {
            if (tmpProtocols.stream().noneMatch(prot -> prot.getId().equals(id))) {
                tmpProtocols.add(configManager.getProtocol(id).orElseGet(() -> {
                    ProtocolConfig protocolConfig = new ProtocolConfig();
                    protocolConfig.setId(id);
                    protocolConfig.refresh();
                    return protocolConfig;
                }));
            }
        });

        configManager.addProtocols(tmpProtocols);
    }


    /**
     * Initialize {@link MetadataService} from {@link WritableMetadataService}'s extension
     */
    private void initMetadataService() {
        // 在initialize调用过了，不知道再来一次的意义是什么
        startMetadataCenter();

        // 进去
        this.metadataService = getDefaultExtension();
        // 进去
        this.metadataServiceExporter = new ConfigurableMetadataServiceExporter(metadataService);
    }

    /**
     * Initialize {@link EventListener}
     */
    private void initEventListener() {
        // Add current instance into listeners 进去
        addEventListener(this);
    }

    /**
     * Start the bootstrap
     */
    public DubboBootstrap start() {
        if (started.compareAndSet(false, true)) {
            ready.set(false);
            // 进去
            initialize();
            if (logger.isInfoEnabled()) {
                // DubboBootstrap is starting...
                logger.info(NAME + " is starting...");
            }
            // 1. export Dubbo Services 进去
            exportServices();

            // Not only provider register
            // 不是仅注册Provider信息或者有其他要暴露的服务（两个都进去）
            if (!isOnlyRegisterProvider() || hasExportedServices()) {
                // 2. export MetadataService
                exportMetadataService();
                // 3. Register the local ServiceInstance if required
                registerServiceInstance();
            }

            // 引用服务（Consumer端走这个逻辑，Provider走上面的123逻辑）
            referServices();

            // 下面是和异步暴露相关的（如果size>0说明是异步，即exportAsyn的值为true）
            if (asyncExportingFutures.size() > 0) {
                // 启动一个线程阻塞，等待这些异步任务完成
                new Thread(() -> {
                    try {
                        // 进去
                        this.awaitFinish();
                    } catch (Exception e) {
                        logger.warn(NAME + " exportAsync occurred an exception.");
                    }
                    ready.set(true);
                    if (logger.isInfoEnabled()) {
                        logger.info(NAME + " is ready.");
                    }
                }).start();
            } else {
                ready.set(true);
                if (logger.isInfoEnabled()) {
                    logger.info(NAME + " is ready.");
                }
            }
            if (logger.isInfoEnabled()) {
                logger.info(NAME + " has started.");
            }
        }
        return this;
    }

    private boolean hasExportedServices() {
        // metadataService的赋值处注意下
        return !metadataService.getExportedURLs().isEmpty();
    }

    /**
     * Block current thread to be await.
     *
     * @return {@link DubboBootstrap}
     */
    public DubboBootstrap await() {
        // if has been waited, no need to wait again, return immediately
        // 默认false
        if (!awaited.get()) {
            // executorService 看下赋值处和调用处
            if (!executorService.isShutdown()) {
                // executeMutually去看下， 很简单，加锁+run
                executeMutually(() -> {
                    // 循环检测（awaited置为true的点跟下）
                    while (!awaited.get()) {
                        if (logger.isInfoEnabled()) {
                            logger.info(NAME + " awaiting ...");
                        }
                        try {
                            // 无限等待
                            condition.await();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }
        }
        return this;
    }

    public DubboBootstrap awaitFinish() throws Exception {
        logger.info(NAME + " waiting services exporting / referring ...");
        // 异步暴露，asyncExportingFutures的填充处取看下
        if (exportAsync && asyncExportingFutures.size() > 0) {
            // allOf将所有的CompletableFuture变成一个
            CompletableFuture future = CompletableFuture.allOf(asyncExportingFutures.toArray(new CompletableFuture[0]));
            // 阻塞
            future.get();
        }
        // 异步引用，参考上面
        if (referAsync && asyncReferringFutures.size() > 0) {
            CompletableFuture future = CompletableFuture.allOf(asyncReferringFutures.toArray(new CompletableFuture[0]));
            future.get();
        }

        logger.info("Service export / refer finished.");
        return this;
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isReady() {
        return ready.get();
    }

    public DubboBootstrap stop() throws IllegalStateException {
        destroy();
        return this;
    }
    /* serve for builder apis, begin */

    private ApplicationBuilder createApplicationBuilder(String name) {
        return new ApplicationBuilder().name(name);
    }

    private RegistryBuilder createRegistryBuilder(String id) {
        return new RegistryBuilder().id(id);
    }

    private ProtocolBuilder createProtocolBuilder(String id) {
        return new ProtocolBuilder().id(id);
    }

    private ServiceBuilder createServiceBuilder(String id) {
        return new ServiceBuilder().id(id);
    }

    private ReferenceBuilder createReferenceBuilder(String id) {
        return new ReferenceBuilder().id(id);
    }

    private ProviderBuilder createProviderBuilder(String id) {
        return new ProviderBuilder().id(id);
    }

    private ConsumerBuilder createConsumerBuilder(String id) {
        return new ConsumerBuilder().id(id);
    }
    /* serve for builder apis, end */

    private DynamicConfiguration prepareEnvironment(ConfigCenterConfig configCenter) {
        if (configCenter.isValid()) {
            if (!configCenter.checkOrUpdateInited()) {
                return null;

            }
            // configCenter.toUrl()，"-- >" 左右分别是toString和toUrl之后的结果
            //  <dubbo:config-center address="zookeeper://127.0.0.1:2181" protocol="zookeeper" port="2181" timeout="3000"
            //  group="dubbo" configFile="dubbo.properties" highestPriority="false" check="true" />
            //   -- >    zookeeper://127.0.0.1:2181/ConfigCenterConfig?check=true&config-file=dubbo.properties&group=dubbo
            //                                                                         &highest-priority=false&timeout=3000
            // getDynamicConfiguration，进去
            DynamicConfiguration dynamicConfiguration = getDynamicConfiguration(configCenter.toUrl());
            // 如果上面的dynamicConfiguration是zkDynamicConfiguration，其getProperties最终是Curator进行ZNode节点的读取
            // 根据key、group获取配置内容
            String configContent = dynamicConfiguration.getProperties(configCenter.getConfigFile(), configCenter.getGroup());

            // 下面类似前面一行代码作用，获取appXX的配置内容
            String appGroup = getApplication().getName();
            String appConfigContent = null;
            if (isNotEmpty(appGroup)) {
                String key = isNotEmpty(configCenter.getAppConfigFile()) ? configCenter.getAppConfigFile() : configCenter.getConfigFile();
                appConfigContent = dynamicConfiguration.getProperties(key, appGroup);
            }
            try {
                // 更新environment的属性

                environment.setConfigCenterFirst(configCenter.isHighestPriority());
                // 进去
                environment.updateExternalConfigurationMap(parseProperties(configContent));
                environment.updateAppExternalConfigurationMap(parseProperties(appConfigContent));
            } catch (IOException e) {
                throw new IllegalStateException("Failed to parse configurations from Config Center.", e);
            }
            return dynamicConfiguration;
        }
        return null;
    }

    /**
     * Add an instance of {@link EventListener}
     *
     * @param listener {@link EventListener}
     * @return {@link DubboBootstrap}
     */

    public DubboBootstrap addEventListener(EventListener<?> listener) {
        // DubboBootStrap本身就是EventListener孙子类
        // 进去
        eventDispatcher.addEventListener(listener);
        return this;
    }

    /**
     * export {@link MetadataService}
     */
    private void exportMetadataService() {
        metadataServiceExporter.export();
    }

    private void unexportMetadataService() {
        if (metadataServiceExporter != null && metadataServiceExporter.isExported()) {
            metadataServiceExporter.unexport();
        }
    }

    private void exportServices() {
        // 从configManager取出所有的ServiceConfig进行服务暴露
        configManager.getServices().forEach(sc -> {
            ServiceConfig serviceConfig = (ServiceConfig) sc;
            serviceConfig.setBootstrap(this);
            // boolean类型值默认为false
            if (exportAsync) { // 异步暴露
                ExecutorService executor = executorRepository.getServiceExporterExecutor();
                Future<?> future = executor.submit(() -> {
                    sc.export();
                    exportedServices.add(sc);
                });
                // 存到容器
                asyncExportingFutures.add(future);
            } else {// 同步暴露，进去
                sc.export();
                // 填充到exportedServices（前面异步暴力完成也是填充到该容器）
                exportedServices.add(sc);
            }
        });
    }

    private void unexportServices() {
        exportedServices.forEach(sc -> {
            configManager.removeConfig(sc);
            // 进去
            sc.unexport();
        });

        asyncExportingFutures.forEach(future -> {
            if (!future.isDone()) {
                // 还有正在异步暴露还未完成的，直接取消
                future.cancel(true);
            }
        });

        asyncExportingFutures.clear();
        exportedServices.clear();
    }

    private void referServices() {
        if (cache == null) {
            cache = ReferenceConfigCache.getCache();
        }

        configManager.getReferences().forEach(rc -> {
            // TODO, compatible with  ReferenceConfig.refer()
            ReferenceConfig referenceConfig = (ReferenceConfig) rc;
            referenceConfig.setBootstrap(this);

            if (rc.shouldInit()) {
                if (referAsync) {
                    // 提交任务，submit进去
                    CompletableFuture<Object> future = ScheduledCompletableFuture.submit(
                            executorRepository.getServiceExporterExecutor(),
                            () -> cache.get(rc)
                    );
                    // 上面submit之后直接返回future(不会阻塞)，填充到集合里面，在跟下asyncReferringFutures.forEach处
                    asyncReferringFutures.add(future);
                } else {
                    cache.get(rc);
                }
            }
        });
    }

    private void unreferServices() {
        if (cache == null) {
            cache = ReferenceConfigCache.getCache();
        }

        // 这里的future就是CompletableFuture
        asyncReferringFutures.forEach(future -> {
            // 判断如果没有完成的话直接cancel，完成时间点在future.complete(xx)的触发
            // 详见ScheduledCompletableFuture.submit方法里面completableFuture.complete(task.get());）
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        asyncReferringFutures.clear();
        cache.destroyAll();
    }

    private void registerServiceInstance() {
        if (CollectionUtils.isEmpty(getServiceDiscoveries())) {
            return;
        }

        ApplicationConfig application = getApplication();

        String serviceName = application.getName();

        URL exportedURL = selectMetadataServiceExportedURL();

        String host = exportedURL.getHost();

        int port = exportedURL.getPort();

        ServiceInstance serviceInstance = createServiceInstance(serviceName, host, port);

        doRegisterServiceInstance(serviceInstance);

        // scheduled task for updating Metadata and ServiceInstance
        executorRepository.nextScheduledExecutor().scheduleAtFixedRate(() -> {
            InMemoryWritableMetadataService localMetadataService = (InMemoryWritableMetadataService) WritableMetadataService.getDefaultExtension();
            localMetadataService.blockUntilUpdated();
            ServiceInstanceMetadataUtils.refreshMetadataAndInstance();
        }, 0, ConfigurationUtils.get(METADATA_PUBLISH_DELAY_KEY, DEFAULT_METADATA_PUBLISH_DELAY), TimeUnit.MICROSECONDS);
    }

    private void doRegisterServiceInstance(ServiceInstance serviceInstance) {
        //FIXME
        publishMetadataToRemote(serviceInstance);

        getServiceDiscoveries().forEach(serviceDiscovery ->
        {
            calInstanceRevision(serviceDiscovery, serviceInstance);
            // register metadata
            serviceDiscovery.register(serviceInstance);
        });
    }

    private void publishMetadataToRemote(ServiceInstance serviceInstance) {
//        InMemoryWritableMetadataService localMetadataService = (InMemoryWritableMetadataService)WritableMetadataService.getDefaultExtension();
//        localMetadataService.blockUntilUpdated();
        RemoteMetadataServiceImpl remoteMetadataService = MetadataUtils.getRemoteMetadataService();
        remoteMetadataService.publishMetadata(serviceInstance.getServiceName());
    }

    private URL selectMetadataServiceExportedURL() {

        URL selectedURL = null;

        SortedSet<String> urlValues = metadataService.getExportedURLs();

        for (String urlValue : urlValues) {
            URL url = URL.valueOf(urlValue);
            if (MetadataService.class.getName().equals(url.getServiceInterface())) {
                continue;
            }
            if ("rest".equals(url.getProtocol())) { // REST first
                selectedURL = url;
                break;
            } else {
                selectedURL = url; // If not found, take any one
            }
        }

        if (selectedURL == null && CollectionUtils.isNotEmpty(urlValues)) {
            selectedURL = URL.valueOf(urlValues.iterator().next());
        }

        return selectedURL;
    }

    private void unregisterServiceInstance() {
        if (serviceInstance != null) {
            getServiceDiscoveries().forEach(serviceDiscovery -> {
                serviceDiscovery.unregister(serviceInstance);
            });
        }
    }

    private ServiceInstance createServiceInstance(String serviceName, String host, int port) {
        this.serviceInstance = new DefaultServiceInstance(serviceName, host, port);
        setMetadataStorageType(serviceInstance, getMetadataType());

        ExtensionLoader<ServiceInstanceCustomizer> loader =
                ExtensionLoader.getExtensionLoader(ServiceInstanceCustomizer.class);
        // FIXME, sort customizer before apply
        loader.getSupportedExtensionInstances().forEach(customizer -> {
            // customizes
            customizer.customize(this.serviceInstance);
        });

        return this.serviceInstance;
    }

    // gx 注册了DubboShutdownHook到jvm关闭钩子上，关闭的时候会挨个调用注册的回调，其中就有调用如下方法的
    public void destroy() {
        if (destroyLock.tryLock()) {
            try {
                // 进去
                DubboShutdownHook.destroyAll();

                if (started.compareAndSet(true, false)
                        && destroyed.compareAndSet(false, true)) {

                    // 下面四个步骤和start()的前四个步骤对应，都进去

                    unregisterServiceInstance();
                    unexportMetadataService();
                    unexportServices();
                    unreferServices();

                    destroyRegistries();
                    DubboShutdownHook.destroyProtocols();
                    destroyServiceDiscoveries();

                    clear();
                    // 关闭线程池
                    shutdown();
                    // 进去
                    release();
                }
            } finally {
                destroyLock.unlock();
            }
        }
    }

    private void destroyRegistries() {
        AbstractRegistryFactory.destroyAll();
    }

    private void destroyServiceDiscoveries() {
        getServiceDiscoveries().forEach(serviceDiscovery -> {
            execute(serviceDiscovery::destroy);
        });
        if (logger.isDebugEnabled()) {
            logger.debug(NAME + "'s all ServiceDiscoveries have been destroyed.");
        }
    }

    private void clear() {
        clearConfigs();
        clearApplicationModel();
    }

    private void clearApplicationModel() {

    }

    private void clearConfigs() {
        configManager.destroy();
        if (logger.isDebugEnabled()) {
            logger.debug(NAME + "'s configs have been clear.");
        }
    }

    private void release() {
        executeMutually(() -> {
            while (awaited.compareAndSet(false, true)) {
                if (logger.isInfoEnabled()) {
                    logger.info(NAME + " is about to shutdown...");
                }
                // 唤醒（对应的condition.await处看下）
                condition.signalAll();
            }
        });
    }

    // gx
    private void shutdown() {
        if (!executorService.isShutdown()) {
            // Shutdown executorService
            executorService.shutdown();
        }
    }

    private void executeMutually(Runnable runnable) {
        try {
            lock.lock();
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    public ApplicationConfig getApplication() {
        ApplicationConfig application = configManager
                .getApplication()
                .orElseGet(() -> {
                    ApplicationConfig applicationConfig = new ApplicationConfig();
                    configManager.setApplication(applicationConfig);
                    return applicationConfig;
                });

        application.refresh();
        return application;
    }

    private MonitorConfig getMonitor() {
        MonitorConfig monitor = configManager
                .getMonitor()
                .orElseGet(() -> {
                    MonitorConfig monitorConfig = new MonitorConfig();
                    configManager.setMonitor(monitorConfig);
                    return monitorConfig;
                });

        monitor.refresh();
        return monitor;
    }

    private MetricsConfig getMetrics() {
        MetricsConfig metrics = configManager
                .getMetrics()
                .orElseGet(() -> {
                    MetricsConfig metricsConfig = new MetricsConfig();
                    configManager.setMetrics(metricsConfig);
                    return metricsConfig;
                });
        metrics.refresh();
        return metrics;
    }

    private ModuleConfig getModule() {
        ModuleConfig module = configManager
                .getModule()
                .orElseGet(() -> {
                    ModuleConfig moduleConfig = new ModuleConfig();
                    configManager.setModule(moduleConfig);
                    return moduleConfig;
                });

        module.refresh();
        return module;
    }

    private SslConfig getSsl() {
        SslConfig ssl = configManager
                .getSsl()
                .orElseGet(() -> {
                    SslConfig sslConfig = new SslConfig();
                    configManager.setSsl(sslConfig);
                    return sslConfig;
                });

        ssl.refresh();
        return ssl;
    }
}
