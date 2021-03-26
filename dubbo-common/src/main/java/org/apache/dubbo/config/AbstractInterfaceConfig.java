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
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.context.ConfigManager;
import org.apache.dubbo.config.support.Parameter;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_VERSION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INVOKER_LISTENER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PID_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REFERENCE_FILTER_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.RELEASE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TAG_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMESTAMP_KEY;

/**
 * AbstractDefaultConfig
 *
 * @export
 */
// OK
public abstract class AbstractInterfaceConfig extends AbstractMethodConfig {

    private static final long serialVersionUID = -1559314110797223229L;

    /**
     * Local impl class name for the service interface
     */
    protected String local;

    /**
     * Local stub class name for the service interface
     */
    protected String stub;

    /**
     * Service monitor
     */
    protected MonitorConfig monitor;

    /**
     * Strategies for generating dynamic agents，there are two strategies can be choosed: jdk and javassist
     */
    protected String proxy;

    /**
     * Cluster type
     */
    protected String cluster;

    /**
     * The {@code Filter} when the provider side exposed a service or the customer side references a remote service used,
     * if there are more than one, you can use commas to separate them
     */
    protected String filter;

    /**
     * The Listener when the provider side exposes a service or the customer side references a remote service used
     * if there are more than one, you can use commas to separate them
     */
    protected String listener;

    /**
     * The owner of the service providers
     */
    protected String owner;

    /**
     * Connection limits, 0 means shared connection, otherwise it defines the connections delegated to the current service
     */
    protected Integer connections;

    /**
     * The layer of service providers
     */
    protected String layer;

    /**
     * The application info
     */
    protected ApplicationConfig application;

    /**
     * The module info
     */
    protected ModuleConfig module;

    /**
     * The registry list the service will register to
     * Also see {@link #registryIds}, only one of them will work.
     */
    protected List<RegistryConfig> registries;

    /**
     * The method configuration
     */
    private List<MethodConfig> methods;

    /**
     * The id list of registries the service will register to
     * Also see {@link #registries}, only one of them will work.
     */
    protected String registryIds;

    // connection events
    protected String onconnect;

    /**
     * Disconnection events
     */
    protected String ondisconnect;

    /**
     * The metrics configuration
     */
    protected MetricsConfig metrics;
    protected MetadataReportConfig metadataReportConfig;

    protected ConfigCenterConfig configCenter;

    // callback limits
    private Integer callbacks;
    // the scope for referring/exporting a service, if it's local, it means searching in current JVM only.
    private String scope;

    protected String tag;

    private Boolean auth;


    /**
     * The url of the reference service
     */
    protected final List<URL> urls = new ArrayList<URL>();

    public List<URL> getExportedUrls() {
        return urls;
    }

    public URL toUrl() {
        return urls.isEmpty() ? null : urls.iterator().next();
    }

    public List<URL> toUrls() {
        return urls;
    }

    /**
     * Check whether the registry config is exists, and then conversion it to {@link RegistryConfig}
     */
    public void checkRegistry() {
        // 进去
        convertRegistryIdsToRegistries();

        for (RegistryConfig registryConfig : registries) {
            // 是否有效，这个是一个小技巧，比如registries里面有很多对象，如果暂时不想某些类型的注册中心对象生效，可以isValid置为false
            if (!registryConfig.isValid()) {
                throw new IllegalStateException("No registry config found or it's not a valid config! " +
                        "The registry config is: " + registryConfig);
            }
        }
    }


    public static void appendRuntimeParameters(Map<String, String> map) {
        map.put(DUBBO_VERSION_KEY, Version.getProtocolVersion());
        map.put(RELEASE_KEY, Version.getVersion());
        map.put(TIMESTAMP_KEY, String.valueOf(System.currentTimeMillis()));
        if (ConfigUtils.getPid() > 0) {
            map.put(PID_KEY, String.valueOf(ConfigUtils.getPid()));
        }
        //"release" -> ""
        //"deprecated" -> "false"
        //"dubbo" -> "2.0.2"
        //"pid" -> "9072"
    }

