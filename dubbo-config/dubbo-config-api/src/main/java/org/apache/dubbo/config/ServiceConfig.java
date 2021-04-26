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
package org.apache.dubbo.config;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.config.event.ServiceConfigExportedEvent;
import org.apache.dubbo.config.event.ServiceConfigUnexportedEvent;
import org.apache.dubbo.config.invoker.DelegateProviderMetaDataInvoker;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.config.utils.ConfigValidationUtils;
import org.apache.dubbo.event.Event;
import org.apache.dubbo.event.EventDispatcher;
import org.apache.dubbo.metadata.ServiceNameMapping;
import org.apache.dubbo.registry.client.metadata.MetadataUtils;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.cluster.ConfiguratorFactory;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ServiceDescriptor;
import org.apache.dubbo.rpc.model.ServiceRepository;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_IP_TO_BIND;
import static org.apache.dubbo.common.constants.CommonConstants.LOCALHOST_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.MAPPING_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METADATA_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METHODS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.MONITOR_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.REGISTER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REMOTE_METADATA_STORAGE_TYPE;
import static org.apache.dubbo.common.constants.CommonConstants.REVISION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;
import static org.apache.dubbo.common.utils.NetUtils.getAvailablePort;
import static org.apache.dubbo.common.utils.NetUtils.getLocalHost;
import static org.apache.dubbo.common.utils.NetUtils.isInvalidLocalHost;
import static org.apache.dubbo.common.utils.NetUtils.isInvalidPort;
import static org.apache.dubbo.config.Constants.DUBBO_IP_TO_REGISTRY;
import static org.apache.dubbo.config.Constants.DUBBO_PORT_TO_BIND;
import static org.apache.dubbo.config.Constants.DUBBO_PORT_TO_REGISTRY;
import static org.apache.dubbo.config.Constants.MULTICAST;
import static org.apache.dubbo.config.Constants.SCOPE_NONE;
import static org.apache.dubbo.remoting.Constants.BIND_IP_KEY;
import static org.apache.dubbo.remoting.Constants.BIND_PORT_KEY;
import static org.apache.dubbo.rpc.Constants.GENERIC_KEY;
import static org.apache.dubbo.rpc.Constants.LOCAL_PROTOCOL;
import static org.apache.dubbo.rpc.Constants.PROXY_KEY;
import static org.apache.dubbo.rpc.Constants.SCOPE_KEY;
import static org.apache.dubbo.rpc.Constants.SCOPE_LOCAL;
import static org.apache.dubbo.rpc.Constants.SCOPE_REMOTE;
import static org.apache.dubbo.rpc.Constants.TOKEN_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.EXPORT_KEY;

// OK
public class ServiceConfig<T> extends ServiceConfigBase<T> {

    public static final Logger logger = LoggerFactory.getLogger(ServiceConfig.class);

    /**
     * A random port cache, the different protocols who has no port specified have different random port
     * 随机端口缓存，没有指定端口的不同协议有不同的随机端口
     */
    // gx
    private static final Map<String/*protocolName*/, Integer/*port*/> RANDOM_PORT_MAP = new HashMap<String, Integer>();

    /**
     * A delayed exposure service timer
     */
    private static final ScheduledExecutorService DELAY_EXPORT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(
            new NamedThreadFactory("DubboServiceDelayExporter", true));

    // 先看getExtensionLoader，然后看getAdaptiveExtension
    private static final Protocol PROTOCOL = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();

    /**
     * A {@link ProxyFactory} implementation that will generate a exported service proxy,the JavassistProxyFactory is its
     * default implementation
     * 一个{@link ProxyFactory}实现，它将生成一个导出的服务代理，JavassistProxyFactory是它的默认实现
     */
    private static final ProxyFactory PROXY_FACTORY = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    /**
     * The exported services
     */
    // export服务（多协议、多注册）之后会返回一个Exporter具体子类实例，就填充到这里里面
    private final List<Exporter<?>> exporters = new ArrayList<Exporter<?>>();

    /**
     * Whether the provider has been exported
     */
    // 区别于上面，这个用以标识该提供者/service是否被暴露了
    private transient volatile boolean exported;

    /**
     * The flag whether a service has unexported ,if the method unexported is invoked, the value is true
     */
    private transient volatile boolean unexported;

    private DubboBootstrap bootstrap;

    public ServiceConfig() {

    }

    public ServiceConfig(Service service) {
        super(service);
    }

    @Parameter(excluded = true)
    public boolean isExported() {
        return exported;
    }

    @Parameter(excluded = true)
    public boolean isUnexported() {
        return unexported;
    }

