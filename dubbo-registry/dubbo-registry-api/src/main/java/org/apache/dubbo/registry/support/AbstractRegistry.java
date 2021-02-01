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
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.COMMA_SPLIT_PATTERN;
import static org.apache.dubbo.common.constants.CommonConstants.FILE_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.ACCEPTS_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.DEFAULT_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;
import static org.apache.dubbo.registry.Constants.REGISTRY_FILESAVE_SYNC_KEY;
import static org.apache.dubbo.registry.Constants.REGISTRY__LOCAL_FILE_CACHE_ENABLED;

/**
 * AbstractRegistry. (SPI, Prototype, ThreadSafe)
 */
// OK
public abstract class AbstractRegistry implements Registry {

    // URL address separator, used in file cache, service provider URL separation
    private static final char URL_SEPARATOR = ' ';
    // URL address separated regular expression for parsing the service provider URL list in the file cache
    // URL地址分隔的正则表达式，用于解析文件缓存中的服务提供商URL列表
    private static final String URL_SPLIT = "\\s+";
    // Max times to retry to save properties to local cache file
    private static final int MAX_RETRY_TIMES_SAVE_PROPERTIES = 3;
    // Log output
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    // Local disk cache, where the special key value.registries records the list of registry centers, and the others are the list of notified service providers
    // 本地磁盘缓存，其中特殊的键值。registries 记录注册中心列表，其他是被通知的服务提供商列表
    private final Properties properties = new Properties();
    // File cache timing writing
    private final ExecutorService registryCacheExecutor = Executors.newFixedThreadPool(1, new NamedThreadFactory("DubboSaveRegistryCache", true));
    // Is it synchronized to save the file
    private boolean syncSaveFile;
    private final AtomicLong lastCacheChanged = new AtomicLong();
    private final AtomicInteger savePropertiesRetryTimes = new AtomicInteger();
    private final Set<URL> registered = new ConcurrentHashSet<>();
    private final ConcurrentMap<URL, Set<NotifyListener>> subscribed = new ConcurrentHashMap<>();
    private final ConcurrentMap<URL, Map<String/*category*/, List<URL>>> notified = new ConcurrentHashMap<>();
    private URL registryUrl;
    // Local disk cache file
    private File file;

