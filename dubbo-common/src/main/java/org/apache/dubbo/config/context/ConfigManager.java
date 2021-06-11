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
package org.apache.dubbo.config.context;

import org.apache.dubbo.common.context.FrameworkExt;
import org.apache.dubbo.common.context.LifecycleAdapter;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.config.AbstractConfig;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ConfigCenterConfig;
import org.apache.dubbo.config.ConsumerConfig;
import org.apache.dubbo.config.MetadataReportConfig;
import org.apache.dubbo.config.MetricsConfig;
import org.apache.dubbo.config.ModuleConfig;
import org.apache.dubbo.config.MonitorConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ProviderConfig;
import org.apache.dubbo.config.ReferenceConfigBase;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfigBase;
import org.apache.dubbo.config.SslConfig;
import org.apache.dubbo.rpc.model.ApplicationModel;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.Optional.ofNullable;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY;
import static org.apache.dubbo.common.utils.ReflectUtils.getProperty;
import static org.apache.dubbo.common.utils.StringUtils.isNotEmpty;
import static org.apache.dubbo.config.AbstractConfig.getTagName;
import static org.apache.dubbo.config.Constants.PROTOCOLS_SUFFIX;
import static org.apache.dubbo.config.Constants.REGISTRIES_SUFFIX;

// OK
// 该类的主要作用就是缓存各种XXConfig实例
public class ConfigManager extends LifecycleAdapter implements FrameworkExt {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    // SPI扩展名
    public static final String NAME = "config";

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    final Map<String/*tagName，比如application*/, Map<String/*id*/, AbstractConfig/*具体的实例比如ApplicationConfig对象*/>> configsCache = newMap();

    public ConfigManager() {
    }

    // ============== ============== ============== ============== ============== ============== ==============
    // ApplicationConfig correlative methods
    public void setApplication(ApplicationConfig application) {
        // unique表示此ApplicationConfig实例只能有一份，进去
        addConfig(application, true);
    }

    public Optional<ApplicationConfig> getApplication() {
        // getTagName(ApplicationConfig.class) = application
        // getConfig进去
        // ofNullable封装成Optional
        return ofNullable(getConfig(getTagName(ApplicationConfig.class)));
    }

    public ApplicationConfig getApplicationOrElseThrow() {
        // getApplication进去，orElseThrow是Optional的方法
        return getApplication().orElseThrow(() -> new IllegalStateException("There's no ApplicationConfig specified."));
    }

    // ============== ============== ============== ============== ============== ============== ==============
    // MonitorConfig correlative methods
    // 参考前面Application
    public void setMonitor(MonitorConfig monitor) {
        addConfig(monitor, true);
    }

    public Optional<MonitorConfig> getMonitor() {
        return ofNullable(getConfig(getTagName(MonitorConfig.class)));
    }

    // ============== ============== ============== ============== ============== ============== ==============
    // ModuleConfig correlative methods
    // 参考前面Application
    public void setModule(ModuleConfig module) {
        addConfig(module, true);
    }

    public Optional<ModuleConfig> getModule() {
        return ofNullable(getConfig(getTagName(ModuleConfig.class)));
    }

    // ============== ============== ============== ============== ============== ============== ==============
    // 参考前面Application
    public void setMetrics(MetricsConfig metrics) {
        addConfig(metrics, true);
    }

    public Optional<MetricsConfig> getMetrics() {
        return ofNullable(getConfig(getTagName(MetricsConfig.class)));
    }

    // ============== ============== ============== ============== ============== ============== ==============
    // 参考前面Application
    public void setSsl(SslConfig sslConfig) {
        addConfig(sslConfig, true);
    }

    public Optional<SslConfig> getSsl() {
        return ofNullable(getConfig(getTagName(SslConfig.class)));
    }

    // 前面都是setXx即设值，不管怎样最多仅一份（addConfig的参数unique=true也看出来了），getXx方法返回Optional，包装的也是一个值。
    // 下面的方法都是addXx了，说明是Xx类型可以存储多份实例。

    // ============== ============== ============== ============== ============== ============== =============
    // ConfigCenterConfig correlative methods

    public void addConfigCenter(ConfigCenterConfig configCenter) {
        // 进去
        addConfig(configCenter);
    }

    public void addConfigCenters(Iterable<ConfigCenterConfig> configCenters) {
        configCenters.forEach(this::addConfigCenter);
    }