    /**
     * Check whether the remote service interface and the methods meet with Dubbo's requirements.it mainly check, if the
     * methods configured in the configuration file are included in the interface of remote service
     * <p>
     * 检查远程服务接口和方法是否符合Dubbo的要求。主要检查配置文件中配置的方法是否包含在远程服务的接口中
     *
     * @param interfaceClass the interface of remote service
     * @param methods        the methods configured
     */ // 方法主要判断interfaceClass接口里面是否完全含有methods这些方法，否则抛异常
    public void checkInterfaceAndMethods(Class<?> interfaceClass, List<MethodConfig> methods) {
        // interface cannot be null
        Assert.notNull(interfaceClass, new IllegalStateException("interface not allow null!"));

        // to verify interfaceClass is an interface
        if (!interfaceClass.isInterface()) {
            throw new IllegalStateException("The interface class " + interfaceClass + " is not a interface!");
        }
        // check if methods exist in the remote service interface
        if (CollectionUtils.isNotEmpty(methods)) {
            for (MethodConfig methodBean : methods) {
                methodBean.setService(interfaceClass.getName());
                methodBean.setServiceId(this.getId());
                methodBean.refresh();
                String methodName = methodBean.getName();
                if (StringUtils.isEmpty(methodName)) {
                    //<dubbo:method> name attribute is required! Please check:
                    // <dubbo:service interface="org.apache.dubbo.config.api.Greeting" ... ><dubbo:method name="" ... /></<dubbo:reference>
                    throw new IllegalStateException("<dubbo:method> name attribute is required! Please check: " +
                            "<dubbo:service interface=\"" + interfaceClass.getName() + "\" ... >" +
                            "<dubbo:method name=\"\" ... /></<dubbo:reference>");
                }
                // 比如当前methodConfig的toString:<dubbo:method name="nihao" service="org.apache.dubbo.config.api.Greeting" />
                // 这里判断接口里面是否含有这个方法（Greeting接口是否含有nihao方法）
                boolean hasMethod = Arrays.stream(interfaceClass.getMethods()).anyMatch(method -> method.getName().equals(methodName));
                if (!hasMethod) { // The interface org.apache.dubbo.config.api.Greeting not found method nihao
                    throw new IllegalStateException("The interface " + interfaceClass.getName()
                            + " not found method " + methodName);
                }
            }
        }
    }


    /**
     * Legitimacy check of stub, note that: the local will deprecated, and replace with <code>stub</code>
     * <p>
     * 检查stub的合法性，注意:local将弃用，并替换为<code>stub</code>
     *
     * @param interfaceClass for provider side, it is the {@link Class} of the service that will be exported; for consumer
     *                       side, it is the {@link Class} of the remote service interface
     *                       对于提供者端，它是将被导出的服务的Class;对于消费者端，它是远程服务接口的Class
     */

    // 方法主要检查local和stub是不是interfaceClass的实现类，以及是否含有interfaceClass类型参数的构造器
    public void checkStubAndLocal(Class<?> interfaceClass) {
        // 上面参数传入的是 接口.class，下面的local、stub是接口的实现类的全限定名（String）

        // 进去
        verifyStubAndLocal(local, "Local", interfaceClass);
        verifyStubAndLocal(stub, "Stub", interfaceClass);
    }

    public void verifyStubAndLocal(String className, String label, Class<?> interfaceClass) {
        if (ConfigUtils.isNotEmpty(className)) {
            // 根据全限定名加载class
            Class<?> localClass = ConfigUtils.isDefault(className) ?
                    // forName进去
                    ReflectUtils.forName(interfaceClass.getName() + label) : ReflectUtils.forName(className);
            // 校验，进去
            verify(interfaceClass, localClass);
        }
    }