    protected void setUrl(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("registry url == null");
        }
        this.registryUrl = url;
    }

    // gx
    public AbstractRegistry(URL url) {
        // 设置 注册 url
        setUrl(url);
        // REGISTRY__LOCAL_FILE_CACHE_ENABLED = "file.cache"
        if (url.getParameter(REGISTRY__LOCAL_FILE_CACHE_ENABLED, true)) {
            // 是否同步保存到文件中  REGISTRY_FILESAVE_SYNC_KEY = "save.file"
            syncSaveFile = url.getParameter(REGISTRY_FILESAVE_SYNC_KEY, false);
            // 缺省文件名 用户家目录/.dubbo文件夹下面 “dubbo-registry-应用名（也就application）-注册中心ip:port.cache ” 文件名
            // eg /Users/gy821075/.dubbo/dubbo-registry-dubbo-demo-api-provider-127.0.0.1-2181.cache
            String defaultFilename = System.getProperty("user.home") + "/.dubbo/dubbo-registry-"
                    + url.getParameter(APPLICATION_KEY) + "-" + url.getAddress().replaceAll(":", "-")
                    + ".cache";
            // 获取注册中心缓存文件的位置，获取不到用上面缺省文件名  FILE_KEY =" file"
            String filename = url.getParameter(FILE_KEY, defaultFilename);
            File file = null;
            if (ConfigUtils.isNotEmpty(filename)) {
                file = new File(filename);
                if (!file.exists() && file.getParentFile() != null && !file.getParentFile().exists()) {
                    if (!file.getParentFile().mkdirs()) {
                        throw new IllegalArgumentException("Invalid registry cache file " + file + ", cause: Failed to create directory " + file.getParentFile() + "!");
                    }
                }
            }
            this.file = file;
            // When starting the subscription center,
            // we need to read the local cache file for future Registry fault tolerance processing.
            // 把文件的内容填充到properties，进去
            loadProperties();
            // 从注册中心url中找到backup属性值，这个backup属性值就是需要恢复的一些服务提供者url，再调用notify进行通知，将那些恢复的url通知给订阅方
            // 进去
            notify(url.getBackupUrls());
        }
    }

    protected static List<URL> filterEmpty(URL url, List<URL> urls) {
        // 就是对urls做一下空条件下的处理，如果不空，最后直接return就行
        if (CollectionUtils.isEmpty(urls)) {
            List<URL> result = new ArrayList<>(1);
            result.add(url.setProtocol(EMPTY_PROTOCOL));
            return result;
        }
        return urls;
    }
    // 实现了Node接口的getUrl方法
    @Override
    public URL getUrl() {
        return registryUrl;
    }



    // 获取 注册、订阅、通知相关的容器

    public Set<URL> getRegistered() {
        return Collections.unmodifiableSet(registered);
    }

    public Map<URL, Set<NotifyListener>> getSubscribed() {
        return Collections.unmodifiableMap(subscribed);
    }

    public Map<URL, Map<String, List<URL>>> getNotified() {
        return Collections.unmodifiableMap(notified);
    }

    public File getCacheFile() {
        return file;
    }

    public Properties getCacheProperties() {
        return properties;
    }

    public AtomicLong getLastCacheChanged() {
        return lastCacheChanged;
    }

    public void doSaveProperties(long version) {
        if (version < lastCacheChanged.get()) {
            return;
        }
        if (file == null) {
            return;
        }
        // Save
        try {
            // 创建一个锁文件
            File lockfile = new File(file.getAbsolutePath() + ".lock");
            if (!lockfile.exists()) {
                lockfile.createNewFile();
            }
            try (RandomAccessFile raf = new RandomAccessFile(lockfile, "rw");
                 FileChannel channel = raf.getChannel()) {
                // 加锁
                FileLock lock = channel.tryLock();
                if (lock == null) {
                    throw new IOException("Can not lock the registry cache file " + file.getAbsolutePath() + ", ignore and retry later, maybe multi java process use the file, please config: dubbo.registry.file=xxx.properties");
                }
                // Save
                try {
                    // 文件本身创建
                    if (!file.exists()) {
                        file.createNewFile();
                    }
                    try (FileOutputStream outputFile = new FileOutputStream(file)) {
                        // properties的数据写到文件
                        properties.store(outputFile, "Dubbo Registry Cache");
                    }
                } finally {
                    // 是否文件锁
                    lock.release();
                }
            }
        } catch (Throwable e) {
            // 异常的话，重试次数++
            savePropertiesRetryTimes.incrementAndGet();
            if (savePropertiesRetryTimes.get() >= MAX_RETRY_TIMES_SAVE_PROPERTIES) {
                logger.warn("Failed to save registry cache file after retrying " + MAX_RETRY_TIMES_SAVE_PROPERTIES + " times, cause: " + e.getMessage(), e);
                savePropertiesRetryTimes.set(0);
                return;
            }
            if (version < lastCacheChanged.get()) {
                savePropertiesRetryTimes.set(0);
                return;
            } else {
                //出现异常 就异步保存
                registryCacheExecutor.execute(new SaveProperties(lastCacheChanged.incrementAndGet()));
            }
            logger.warn("Failed to save registry cache file, will retry, cause: " + e.getMessage(), e);
        }
    }

    // gx easy
    private void loadProperties() {
        if (file != null && file.exists()) {
            InputStream in = null;
            try {
                in = new FileInputStream(file);
                properties.load(in);
                if (logger.isInfoEnabled()) {
                    logger.info("Load registry cache file " + file + ", data: " + properties);
                }
            } catch (Throwable e) {
                logger.warn("Failed to load registry cache file " + file, e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        logger.warn(e.getMessage(), e);
                    }
                }
            }
        }
    }

    // cache文件的内容eg
    //#Dubbo metadataReport Cache
    //#Wed Jan 06 19:34:34 CST 2021
    //org.apache.dubbo.demo.DemoService\:\:\:provider\:dubbo-demo-api-provider={"parameters"\:{"side"\:"provider","release"\:"","methods"\:"sayHello,sayHelloAsync","deprecated"\:"false","dubbo"\:"2.0.2","interface"\:"org.apache.dubbo.demo.DemoService","generic"\:"false","default"\:"true","metadata-type"\:"remote","application"\:"dubbo-demo-api-provider","dynamic"\:"true","anyhost"\:"true"},"canonicalName"\:"org.apache.dubbo.demo.DemoService","codeSource"\:"file\:/Users/gy821075/IdeaProjects/dubbo/dubbo-demo。。。。。。
    public List<URL> getCacheUrls(URL url) {
        //"com.test" -> "http://192.168.0.3:9090/registry?check=false&file=N/A&interface=com.test"
        //"org.apache.dubbo.demo.DemoService" -> "dubbo://192.168.0.1:2200?application=demo-consumer&category=consumer&check=false&dubbo=2.0.2&interface=org.apache.dubbo.demo.DemoService&methods=sayHello&pid=1676&qos.port=333333&side=consumer&timestamp=1609987359134 dubbo://192.168.0.2:2201?application=demo-consumer&category=consumer&check=false&dubbo=2.0.2&interface=org.apache.dubbo.demo.DemoService&methods=sayHello&pid=1676&qos.port=333333&side=consumer&timestamp=1609987359134 dubbo://192.168.0.3:2202?application=demo-consumer&category=consumer&check=false&dubbo=2.0.2&interface=org.apache.dubbo.demo.DemoService&methods=sayHello&pid=1676&qos.port=333333&side=consumer&timestamp=1609987359134"
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            if (StringUtils.isNotEmpty(key) && key.equals(url.getServiceKey())
                    && (Character.isLetter(key.charAt(0)) || key.charAt(0) == '_')
                    && StringUtils.isNotEmpty(value)) {
                String[] arr = value.trim().split(URL_SPLIT);
                List<URL> urls = new ArrayList<>();
                for (String u : arr) {
                    urls.add(URL.valueOf(u));
                }
                return urls;
            }
        }
        return null;
    }

    // 根据url查找对应url集合
    @Override
    public List<URL> lookup(URL url) {
        List<URL> result = new ArrayList<>();
        // 先从已经通知notified 缓存集合中获取分组url的map集合，如果分组url的map集合 不是空然后遍历该集合，如果protocol不是empty的就添加到结果集合中返回。
        Map<String, List<URL>> notifiedUrls = getNotified().get(url);
        if (CollectionUtils.isNotEmptyMap(notifiedUrls)) {
            for (List<URL> urls : notifiedUrls.values()) {
                for (URL u : urls) {
                    // protocol不是空的话
                    if (!EMPTY_PROTOCOL.equals(u.getProtocol())) {
                        result.add(u);
                    }
                }
            }
        } else {
            // 如果这个分组url的map集合是空，就实现一个listener，然后订阅该url ，如果有变动就会通知该listener的notify方法，该方法中就把
            // urls设置到reference中，调用reference获取urls，遍历urls，procotol不是empty的就添加到结果集合中返回

            final AtomicReference<List<URL>> reference = new AtomicReference<>();
            NotifyListener listener = reference::set;
            subscribe(url, listener); // Subscribe logic guarantees the first notify to return
            List<URL> urls = reference.get();
            if (CollectionUtils.isNotEmpty(urls)) {
                for (URL u : urls) {
                    if (!EMPTY_PROTOCOL.equals(u.getProtocol())) {
                        result.add(u);
                    }
                }
            }
        }
        return result;
    }

    // easy
    @Override
    public void register(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("register url == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Register: " + url);
        }
        registered.add(url);
    }

    // easy
    @Override
    public void unregister(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("unregister url == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Unregister: " + url);
        }
        registered.remove(url);
    }

    @Override
    public void subscribe(URL url, NotifyListener listener) {
        if (url == null) {
            throw new IllegalArgumentException("subscribe url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("subscribe listener == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Subscribe: " + url);
        } // 添加到容器，一个url可对应多个NotifyListener（url是provider的信息，比如 provider://30.25.58.102:20880/.... listener比如Registry$OverrideListener）
        Set<NotifyListener> listeners = subscribed.computeIfAbsent(url, n -> new ConcurrentHashSet<>());
        listeners.add(listener);
    }

    @Override
    public void unsubscribe(URL url, NotifyListener listener) {
        if (url == null) {
            throw new IllegalArgumentException("unsubscribe url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("unsubscribe listener == null");
        }
        if (logger.isInfoEnabled()) {
            logger.info("Unsubscribe: " + url);
        }
        Set<NotifyListener> listeners = subscribed.get(url);
        if (listeners != null) {
            listeners.remove(listener);
        }

        // do not forget remove notified
        notified.remove(url);
    }

    // 方法很easy，就是把注册和订阅的容器拿出来再放回去 该方法在子类重连接的时候会被用到）
    protected void recover() throws Exception {
        // register
        Set<URL> recoverRegistered = new HashSet<>(getRegistered());
        if (!recoverRegistered.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Recover register url " + recoverRegistered);
            }
            for (URL url : recoverRegistered) {
                register(url);
            }
        }
        // subscribe
        Map<URL, Set<NotifyListener>> recoverSubscribed = new HashMap<>(getSubscribed());
        if (!recoverSubscribed.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Recover subscribe url " + recoverSubscribed.keySet());
            }
            for (Map.Entry<URL, Set<NotifyListener>> entry : recoverSubscribed.entrySet()) {
                URL url = entry.getKey();
                for (NotifyListener listener : entry.getValue()) {
                    subscribe(url, listener);
                }
            }
        }
    }

    protected void notify(List<URL> urls) {
        if (CollectionUtils.isEmpty(urls)) {
            return;
        }

        for (Map.Entry<URL, Set<NotifyListener>> entry : getSubscribed().entrySet()) {
            URL url = entry.getKey();

            if (!UrlUtils.isMatch(url, urls.get(0))) {
                continue;
            }

            Set<NotifyListener> listeners = entry.getValue();
            if (listeners != null) {
                for (NotifyListener listener : listeners) {
                    try {
                        // 进去
                        notify(url, listener, filterEmpty(url, urls));
                    } catch (Throwable t) {
                        logger.error("Failed to notify registry event, urls: " + urls + ", cause: " + t.getMessage(), t);
                    }
                }
            }
        }
    }

    /**
     * Notify changes from the Provider side.
     *
     * @param url      consumer side url
     * @param listener listener
     * @param urls     provider latest urls
     */
    // 验证参数是否为空，接着遍历urls，根据category 来分类，然后从notified 已经通知的缓存中获取分类url信息，获取的结果是个map，
    // Map<String, List<URL>> key是分组 ，value就是在该组下面的url。接着就是遍历上面分组出来的那个result，塞到从缓存中获取的那个map中，
    // 接着调用 listener.notify方法,最后进行saveProperties。
    protected void notify(URL url, NotifyListener listener, List<URL> urls) {
        if (url == null) {
            throw new IllegalArgumentException("notify url == null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("notify listener == null");
        }
        if ((CollectionUtils.isEmpty(urls))
                && !ANY_VALUE.equals(url.getServiceInterface())) {
            logger.warn("Ignore empty notify urls for subscribe url " + url);
            return;
        }
        if (logger.isInfoEnabled()) {
            logger.info("Notify urls for subscribe url " + url + ", urls: " + urls);
        }
        // keep every provider's category.
        // result放了什么：key：category , value：为该category的所有子节点的url
        Map<String, List<URL>> result = new HashMap<>();
        for (URL u : urls) {
            // 判断url和u是否匹配,url是消费者的URL，urls是提供者的URL,主要判断一些属性值是否相同，例如group、version.....
            if (UrlUtils.isMatch(url, u)) {
                String category = u.getParameter(CATEGORY_KEY, DEFAULT_CATEGORY);// 取出category的值
                List<URL> categoryList = result.computeIfAbsent(category, k -> new ArrayList<>());
                categoryList.add(u);
            }
        }
        if (result.size() == 0) {
            return;
        }
        // 为当前消费者url创建一个map，而这个map存的内容和上面的result一模一样
        Map<String, List<URL>> categoryNotified = notified.computeIfAbsent(url, u -> new ConcurrentHashMap<>());
        for (Map.Entry<String, List<URL>> entry : result.entrySet()) {
            String category = entry.getKey();
            List<URL> categoryList = entry.getValue();
            // 其实就是把result灌入到categoryNotified
            categoryNotified.put(category, categoryList);
            // 通知
            listener.notify(categoryList);
            // We will update our cache file after each notification.
            // When our Registry has a subscribe failure due to network jitter, we can return at least the existing cache URL.
            // 我们将在每个通知后更新我们的缓存文件。
            // 当注册表由于网络抖动而订阅失败时，我们至少可以返回现有的缓存URL。
            // 进去
            saveProperties(url);
        }
    }

    // 先是根据url从已经通知缓存中获取对应的分类url集合，然后遍历拼接url.toFullString()，中间用空字符串隔开，然后将拼接的字符串塞进properties中，
    // 获取版本信息，判断syncSaveFile变量同步保存还是异步保存，同步的话就直接调用doSaveProperties方法进行保存，异步的话
    // 使用registryCacheExecutor线程池，提交任务的方式
    private void saveProperties(URL url) {
        if (file == null) {
            return;
        }

        try {
            StringBuilder buf = new StringBuilder();
            // 根据url从 已经通知集合中获取 分类 urls
            Map<String, List<URL>> categoryNotified = notified.get(url);
            if (categoryNotified != null) {
                // 遍历拼接 url
                for (List<URL> us : categoryNotified.values()) {
                    for (URL u : us) {
                        if (buf.length() > 0) {
                            // 使用空字符串隔开
                            buf.append(URL_SEPARATOR);
                        }
                        buf.append(u.toFullString());
                    }
                }
            }
            // key 接口全类名  value urls.toFullString
            properties.setProperty(url.getServiceKey(), buf.toString());
            // 版本号就是递增计数器
            long version = lastCacheChanged.incrementAndGet();
            // 同步保存文件
            if (syncSaveFile) {
                // 进去
                doSaveProperties(version);
            } else {
                // 异步...搞一个线程
                registryCacheExecutor.execute(new SaveProperties(version));
            }
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
    }
    // 实现了Node接口的destroy方法
    @Override
    public void destroy() {
        if (logger.isInfoEnabled()) {
            logger.info("Destroy registry:" + getUrl());
        }
        Set<URL> destroyRegistered = new HashSet<>(getRegistered());
        if (!destroyRegistered.isEmpty()) {
            for (URL url : new HashSet<>(getRegistered())) {
                if (url.getParameter(DYNAMIC_KEY, true)) {
                    try {
                        // unregister
                        unregister(url);
                        if (logger.isInfoEnabled()) {
                            logger.info("Destroy unregister url " + url);
                        }
                    } catch (Throwable t) {
                        logger.warn("Failed to unregister url " + url + " to registry " + getUrl() + " on destroy, cause: " + t.getMessage(), t);
                    }
                }
            }
        }
        Map<URL, Set<NotifyListener>> destroySubscribed = new HashMap<>(getSubscribed());
        if (!destroySubscribed.isEmpty()) {
            for (Map.Entry<URL, Set<NotifyListener>> entry : destroySubscribed.entrySet()) {
                URL url = entry.getKey();
                for (NotifyListener listener : entry.getValue()) {
                    try {
                        // unsubscribe
                        unsubscribe(url, listener);
                        if (logger.isInfoEnabled()) {
                            logger.info("Destroy unsubscribe url " + url);
                        }
                    } catch (Throwable t) {
                        logger.warn("Failed to unsubscribe url " + url + " to registry " + getUrl() + " on destroy, cause: " + t.getMessage(), t);
                    }
                }
            }
        }
        // 调用父类的方法，把this这个registry从父类的容器中移除
        AbstractRegistryFactory.removeDestroyedRegistry(this);
    }

    protected boolean acceptable(URL urlToRegistry) {
        // ACCEPTS_KEY = "accepts"
        String pattern = registryUrl.getParameter(ACCEPTS_KEY);

        // 为空的话，允许/接受
        if (StringUtils.isEmpty(pattern)) {
            return true;
        }

        // 不为空，按照,分割，看看协议类型是否支持
        return Arrays.stream(COMMA_SPLIT_PATTERN.split(pattern))
                .anyMatch(p -> p.equalsIgnoreCase(urlToRegistry.getProtocol()));
    }

    @Override
    public String toString() {
        return getUrl().toString();
    }

    // easy
    private class SaveProperties implements Runnable {
        private long version;

        private SaveProperties(long version) {
            this.version = version;
        }

        @Override
        public void run() {
            doSaveProperties(version);
        }
    }

}