    // gx
    public void unexport() {
        // 如果该服务还没有暴露过，直接给他返回。 这就是设计两个标记的意义，就知道为啥既有start也有stop 了，因为如果没有暴露过的话，压根就不需要unexport
        if (!exported) {
            return;
        }
        // 如果之前已经unexported,肯定不需要重复unexport操作直接返回。
        if (unexported) {
            return;
        }
        // 因为暴露服务(ServiceConfig)是多协议多注册中心的。且每暴露一个之后会以Exporter类型对象返回，存到exporters容器里面，所以挨个调用unexport
        if (!exporters.isEmpty()) {
            for (Exporter<?> exporter : exporters) {
                try {
                    // 挨个调用exporter.unexport()，进去
                    exporter.unexport();
                } catch (Throwable t) {
                    logger.warn("Unexpected error occured when unexport " + exporter, t);
                }
            }
            // 清空缓存
            exporters.clear();
        }
        // 打标记。
        unexported = true;

        // dispatch a ServiceConfigUnExportedEvent since 2.7.4
        // 和export方法最后一个步骤exported()内部类似，会产生ServiceConfigExportedEvent事件并进行dispatch。
        dispatch(new ServiceConfigUnexportedEvent(this));
    }

    // 加锁 并发只允许一个线程进行暴露
    public synchronized void export() {
        if (bootstrap == null) {
            // 通过静态方法（可以理解为工厂方法）获取bootstrap实例，内部用了单例模式，进去
            bootstrap = DubboBootstrap.getInstance();
            // 初始化bootstrap
            bootstrap.initialize();
        }

        // 检查和更新配置（很重要，进去），进去
        checkAndUpdateSubConfigs();

        // 父类继承来的属性，初始化、填充一些元数据
        serviceMetadata.setVersion(getVersion());
        serviceMetadata.setGroup(getGroup());
        serviceMetadata.setDefaultGroup(getGroup());
        serviceMetadata.setServiceType(getInterfaceClass());
        serviceMetadata.setServiceInterfaceName(getInterface());
        serviceMetadata.setTarget(getRef());


        // 父类的方法，是否导出服务，进去
        // 有时候我们只是想本地启动服务进行一些调试工作，我们并不希望把本地启动的服务暴露出去给别人调用。此时，我们可通过配置 export 禁止服务导出，比如：
        // <dubbo:provider export="false" />
        if (!shouldExport()) {
            return;
        }

        // 父类的方法，是否延迟导出服务，进去
        if (shouldDelay()) {
            // 延时导出服务
            DELAY_EXPORT_EXECUTOR.schedule(this::doExport, getDelay(), TimeUnit.MILLISECONDS);
        } else {
            // 进去
            doExport();
        }
        // 导出完毕的后置动作
        exported();
    }

    // 注意看子类ServiceBean的重写方法
    public void exported() {
        // 爷爷AbstractInterfaceConfig的方法
        List<URL> exportedURLs = this.getExportedUrls();
        exportedURLs.forEach(url -> {
            Map<String, String> parameters = getApplication().getParameters();
            // 做映射关系，获取具体的扩展实例，然后调用map方法，将指定的Dubbo服务接口、组、版本和协议映射到当前的Dubbo服务名称
            // 如果parameters为null，则使用默认的扩展实例，扩展名为config，扩展实例为DynamicConfigurationServiceNameMapping
            // 实际就是注册到zk，zkpath的节点值分别为：/dubbo/config/mapping/samples.servicediscovery.demo.DemoService/demo-provider ----> 当前时间戳
            ServiceNameMapping.getExtension(parameters != null ? parameters.get(MAPPING_KEY) : null).map(url);
        });
        // dispatch a ServiceConfigExportedEvent since 2.7.4
        dispatch(new ServiceConfigExportedEvent(this));
    }

