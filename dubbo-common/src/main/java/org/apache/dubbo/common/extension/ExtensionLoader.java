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
package org.apache.dubbo.common.extension;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.context.Lifecycle;
import org.apache.dubbo.common.extension.support.ActivateComparator;
import org.apache.dubbo.common.extension.support.WrapperComparator;
import org.apache.dubbo.common.lang.Prioritized;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.Holder;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.sort;
import static java.util.ServiceLoader.load;
import static java.util.stream.StreamSupport.stream;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.REMOVE_VALUE_PREFIX;

/**
 * {@link org.apache.dubbo.rpc.model.ApplicationModel}, {@code DubboBootstrap} and this class are
 * at present designed to be singleton or static (by itself totally static or uses some static fields).
 * So the instances returned from them are of process or classloader scope. If you want to support
 * multiple dubbo servers in a single process, you may need to refactor these three classes.
 * <p>
 * Load dubbo extensions
 * <ul>
 * <li>auto inject dependency extension </li>
 * <li>auto wrap extension in wrapper </li>
 * <li>default extension is an adaptive instance</li>
 * </ul>
 *{@link org.apache.dubbo.rpc.model.ApplicationModel}、{@code DubboBootstrap} 和这个类目前被设计为单例或静态（本身完全静态或使用一些静态字段）。
 *   * 所以从它们返回的实例是进程或类加载器范围的。 如果你想在一个进程中支持多个dubbo服务器，你可能需要重构这三个类。
 *   * <p>
 *   * 加载dubbo扩展
 *   * <ul>
 *   * <li>自动注入依赖扩展</li>
 *   * <li>在包装器中自动包装扩展</li>
 *   * <li>默认扩展是一个自适应实例</li>
 *   * </ul>
 * @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/jar/jar.html#Service%20Provider">Service Provider in Java 5</a>
 * @see org.apache.dubbo.common.extension.SPI
 * @see org.apache.dubbo.common.extension.Adaptive
 * @see org.apache.dubbo.common.extension.Activate
 */
public class ExtensionLoader<T> {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*");

    // 扩展加载器集合，key为扩展接口，例如Protocol等
    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>(64);

    // 扩展实现集合，key为扩展实现类，value为扩展对象
    // 例如key为Class<DubboProtocol>，value为DubboProtocol类对象实例
    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>(64);

    // 扩展接口，例如Protocol等(带有@SPI注解的)
    private final Class<?> type;

    // 对象工厂，获得扩展实现的实例，用于injectExtension方法中将扩展实现类的实例注入到相关的依赖属性。
    // 比如StubProxyFactoryWrapper类中有Protocol protocol属性，就是通过set方法把Protocol的实现类实例赋值
    // 所有的SPI类（除ExtensionFactory之外）对应的ExtensionLoader实例的objectFactory属性的类型都是AdaptiveExtensionFactory类
    private final ExtensionFactory objectFactory;

    // 缓存的扩展名与扩展类映射(扩展文件k=v)，和cachedClasses的key和value对换。
    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<>();

    // 缓存的扩展实现类集合
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    // 扩展名与加有@Activate的自动激活类的映射
    private final Map<String, Object> cachedActivates = new ConcurrentHashMap<>();

    // 缓存的扩展对象集合，key为扩展名，value为扩展对象
    // 例如Protocol扩展，key为dubbo，value为DubboProcotol实现类对象（存在Holder中）
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();

    // 缓存的自适应( Adaptive )扩展对象，例如例如AdaptiveExtensionFactory类的对象
    private final Holder<Object> cachedAdaptiveInstance = new Holder<>();

    //缓存的自适应扩展对象的类，例如AdaptiveExtensionFactory类
    private volatile Class<?> cachedAdaptiveClass = null;

    // 缓存的默认扩展名，就是@SPI中设置的值
    private String cachedDefaultName;

    // 创建cachedAdaptiveInstance异常
    private volatile Throwable createAdaptiveInstanceError;

    //拓展Wrapper实现类集合
    private Set<Class<?>> cachedWrapperClasses;

    //拓展名与加载对应拓展类发生的异常的映射
    private Map<String, IllegalStateException> exceptions = new ConcurrentHashMap<>();

    // 进去
    private static volatile LoadingStrategy[] strategies = loadLoadingStrategies();

    public static void setLoadingStrategies(LoadingStrategy... strategies) {
        if (ArrayUtils.isNotEmpty(strategies)) {
            ExtensionLoader.strategies = strategies;
        }
    }

    /**
     * Load all {@link Prioritized prioritized} {@link LoadingStrategy Loading Strategies} via {@link ServiceLoader}
     *
     * @return non-null
     * @since 2.7.7
     */
    private static LoadingStrategy[] loadLoadingStrategies() {
        // load方法，调用的是ServiceLoader。进去断点看下
        // 看完后大概是这样的，是获取到META-INF/services/LoadingStrategy(全限定名)文件，然后文件io流读取每一行类的全路径，利用线程上下文加载器加载到jvm
        return stream(load(LoadingStrategy.class).spliterator(), false)
                .sorted()
                .toArray(LoadingStrategy[]::new);
    }

    /**
     * Get all {@link LoadingStrategy Loading Strategies}
     *
     * @return non-null
     * @see LoadingStrategy
     * @see Prioritized
     * @since 2.7.7
     */
    public static List<LoadingStrategy> getLoadingStrategies() {
        return asList(strategies);
    }

    // 私有的
    private ExtensionLoader(Class<?> type) {
        this.type = type;
        // ExtensionFactory接口看下。然后再次调用getExtensionLoader，为了防止无限递归，当type == ExtensionFactory.class赋值为null，结束递归

        // getAdaptiveExtension调用时机注意：创建非ExtensionFactory的其他loader的时候（即该构造方法），这里又会调用getExtensionLoader创建ExtensionFactory loader，
        // 这个 ExtensionFactory loader同时调用getAdaptiveExtension。注：不光是ExtensionFactory loader，还有其他的loader也会调用getAdaptiveExtension，比如ServiceConfig里面的PROTOCOL属性

        // 而这个getAdaptiveExtension方法作用就是获取type的自适应扩展子类（带有@Adaptive注解的）（注意type是带有@SPI注解的接口，比如这里的Protocol、ExtensionFactory）。进去
        // 所有的SPI类（除ExtensionFactory之外）对应的ExtensionLoader实例的objectFactory属性的类型都是AdaptiveExtensionFactory类
        objectFactory = (type == ExtensionFactory.class ? null : ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension());
    }