    public Optional<Collection<ConfigCenterConfig>> getDefaultConfigCenter() {
        // getTagName:判断类名是否以Config、Bean、ConfigBase结尾是的话，去除结尾并根据驼峰解析以-连接，比如ConfigCenterConfig最后就是config-center
        // getConfigsMap:以config-center作为key从类属性configsCache取value(map结构)，取不到初始化一个空map(注意内部用读锁保护configsCache了)
        // getDefaultConfigs:根据上面取出来的value，即 Map<String, AbstractConfig>，遍历value，收集默认的AbstractConfig存到list返回
        Collection<ConfigCenterConfig> defaults = getDefaultConfigs(getConfigsMap(getTagName(ConfigCenterConfig.class)));
        if (CollectionUtils.isEmpty(defaults)) {
            // 如果为空，那么获取配置中心，不带Default，内部和前面差不多
            defaults = getConfigCenters();
        }
        // 默认的可以有多个，所以defaults是一个集合
        return Optional.ofNullable(defaults);
    }

    public ConfigCenterConfig getConfigCenter(String id) {
        return getConfig(getTagName(ConfigCenterConfig.class), id);
    }

    public Collection<ConfigCenterConfig> getConfigCenters() {
        return getConfigs(getTagName(ConfigCenterConfig.class));
    }

    // ============== ============== ============== ============== ============== ============== ==============
    // 参考前面ConfigCenter
    // MetadataReportConfig correlative methods

    public void addMetadataReport(MetadataReportConfig metadataReportConfig) {
        addConfig(metadataReportConfig);
    }

    public void addMetadataReports(Iterable<MetadataReportConfig> metadataReportConfigs) {
        metadataReportConfigs.forEach(this::addMetadataReport);
    }

    public Collection<MetadataReportConfig> getMetadataConfigs() {
        return getConfigs(getTagName(MetadataReportConfig.class));
    }

    public Collection<MetadataReportConfig> getDefaultMetadataConfigs() {
        Collection<MetadataReportConfig> defaults = getDefaultConfigs(getConfigsMap(getTagName(MetadataReportConfig.class)));
        if (CollectionUtils.isEmpty(defaults)) {
            return getMetadataConfigs();
        }
        return defaults;
    }

    // ============== ============== ============== ============== ============== ============== ==============
    // 参考前面ConfigCenter
    // ProviderConfig correlative methods

    public void addProvider(ProviderConfig providerConfig) {
        addConfig(providerConfig);
    }

    public void addProviders(Iterable<ProviderConfig> providerConfigs) {
        providerConfigs.forEach(this::addProvider);
    }

    public Optional<ProviderConfig> getProvider(String id) {
        return ofNullable(getConfig(getTagName(ProviderConfig.class), id));
    }

    /**
     * Only allows one default ProviderConfig
     */
    public Optional<ProviderConfig> getDefaultProvider() {
        // 三个都进去
        List<ProviderConfig> providerConfigs = getDefaultConfigs(getConfigsMap(getTagName(ProviderConfig.class)));
        if (CollectionUtils.isNotEmpty(providerConfigs)) {
            // Optional包装一下，注意的地方就是ProviderConfig类型的默认实例仅允许有一份（前面的ProviderConfig、ConfigCenter默认实例是允许多份的）
            return Optional.of(providerConfigs.get(0));
        }
        return Optional.empty();
    }

    public Collection<ProviderConfig> getProviders() {
        return getConfigs(getTagName(ProviderConfig.class));
    }
    // ============== ============== ============== ============== ============== ============== ==============
    // 参考前面ConfigCenter
    // ConsumerConfig correlative methods

    public void addConsumer(ConsumerConfig consumerConfig) {
        addConfig(consumerConfig);
    }

    public void addConsumers(Iterable<ConsumerConfig> consumerConfigs) {
        consumerConfigs.forEach(this::addConsumer);
    }

    public Optional<ConsumerConfig> getConsumer(String id) {
        return ofNullable(getConfig(getTagName(ConsumerConfig.class), id));
    }

    /**
     * Only allows one default ConsumerConfig
     */
    public Optional<ConsumerConfig> getDefaultConsumer() {
        List<ConsumerConfig> consumerConfigs = getDefaultConfigs(getConfigsMap(getTagName(ConsumerConfig.class)));
        if (CollectionUtils.isNotEmpty(consumerConfigs)) {
            return Optional.of(consumerConfigs.get(0));
        }
        return Optional.empty();
    }

    public Collection<ConsumerConfig> getConsumers() {
        return getConfigs(getTagName(ConsumerConfig.class));
    }

    // ============== ============== ============== ============== ============== ============== ==============
    // 参考前面ConfigCenter
    // ProtocolConfig correlative methods