    private void verify(Class<?> interfaceClass, Class<?> localClass) {
        // 看日志
        if (!interfaceClass.isAssignableFrom(localClass)) {
            // eg:The local implementation class org.apache.dubbo.config.mock.GreetingLocal1 not implement interface org.apache.dubbo.config.api.Greeting
            throw new IllegalStateException("The local implementation class " + localClass.getName() +
                    " not implement interface " + interfaceClass.getName());
        }

        try {
            // 检查localClass是否有一个带参数的构造函数，其类型是interfaceClass，进去
            ReflectUtils.findConstructor(localClass, interfaceClass);
        } catch (NoSuchMethodException e) {
            // eg:No such constructor "public GreetingLocal2(org.apache.dubbo.config.api.Greeting)" in local implementation class org.apache.dubbo.config.mock.GreetingLocal2
            throw new IllegalStateException("No such constructor \"public " + localClass.getSimpleName() +
                    "(" + interfaceClass.getName() + ")\" in local implementation class " + localClass.getName());
        }
    }

    // 方法整体逻辑和 convertRegistryIdsToRegistries 相似
    private void convertRegistryIdsToRegistries() {
        // 进去
        computeValidRegistryIds();
        if (StringUtils.isEmpty(registryIds)) {
            if (CollectionUtils.isEmpty(registries)) {
                // ids和list都为空

                // 从ConfigManager获取默认的注册中心，进去（比如demo-api-provider的程序，一开始就调用dubboBootstrap.registery(xx)方法xx注册到了configManager，所以肯定能取到）
                List<RegistryConfig> registryConfigs = ApplicationModel.getConfigManager().getDefaultRegistries();
                if (registryConfigs.isEmpty()) {
                    // 为空的话构建一个
                    registryConfigs = new ArrayList<>();
                    RegistryConfig registryConfig = new RegistryConfig();
                    // refresh把CompositeConfiguration的一些值填充到registryConfig，结合testCheckRegistry1，那么有两个值，对应的
                    // toString的结果为：<dubbo:registry address="addr1" port="0"/>
                    registryConfig.refresh();
                    registryConfigs.add(registryConfig);
                } else { // 一般前面从configManager是能取到的，因为业务方一般都会制定注册中心信息
                    registryConfigs = new ArrayList<>(registryConfigs);
                }
                setRegistries(registryConfigs); // 赋值给自己的属性
            }
        } else {
            // 下面的逻辑不说了，参考 convertRegistryIdsToRegistries

            String[] ids = COMMA_SPLIT_PATTERN.split(registryIds);
            List<RegistryConfig> tmpRegistries = new ArrayList<>();
            Arrays.stream(ids).forEach(id -> {
                if (tmpRegistries.stream().noneMatch(reg -> reg.getId().equals(id))) {
                    Optional<RegistryConfig> globalRegistry = ApplicationModel.getConfigManager().getRegistry(id);
                    if (globalRegistry.isPresent()) {
                        tmpRegistries.add(globalRegistry.get());
                    } else {
                        RegistryConfig registryConfig = new RegistryConfig();
                        registryConfig.setId(id);
                        registryConfig.refresh();
                        tmpRegistries.add(registryConfig);
                    }
                }
            });

            if (tmpRegistries.size() > ids.length) {
                throw new IllegalStateException("Too much registries found, the registries assigned to this service " +
                        "are :" + registryIds + ", but got " + tmpRegistries.size() + " registries!");
            }

            setRegistries(tmpRegistries);
        }

    }

    public void completeCompoundConfigs(AbstractInterfaceConfig interfaceConfig) {
        if (interfaceConfig != null) {
            if (application == null) {
                setApplication(interfaceConfig.getApplication());
            }
            if (module == null) {
                setModule(interfaceConfig.getModule());
            }
            if (registries == null) {
                setRegistries(interfaceConfig.getRegistries());
            }
            if (monitor == null) {
                setMonitor(interfaceConfig.getMonitor());
            }
        }
        if (module != null) {
            if (registries == null) {
                setRegistries(module.getRegistries());
            }
            if (monitor == null) {
                setMonitor(module.getMonitor());
            }
        }
        if (application != null) {
            if (registries == null) {
                setRegistries(application.getRegistries());
            }
            if (monitor == null) {
                setMonitor(application.getMonitor());
            }
        }
    }