    private void checkAndUpdateSubConfigs() {
        // Use default configs defined explicitly with global scope
        // 以下三个都是父类的方法，进去
        completeCompoundConfigs();
        // 检测 ProviderConfig 是否为空，为空则新建一个，进去
        checkDefault();
        // 检查 ProtocolConfig 是否为空，为空的话内部会创建，进去
        checkProtocol();
        // 看了下没有SPI接口的子类
        List<ConfigInitializer> configInitializers = ExtensionLoader.getExtensionLoader(ConfigInitializer.class)
                .getActivateExtension(URL.valueOf("configInitializer://"), (String[]) null);
        configInitializers.forEach(e -> e.initServiceConfig(this));

        // 判断protocols的长度是否为1，且名称为injvm ，进去
        if (!isOnlyInJvm()) {
            // 如果前面返回false，表示不是像jvm/内存注册，那么肯定就是像远端注册中心注册，所以这里checkRegistry，防止为空，内部判断为空会创建，
            // 和前面的checkDefault、checkProtocol类似，进去
            checkRegistry();
        }
        // AbstractConfig的refresh方法。主要是prefix的值得到更新，比如prefix = "dubbo.service.samples.servicediscovery.demo.DemoService"
        this.refresh();

        // 检测 interfaceName 是否合法（<dubbo:service> 标签的 interface 属性合法性）
        if (StringUtils.isEmpty(interfaceName)) {
            // 日志
            throw new IllegalStateException("<dubbo:service interface=\"\" /> interface not allow null!");
        }

        // 检测 ref 是否为泛化服务类型。ref是具体实现类对象
        if (ref instanceof GenericService) {
            // 设置 interfaceClass 为 GenericService.class
            interfaceClass = GenericService.class;
            if (StringUtils.isEmpty(generic)) {
                // 设置 generic = "true"
                generic = Boolean.TRUE.toString();
            }
            // ref 非 GenericService 类型
        } else {
            try {
                // 使用线程上下文加载器加载接口（根据String全限定名）同时初始化--->其实主要是为了拿到Class
                interfaceClass = Class.forName(interfaceName, true, Thread.currentThread()
                        .getContextClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            // 判断接口是否包含getMethods()这些方法，进去
            checkInterfaceAndMethods(interfaceClass, getMethods());
            // 对 ref 合法性进行检测，进去
            checkRef();
            // 设置 generic = "false"
            generic = Boolean.FALSE.toString();
        }
        // local 和 stub 在功能应该是一致的，用于配置本地存根
        if (local != null) {
            if ("true".equals(local)) {
                local = interfaceName + "Local";
            }
            Class<?> localClass;
            try {
                // 获取本地存根类
                localClass = ClassUtils.forNameWithThreadContextClassLoader(local);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            // 检测本地存根类是否可赋值给接口类，若不可赋值则会抛出异常，提醒使用者本地存根类类型不合法
            if (!interfaceClass.isAssignableFrom(localClass)) {
                // 日志
                throw new IllegalStateException("The local implementation class " + localClass.getName() + " not implement interface " + interfaceName);
            }
        }
        if (stub != null) {
            // 此处的代码和上一个 if 分支的代码基本一致

            if ("true".equals(stub)) {
                stub = interfaceName + "Stub";
            }
            Class<?> stubClass;
            try {
                stubClass = ClassUtils.forNameWithThreadContextClassLoader(stub);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
            if (!interfaceClass.isAssignableFrom(stubClass)) {
                throw new IllegalStateException("The stub implementation class " + stubClass.getName() + " not implement interface " + interfaceName);
            }
        }
        // 再检查一遍stub和local，进去
        checkStubAndLocal(interfaceClass);
        // 检查mock，进去
        ConfigValidationUtils.checkMock(interfaceClass, this);
        // 一些杂七杂八的配置检查，进去
        ConfigValidationUtils.validateServiceConfig(this);
        // 前面config信息配置完之后，触发后置操作，不过目前没实现类，进去
        postProcessConfig();
    }


    // 加锁了
    protected synchronized void doExport() {
        if (unexported) {
            throw new IllegalStateException("The service " + interfaceClass.getName() + " has already unexported!");
        }
        if (exported) {
            return;
        }
        exported = true;

        if (StringUtils.isEmpty(path)) {
            path = interfaceName;
        }
        // 导出服务
        doExportUrls();

        //dubbo2.7版本--待修正
        //以上就是配置检查的相关分析，代码比较多，需要大家耐心看一下。下面对配置检查的逻辑进行简单的总结，如下：
        //
        //1. 检测 <dubbo:service> 标签的 interface 属性合法性，不合法则抛出异常
        //2. 检测 ProviderConfig、ApplicationConfig 等核心配置类对象是否为空，若为空，则尝试从其他配置类对象中获取相应的实例。
        //3. 检测并处理泛化服务和普通服务类
        //4. 检测本地存根配置，并进行相应的处理
        //5. 对 ApplicationConfig、RegistryConfig 等配置类进行检测，为空则尝试创建，若无法创建则抛出异常
        //
        //配置检查并非本文重点，因此这里不打算对 doExport 方法所调用的方法进行分析（doExportUrls 方法除外）。在这些方法中，除了 appendProperties 方法稍微复杂一些，其他方法逻辑不是很复杂。因此，大家可自行分析。
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void doExportUrls() {
        // 获取服务仓库，注意其构造方法就放了4个service，去看下
        ServiceRepository repository = ApplicationModel.getServiceRepository();
        // 两个都进去
        ServiceDescriptor serviceDescriptor = repository.registerService(getInterfaceClass());
        // 此时repository如下：
        //"org.apache.dubbo.rpc.service.EchoService" -> {ServiceDescriptor@2344}
        //"org.apache.dubbo.rpc.service.GenericService" -> {ServiceDescriptor@2346}
        //"org.apache.dubbo.monitor.MetricsService" -> {ServiceDescriptor@2348}
        //"org.apache.dubbo.monitor.MonitorService" -> {ServiceDescriptor@2350}
        //"org.apache.dubbo.demo.DemoService" -> {ServiceDescriptor@2352} --- >注意

        // 进去
        repository.registerProvider(
                // 进去
                getUniqueServiceName(), // serviceKey
                ref, // serviceInstance
                serviceDescriptor, // serviceModel
                this, // serviceConfig
                serviceMetadata // serviceMetadata
        );
        // 此时repository如下：
        // services = {ConcurrentHashMap@2333}  size = 5
        // consumers = {ConcurrentHashMap@2334}  size = 0
        // providers = {ConcurrentHashMap@2335}  size = 1
        //  "org.apache.dubbo.demo.DemoService" -> {ProviderModel@2369} ----> 注意
        // providersWithoutGroup = {ConcurrentHashMap@2336}  size = 1
        //  "org.apache.dubbo.demo.DemoService:null" -> {ProviderModel@2369} ---->注意

        // 加载注册中心链接(内部会将多个RegistryConfig转化为多个对应个URL)，进去
        // eg registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&pid=6714&registry=zookeeper&timestamp=1609849127050
        // eg service-discovery-registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=demo-provider&dubbo=2.0.2&id=org.apache.dubbo.config.RegistryConfig&metadata-type=remote&pid=67248&registry=zookeeper&registry-type=service&timestamp=1619165300619
        List<URL> registryURLs = ConfigValidationUtils.loadRegistries(this, true);

        // Dubbo 允许我们使用不同的协议导出服务，也允许我们向多个注册中心注册服务。Dubbo 在 doExportUrls 方法中对多协议，多注册中心进行了支持。
        // 遍历 protocols，并在每个协议下导出服务，并且在导出服务的过程中，将服务注册到注册中心
        for (ProtocolConfig protocolConfig : protocols) { // protocols的填充实际注意下（checkProtocol）
            String pathKey = URL.buildKey(
                    getContextPath(protocolConfig) // 返回的为Optional，进去
                    .map(p -> p + "/" + path)
                    .orElse(path), group, version
            );// eg org.apache.dubbo.demo.DemoService
            // In case user specified path, register service one more time to map it to path.
            repository.registerService(pathKey, interfaceClass);
            serviceMetadata.setServiceKey(pathKey);
            // 核心，for1Protocol的意思是使用 1个（这个） 协议进行导出。第二个参数是list，多个注册中心（多协议、多注册中心）。进去
            doExportUrlsFor1Protocol(protocolConfig, registryURLs);
        }
    }

    private void doExportUrlsFor1Protocol(ProtocolConfig protocolConfig, List<URL> registryURLs) {
        String name = protocolConfig.getName(); // eg: dubbo
        if (StringUtils.isEmpty(name)) {
            name = DUBBO;
        }
        // -----------------------------------✨✨分割线✨✨---------------------------------------------------------------------
        Map<String, String> map = new HashMap<String, String>();
        map.put(SIDE_KEY, PROVIDER_SIDE); // side -> provider
        ServiceConfig.appendRuntimeParameters(map);
        AbstractConfig.appendParameters(map, getMetrics());
        AbstractConfig.appendParameters(map, getApplication());
        AbstractConfig.appendParameters(map, getModule());
        AbstractConfig.appendParameters(map, provider);
        AbstractConfig.appendParameters(map, protocolConfig);
        AbstractConfig.appendParameters(map, this);
        // 进去
        MetadataReportConfig metadataReportConfig = getMetadataReportConfig();
        if (metadataReportConfig != null && metadataReportConfig.isValid()) {
            map.putIfAbsent(METADATA_KEY, REMOTE_METADATA_STORAGE_TYPE);// "metadata-type" -> "remote"
        }
        // 前面也有这个填充map的类似过程，即 loadRegistries 的时候

        // 此时map如下：
        //"side" -> "provider"
        //"release" -> ""   -------------------appendRuntimeParameters
        //"dubbo" -> "2.0.2"------------------- ........
        //"pid" -> "9072"------------------- ..........
        //"timestamp" -> "1609903950153" ------appendRuntimeParameters
        //"application" -> "dubbo-demo-api-provider" ------ getApplication
        //"dynamic" -> "true"           --------   provider（这个属性的默认值就是true）
        //"deprecated" -> "false"       --------   provider（这个属性的默认值就是false）
        //"default" -> "true"            -------- protocolConfig（convertProtocolIdsToProtocols内部创建protocolConfig的时候setDefault(true) ）
        //"generic" -> "false"          ------- this（checkAndUpdateSubConfigs 内部会检查，并给generic赋值）
        //"interface" -> "org.apache.dubbo.demo.DemoService" ---- this(比如demo-api-provider调用了setInterface)
        //"metadata-type" -> "remote"

// -----------------------------------✨✨分割线✨✨---------------------------------------------------------------------
        if (CollectionUtils.isNotEmpty(getMethods())) {
            for (MethodConfig method : getMethods()) {
                AbstractConfig.appendParameters(map, method, method.getName());
                String retryKey = method.getName() + ".retry";
                if (map.containsKey(retryKey)) {
                    String retryValue = map.remove(retryKey);
                    if ("false".equals(retryValue)) {
                        map.put(method.getName() + ".retries", "0");
                    }
                }
                List<ArgumentConfig> arguments = method.getArguments();
                if (CollectionUtils.isNotEmpty(arguments)) {
                    for (ArgumentConfig argument : arguments) {
                        // convert argument type
                        if (argument.getType() != null && argument.getType().length() > 0) {
                            Method[] methods = interfaceClass.getMethods();
                            // visit all methods
                            if (methods.length > 0) {
                                for (int i = 0; i < methods.length; i++) {
                                    String methodName = methods[i].getName();
                                    // target the method, and get its signature
                                    if (methodName.equals(method.getName())) {
                                        Class<?>[] argtypes = methods[i].getParameterTypes();
                                        // one callback in the method
                                        if (argument.getIndex() != -1) {
                                            if (argtypes[argument.getIndex()].getName().equals(argument.getType())) {
                                                AbstractConfig.appendParameters(map, argument, method.getName() + "." + argument.getIndex());
                                            } else {
                                                throw new IllegalArgumentException("Argument config error : the index attribute and type attribute not match :index :" + argument.getIndex() + ", type:" + argument.getType());
                                            }
                                        } else {
                                            // multiple callbacks in the method
                                            for (int j = 0; j < argtypes.length; j++) {
                                                Class<?> argclazz = argtypes[j];
                                                if (argclazz.getName().equals(argument.getType())) {
                                                    AbstractConfig.appendParameters(map, argument, method.getName() + "." + j);
                                                    if (argument.getIndex() != -1 && argument.getIndex() != j) {
                                                        throw new IllegalArgumentException("Argument config error : the index attribute and type attribute not match :index :" + argument.getIndex() + ", type:" + argument.getType());
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (argument.getIndex() != -1) {
                            AbstractConfig.appendParameters(map, argument, method.getName() + "." + argument.getIndex());
                        } else {
                            throw new IllegalArgumentException("Argument config must set index or type attribute.eg: <dubbo:argument index='0' .../> or <dubbo:argument type=xxx .../>");
                        }

                    }
                }
            } // end of methods for
        }
// -----------------------------------✨✨分割线✨✨---------------------------------------------------------------------
        if (ProtocolUtils.isGeneric(generic)) {
            map.put(GENERIC_KEY, generic);
            map.put(METHODS_KEY, ANY_VALUE);
        } else {
            String revision = Version.getVersion(interfaceClass, version);
            // 一般是null。不会进入
            if (revision != null && revision.length() > 0) {
                map.put(REVISION_KEY, revision);
            }

            // 进去（生成的Wrapper0.class见：wrapper-doExportUrlsFor1Protocol-interfaceClass/Wrapper0.class）
            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            // eg : interfaceClass = org.apache.dubbo.demo.DemoService, methods = ["sayHelloAsync" ,"sayHello]
            if (methods.length == 0) {
                logger.warn("No method found in service interface " + interfaceClass.getName());
                map.put(METHODS_KEY, ANY_VALUE);
            } else { // methods -> sayHello,sayHelloAsync
                map.put(METHODS_KEY, StringUtils.join(new HashSet<String>(Arrays.asList(methods)), ","));
            }
        }
        // ====================================================================
        /**
         * Here the token value configured by the provider is used to assign the value to ServiceConfig#token
         */
        if (ConfigUtils.isEmpty(token) && provider != null) {
            token = provider.getToken();
        }

        if (!ConfigUtils.isEmpty(token)) {
            if (ConfigUtils.isDefault(token)) {
                map.put(TOKEN_KEY, UUID.randomUUID().toString());
            } else {
                map.put(TOKEN_KEY, token);
            }
        }
        // ====================================================================
        // init serviceMetadata attachments
        serviceMetadata.getAttachments().putAll(map);
        // export service
        // 获取当前配置的主机所在ip，eg: 30.25.58.102，内部给map填充 anyhost -> true、bind.port -> 20880 ， 进去
        String host = findConfigedHosts(protocolConfig, registryURLs, map);
        // 获取当前配置的主机所在port，eg:20880 ，内部给map填充bind.port -> 20880，进去
        Integer port = findConfigedPorts(protocolConfig, name, map);
        // -----------------------------------✨✨分割线✨✨---------------------------------------------------------------------
        // map全部填充完毕，根据其以及其他一些参数构建url
        URL url = new URL(name, host, port, getContextPath(protocolConfig).map(p -> p + "/" + path).orElse(path), map);
        // url eg:dubbo://30.25.58.102:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=30.25.58.102&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&metadata-type=remote&methods=sayHello,sayHelloAsync&pid=9682&release=&side=provider&timestamp=1609906152829
        // You can customize Configurator to append extra parameters
        if (ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                .hasExtension(url.getProtocol())) {
            url = ExtensionLoader.getExtensionLoader(ConfiguratorFactory.class)
                    .getExtension(url.getProtocol()).getConfigurator(url).configure(url);
        }


        // 可以对比下Registry生成的url 和 Protocol+provider+service...复合多个对象生成的url 案例，如下：
        //    registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&pid=6714&registry=zookeeper&timestamp=1609849127050
        //       dubbo://30.25.58.102:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=30.25.58.102&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&metadata-type=remote&methods=sayHello,sayHelloAsync&pid=9682&release=&side=provider&timestamp=1609906152829
        // url生成了，就开始暴露服务了，接着往下看
        // -----------------------------------✨✨分割线✨✨---------------------------------------------------------------------
        // 这个值一般默认都是null的
        String scope = url.getParameter(SCOPE_KEY); // SCOPE_KEY = "scope"
        // don't export when none is configured
        if (!SCOPE_NONE.equalsIgnoreCase(scope)) { // SCOPE_NONE = "none"

            // export to local if the config is not remote (export to remote only when config is remote)
            if (!SCOPE_REMOTE.equalsIgnoreCase(scope)) {
                // scope为null或者值不是"remote"进这个分支，exportLocal进去
                exportLocal(url);
            }
            // export to remote if the config is not local (export to local only when config is local)
            if (!SCOPE_LOCAL.equalsIgnoreCase(scope)) {
                // scope为null或者值不是"local"，进这个分支
                if (CollectionUtils.isNotEmpty(registryURLs)) {
                    // 遍历所有的注册中心，向每个注册中心进行注册，如果scope为null的话，那么前面也会exportLocal，eg: registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=dubbo-demo-api-provider&dubbo=2.0.2&pid=11176&registry=zookeeper&timestamp=1609916478949
                    for (URL registryURL : registryURLs) {
                        //if protocol is only injvm ,not register, 跳过injvm://的
                        if (LOCAL_PROTOCOL.equalsIgnoreCase(url.getProtocol())) {
                            continue;
                        }
                        // 填充registryURL的dynamic参数到url（如果为true，创建zk节点的时候是临时节点）
                        url = url.addParameterIfAbsent(DYNAMIC_KEY, registryURL.getParameter(DYNAMIC_KEY));
                        URL monitorUrl = ConfigValidationUtils.loadMonitor(this, registryURL);
                        if (monitorUrl != null) {
                            // 填充registryURL的monitor参数到url
                            url = url.addParameterAndEncoded(MONITOR_KEY, monitorUrl.toFullString());
                        }
                        if (logger.isInfoEnabled()) {
                            if (url.getParameter(REGISTER_KEY, true)) {
                                // 日志注意下
                                logger.info("Register dubbo service " + interfaceClass.getName() + " url " + url + " to registry " + registryURL);
                            } else {
                                logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
                            }
                        }

                        // For providers, this is used to enable custom proxy to generate invoker
                        // 对于providers，这用于启用自定义代理来生成调用程序
                        String proxy = url.getParameter(PROXY_KEY);
                        if (StringUtils.isNotEmpty(proxy)) {
                            // 填充url的proxy参数到registryURL
                            registryURL = registryURL.addParameter(PROXY_KEY, proxy);
                        }
                        // 获取invoker（ref是实现类，比如DemoServiceImpl），内部就是利用Javassist生成了一个Wrapper0（内部含有ref的
                        // 调用逻辑），然后用AbstractProxyInvoker包装了下
                        Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass,
                                // 关键点：将url以"参数对"(key为export)添加到registryURL，进去（去看下文件:url以参数对添加到registryURL的结果.md）  ****
                                registryURL.addParameterAndEncoded(EXPORT_KEY, url.toFullString()));
                        // 使用DelegateProviderMetaDataInvoker包装下，进去
                        DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);
                        // 去看 Protocol$Adaptive.export 方法，内部会调用wrapperInvoker.getUrl(可以进去看下)，这个url是一般是
                        // registry://xx的，所以肯定会取到RegistryProtocol，当然会被一些Wrapper包装
                        Exporter<?> exporter = PROTOCOL.export(wrapperInvoker);
                        exporters.add(exporter);
                    }
                } else {
                    // registryURLs 为空，进这个分支。该分支步骤和前面分支后几行代码基本一致，最大区别就在getInvoker传入的第三个url参数不同

                    if (logger.isInfoEnabled()) {
                        logger.info("Export dubbo service " + interfaceClass.getName() + " to url " + url);
                    }
                    Invoker<?> invoker = PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, url);
                    DelegateProviderMetaDataInvoker wrapperInvoker = new DelegateProviderMetaDataInvoker(invoker, this);

                    Exporter<?> exporter = PROTOCOL.export(wrapperInvoker);
                    exporters.add(exporter);
                }

                MetadataUtils.publishServiceDefinition(url);
            }
        }
        this.urls.add(url);
    }


    // 下面方法eg build之前是dubbo://xxx和之后是injvm:// ---> dubbo://30.25.58.102:20880/org.apache.dubbo.demo.DemoService?anyhost=true&application=dubbo-demo-api-provider&bind.ip=30.25.58.102&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=org.apache.dubbo.demo.DemoService&metadata-type=remote&methods=sayHello,sayHelloAsync&pid=9682&release=&side=provider&timestamp=1609906152829
    //
    // injvm://127.0.0.1//org.apache.dubbo.demo.DemoService
    // ?anyhost=true
    // &application=dubbo-demo-api-provider
    // &bind.ip=30.25.58.102
    // &bind.port=20880
    // &default=true
    // &deprecated=false
    // &dubbo=2.0.2
    // &dynamic=true
    // &generic=false
    // &interface=org.apache.dubbo.demo.DemoService
    // &metadata-type=remote
    // &methods=sayHello,sayHelloAsync
    // &pid=9682
    // &release=
    // &side=provider
    // &timestamp=1609906152829

    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * always export injvm
     */
    // 导出服务到本地
    private void exportLocal(URL url) {
        URL local = URLBuilder.from(url)
                .setProtocol(LOCAL_PROTOCOL) // 设置injvm
                .setHost(LOCALHOST_VALUE)
                .setPort(0)
                .build();
        // 创建 Invoker，并导出服务，这里的 protocol 会在运行时调用 InjvmProtocol 的 export 方法

        // 全局搜一下 Protocol$Adaptive 、 ProxyFactory$Adaptive，分别看他们的export和getInvoker，从url取得key是什么（并和上面的
        // url值做对比）从而确定用的是什么扩展类实例。（1）前面setProtocol(LOCAL_PROTOCOL)，而 Protocol$Adaptive export方法内部有
        // String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() ) 所以取出的扩展实例是InjvmProtocol（不
        // 过需要注意被多个Wrapper包装了）。（2）再比如 ProxyFactory$Adaptive getInvoker方法内部有
        // String extName = url.getParameter("proxy", "javassist")，所以取出的扩展实例是 JavassistProxyFactory（也注意其被StubProxyFactoryWrapper包装了）
        Exporter<?> exporter = PROTOCOL.export(
                // 这里的local只是前面protocol+provider+service...复合多个对象（代表provider信息）生成的url，没有和Registry的url结合！而前面向注册中心暴露的时候会融合这两个url（上面 ****）
                PROXY_FACTORY.getInvoker(ref, (Class) interfaceClass, local));
        exporters.add(exporter); // exporter是ListenerExporterWrapper实例
        // 日志注意下
        logger.info("Export dubbo service " + interfaceClass.getName() + " to local registry url : " + local);
        // exportLocal 方法比较简单，首先根据 URL 协议头决定是否导出服务。若需导出，则创建一个新的 URL 并将协议头、主机名以及端口设置成新的值。
        // 然后创建 Invoker，并调用 InjvmProtocol 的 export 方法导出服务。下面我们来看一下 InjvmProtocol 的 export 方法都做了哪些事情
    }

    /**
     * Determine if it is injvm
     *
     * @return
     */
    private boolean isOnlyInJvm() {
        // protocols的长度是否为1，且名称为injvm
        return getProtocols().size() == 1
                && LOCAL_PROTOCOL.equalsIgnoreCase(getProtocols().get(0).getName());// 默认 new ProtocolConfig+refresh 后的name为dubbo
    }


    /**
     * Register & bind IP address for service provider, can be configured separately.服务提供商注册和绑定IP地址，可单独配置。
     * Configuration priority: environment variables -> java system properties -> host property in config file ->
     * /etc/hosts -> default network address -> first available network address
     * 配置优先级:环境变量—> java系统属性—>配置文件中的主机属性—>/etc/hosts ->默认网络地址->第一个可用的网络地址
     * @param protocolConfig
     * @param registryURLs
     * @param map
     * @return
     */
    private String findConfigedHosts(ProtocolConfig protocolConfig,
                                     List<URL> registryURLs,
                                     Map<String, String> map) {
        boolean anyhost = false;

        // 1.先利用protocolConfig构建key然后从环境变量获取
        String hostToBind = getValueFromConfig(protocolConfig, DUBBO_IP_TO_BIND);
        if (hostToBind != null && hostToBind.length() > 0 && isInvalidLocalHost(hostToBind)) {
            throw new IllegalArgumentException("Specified invalid bind ip from property:" + DUBBO_IP_TO_BIND + ", value:" + hostToBind);
        }

        // if bind ip is not found in environment, keep looking up
        if (StringUtils.isEmpty(hostToBind)) {
            // 2.从protocolConfig本身（java系统属性）获取
            hostToBind = protocolConfig.getHost();
            if (provider != null && StringUtils.isEmpty(hostToBind)) {
                // 3.从providerConfig本身获取
                hostToBind = provider.getHost();
            }
            // 验证是否为无效的本地地址，进去
            if (isInvalidLocalHost(hostToBind)) {
                anyhost = true;
                try {// 日志
                    logger.info("No valid ip found from environment, try to find valid host from DNS.");
                    // 4./etc/hosts，eg 30.25.58.102
                    hostToBind = InetAddress.getLocalHost().getHostAddress();
                } catch (UnknownHostException e) {
                    logger.warn(e.getMessage(), e);
                }
                // 5.和注册中心建立连接获取地址
                if (isInvalidLocalHost(hostToBind)) {
                    if (CollectionUtils.isNotEmpty(registryURLs)) {
                        for (URL registryURL : registryURLs) {
                            if (MULTICAST.equalsIgnoreCase(registryURL.getParameter("registry"))) {
                                // skip multicast registry since we cannot connect to it via Socket
                                continue;
                            }
                            // 创建socket和registry进行连接，哪个连接上了，就返回socket的本机地址，并break
                            try (Socket socket = new Socket()) {
                                SocketAddress addr = new InetSocketAddress(registryURL.getHost(), registryURL.getPort());
                                socket.connect(addr, 1000);
                                hostToBind = socket.getLocalAddress().getHostAddress();
                                break;
                            } catch (Exception e) {
                                logger.warn(e.getMessage(), e);
                            }
                        }
                    }
                    // 6.如果还无效，内部遍历网卡，获取第一个可用的网络地址，进去
                    if (isInvalidLocalHost(hostToBind)) {
                        hostToBind = getLocalHost();
                    }
                }
            }
        }

        map.put(BIND_IP_KEY, hostToBind);// bind.ip -> 30.25.58.102

        // registry ip is not used for bind ip by default
        String hostToRegistry = getValueFromConfig(protocolConfig, DUBBO_IP_TO_REGISTRY);
        if (hostToRegistry != null && hostToRegistry.length() > 0 && isInvalidLocalHost(hostToRegistry)) {
            throw new IllegalArgumentException("Specified invalid registry ip from property:" + DUBBO_IP_TO_REGISTRY + ", value:" + hostToRegistry);
        } else if (StringUtils.isEmpty(hostToRegistry)) {
            // bind ip is used as registry ip by default
            hostToRegistry = hostToBind;
        }

        // anyhost -> true
        map.put(ANYHOST_KEY, String.valueOf(anyhost));

        return hostToRegistry;
    }


    /**
     * Register port and bind port for the provider, can be configured separately
     * Configuration priority: environment variable -> java system properties -> port property in protocol config file
     * -> protocol default port
     *
     * @param protocolConfig
     * @param name
     * @return
     */
    // 下面方法的很多内容和findConfigedIp方法类似
    private Integer findConfigedPorts(ProtocolConfig protocolConfig,
                                      String name,
                                      Map<String, String> map) {
        Integer portToBind = null;

        // parse bind port from environment，进去
        String port = getValueFromConfig(protocolConfig, DUBBO_PORT_TO_BIND);
        // 字符串转Integer，且内部做有效验证，进去
        portToBind = parsePort(port);

        // if there's no bind port found from environment, keep looking up.
        if (portToBind == null) {
            // 前面getEnv获取不到，这里直接从protocolConfig对象获取
            portToBind = protocolConfig.getPort();
            if (provider != null && (portToBind == null || portToBind == 0)) {
                // 从providerConfig对象获取
                portToBind = provider.getPort();
            }
            // 获取该扩展实例的默认端口， 比如DubboProtocol的defaultPort = 20880，进去
            final int defaultPort = ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(name).getDefaultPort();
            if (portToBind == null || portToBind == 0) {
                portToBind = defaultPort;
            }
            if (portToBind <= 0) {
                // 从缓存中获取协议的随机端口值，进去
                portToBind = getRandomPort(name);
                if (portToBind == null || portToBind < 0) {
                    // 获取可用端口，进去
                    portToBind = getAvailablePort(defaultPort);
                    // 存入缓存
                    putRandomPort(name, portToBind);
                }
            }
        }

        // save bind port, used as url's key later
        // bind.port -> 20880
        map.put(BIND_PORT_KEY, String.valueOf(portToBind));

        // registry port, not used as bind port by default 注册表端口，默认情况下不用作绑定端口（前面获取的一直是bindPort）
        String portToRegistryStr = getValueFromConfig(protocolConfig, DUBBO_PORT_TO_REGISTRY);
        Integer portToRegistry = parsePort(portToRegistryStr);
        if (portToRegistry == null) {
            portToRegistry = portToBind;
        }

        return portToRegistry;
    }

    // 字符串转Integer，且做有效验证
    private Integer parsePort(String configPort) {
        Integer port = null;
        if (configPort != null && configPort.length() > 0) {
            try {
                Integer intPort = Integer.parseInt(configPort);
                // 是否有效，进去
                if (isInvalidPort(intPort)) {
                    throw new IllegalArgumentException("Specified invalid port from env value:" + configPort);
                }
                port = intPort;
            } catch (Exception e) {
                throw new IllegalArgumentException("Specified invalid port from env value:" + configPort);
            }
        }
        return port;
    }

    private String getValueFromConfig(ProtocolConfig protocolConfig, String key) {
        // DUBBO_ 构建前缀
        String protocolPrefix = protocolConfig.getName().toUpperCase() + "_";
        // DUBBO_ + DUBBO_IP_TO_BIND ，前缀拼接key，内部getEnv
        String value = ConfigUtils.getSystemProperty(protocolPrefix + key);
        if (StringUtils.isEmpty(value)) {
            // 去除前缀 直接根据key获取，内部getEnv
            value = ConfigUtils.getSystemProperty(key);
        }
        return value;
    }

    private Integer getRandomPort(String protocol) {
        protocol = protocol.toLowerCase();
        // 如果没有，返回最小值
        return RANDOM_PORT_MAP.getOrDefault(protocol, Integer.MIN_VALUE);
    }

    private void putRandomPort(String protocol, Integer port) {
        protocol = protocol.toLowerCase();
        if (!RANDOM_PORT_MAP.containsKey(protocol)) {
            RANDOM_PORT_MAP.put(protocol, port);
            // 日志
            logger.warn("Use random available port(" + port + ") for protocol " + protocol);
        }
    }

    private void postProcessConfig() {
        List<ConfigPostProcessor> configPostProcessors = ExtensionLoader.getExtensionLoader(ConfigPostProcessor.class)
                .getActivateExtension(URL.valueOf("configPostProcessor://"), (String[]) null);// 根据url条件激活某些扩展
        configPostProcessors.forEach(component -> component.postProcessServiceConfig(this));// 挨个调用
    }

    /**
     * Dispatch an {@link Event event}
     *
     * @param event an {@link Event event}
     * @since 2.7.5
     */
    private void dispatch(Event event) {
        EventDispatcher.getDefaultExtension().dispatch(event);
    }

    public DubboBootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(DubboBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }
}