    public void addProtocol(ProtocolConfig protocolConfig) {
        addConfig(protocolConfig);
    }

    public void addProtocols(Iterable<ProtocolConfig> protocolConfigs) {
        if (protocolConfigs != null) {
            protocolConfigs.forEach(this::addProtocol);
        }
    }

    public Optional<ProtocolConfig> getProtocol(String id) {
        return ofNullable(getConfig(getTagName(ProtocolConfig.class), id));
    }

    public List<ProtocolConfig> getDefaultProtocols() {
        // 和前面的getDefaultXXs不一样，这里没用Optional包装下
        return getDefaultConfigs(getConfigsMap(getTagName(ProtocolConfig.class)));
    }

    public Collection<ProtocolConfig> getProtocols() {
        return getConfigs(getTagName(ProtocolConfig.class));
    }

    public Set<String> getProtocolIds() {

        Set<String> protocolIds = new HashSet<>();
        // getExternalConfigurationMap 里面容器 对应的设置点 在prepareEnvironment里面，其实就是写数据到了zk的/dubbo/config/dubbo/dubbo.properties
        //  文件里面的内容就是dubbo.registries.r1.address = .... 或者 dubbo.protocols.rmi.address = ...这种，中间的r1、rmi就是这里的registryId或者protocolId
        // getSubProperties 进去
        protocolIds.addAll(getSubProperties(ApplicationModel.getEnvironment()
                .getExternalConfigurationMap(), PROTOCOLS_SUFFIX));
        protocolIds.addAll(getSubProperties(ApplicationModel.getEnvironment()
                .getAppExternalConfigurationMap(), PROTOCOLS_SUFFIX));

        return unmodifiableSet(protocolIds);
    }


    // ============== ============== ============== ============== ============== ============== ==============
    // 参考前面ConfigCenter
    // RegistryConfig correlative methods

    public void addRegistry(RegistryConfig registryConfig) {
        addConfig(registryConfig);
    }

    public void addRegistries(Iterable<RegistryConfig> registryConfigs) {
        if (registryConfigs != null) {
            registryConfigs.forEach(this::addRegistry);
        }
    }

    public Optional<RegistryConfig> getRegistry(String id) {
        return ofNullable(getConfig(getTagName(RegistryConfig.class), id));
    }

    public List<RegistryConfig> getDefaultRegistries() {
        return getDefaultConfigs(getConfigsMap(getTagName(RegistryConfig.class)));
    }

    public Collection<RegistryConfig> getRegistries() {
        return getConfigs(getTagName(RegistryConfig.class));
    }

    public Set<String> getRegistryIds() {
        Set<String> registryIds = new HashSet<>();

        // getExternalConfigurationMap 里面容器 对应的设置点 在prepareEnvironment里面，其实就是写数据到了zk的/dubbo/config/dubbo/dubbo.properties
        //  文件里面的内容就是dubbo.registries.r1.address = .... 或者 dubbo.protocols.rmi.address = ...这种，中间的r1、rmi就是这里的registryId或者protocolId
        // getSubProperties 进去
        registryIds.addAll(getSubProperties(ApplicationModel.getEnvironment().getExternalConfigurationMap(),
                REGISTRIES_SUFFIX));
        registryIds.addAll(getSubProperties(ApplicationModel.getEnvironment().getAppExternalConfigurationMap(),
                REGISTRIES_SUFFIX));

        return unmodifiableSet(registryIds);
    }

    // ============== ============== ============== ============== ============== ============== ==============
    // 参考前面ConfigCenter
    // ServiceConfig correlative methods

    public void addService(ServiceConfigBase<?> serviceConfig) {
        addConfig(serviceConfig);
    }

    public void addServices(Iterable<ServiceConfigBase<?>> serviceConfigs) {
        serviceConfigs.forEach(this::addService);
    }

    public Collection<ServiceConfigBase> getServices() {
        return getConfigs(getTagName(ServiceConfigBase.class));
    }

    public <T> ServiceConfigBase<T> getService(String id) {
        return getConfig(getTagName(ServiceConfigBase.class), id);
    }

    // ============== ============== ============== ============== ============== ============== ==============
    // 参考前面ConfigCenter
    // ReferenceConfig correlative methods

    public void addReference(ReferenceConfigBase<?> referenceConfig) {
        addConfig(referenceConfig);
    }

    public void addReferences(Iterable<ReferenceConfigBase<?>> referenceConfigs) {
        referenceConfigs.forEach(this::addReference);
    }

