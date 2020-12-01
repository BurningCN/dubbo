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
 *
 * @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/jar/jar.html#Service%20Provider">Service Provider in Java 5</a>
 * @see org.apache.dubbo.common.extension.SPI
 * @see org.apache.dubbo.common.extension.Adaptive
 * @see org.apache.dubbo.common.extension.Activate
 */
public class ExtensionLoader<T> {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*");

    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<>(64);

    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<>(64);

    private final Class<?> type;

    private final ExtensionFactory objectFactory;

    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<>();

    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<>();

    private final Map<String, Object> cachedActivates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<>();
    private final Holder<Object> cachedAdaptiveInstance = new Holder<>();
    private volatile Class<?> cachedAdaptiveClass = null;
    private String cachedDefaultName;
    private volatile Throwable createAdaptiveInstanceError;

    private Set<Class<?>> cachedWrapperClasses;

    private Map<String, IllegalStateException> exceptions = new ConcurrentHashMap<>();
    // todo
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
        // 看完后大概是这样的，是获取到META-INF/services下的文件，然后文件读处类的全路径利用线程上下文加载器加载到jvm
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

    private ExtensionLoader(Class<?> type) {
        this.type = type;
        // ExtensionFactory接口看下。然后再次调用getExtensionLoader，为了防止无限递归，当type == ExtensionFactory.class赋值为null，结束递归

        // getAdaptiveExtension调用时机注意：创建非ExtensionFactory的其他loader的时候（即该构造方法），这里又会调用getExtensionLoader创建ExtensionFactory loader，
        // 这个 ExtensionFactory loader同时调用getAdaptiveExtension。注：不光是ExtensionFactory loader，还有其他的loader也会调用getAdaptiveExtension，比如ServiceConfig里面的PROTOCOL属性

        // 而这个getAdaptiveExtension方法作用就是根据SPI机制获取type的各个subClass并加载到jvm（注意type是带有@SPI注解的接口，比如这里的Protocol、ExtensionFactory）。进去
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

    // For testing purposes only
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
    public List<T> getActivateExtension(URL url, String key) {
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
    public List<T> getActivateExtension(URL url, String key, String group) {
        String value = url.getParameter(key);
        return getActivateExtension(url, StringUtils.isEmpty(value) ? null : COMMA_SPLIT_PATTERN.split(value), group);
    }

    /**
     * Get activate extensions.
     *
     * @param url    url
     * @param values extension point names
     * @param group  group
     * @return extension list which are activated
     * @see org.apache.dubbo.common.extension.Activate
     */
    public List<T> getActivateExtension(URL url, String[] values, String group) {
        List<T> activateExtensions = new ArrayList<>();
        List<String> names = values == null ? new ArrayList<>(0) : asList(values);
        if (!names.contains(REMOVE_VALUE_PREFIX + DEFAULT_KEY)) {
            getExtensionClasses();
            for (Map.Entry<String, Object> entry : cachedActivates.entrySet()) {
                String name = entry.getKey();
                Object activate = entry.getValue();

                String[] activateGroup, activateValue;

                if (activate instanceof Activate) {
                    activateGroup = ((Activate) activate).group();
                    activateValue = ((Activate) activate).value();
                } else if (activate instanceof com.alibaba.dubbo.common.extension.Activate) {
                    activateGroup = ((com.alibaba.dubbo.common.extension.Activate) activate).group();
                    activateValue = ((com.alibaba.dubbo.common.extension.Activate) activate).value();
                } else {
                    continue;
                }
                if (isMatchGroup(group, activateGroup)
                        && !names.contains(name)
                        && !names.contains(REMOVE_VALUE_PREFIX + name)
                        && isActive(activateValue, url)) {
                    activateExtensions.add(getExtension(name));
                }
            }
            activateExtensions.sort(ActivateComparator.COMPARATOR);
        }
        List<T> loadedExtensions = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (!name.startsWith(REMOVE_VALUE_PREFIX)
                    && !names.contains(REMOVE_VALUE_PREFIX + name)) {
                if (DEFAULT_KEY.equals(name)) {
                    if (!loadedExtensions.isEmpty()) {
                        activateExtensions.addAll(0, loadedExtensions);
                        loadedExtensions.clear();
                    }
                } else {
                    loadedExtensions.add(getExtension(name));
                }
            }
        }
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
                key = arr[0];
                keyValue = arr[1];
            }

            for (Map.Entry<String, String> entry : url.getParameters().entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if ((k.equals(key) || k.endsWith("." + key))
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
        // cachedClasses\cachedInstances都有缓存
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

    public T getExtension(String name, boolean wrap) {
        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Extension name == null");
        }
        if ("true".equals(name)) {
            // todo待分析
            return getDefaultExtension();
        }
        // 进去
        final Holder<Object> holder = getOrCreateHolder(name);
        Object instance = holder.get();
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                // 这里才是真正创建实例，进去
                if (instance == null) {
                    instance = createExtension(name, wrap);
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
        if (StringUtils.isBlank(cachedDefaultName) || "true".equals(cachedDefaultName)) {
            return null;
        }
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

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " already exists (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
        } else {
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
     * @deprecated not recommended any longer, and use only when test
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
            if (!cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " doesn't exist (Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
            cachedInstances.remove(name);
        } else {
            if (cachedAdaptiveClass == null) {
                throw new IllegalStateException("Adaptive Extension doesn't exist (Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
            cachedAdaptiveInstance.set(null);
        }
    }

    @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() {
        // cachedAdaptiveInstance结构看下，内部的属性是volatile修饰的
        Object instance = cachedAdaptiveInstance.get();
        if (instance == null) {
            if (createAdaptiveInstanceError != null) {
                throw new IllegalStateException("Failed to create adaptive instance: " +
                        createAdaptiveInstanceError.toString(),
                        createAdaptiveInstanceError);
            }

            // sync锁
            synchronized (cachedAdaptiveInstance) {
                // 惯用法，防止别的线程创建好了，所以进来先检查是否为null
                instance = cachedAdaptiveInstance.get();
                if (instance == null) {
                    try {
                        // 创建subClass的实例，进去
                        instance = createAdaptiveExtension();
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

    @SuppressWarnings("unchecked")
    private T createExtension(String name, boolean wrap) {
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null) {
            throw findException(name);
        }
        try {
            // 实例也有缓存即这里的EXTENSION_INSTANCES，对比还有cachedClass、cacheInstances
            // 区别于cacheInstances，其key是name(SPI文件等号左边的)
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                // 创建实例，比如SpiExtensionFactory
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            // 注射实例
            injectExtension(instance);


            // 是否需要包装
            if (wrap) {

                List<Class<?>> wrapperClassesList = new ArrayList<>();
                if (cachedWrapperClasses != null) {
                    wrapperClassesList.addAll(cachedWrapperClasses);
                    wrapperClassesList.sort(WrapperComparator.COMPARATOR);
                    Collections.reverse(wrapperClassesList);
                }

                if (CollectionUtils.isNotEmpty(wrapperClassesList)) {
                    for (Class<?> wrapperClass : wrapperClassesList) {
                        Wrapper wrapper = wrapperClass.getAnnotation(Wrapper.class);
                        if (wrapper == null
                                || (ArrayUtils.contains(wrapper.matches(), name) && !ArrayUtils.contains(wrapper.mismatches(), name))) {
                            instance = injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
                        }
                    }
                }
            }

            // 进去
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

    private T injectExtension(T instance) {
        // 什么情况为null呢？就是那些XXXExtensionFactory的子类对应的ExtensionLoader
        if (objectFactory == null) {
            return instance;
        }

        try {
            for (Method method : instance.getClass().getMethods()) {
                if (!isSetter(method)) {
                    continue;
                }
                /**
                 * Check {@link DisableInject} to see if we need auto injection for this property
                 */
                if (method.getAnnotation(DisableInject.class) != null) {
                    continue;
                }
                // 获取setXXX方法第一个参数类型
                Class<?> pt = method.getParameterTypes()[0];
                // 判断是不是八大基本数据类型、str类型，进去
                if (ReflectUtils.isPrimitives(pt)) {
                    continue;
                }

                try {
                    // 获取属性名称
                    String property = getSetterProperty(method);
                    // eg，pt = ZookeeperTransporter.class,property = ZookeeperTransporter ，getExtension获取扩展类，进去
                    // objectFactory的两个子类
                    Object object = objectFactory.getExtension(pt, property);
                    if (object != null) {
                        // 执行set方法方法
                        method.invoke(instance, object);
                    }
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
        // eg:[{key:spi,value:org.apache.dubbo.common.extension.factory.SpiExtensionFactory}
        //     {key:adaptive,value:org.apache.dubbo.common.extension.factory.AdaptiveExtensionFactory}...]
        Map<String, Class<?>> classes = cachedClasses.get();
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    // 返回的是一个map，进去
                    classes = loadExtensionClasses();
                    // 缓存起来
                    cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    /**
     * synchronized in getExtensionClasses
     */
    private Map<String, Class<?>> loadExtensionClasses() {

        // 缓存默认扩展类名称，进去
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
    //"registry" -> {Class@1219} "class org.apache.dubbo.registry.integration.InterfaceCompatibleRegistryProtocol"
    //"rest" -> {Class@1223} "class org.apache.dubbo.rpc.protocol.rest.RestProtocol"
    //"hessian" -> {Class@1225} "class org.apache.dubbo.rpc.protocol.hessian.HessianProtocol"
    //"injvm" -> {Class@1221} "class org.apache.dubbo.rpc.protocol.injvm.InjvmProtocol"
    //"service-discovery-registry" -> {Class@1218} "class org.apache.dubbo.registry.client.RegistryProtocol"
    //"dubbo" -> {Class@1226} "class org.apache.dubbo.rpc.protocol.dubbo.DubboProtocol"
    //"mock" -> {Class@1232} "class org.apache.dubbo.rpc.support.MockProtocol"
    //"rmi" -> {Class@1224} "class org.apache.dubbo.rpc.protocol.rmi.RmiProtocol"
    //
    //"jdk" -> {Class@1465} "class org.apache.dubbo.common.compiler.support.JdkCompiler"
    //"javassist" -> {Class@1466} "class org.apache.dubbo.common.compiler.support.JavassistCompiler"

    /**
     * extract and cache default extension name if exists
     */
    private void cacheDefaultExtensionName() {
        // 这里的type比如ExtensionFactory接口、Protocol接口
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        if (defaultAnnotation == null) {
            return;
        }

        String value = defaultAnnotation.value();// protocol的注解值为dubbo
        if ((value = value.trim()).length() > 0) {
            String[] names = NAME_SEPARATOR.split(value);
            if (names.length > 1) {// 只能有一个值
                throw new IllegalStateException("More than 1 default extension name on extension " + type.getName()
                        + ": " + Arrays.toString(names));
            }
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
        // eg： META-INF/dubbo/internal/org.apache.dubbo.common.extension.ExtensionFactory
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
                if (classLoader != null) {
                    // classLoader对象的实例方法
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
                    // 进去
                    loadResource(extensionClasses, classLoader, resourceURL, overridden, excludedPackages);
                }
            }
        } catch (Throwable t) {
            logger.error("Exception occurred when loading extension class (interface: " +
                    type + ", description file: " + fileName + ").", t);
        }
    }

    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader,
                              java.net.URL resourceURL, boolean overridden, String... excludedPackages) {
        try {
            // 建立输入流，将字节流转化为了字符流
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), StandardCharsets.UTF_8))) {
                String line;
                // 读一行，eg adaptive=org.apache.dubbo.common.extension.factory.AdaptiveExtensionFactory
                // 可以看common模块META-INF的ExtensionFactory文件，那么这里会循环读三行
                while ((line = reader.readLine()) != null) {
                    final int ci = line.indexOf('#');
                    if (ci >= 0) {
                        line = line.substring(0, ci);
                    }
                    line = line.trim();
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            int i = line.indexOf('=');// 等号切割
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

    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name,
                           boolean overridden) throws NoSuchMethodException {
        // 判断clazz是否是type的子类/子接口 eg:type为ExtensionFactory.class.getName(),clazz为AdaptiveExtensionFactory.class
        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Error occurred when loading extension class (interface: " +
                    type + ", class line: " + clazz.getName() + "), class "
                    + clazz.getName() + " is not subtype of interface.");
        }
        // 类是否含有Adaptive注解（比如AdaptiveExtensionFactory）
        if (clazz.isAnnotationPresent(Adaptive.class)) {
            // 缓存该Adaptive子类，进去
            cacheAdaptiveClass(clazz, overridden);

        // 是否是wrapper的，进去
        } else if (isWrapperClass(clazz)) {
            // 进去
            cacheWrapperClass(clazz);
        } else {
            // 大部分是这个，子类实现@Spi注解接口，类含有@Active注解或者不含有任何注解。
            // 随便找个文件，比如org.apache.dubbo.common.threadpool.ThreadPool里面的实现类或者ActivateExt1Impl1实现类
            clazz.getConstructor();
            // name就是文件里面的=左边，比如spi、adaptive
            if (StringUtils.isEmpty(name)) {
                // 如果name为空，那么就截取实现类的前缀并转为小写，比如FixedThreadPool就是fixed，进去
                // 这一点可以体现出我们在文件里面的内容不一定是xx=xxType这种方式，可以直接xxType
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
        // 自己全局搜下@Active注解
        Activate activate = clazz.getAnnotation(Activate.class);
        if (activate != null) {
            // 缓存起来，activate注解里面有不同的值
            cachedActivates.put(name, activate);
        } else {
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
    private boolean isWrapperClass(Class<?> clazz) {
        try {
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private String findAnnotationName(Class<?> clazz) {
        // 是否含有@Extension注解，一般都不会含有
        org.apache.dubbo.common.Extension extension = clazz.getAnnotation(org.apache.dubbo.common.Extension.class);
        if (extension != null) {
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

    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        try {
            // getAdaptiveExtensionClass获取扩展类并加载到jvm，方法返回Class，进去
            // 然后newInstance，在调用injectExtension，进去
            return injectExtension((T) getAdaptiveExtensionClass().newInstance());
        } catch (Exception e) {
            throw new IllegalStateException("Can't create adaptive extension " + type + ", cause: " + e.getMessage(), e);
        }
    }

    // 获取所有适配扩展的class（就是属性type的实现类）
    private Class<?> getAdaptiveExtensionClass() {
        // 核心！！！大概原理说下就是：根据多个LoadingStrategy加载策略，获取META-INF下的文件，读取里面的信息，获取多个类全限定名，
        // 利用线程上下文加载器（appClassLoader）加载到jvm。方法进去
        getExtensionClasses();
        if (cachedAdaptiveClass != null) {
            // 这里比如就是 AdaptiveExtensionFactory.class（cachedAdaptiveClass的赋值处在getExtensionClasses最里面的addExtension方法调用）
            return cachedAdaptiveClass;
        }
        // ExtensionFactory接口会在上面的分支返回（因为该接口含有@Adaptive注解子类），Protocol接口会走下面的方法（不有@Adaptive注解子类）
        // 此时Protocol接口也有了"Adaptive"子类(实际是通过Javassist生成的，且类头上不会有@Adaptive注解)
        return cachedAdaptiveClass = createAdaptiveExtensionClass();
    }

    private Class<?> createAdaptiveExtensionClass() {
        // eg type=protocol、cachedDefaultName = dubbo(这个值来源于Protocol接口上的注解里的值)
        // generat内部会搞一个type的实现类，以源码str的方式
        String code = new AdaptiveClassCodeGenerator(type, cachedDefaultName).generate();
        ClassLoader classLoader = findClassLoader();
        // 获取compiler接口的实现类(只有两个jdk和javassist)，将上面的code str编译成类
        org.apache.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(org.apache.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        // 编译，方法返回Class。进去
        return compiler.compile(code, classLoader);
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + type.getName() + "]";
    }

}