    protected void computeValidRegistryIds() {
        if (StringUtils.isEmpty(getRegistryIds())) {
            // 如果当前getRegistryIds为空，那么从ApplicationConfig获取registryIds
            if (getApplication() != null && StringUtils.isNotEmpty(getApplication().getRegistryIds())) {
                setRegistryIds(getApplication().getRegistryIds());// 赋值给自己的属性
            }
        }
    }

    /**
     * @return local
     * @deprecated Replace to <code>getStub()</code>
     */
    @Deprecated
    public String getLocal() {
        return local;
    }

    /**
     * @param local
     * @deprecated Replace to <code>setStub(Boolean)</code>
     */
    @Deprecated
    public void setLocal(Boolean local) {
        if (local == null) {
            setLocal((String) null);
        } else {
            setLocal(local.toString());
        }
    }

    /**
     * @param local
     * @deprecated Replace to <code>setStub(String)</code>
     */
    @Deprecated
    public void setLocal(String local) {
        this.local = local;
    }

    public String getStub() {
        return stub;
    }

    public void setStub(Boolean stub) {
        if (stub == null) {
            setStub((String) null);
        } else {
            setStub(stub.toString());
        }
    }

    public void setStub(String stub) {
        this.stub = stub;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {

        this.proxy = proxy;
    }

    public Integer getConnections() {
        return connections;
    }

    public void setConnections(Integer connections) {
        this.connections = connections;
    }

    @Parameter(key = REFERENCE_FILTER_KEY, append = true)
    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    @Parameter(key = INVOKER_LISTENER_KEY, append = true)
    public String getListener() {
        return listener;
    }

    public void setListener(String listener) {
        this.listener = listener;
    }

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public ApplicationConfig getApplication() {
        if (application != null) {
            return application;
        }
        // 上面为null的话，从configManager获取，再获取不到就抛异常，进去，以demo-api-provider举例，一般都是走到这一步，而启动程序开始就DubboBootstrap.application(xx)注册了到configManager
        return ApplicationModel.getConfigManager().getApplicationOrElseThrow();
    }

    @Deprecated
    public void setApplication(ApplicationConfig application) {
        this.application = application;
        if (application != null) {
            // 获取configManager
            ConfigManager configManager = ApplicationModel.getConfigManager();
            // 如果configManager没有Application的话，把当前application的赋值进去
            configManager.getApplication().orElseGet(() -> {
                // 进去
                configManager.setApplication(application);
                return application;
            });
        }
    }

    public ModuleConfig getModule() {
        if (module != null) {
            return module;
        }
        return ApplicationModel.getConfigManager().getModule().orElse(null);
    }

    @Deprecated
    public void setModule(ModuleConfig module) {
        this.module = module;
        if (module != null) {
            ConfigManager configManager = ApplicationModel.getConfigManager();
            configManager.getModule().orElseGet(() -> {
                configManager.setModule(module);
                return module;
            });
        }
    }

    public RegistryConfig getRegistry() {
        return CollectionUtils.isEmpty(registries) ? null : registries.get(0);
    }

    public void setRegistry(RegistryConfig registry) {
        List<RegistryConfig> registries = new ArrayList<RegistryConfig>(1);
        registries.add(registry);
        setRegistries(registries);
    }

    public List<RegistryConfig> getRegistries() {// 注意setRegistries的调用处，就知道registries怎么就能有值了
        return registries;
    }

    @SuppressWarnings({"unchecked"})
    public void setRegistries(List<? extends RegistryConfig> registries) {
        this.registries = (List<RegistryConfig>) registries;
    }

    @Parameter(excluded = true)
    public String getRegistryIds() {
        return registryIds;
    }

    public void setRegistryIds(String registryIds) {
        this.registryIds = registryIds;
    }


    public List<MethodConfig> getMethods() {
        return methods;
    }

    // ======== Deprecated ========

    @SuppressWarnings("unchecked")
    public void setMethods(List<? extends MethodConfig> methods) {
        this.methods = (List<MethodConfig>) methods;
    }


    public MonitorConfig getMonitor() {
        if (monitor != null) {
            return monitor;
        }
        // FIXME: instead of return null, we should set default monitor when getMonitor() return null in ConfigManager
        return ApplicationModel.getConfigManager().getMonitor().orElse(null);
    }

    @Deprecated
    public void setMonitor(String monitor) {
        setMonitor(new MonitorConfig(monitor));
    }

    @Deprecated
    public void setMonitor(MonitorConfig monitor) {
        this.monitor = monitor;
        if (monitor != null) {
            ConfigManager configManager = ApplicationModel.getConfigManager();
            configManager.getMonitor().orElseGet(() -> {
                configManager.setMonitor(monitor);
                return monitor;
            });
        }
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    @Deprecated
    public ConfigCenterConfig getConfigCenter() {
        if (configCenter != null) {
            return configCenter;
        }
        Collection<ConfigCenterConfig> configCenterConfigs = ApplicationModel.getConfigManager().getConfigCenters();
        if (CollectionUtils.isNotEmpty(configCenterConfigs)) {
            return configCenterConfigs.iterator().next();
        }
        return null;
    }

    @Deprecated
    public void setConfigCenter(ConfigCenterConfig configCenter) {
        this.configCenter = configCenter;
        if (configCenter != null) {
            ConfigManager configManager = ApplicationModel.getConfigManager();
            Collection<ConfigCenterConfig> configs = configManager.getConfigCenters();
            if (CollectionUtils.isEmpty(configs)
                    // noneMatch api 注意下
                    || configs.stream().noneMatch(existed -> existed.equals(configCenter))) {
                // 进去
                configManager.addConfigCenter(configCenter);
            }
        }
    }

    public Integer getCallbacks() {
        return callbacks;
    }

    public void setCallbacks(Integer callbacks) {
        this.callbacks = callbacks;
    }

    public String getOnconnect() {
        return onconnect;
    }

    public void setOnconnect(String onconnect) {
        this.onconnect = onconnect;
    }

    public String getOndisconnect() {
        return ondisconnect;
    }

    public void setOndisconnect(String ondisconnect) {
        this.ondisconnect = ondisconnect;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    @Deprecated
    public MetadataReportConfig getMetadataReportConfig() {
        if (metadataReportConfig != null) {
            return metadataReportConfig;
        }
        // 进去。一般是能获得的（useRegistryAsMetadataCenterIfNecessary会填充MetadataReportConfig到configManger）
        Collection<MetadataReportConfig> metadataReportConfigs = ApplicationModel.getConfigManager().getMetadataConfigs();
        if (CollectionUtils.isNotEmpty(metadataReportConfigs)) {
            return metadataReportConfigs.iterator().next();
        }
        return null;
    }

    @Deprecated
    public void setMetadataReportConfig(MetadataReportConfig metadataReportConfig) {
        this.metadataReportConfig = metadataReportConfig;
        if (metadataReportConfig != null) {
            ConfigManager configManager = ApplicationModel.getConfigManager();
            Collection<MetadataReportConfig> configs = configManager.getMetadataConfigs();
            if (CollectionUtils.isEmpty(configs)
                    || configs.stream().noneMatch(existed -> existed.equals(metadataReportConfig))) {
                configManager.addMetadataReport(metadataReportConfig);
            }
        }
    }

    @Deprecated
    public MetricsConfig getMetrics() {
        if (metrics != null) {
            return metrics;
        }
        return ApplicationModel.getConfigManager().getMetrics().orElse(null);
    }

    @Deprecated
    public void setMetrics(MetricsConfig metrics) {
        this.metrics = metrics;
        if (metrics != null) {
            ConfigManager configManager = ApplicationModel.getConfigManager();
            configManager.getMetrics().orElseGet(() -> {
                configManager.setMetrics(metrics);
                return metrics;
            });
        }
    }

    @Parameter(key = TAG_KEY, useKeyAsProperty = false)
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Boolean getAuth() {
        return auth;
    }

    public void setAuth(Boolean auth) {
        this.auth = auth;
    }

    public SslConfig getSslConfig() {
        return ApplicationModel.getConfigManager().getSsl().orElse(null);
    }
}