    public Collection<ReferenceConfigBase<?>> getReferences() {
        return getConfigs(getTagName(ReferenceConfigBase.class));
    }

    public <T> ReferenceConfigBase<T> getReference(String id) {
        return getConfig(getTagName(ReferenceConfigBase.class), id);
    }

    protected static Set<String> getSubProperties(Map<String, String> properties, String prefix) {
        return properties.keySet().stream().filter(k -> k.contains(prefix)).map(k -> {
            // 比如"dubbo.protocols.dubbo1.port" -> "20991"
            // k = dubbo1.port
            k = k.substring(prefix.length());
            // return dubbo1
            return k.substring(0, k.indexOf("."));
        }).collect(Collectors.toSet());
    }



    public void refreshAll() {
        write(() -> {
            // refresh all configs here 这里也能看到前三个类型的Config类型对象都是单个的，后四个都是list(同类型Config可以有多个)
            // 调用每个config对象的refresh方法，其实都是AbstractConfig的refresh方法，只是this指针不同

            getApplication().ifPresent(ApplicationConfig::refresh);
            getMonitor().ifPresent(MonitorConfig::refresh);
            getModule().ifPresent(ModuleConfig::refresh);

            getProtocols().forEach(ProtocolConfig::refresh);
            getRegistries().forEach(RegistryConfig::refresh);
            getProviders().forEach(ProviderConfig::refresh);
            getConsumers().forEach(ConsumerConfig::refresh);
        });

    }

    /**
     * In some scenario,  we may nee to add and remove ServiceConfig or ReferenceConfig dynamically.
     * 在某些场景中，我们可能需要动态地添加和删除ServiceConfig或ReferenceConfig。
     *
     * @param config the config instance to remove.
     */
    public void removeConfig(AbstractConfig config) {
        if (config == null) {
            return;
        }
        // 从下面的代码就知道configsCache的kv分别是存储的什么东西了  {tagName:{id:config} },....

        Map<String, AbstractConfig> configs = configsCache.get(getTagName(config.getClass()));
        if (CollectionUtils.isNotEmptyMap(configs)) {
            configs.remove(getId(config));
        }
    }

    public void clear() {
        // map.clear清空配置缓存
        // write进去
        write(this.configsCache::clear);
    }

    /**
     * @throws IllegalStateException
     * @since 2.7.8
     */
    @Override
    public void destroy() throws IllegalStateException {
        // 进去
        clear();
    }

    /**
     * Add the dubbo {@link AbstractConfig config}
     *
     * @param config the dubbo {@link AbstractConfig config}
     */
    public void addConfig(AbstractConfig config) {
        addConfig(config, false);
    }

    protected void addConfig(AbstractConfig config, boolean unique) {
        if (config == null) {
            return;
        }
        write(() -> {

            //  computeIfAbsent 的含义可以全局搜下，在其他地方做过记录，再说一下：如果key存在，返回key的val，如果不存在，将第二个参数作为val赋值到map，并返回该val
            //  这里的type和和tagName一样（可以加输出试试），本身就是这样的，tagName输出作为第二个的输入
            Map<String, AbstractConfig> configsMap = configsCache.computeIfAbsent(getTagName(config.getClass()), type -> newMap());
            // addIfAbsent的作用就是看能否把config对象填充到configsMap（当然configsCache的key已经有值了，但是val即configsMap可能是一个空map（当然也可能有值））
            // 进去
            addIfAbsent(config, configsMap, unique);
        });
    }


    // 下面测试的putIfAbsent和computeIfAbsent区别
    public static void main(String[] args) {
        Map<String,String> map = new HashMap<>();
        map.put("1","1");
        map.put("2","2");
        String s = map.putIfAbsent("1", "11");
        String s1 = map.putIfAbsent("sdsdsd", "wee");
        System.out.println(s+","+s1);// 1,null
        String s2 = map.computeIfAbsent("2", type->"22");
        String s3 = map.computeIfAbsent("dfff", type->"sdsd");
        System.out.println(s2+","+s3);// 2,sdsd
    }

    protected <C extends AbstractConfig> Map<String, C> getConfigsMap(String configType) {
        // getOrDefault是map的api
        return (Map<String, C>) read(() -> configsCache.getOrDefault(configType, emptyMap()));
    }

    protected <C extends AbstractConfig> Collection<C> getConfigs(String configType) {
        // 调用上面的getConfigsMap方法，并返回values
        return (Collection<C>) read(() -> getConfigsMap(configType).values());
    }