    private static <T> boolean withExtensionAnnotation(Class<T> type) {
        // isAnnotationPresent api
        return type.isAnnotationPresent(SPI.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        // 必须是接口
        if (!type.isInterface()) {
            throw new IllegalArgumentException("Extension type (" + type + ") is not an interface!");
        }
        // 必须有spi标记，进去
        if (!withExtensionAnnotation(type)) {
            throw new IllegalArgumentException("Extension type (" + type +
                    ") is not an extension, because it is NOT annotated with @" + SPI.class.getSimpleName() + "!");
        }

        // 从缓存中根据接口取对应的ExtensionLoader
        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            // type就是带有@SPI注解的接口，每一个type都有一个ExtensionLoader，ExtensionLoader构造器进去
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            // 取出来返回
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    // For testing purposes only 注意调用处DubboBootstrap
    public static void resetExtensionLoader(Class type) {
        ExtensionLoader loader = EXTENSION_LOADERS.get(type);
        if (loader != null) {
            // Remove all instances associated with this loader as well
            Map<String, Class<?>> classes = loader.getExtensionClasses();
            for (Map.Entry<String, Class<?>> entry : classes.entrySet()) {
                EXTENSION_INSTANCES.remove(entry.getValue());
            }
            classes.clear();
            EXTENSION_LOADERS.remove(type);
        }
    }

    //注意调用处DubboBootstrap 新版本
    public static void destroyAll() {
        EXTENSION_INSTANCES.forEach((_type, instance) -> {
            if (instance instanceof Lifecycle) {
                Lifecycle lifecycle = (Lifecycle) instance;
                try {
                    lifecycle.destroy();
                } catch (Exception e) {
                    logger.error("Error destroying extension " + lifecycle, e);
                }
            }
        });
    }

    private static ClassLoader findClassLoader() {
        // 进去
        return ClassUtils.getClassLoader(ExtensionLoader.class);
    }

    public String getExtensionName(T extensionInstance) {
        return getExtensionName(extensionInstance.getClass());
    }

    public String getExtensionName(Class<?> extensionClass) {
        getExtensionClasses();// load class
        return cachedNames.get(extensionClass);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, key, null)}
     *
     * @param url url
     * @param key url parameter key which used to get extension point names
     * @return extension list which are activated.
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String, String)
     */
    // 在所有的激活中，要使用key 指定的扩展
    public List<T> getActivateExtension(URL url, String key) {
        // 进去
        return getActivateExtension(url, key, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, values, null)}
     *
     * @param url    url
     * @param values extension point names
     * @return extension list which are activated
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String[], String)
     */
    // 在所有的激活中 values指定的扩展
    public List<T> getActivateExtension(URL url, String[] values) {
        return getActivateExtension(url, values, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, url.getParameter(key).split(","), null)}
     *
     * @param url   url
     * @param key   url parameter key which used to get extension point names
     * @param group group
     * @return extension list which are activated.
     * @see #getActivateExtension(org.apache.dubbo.common.URL, String[], String)
     */
    // 在所有的激活中，要指定的group 外加 使用key 指定的扩展
    public List<T> getActivateExtension(URL url, String key, String group) {
        // 从url获取参数值（扩展名，比如?ext=ext=order1,default）
        String value = url.getParameter(key);
        // 进去（value不为空的话，根据逗号分隔填充到数组，数组里是多个扩展名，即要/激活这些扩展名实例）
        return getActivateExtension(url, StringUtils.isEmpty(value) ? null : COMMA_SPLIT_PATTERN.split(value), group);
    }

    /**
     * Get activate extensions.
     *
     * @param url    url
     * @param values extension point names  扩展点名称，数组，多个扩展名
     * @param group  group
     * @return extension list which are activated
     * @see org.apache.dubbo.common.extension.Activate
     */

    // 其他的getActivateExtension最后其实都由下面方法实现
    public List<T> getActivateExtension(URL url, String[] values, String group) {
        List<T> activateExtensions = new ArrayList<>();
        List<String> names = values == null ? new ArrayList<>(0) : asList(values);
        if (!names.contains(REMOVE_VALUE_PREFIX + DEFAULT_KEY)) {
            getExtensionClasses();

            // ====================activateExtensions第1次填充处===============================

            // cachedActivates的赋值处注意（getExtensionClasses内部）
            for (Map.Entry<String, Object> entry : cachedActivates.entrySet()) {
                String name = entry.getKey();// 扩展名
                Object activate = entry.getValue();// @Activate注解对象

                String[] activateGroup, activateValue;

                if (activate instanceof Activate) {
                    // eg: @Activate(group = {"group1", "group2"},value={"v1","v2"})
                    activateGroup = ((Activate) activate).group();
                    activateValue = ((Activate) activate).value();
                } else if (activate instanceof com.alibaba.dubbo.common.extension.Activate) {
                    activateGroup = ((com.alibaba.dubbo.common.extension.Activate) activate).group();
                    activateValue = ((com.alibaba.dubbo.common.extension.Activate) activate).value();
                } else {
                    continue;
                }
                // 满足条件的话则激活，填充到activateExtensions，看下每个条件
                if (isMatchGroup(group, activateGroup)
                        && !names.contains(name) // 这里不匹配扩展名，后面的for会匹配（只校验和@Activate注解里的值相关的）
                        && !names.contains(REMOVE_VALUE_PREFIX + name)
                        // 用户传入的url是否含有activateValue数组的某个元素(含有一个即可)对应的参数，进去
                        && isActive(activateValue, url)) {
                    // getExtension进去，内部会反射new instance()
                    activateExtensions.add(getExtension(name));
                }
            }
            // @Activate(order=1..)可以配置order，做排序
            activateExtensions.sort(ActivateComparator.COMPARATOR);
        }

        // ====================activateExtensions第2次填充处===============================

        List<T> loadedExtensions = new ArrayList<>();
        // 遍历业务方需要的扩展名(url里面的，详见testLoadDefaultActivateExtension)
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            // name不是 - 开头的，且names不包含-name（一个扩展名被删除，前面会带有-）
            if (!name.startsWith(REMOVE_VALUE_PREFIX)
                    && !names.contains(REMOVE_VALUE_PREFIX + name)) {
                // name是否是默认值default
                if (DEFAULT_KEY.equals(name)) {
                    if (!loadedExtensions.isEmpty()) {
                        // 填充到开头（default扩展名不处理）
                        activateExtensions.addAll(0, loadedExtensions);
                        loadedExtensions.clear();
                    }
                } else {
                    // 直接获取该扩展名对应的扩展类实例
                    loadedExtensions.add(getExtension(name));
                }
            }
        }
        // 最后在填充一次（前面只有name是默认值的时候才会填充，万一都没有默认值）
        if (!loadedExtensions.isEmpty()) {
            activateExtensions.addAll(loadedExtensions);
        }
        return activateExtensions;
    }

    private boolean isMatchGroup(String group, String[] groups) {
        if (StringUtils.isEmpty(group)) {
            return true;
        }
        if (groups != null && groups.length > 0) {
            for (String g : groups) {
                if (group.equals(g)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isActive(String[] keys, URL url) {
        if (keys.length == 0) {
            return true;
        }
        for (String key : keys) {
            // @Active(value="key1:value1, key2:value2")
            String keyValue = null;
            if (key.contains(":")) {
                String[] arr = key.split(":");
                key = arr[0];// k
                keyValue = arr[1];// v
            }

            // 获取url的kv参数对
            for (Map.Entry<String, String> entry : url.getParameters().entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                // 比较url的kv和@Activate注解里的kv

                // k要么相等或者url的k以[@Activate注解里的kv的.+k]结尾
                if ((k.equals(key) || k.endsWith("." + key))
                        // value都相等，或者@Activate注解里的kv的v为null，但是url的kv的v不为空
                        && ((keyValue != null && keyValue.equals(v)) || (keyValue == null && ConfigUtils.isNotEmpty(v)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get extension's instance. Return <code>null</code> if extension is not found or is not initialized. Pls. note
     * that this method will not trigger extension load.
     * <p>
     * In order to trigger extension load, call {@link #getExtension(String)} instead.
     *
     * @see #getExtension(String)
     */
    @SuppressWarnings("unchecked")
    public T getLoadedExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Holder<Object> holder = getOrCreateHolder(name);
        return (T) holder.get();
    }

    private Holder<Object> getOrCreateHolder(String name) {
        // cachedClasses、cachedInstances都有缓存
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) { // 创建一个空holder而已
            cachedInstances.putIfAbsent(name, new Holder<>());
            holder = cachedInstances.get(name);
        }
        return holder;
    }

    /**
     * Return the list of extensions which are already loaded.
     * <p>
     * Usually {@link #getSupportedExtensions()} should be called in order to get all extensions.
     *
     * @see #getSupportedExtensions()
     */
    public Set<String> getLoadedExtensions() {
        return Collections.unmodifiableSet(new TreeSet<>(cachedInstances.keySet()));
    }

    public List<T> getLoadedExtensionInstances() {
        List<T> instances = new ArrayList<>();
        cachedInstances.values().forEach(holder -> instances.add((T) holder.get()));
        return instances;
    }

    public Object getLoadedAdaptiveExtensionInstances() {
        return cachedAdaptiveInstance.get();
    }

//    public T getPrioritizedExtensionInstance() {
//        Set<String> supported = getSupportedExtensions();
//
//        Set<T> instances = new HashSet<>();
//        Set<T> prioritized = new HashSet<>();
//        for (String s : supported) {
//
//        }
//
//    }

    /**
     * Find the extension with the given name. If the specified name is not found, then {@link IllegalStateException}
     * will be thrown.
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        // 进去
        return getExtension(name, true);
    }

    // 参数name就是spi文件的等号左边的key
    public T getExtension(String name, boolean wrap) {
        if (StringUtils.isEmpty(name)) {// 注意isEmpty和isBlank的区别，后者考虑到了空格（自己看下源码）
            throw new IllegalArgumentException("Extension name == null");
        }
        if ("true".equals(name)) {
            // 获取默认的拓展实现类
            return getDefaultExtension();
        }
        // Holder，顾名思义，用于持有目标对象 进去
        final Holder<Object> holder = getOrCreateHolder(name);
        Object instance = holder.get();
        // 双重检查
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    // 这里才是真正创建实例，进去
                    instance = createExtension(name, wrap);
                    // 设置实例到 holder 中
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * Get the extension by specified name if found, or {@link #getDefaultExtension() returns the default one}
     *
     * @param name the name of extension
     * @return non-null
     */
    public T getOrDefaultExtension(String name) {
        return containsExtension(name) ? getExtension(name) : getDefaultExtension();
    }

    /**
     * Return default extension, return <code>null</code> if it's not configured.
     */
    public T getDefaultExtension() {
        getExtensionClasses();
        // cachedDefaultName会在上面的方法得到赋值
        if (StringUtils.isBlank(cachedDefaultName) || "true".equals(cachedDefaultName)) {
            return null;
        }
        // 比如这里的值impl1(这个值来自SimpleExt类上面的@SPI注解里面的内容)
        return getExtension(cachedDefaultName);
    }

    public boolean hasExtension(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        Class<?> c = this.getExtensionClass(name);
        return c != null;
    }

    public Set<String> getSupportedExtensions() {
        // key就是spi文件里面=的左部分，如果没有=，那么就是xxxType的前缀xxx，value就是=右边的部分。进去
        Map<String, Class<?>> clazzes = getExtensionClasses();
        // 获取所有的key填充到treeSet返回。注意是不可修改的，如果调用方写操作会抛异常
        return Collections.unmodifiableSet(new TreeSet<>(clazzes.keySet()));
    }

    // 获取所有子类的实例
    public Set<T> getSupportedExtensionInstances() {
        List<T> instances = new LinkedList<>();
        // 获取所有spi文件内容的等号左边部分，进去
        Set<String> supportedExtensions = getSupportedExtensions();
        if (CollectionUtils.isNotEmpty(supportedExtensions)) {
            for (String name : supportedExtensions) {
                // 创建实例并填充到instances容器，getExtension 进去
                instances.add(getExtension(name));
            }
        }
        // Collections.sort根据优先级排序，第二个参数指定comparator
        sort(instances, Prioritized.COMPARATOR);
        // 转化为set
        return new LinkedHashSet<>(instances);
    }

    /**
     * Return default extension name, return <code>null</code> if not configured.
     */
    public String getDefaultExtensionName() {
        getExtensionClasses();
        return cachedDefaultName;
    }

    /**
     * Register new extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension with the same name has already been registered.
     */
    public void addExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement the Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        // 根据子类头上是否带有Adaptive注解，进不同的分支，目的是为了给对应的缓存容器存值
        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            // 重复添加name
            if (cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " already exists (Extension " + type + ")!");
            }

            // 这两个正好kv相反
            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
        } else {
            // cachedAdaptiveClass只能在不存在的情况下添加
            if (cachedAdaptiveClass != null) {
                throw new IllegalStateException("Adaptive Extension already exists (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
        }
    }

    /**
     * Replace the existing extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension to be placed doesn't exist
     * @deprecated not recommended any longer, and use only when test ----注意这句话
     */
    @Deprecated
    public void replaceExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + " doesn't implement Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + " can't be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            // 只能对已存在的扩展名进行(扩展类的)替换
            if (!cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " doesn't exist (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
            // 移除之前（扩展名为name对应的扩展类）的实例
            cachedInstances.remove(name);
        } else {
            if (cachedAdaptiveClass == null) {
                throw new IllegalStateException("Adaptive Extension doesn't exist (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
            // 和前面一样，移除旧的实例
            cachedAdaptiveInstance.set(null);
        }
    }

    // getAdaptiveExtension 方法是获取自适应拓展的入口方法
    // getAdaptiveExtension 方法首先会检查缓存，缓存未命中，则调用 createAdaptiveExtension 方法创建自适应拓展
    @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() {
        // 从缓存中获取自适应拓展，cachedAdaptiveInstance结构看下，内部的属性是volatile修饰的
        Object instance = cachedAdaptiveInstance.get();
        // 缓存未命中
        if (instance == null) {
            if (createAdaptiveInstanceError != null) {
                throw new IllegalStateException("Failed to create adaptive instance: " +
                        createAdaptiveInstanceError.toString(),
                        createAdaptiveInstanceError);
            }

            // sync锁
            synchronized (cachedAdaptiveInstance) {
                // 双重检查。惯用法，防止别的线程创建好了，所以进来先检查是否为null
                instance = cachedAdaptiveInstance.get();
                if (instance == null) {
                    try {
                        // 创建自适应拓展，type的subClass实例，进去
                        instance = createAdaptiveExtension();
                        // 设置自适应拓展到缓存中
                        cachedAdaptiveInstance.set(instance);
                    } catch (Throwable t) {
                        createAdaptiveInstanceError = t;
                        throw new IllegalStateException("Failed to create adaptive instance: " + t.toString(), t);
                    }
                }
            }
        }

        return (T) instance;
    }

    private IllegalStateException findException(String name) {
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (entry.getKey().toLowerCase().contains(name.toLowerCase())) {
                return entry.getValue();
            }
        }
        StringBuilder buf = new StringBuilder("No such extension " + type.getName() + " by name " + name);


        int i = 1;
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (i == 1) {
                buf.append(", possible causes: ");
            }

            buf.append("\r\n(");
            buf.append(i++);
            buf.append(") ");
            buf.append(entry.getKey());
            buf.append(":\r\n");
            buf.append(StringUtils.toString(entry.getValue()));
        }
        return new IllegalStateException(buf.toString());
    }

    //createExtension 方法的逻辑稍复杂一下，包含了如下的步骤：
    //
    //1 通过 getExtensionClasses 获取所有的拓展类
    //2 通过反射创建拓展对象
    //3 向拓展对象中注入依赖
    //4 将拓展对象包裹在相应的 Wrapper 对象中
    //以上步骤中，第一个步骤是加载拓展类的关键，第三和第四个步骤是 Dubbo IOC 与 AOP 的具体实现。
    @SuppressWarnings("unchecked")
    private T createExtension(String name, boolean wrap) {
        // 从配置文件中加载所有的拓展类，可得到“配置项名称”到“配置类”的映射关系表
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            // 如果找不到扩展名为name的则会进这个分支（比如 getExtensionLoader(SimpleExt.class).getExtension("XXX");），进去
            throw findException(name);
        }
        try {
            // 实例也有缓存即这里的EXTENSION_INSTANCES，对比还有cachedClass、cacheInstances
            // 区别于cacheInstances，其key是name(SPI文件等号左边的)
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                // 通过反射创建拓展对象实例，比如SpiExtensionFactory
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            // 向实例中注入依赖
            injectExtension(instance);


            // 拓展类对象实例是否需要包装
            if (wrap) {

                List<Class<?>> wrapperClassesList = new ArrayList<>();
                // 该cachedWrapperClasses会在加载文件资源发现类如果满足(loadClass方法内调用)isWrapperClass方法的时候进行填充（可以看下Ext5Wrapper1、Ext5Wrapper2）
                if (cachedWrapperClasses != null) {
                    wrapperClassesList.addAll(cachedWrapperClasses);
                    // 给wrapper排个序，COMPARATOR进去
                    wrapperClassesList.sort(WrapperComparator.COMPARATOR);
                    // 顺序翻转一下
                    Collections.reverse(wrapperClassesList);
                }

                if (CollectionUtils.isNotEmpty(wrapperClassesList)) {
                    // 循环创建 Wrapper 实例（多层包装、其实装饰者模式）
                    for (Class<?> wrapperClass : wrapperClassesList) {
                        Wrapper wrapper = wrapperClass.getAnnotation(Wrapper.class);
                        if (wrapper == null
                                || (ArrayUtils.contains(wrapper.matches(), name) && !ArrayUtils.contains(wrapper.mismatches(), name))) {
                            // 将当前 instance 作为参数传给 Wrapper 的构造方法，并通过反射创建 Wrapper 实例。
                            // 然后向 Wrapper 实例中注入依赖，最后将 Wrapper 实例再次赋值给 instance 变量
                            instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
                        }
                    }
                    // for循环结束Ext5Wrapper2里面含有Ext5Wrapper1成员，而Ext5Wrapper1里面含有实际的目标对象Ext5Impl1（详见test_getExtension_WithWrapper测试方法第一行）
                }
            }

            // 前面通过newInstance构造了之后，下面进入初始化，进去
            initExtension(instance);
            return instance;
        } catch (Throwable t) {
            throw new IllegalStateException("Extension instance (name: " + name + ", class: " +
                    type + ") couldn't be instantiated: " + t.getMessage(), t);
        }
    }

    private boolean containsExtension(String name) {
        return getExtensionClasses().containsKey(name);
    }

    // injectExtension方法就是dubbo实现的ioc思想。
    // Dubbo IOC 是通过 setter 方法注入依赖。Dubbo 首先会通过反射获取到实例的所有方法，然后再遍历方法列表，检测方法名是否具有 setter 方法特征。
    // 若有，则通过 ObjectFactory 获取依赖对象，最后通过反射调用 setter 方法将依赖设置到目标对象中
    private T injectExtension(T instance) {
        // 什么情况为null呢？就是那些XXXExtensionFactory的子类对应的ExtensionLoader
        if (objectFactory == null) {
            return instance;
        }

        try {
            // 遍历目标类的所有方法
            for (Method method : instance.getClass().getMethods()) {

                // 检测方法是否以 set 开头，且方法仅有一个参数，且方法访问级别为 public ,进去
                if (!isSetter(method)) {
                    continue;
                }

                // 如果方法上面带有该注解，那么不需要注入，比如InjectExtImpl的setSimpleExt1方法上面
                if (method.getAnnotation(DisableInject.class) != null) {
                    continue;
                }

                // 获取setXXX方法第一个参数类型
                Class<?> pt = method.getParameterTypes()[0];
                // 判断是不是八大基本数据类型、str类型、字符类型、data类型...是的话continue，因为只注入属于SPI扩展类相关的对象，
                // 比如InjectExtImpl的setGenericType方法的参数就是Object类型的。进去
                if (ReflectUtils.isPrimitives(pt)) {
                    continue;
                }

                try {
                    // 获取属性名称，比如InjectExtImpl类的simpleExt属性（和setSimpleExt方法），进去
                    String property = getSetterProperty(method);
                    // 从 ObjectFactory 中获取依赖对象，objectFactory的两个子类，这里的是AdaptiveExtensionFactory。
                    // 内部实际是调用getAdaptiveExtension(如果没有手工提供的@Adaptive子类，那么会自动生成，比如SimpleExt$Adaptive)，进去
                    Object object = objectFactory.getExtension(pt, property);
                    if (object != null) {
                        // 通过反射调用 setter 方法设置依赖
                        method.invoke(instance, object);
                    }
                    // 在上面代码中，objectFactory 变量的类型为 AdaptiveExtensionFactory，AdaptiveExtensionFactory 内部维护了一个
                    // ExtensionFactory 列表，用于存储其他类型的 ExtensionFactory。Dubbo 目前提供了两种 ExtensionFactory，
                    // 分别是 SpiExtensionFactory 和 SpringExtensionFactory。前者用于创建"自适应的拓展"（注意自适应，AdaptiveXX的），
                    // 后者是用于从 Spring 的 IOC 容器中获取所需的拓展。这两个类的类的代码不是很复杂，这里就不一一分析了。
                    //
                    // Dubbo IOC 目前仅支持 setter 方式注入，总的来说，逻辑比较简单易懂。
                } catch (Exception e) {
                    logger.error("Failed to inject via method " + method.getName()
                            + " of interface " + type.getName() + ": " + e.getMessage(), e);
                }

            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return instance;
    }

    private void initExtension(T instance) {
        if (instance instanceof Lifecycle) {
            Lifecycle lifecycle = (Lifecycle) instance;
            lifecycle.initialize();
        }
    }

    /**
     * get properties name for setter, for instance: setVersion, return "version"
     * <p>
     * return "", if setter name with length less than 3
     */
    private String getSetterProperty(Method method) {
        return method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";
    }

    /**
     * return true if and only if:
     * <p>
     * 1, public
     * <p>
     * 2, name starts with "set"
     * <p>
     * 3, only has one parameter
     */
    private boolean isSetter(Method method) {
        return method.getName().startsWith("set")
                && method.getParameterTypes().length == 1
                && Modifier.isPublic(method.getModifiers());
    }

    private Class<?> getExtensionClass(String name) {
        if (type == null) {
            throw new IllegalArgumentException("Extension type == null");
        }
        if (name == null) {
            throw new IllegalArgumentException("Extension name == null");
        }
        return getExtensionClasses().get(name);
    }

    private Map<String, Class<?>> getExtensionClasses() {
        // cachedClasses缓存所有的subClass.class，其实就是spi在META-INF文件内容的等号左右部分，只是value不是str，只是Class
        // eg:[{    key:spi,        value:org.apache.dubbo.common.extension.factory.SpiExtensionFactory}
        //     {    key:adaptive,   value:org.apache.dubbo.common.extension.factory.AdaptiveExtensionFactory}...]
        Map<String, Class<?>> classes = cachedClasses.get();
        // 双重检查
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    // 加载拓展类，返回的是一个map，进去
                    classes = loadExtensionClasses();
                    // 缓存起来
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;

        // 注：这里也是先检查缓存，若缓存未命中，则通过 synchronized 加锁。加锁后再次检查缓存，并判空。此时如果 classes 仍为 null，
        // 则通过 loadExtensionClasses 加载拓展类。
    }

    /**
     * synchronized in getExtensionClasses
     */
    // loadExtensionClasses 方法总共做了两件事情，一是对 SPI 注解进行解析，二是调用 loadDirectory 方法加载指定文件夹配置文件
    private Map<String, Class<?>> loadExtensionClasses() {

        // 缓存默认扩展类名称(就是SPI注解里面的值)，进去
        cacheDefaultExtensionName();

        // 待返回的结果，在下面的loadDirectory内部会得到填充
        Map<String, Class<?>> extensionClasses = new HashMap<>();

        // strategies属性的赋值处去看下，主要调用了loadLoadingStrategies
        // 主要加载了common模块的META-INF/services/org.apache.dubbo.common.extension.LoadingStrategy文件里面的三个类
        // LoadingStrategy默认三个实现类（去看下），每个类代表一个加载策略，策略的最大区别点是定义了不同的加载路径（META-INF下面的不同子目录）
        for (LoadingStrategy strategy : strategies) {
            // 核心！！！注意几个参数
            loadDirectory(extensionClasses, strategy.directory(), type.getName(), strategy.preferExtensionClassLoader(), strategy.overridden(), strategy.excludedPackages());
            // 再次加载，其他参数不变，只是将org.apache变成com.alibaba
            loadDirectory(extensionClasses, strategy.directory(), type.getName().replace("org.apache", "com.alibaba"), strategy.preferExtensionClassLoader(), strategy.overridden(), strategy.excludedPackages());
        }
        // 断点下，Protocol接口的子类有8个。可以搜下org.apache.dubbo.rpc.Protocol(有多个文件)。Compile接口有两个:jdk+javassit
        // 具体看下面大块注释
        return extensionClasses;
    }
    // ===========Protocol spi
    //"registry" -> {Class@1219} "class org.apache.dubbo.registry.integration.InterfaceCompatibleRegistryProtocol"
    //"rest"     -> {Class@1223} "class org.apache.dubbo.rpc.protocol.rest.RestProtocol"
    //"hessian"  -> {Class@1225} "class org.apache.dubbo.rpc.protocol.hessian.HessianProtocol"
    //"injvm"    -> {Class@1221} "class org.apache.dubbo.rpc.protocol.injvm.InjvmProtocol"
    //"dubbo"    -> {Class@1226} "class org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol"
    //"mock"     -> {Class@1232} "class org.apache.dubbo.rpc.support.MockProtocol"
    //"rmi"      -> {Class@1224} "class org.apache.dubbo.rpc.protocol.rmi.RmiProtocol"
    //"service-discovery-registry" -> {Class@1218} "class org.apache.dubbo.registry.client.RegistryProtocol"
    //===============Compiler spi
    //"jdk"      -> {Class@1465} "class org.apache.dubbo.common.compiler.support.JdkCompiler"
    //"javassist"-> {Class@1466} "class org.apache.dubbo.common.compiler.support.JavassistCompiler"

    /**
     * extract and cache default extension name if exists
     */
    private void cacheDefaultExtensionName() {
        // 获取 SPI 注解，这里的 type 变量是在调用 getExtensionLoader 方法时传入的
        // 这里的type比如ExtensionFactory接口、Protocol接口
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        if (defaultAnnotation == null) {
            return;
        }

        // Protocol 接口名上的SPI注解值为dubbo（里面的值就是默认值）
        String value = defaultAnnotation.value();
        if ((value = value.trim()).length() > 0) {
            // 对 SPI 注解内容进行切分
            String[] names = NAME_SEPARATOR.split(value);
            // 检测 SPI 注解内容是否合法，不合法则抛出异常
            if (names.length > 1) {
                // 注意日志,默认值只能有一个值，这里的默认也即默认的扩展实现类，比如Protocol接口的默认实现使用的就是DubboProtocol
                throw new IllegalStateException("More than 1 default extension name on extension " + type.getName()
                        + ": " + Arrays.toString(names));
            }
            // 设置默认名称，参考 getDefaultExtension 方法
            if (names.length == 1) {
                cachedDefaultName = names[0];// dubbo
            }
        }
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type) {
        loadDirectory(extensionClasses, dir, type, false, false);
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir, String type,
                               boolean extensionLoaderClassLoaderFirst, boolean overridden, String... excludedPackages) {
        // fileName = 文件夹路径 + type 全限定名  eg： META-INF/dubbo/internal/org.apache.dubbo.common.extension.ExtensionFactory
        String fileName = dir + type;
        try {
            Enumeration<java.net.URL> urls = null;

            // 获取加载器，肯定是线程上下文（默认是AppClassLoader）。spi就靠这个线程上下文，进去
            ClassLoader classLoader = findClassLoader();

            // try to load from ExtensionLoader's ClassLoader first  默认false
            if (extensionLoaderClassLoaderFirst) {
                ClassLoader extensionLoaderClassLoader = ExtensionLoader.class.getClassLoader();
                if (ClassLoader.getSystemClassLoader() != extensionLoaderClassLoader) {
                    urls = extensionLoaderClassLoader.getResources(fileName);
                }
            }

            if (urls == null || !urls.hasMoreElements()) {
                // 根据文件名加载所有的同名文件

                if (classLoader != null) {
                    // classLoader对象的实例方法，一般这个分支
                    urls = classLoader.getResources(fileName);
                } else {
                    // ClassLoader类的静态方法
                    urls = ClassLoader.getSystemResources(fileName);
                }
            }

            if (urls != null) {
                // 遍历每一个本地资源文件路径
                while (urls.hasMoreElements()) {
                    // eg:file:/Users/gy821075/IdeaProjects/dubbo/dubbo-common/target/classes/META-INF/dubbo/internal/org.apache.dubbo.common.extension.ExtensionFactory
                    // 这个在common包下
                    java.net.URL resourceURL = urls.nextElement();
                    // 注意几个参数。最后一个是null，第三个参数默认是false，在ServicesLoadingStrategy和DubboLoadingStrategy的实现是true。
                    // 加载资源 进去
                    loadResource(extensionClasses, classLoader, resourceURL, overridden, excludedPackages);
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", description file: " + fileName + ").", t);
        }
    }

    // loadResource 方法用于读取和解析配置文件，并通过反射加载类，最后调用 loadClass 方法进行其他操作
    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader,
                              java.net.URL resourceURL, boolean overridden, String... excludedPackages) {
        try {
            // 建立输入流，将字节流转化为了字符流。注意这里的用法try with resource https://www.cnblogs.com/itZhy/p/7636615.html
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), StandardCharsets.UTF_8))) {
                String line;
                // 读一行，eg adaptive=org.apache.dubbo.common.extension.factory.AdaptiveExtensionFactory
                // 可以看common模块META-INF的ExtensionFactory文件，那么这里会循环读三行
                while ((line = reader.readLine()) != null) {
                    // 定位 # 字符
                    final int ci = line.indexOf('#');
                    if (ci >= 0) {
                        // 截取 # 之前的字符串，# 之后的内容为注释，需要忽略（可以看org.apache.dubbo.common.extension.ext1.SimpleExt文件的内容，里面就有#注释，里面的第一行注释、最后一行前面的空格都是故意设计的，空格会被下面的trim截取调）
                        line = line.substring(0, ci);
                    }
                    line = line.trim();
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            // 以等于号 = 为界，截取键与值
                            int i = line.indexOf('=');
                            if (i > 0) {
                                // adaptive、spi
                                name = line.substring(0, i).trim();
                                // org.apache.dubbo.common.extension.factory.AdaptiveExtensionFactory  、SpiExtensionFactory
                                line = line.substring(i + 1).trim();
                            }
                            // isExcluded查看该类是否被排除(默认是null，不会排除该类的加载)，进去
                            if (line.length() > 0 && !isExcluded(line, excludedPackages)) {
                                // 第三个参数，加载line这个类（line是类的全限定名字符串），true表示加载之后需要初始化（加载、连接、初始化），指定的classLoader就是线程上下文加载器
                                // 注意extensionClasses容器现在一直还没数据，loadClass进去
                                loadClass(extensionClasses, resourceURL, Class.forName(line, true, classLoader), name, overridden);
                            }
                        } catch (Throwable t) {
                            IllegalStateException e = new IllegalStateException("Failed to load extension class (interface: " + type + ", class line: " + line + ") in " + resourceURL + ", cause: " + t.getMessage(), t);
                            exceptions.put(line, e);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", class file: " + resourceURL + ") in " + resourceURL, t);
        }
    }

    private boolean isExcluded(String className, String... excludedPackages) {
        if (excludedPackages != null) {
            for (String excludePackage : excludedPackages) {// 是否被排除，排除的不需要加载
                if (className.startsWith(excludePackage + ".")) {
                    return true;
                }
            }
        }
        return false;
    }

    // loadClass 方法用于主要用于操作extensionClasses缓存
    // loadClass 方法操作了不同的缓存，比如 cachedAdaptiveClass、cachedWrapperClasses 和 cachedNames 等等。除此之外，该方法没有其他什么逻辑了。
    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name,
                           boolean overridden) throws NoSuchMethodException {
        // 判断clazz是否是type的子类/子接口 eg:type为ExtensionFactory.class.getName(),clazz为AdaptiveExtensionFactory.class
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Error occurred when loading extension class (interface: " +
                    type + ", class line: " + clazz.getName() + "), class "
                    // subType日志
                    + clazz.getName() + " is not subtype of interface.");
        }
        // 类是否含有Adaptive注解（比如AdaptiveExtensionFactory）
        if (clazz.isAnnotationPresent(Adaptive.class)) {
            // 缓存该Adaptive子类，进去
            cacheAdaptiveClass(clazz, overridden);

        // 检测 clazz 是否是 Wrapper 类型，进去
        } else if (isWrapperClass(clazz)) {
            //  存储 clazz 到 cachedWrapperClasses 缓存中，进去
            cacheWrapperClass(clazz);

        // 程序进入此分支，表明 clazz 是一个普通的拓展类
        } else {
            // 大部分是这个，子类实现@Spi注解接口，类含有@Activate注解或者不含有任何注解。
            // 随便找个文件，比如org.apache.dubbo.common.threadpool.ThreadPool里面的实现类或者ActivateExt1Impl1实现类
            // 检测 clazz 是否有默认的构造方法，如果没有，则抛出异常
            clazz.getConstructor();
            // name就是文件里面的=左边，比如spi、adaptive
            if (StringUtils.isEmpty(name)) {
                // 如果name为空，那么就截取实现类的前缀并转为小写，比如FixedThreadPool就是fixed，进去
                // 这一点可以体现出我们在文件里面的内容不一定是xx=xxType这种方式，可以直接xxType-->这主要是兼容原生jdk的spi，
                // 因为原生spi的文件内容只有value（实现类全路径）没有key（扩展名），且在META-INF/services包下
                name = findAnnotationName(clazz);
                if (name.length() == 0) {
                    throw new IllegalStateException("No such extension name for the class " + clazz.getName() + " in the config " + resourceURL);
                }
            }

            // 正则校验name是否格式正确。
            String[] names = NAME_SEPARATOR.split(name);
            if (ArrayUtils.isNotEmpty(names)) {
                // 对比前面的，cacheAdaptiveClass、cacheWrapperClass，这里是cacheActivateClass且传入了两个参数进去
                cacheActivateClass(clazz, names[0]);
                for (String n : names) {
                    // 缓存起来eg:FixedThreadPool.class:fixed
                    cacheName(clazz, n);
                    // 填充到结果容器，进去
                    saveInExtensionClass(extensionClasses, clazz, n, overridden);
                }
            }
        }
    }
    /**
     * cache name
     */
    private void cacheName(Class<?> clazz, String name) {
        if (!cachedNames.containsKey(clazz)) {
            cachedNames.put(clazz, name);
        }
    }

    /**
     * put clazz in extensionClasses
     */
    private void saveInExtensionClass(Map<String, Class<?>> extensionClasses, Class<?> clazz, String name, boolean overridden) {
        Class<?> c = extensionClasses.get(name);
        if (c == null || overridden) {
            // 填充
            extensionClasses.put(name, clazz);
        } else if (c != clazz) {
            String duplicateMsg = "Duplicate extension " + type.getName() + " name " + name + " on " + c.getName() + " and " + clazz.getName();
            logger.error(duplicateMsg);
            throw new IllegalStateException(duplicateMsg);
        }
    }

    /**
     * cache Activate class which is annotated with <code>Activate</code>
     * <p>
     * for compatibility, also cache class with old alibaba Activate annotation
     */
    private void cacheActivateClass(Class<?> clazz, String name) {
        // 可以参考ActivateExt1以及对应的testLoadActivateExtension测试方法
        Activate activate = clazz.getAnnotation(Activate.class);
        if (activate != null) {
            // 缓存起来，Activate注解里面有不同的值
            cachedActivates.put(name, activate);
        } else {
            // 兼容旧版本的Activate注解（看方法名上面注释）
            // support com.alibaba.dubbo.common.extension.Activate
            com.alibaba.dubbo.common.extension.Activate oldActivate = clazz.getAnnotation(com.alibaba.dubbo.common.extension.Activate.class);
            if (oldActivate != null) {
                cachedActivates.put(name, oldActivate);
            }
        }
    }

    /**
     * cache Adaptive class which is annotated with <code>Adaptive</code>
     */
    private void cacheAdaptiveClass(Class<?> clazz, boolean overridden) {
        // 为null(没缓存过);或者不为null且overridden=true即允许覆盖（overridden取决于LoadingStrategy）
        // 这样确保一个带有@SPI注解的接口可能有多个子类带有@Adaptive注解，但是只有一个子类的class会赋值到这里（当然所有子类肯定被加载了）
        if (cachedAdaptiveClass == null || overridden) {
            // 缓存起来
            cachedAdaptiveClass = clazz;
        } else if (!cachedAdaptiveClass.equals(clazz)) {
            // 如果不允许覆盖，且cachedAdaptiveClass有多个，抛异常，看下面日志
            // 从这也能看出带有@SPI注解的接口只有overridden为true的时候才能有多个带有@Adaptive注解的子类
            throw new IllegalStateException("More than 1 adaptive class found: "
                    + cachedAdaptiveClass.getName()
                    + ", " + clazz.getName());
        }
    }

    /**
     * cache wrapper class
     * <p>
     * like: ProtocolFilterWrapper, ProtocolListenerWrapper
     */
    private void cacheWrapperClass(Class<?> clazz) {
        if (cachedWrapperClasses == null) {
            cachedWrapperClasses = new ConcurrentHashSet<>();
        }
        cachedWrapperClasses.add(clazz);
    }

    /**
     * test if clazz is a wrapper class
     * <p>
     * which has Constructor with given class type as its only argument
     */
    // 看上面注释
    private boolean isWrapperClass(Class<?> clazz) {
        try {
            // 查看类是否含有拷贝构造函数，如果有的话，那么这就是一个WrapperClass，比如看Ext5Wrapper1类
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            // 没有对应方法的话，会抛异常
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private String findAnnotationName(Class<?> clazz) {
        // 是否含有@Extension注解，一般都不会含有
        org.apache.dubbo.common.Extension extension = clazz.getAnnotation(org.apache.dubbo.common.Extension.class);
        if (extension != null) {
            // 有的话获取里面的值，eg：@Extension("impl1")
            return extension.value();
        }

        // 获取类名称
        String name = clazz.getSimpleName();
        // 类的名称是否以type的名称结尾，比如Adaptive/SpiExtensionFactory和ExtensionFactory
        if (name.endsWith(type.getSimpleName())) {
            // 获取前缀，比如Adaptive/Spi
            name = name.substring(0, name.length() - type.getSimpleName().length());
        }
        // 转小写
        return name.toLowerCase();
    }

    // createAdaptiveExtension 方法的代码比较少，但却包含了三个逻辑，分别如下：
    //
    //  1. 调用 getAdaptiveExtensionClass 方法获取自适应拓展 Class 对象
    //  2. 通过反射进行实例化
    //  3. 调用 injectExtension 方法向拓展实例中注入依赖
    //
    // 前两个逻辑比较好理解，第三个逻辑用于向自适应拓展对象中注入依赖。这个逻辑看似多余，但有存在的必要，这里简单说明一下。
    // 前面说过，Dubbo 中有两种类型的自适应拓展，一种是手工编码的（自己提供@Adaptive注解的子类），一种是自动生成的（内部字符串拼接源码，结合javassist compiler）。
    // 手工编码的自适应拓展中可能存在着一些依赖，而自动生成的 Adaptive 拓展则不会依赖其他类。这里调用 injectExtension 方法的目的是为手工编码的自适应拓展注入依赖，
    // 这一点需要大家注意一下。关于 injectExtension 方法，前文已经分析过了，这里不再赘述。接下来，分析 getAdaptiveExtensionClass 方法的逻辑。
    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        try {
            // getAdaptiveExtensionClass获取扩展类并加载到jvm，方法返回Class，进去
            // 然后newInstance，在调用injectExtension，ioc的方式注入依赖的扩展类，进去
            return injectExtension((T) getAdaptiveExtensionClass().newInstance());
        } catch (Exception e) {
            throw new IllegalStateException("Can't create adaptive extension " + type + ", cause: " + e.getMessage(), e);
        }
    }

    //getAdaptiveExtensionClass 方法同样包含了三个逻辑，如下：
    //
    //1. 调用 getExtensionClasses 获取所有的拓展类
    //2. 检查缓存，若缓存不为空，则返回缓存
    //3. 若缓存为空，则调用 createAdaptiveExtensionClass 创建自适应拓展类
    //
    // 这三个逻辑看起来平淡无奇，似乎没有多讲的必要。但是这些平淡无奇的代码中隐藏了着一些细节，需要说明一下。首先从第一个逻辑说起，
    // getExtensionClasses 这个方法用于获取某个接口的所有实现类。比如该方法可以获取 Protocol 接口的 DubboProtocol、HttpProtocol、
    // InjvmProtocol 等实现类。在获取实现类的过程中，如果某个实现类被 Adaptive 注解修饰了（手工编码的），那么该类就会被赋值给 cachedAdaptiveClass 变量。
    // 此时，上面步骤中的第二步条件成立（缓存不为空），直接返回 cachedAdaptiveClass 即可。如果所有的实现类均未被 Adaptive 注解修饰，
    // 那么执行第三步逻辑，创建自适应拓展类（自动生成的），即分析下createAdaptiveExtensionClass

    // 获取所有适配（有@Adaptive注解的）扩展的class（就是属性type的实现类）
    private Class<?> getAdaptiveExtensionClass() {
        // 核心！！！大概原理说下就是：根据多个LoadingStrategy加载策略，获取META-INF下的文件，读取里面的信息，获取多个类全限定名，
        // 利用线程上下文加载器（appClassLoader）加载到jvm。方法进去
        getExtensionClasses();
        // cachedAdaptiveClass（有可能）会在上面的方法得到赋值
        if (cachedAdaptiveClass != null) {
            // 这里比如就是 AdaptiveExtensionFactory.class（cachedAdaptiveClass的赋值处在getExtensionClasses最里面的addExtension方法调用）
            return cachedAdaptiveClass;
        }
        // ExtensionFactory接口会在上面的分支返回（因为该接口含有@Adaptive注解子类），Protocol接口会走下面的方法（不含有@Adaptive注解的子类）
        // 此时Protocol接口也有了"Adaptive"子类(实际是通过Javassist生成的，且类头上不会有@Adaptive注解)
        return cachedAdaptiveClass = createAdaptiveExtensionClass();
    }

    // createAdaptiveExtensionClass 方法用于生成自适应拓展类，该方法首先会生成自适应拓展类的源码，然后通过 Compiler 实例
    // Dubbo 默认使用 javassist 作为编译器编译源码，得到代理类 Class 实例。接下来，我们把重点放在代理类代码生成的逻辑上，其他逻辑大家自行分析。
    private Class<?> createAdaptiveExtensionClass() {
        // 构建自适应拓展代码
        // eg type=protocol、cachedDefaultName = dubbo(这个值来源于Protocol接口上的注解里的值)
        // generate内部会搞一个type的实现类，以源码str的方式
        String code = new AdaptiveClassCodeGenerator(type, cachedDefaultName).generate();
        ClassLoader classLoader = findClassLoader();
        // 获取compiler接口的实现类(只有两个jdk和javassist，默认使用 javassist 作为编译器)，将上面的code str编译成类
        // 调用的getAdaptiveExtension，这里返回的是AdaptiveCompile类实例
        org.apache.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        // 编译，方法返回Class。进去（AdaptiveCompile的compile）
        return compiler.compile(code, classLoader);
    }


    @Override
    public String toString() {
        return this.getClass().getName() + "[" + type.getName() + "]";
    }

}