    protected <C extends AbstractConfig> C getConfig(String configType, String id) {
        return read(() -> {
            // 调用上面的getConfigsMap方法
            Map<String, C> configsMap = (Map) configsCache.getOrDefault(configType, emptyMap());
            // 根据id取config，这里也能看出configsCache的结构 {config'sTagName:{id:config}}
            return configsMap.get(id);
        });
    }

    // 这个是给那些setXx方法（即在同类型下仅有一份实例Config）调用的
    protected <C extends AbstractConfig> C getConfig(String configType) throws IllegalStateException {
        return read(() -> {
            // configType 就是getTagName返回的（getOrDefault是map的api）
            Map<String, C> configsMap = (Map) configsCache.getOrDefault(configType, emptyMap());
            int size = configsMap.size();
            if (size < 1) {
//                throw new IllegalStateException("No such " + configType.getName() + " is found");
                return null;
            } else if (size > 1) {
                logger.warn("Expected single matching of " + configType + ", but found " + size + " instances, will randomly pick the first one.");
            }

            // 取第一个val，实际configsMap.size 为 1
            return configsMap.values().iterator().next();
        });
    }

    private void write(Runnable runnable) {
        // 调用重载的write方法，其参数是callable
        write(() -> {
            runnable.run();
            return null;
        });
    }

    // gx 仅被上面调用了
    // 这种思想第一次见，ConfigManager内部关于configsCache的读写业务逻辑操作都封装了runnable任务/callable，并传给write或者后面的read
    // 并且write、read方法内部使用读写锁保护了configCache
    private <V> V write(Callable<V> callable) {
        V value = null;
        // 写锁
        Lock writeLock = lock.writeLock();
        try {
            writeLock.lock();
            // 执行任务
            value = callable.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable e) {
            throw new RuntimeException(e.getCause());
        } finally {
            writeLock.unlock();
        }
        return value;
    }

    private <V> V read(Callable<V> callable) {
        Lock readLock = lock.readLock();
        V value = null;
        try {
            readLock.lock();
            value = callable.call();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            readLock.unlock();
        }
        return value;
    }

    static <C extends AbstractConfig> void addIfAbsent(C config, Map<String, C> configsMap, boolean unique)
            throws IllegalStateException {

        if (config == null || configsMap == null) {
            return;
        }

        if (unique) { // check duplicate
            configsMap.values().forEach(c -> {
                // 进去
                checkDuplicate(c, config);
            });
        }

        // 进去
        String key = getId(config);
        C existedConfig = configsMap.get(key);
        if (existedConfig != null && !config.equals(existedConfig)) {
            if (logger.isWarnEnabled()) {
                String type = config.getClass().getSimpleName();
                // 日志
                logger.warn(String.format("Duplicate %s found, there already has one default %s or more than two %ss have the same id, " +
                        "you can try to give each %s a different id : %s", type, type, type, type, config));
            }
        } else {
            // 存到map
            configsMap.put(key, config);
        }
    }

    private static void checkDuplicate(AbstractConfig oldOne, AbstractConfig newOne) throws IllegalStateException {
        // todo need pr 疑问点，这里equals的比较应该没有!吧
        if (oldOne != null && !oldOne.equals(newOne)) {
            String configName = oldOne.getClass().getSimpleName();
            // 重复的话没啥特别操作，只是记录了警告日志
            logger.warn("Duplicate Config found for " + configName + ", you should use only one unique " + configName + " for one application.");
        }
    }

    private static Map newMap() {
        return new HashMap<>();
    }



    static <C extends AbstractConfig> String getId(C config) {
        String id = config.getId();
        // 逻辑看下，很简单
        return isNotEmpty(id) ? id : isDefaultConfig(config) ?
                config.getClass().getSimpleName() + "#" + DEFAULT_KEY : null; // eg:ProviderConfig#default
    }

    static <C extends AbstractConfig> boolean isDefaultConfig(C config) {
        // 调用config的isDefault方法 ， 进去
        Boolean isDefault = getProperty(config, "isDefault");
        // isDefault方法返回null（先前没有调用过setDefault，isDefault属性的默认值就是null），或者返回true ，那么结果就返回true
        return isDefault == null || TRUE.equals(isDefault);
    }

    static <C extends AbstractConfig> List<C> getDefaultConfigs(Map<String, C> configsMap) {
        // 遍历所有的value（AbstractConfig实例对象）
        return configsMap.values()
                .stream()
                // 选出是defaultConfig的，进去
                .filter(ConfigManager::isDefaultConfig)
                .collect(Collectors.toList());
    }
}
