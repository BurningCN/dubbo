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
import org.apache.dubbo.common.URLStrParser;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.url.component.DubboServiceAddressURL;
import org.apache.dubbo.common.url.component.ServiceAddressURL;
import org.apache.dubbo.common.url.component.URLAddress;
import org.apache.dubbo.common.url.component.URLParam;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.registry.NotifyListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.URLStrParser.ENCODED_AND_MARK;
import static org.apache.dubbo.common.URLStrParser.ENCODED_PID_KEY;
import static org.apache.dubbo.common.URLStrParser.ENCODED_QUESTION_MARK;
import static org.apache.dubbo.common.URLStrParser.ENCODED_TIMESTAMP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CACHE_CLEAR_TASK_INTERVAL;
import static org.apache.dubbo.common.constants.CommonConstants.CACHE_CLEAR_WAITING_THRESHOLD;
import static org.apache.dubbo.common.constants.CommonConstants.CHECK_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_SEPARATOR;
import static org.apache.dubbo.common.constants.CommonConstants.PROTOCOL_SEPARATOR_ENCODED;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;
import static org.apache.dubbo.common.constants.RegistryConstants.PROVIDERS_CATEGORY;
import static org.apache.dubbo.common.url.component.DubboServiceAddressURL.PROVIDER_FIRST_KEYS;

/**
 * Useful for registries who's sdk returns raw string as provider instance, for example, zookeeper and etcd.
 * 对于 sdk 返回原始字符串作为提供者实例的注册中心很有用，例如，zookeeper 和 etcd。
 */
// doc3.0/浅析 Dubbo 3.0 中对 URL 的优化.docx
public abstract class CacheableFailbackRegistry extends FailbackRegistry {
    private static final Logger logger = LoggerFactory.getLogger(CacheableFailbackRegistry.class);
    private static String[] VARIABLE_KEYS = new String[]{ENCODED_TIMESTAMP_KEY, ENCODED_PID_KEY};

    // 可以看到在 CacheableFailbackRegistry 缓存中，我们新增了 3 个缓存属性 stringAddress，stringParam 和 stringUrls。我们来针对
    // 上文中的 2 个具体的场景来对这 3 个属性做具体的解析。
    // 1、某个 Consumer 依赖大量的 Provider，并且其中某个 Provider 因为网络等原因频繁上下线：为了优化这个场景，我们主要是用了 stringUrls
    // 这个属性，我们先来看看对应的代码片段。

    protected final static Map<String, URLAddress> stringAddress = new ConcurrentHashMap<>();
    protected final static Map<String, URLParam> stringParam = new ConcurrentHashMap<>();
    protected final Map<URL, Map<String, ServiceAddressURL>> stringUrls = new HashMap<>();

    private static final ScheduledExecutorService cacheRemovalScheduler;
    private static final int cacheRemovalTaskIntervalInMillis;
    private static final int cacheClearWaitingThresholdInMillis;
    private final static Map<ServiceAddressURL, Long> waitForRemove = new ConcurrentHashMap<>();
    private static final Semaphore semaphore = new Semaphore(1);

    private final Map<String, String> extraParameters;


    static {
        ExecutorRepository executorRepository = ExtensionLoader.getExtensionLoader(ExecutorRepository.class).getDefaultExtension();
        cacheRemovalScheduler = executorRepository.nextScheduledExecutor();
        // 默认2分钟
        cacheRemovalTaskIntervalInMillis = getIntConfig(CACHE_CLEAR_TASK_INTERVAL, 2 * 60 * 1000);
        // 默认5分钟
        cacheClearWaitingThresholdInMillis = getIntConfig(CACHE_CLEAR_WAITING_THRESHOLD, 5 * 60 * 1000);
    }

    public CacheableFailbackRegistry(URL url) {
        super(url);
        extraParameters = new HashMap<>(8);
        // 就这一处存放了值 跟踪下调用处 主要就是控制在订阅的时候没有服务提供者信息的时候是否抛异常
        extraParameters.put(CHECK_KEY, String.valueOf(false));
    }

    protected static int getIntConfig(String key, int def) {
        String str = ConfigurationUtils.getProperty(key);
        int result = def;
        if (StringUtils.isNotEmpty(str)) {
            try {
                // parseInt内部含有NumberFormatException异常声明，但是这里我们可以吧try-catch去掉，因为这是运行时异常，不需要必须加上，当然我们也可以加上
                result = Integer.parseInt(str);
            } catch (NumberFormatException e) {
                logger.warn("Invalid registry properties configuration key " + key + ", value " + str);
            }
        }
        return result;
    }

    @Override
    public void doUnsubscribe(URL url, NotifyListener listener) {
        this.evictURLCache(url);
    }

    protected void evictURLCache(URL url) {
        // 从三个缓存容器中清空，stringUrls是直接清空，后两个容器是在延时任务中执行的
        Map<String, ServiceAddressURL> oldURLs = stringUrls.remove(url);
        try {
            if (oldURLs != null && oldURLs.size() > 0) {
                logger.info("Evicting urls for service " + url.getServiceKey() + ", size " + oldURLs.size());
                Long currentTimestamp = System.currentTimeMillis();
                for (Map.Entry<String, ServiceAddressURL> entry : oldURLs.entrySet()) {
                    waitForRemove.put(entry.getValue(), currentTimestamp);
                }
                if (CollectionUtils.isNotEmptyMap(waitForRemove)) {
                    // 限制并发数
                    if (semaphore.tryAcquire()) {
                        cacheRemovalScheduler.schedule(new RemovalTask(), cacheRemovalTaskIntervalInMillis, TimeUnit.MILLISECONDS);
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to evict url for " + url.getServiceKey(), e);
        }
    }
    /**
     * @param consumer consumer的URL对象
     * @param providers 注册中心推送的providers下的地址列表
     */
    protected List<URL> toUrlsWithoutEmpty(URL consumer, Collection<String> providers) {
        // 先查出缓存中consumer目前对应的providers
        // keep old urls
        Map<String, ServiceAddressURL> oldURLs = stringUrls.get(consumer);
        // create new urls
        Map<String, ServiceAddressURL> newURLs;
        // 移除几个参数
        URL copyOfConsumer = removeParamsFromConsumer(consumer);
        if (oldURLs == null) {
            // 如果缓存中没有providers，则直接创建（没办法，没有命中缓存，只能挨个createURL）
            newURLs = new HashMap<>();
            for (String rawProvider : providers) {
                // 脱去一些参数
                rawProvider = stripOffVariableKeys(rawProvider);
                ServiceAddressURL cachedURL = createURL(rawProvider, copyOfConsumer, getExtraParameters());
                if (cachedURL == null) {
                    logger.warn("Invalid address, failed to parse into URL " + rawProvider);
                    continue;
                }
                newURLs.put(rawProvider, cachedURL);
            }
        } else {
            newURLs = new HashMap<>((int) (oldURLs.size() / .75 + 1));
            // maybe only default , or "env" + default
            for (String rawProvider : providers) {
                rawProvider = stripOffVariableKeys(rawProvider);
                ServiceAddressURL cachedURL = oldURLs.remove(rawProvider);
                if (cachedURL == null) {
                    cachedURL = createURL(rawProvider, copyOfConsumer, getExtraParameters());
                    if (cachedURL == null) {
                        logger.warn("Invalid address, failed to parse into URL " + rawProvider);
                        continue;
                    }
                }
                // provider存在则直接放入新的列表中（命中缓存，省去了createURL的过程）
                newURLs.put(rawProvider, cachedURL);
            }
        }

        evictURLCache(consumer);
        // 更新缓存
        stringUrls.put(consumer, newURLs);

        // 我们看到当新的 providers 列表推送过来时，如果 URLParam 和 URLAddress 完全无变更的话，会直接省略 createURL() 步骤，
        // 从 stringUrls 中直接获取缓存的值，此处便能省略很多无用的 URL 创建过程，大大减少了 CPU 和内存的消耗。
        return new ArrayList<>(newURLs.values());
    }

    protected List<URL> toUrlsWithEmpty(URL consumer, String path, Collection<String> providers) {
        List<URL> urls = new ArrayList<>(1);
        boolean isProviderPath = path.endsWith(PROVIDERS_CATEGORY);
        if (isProviderPath) {
            if (CollectionUtils.isNotEmpty(providers)) {
                urls = toUrlsWithoutEmpty(consumer, providers);
            } else {
                // clear cache on empty notification: unsubscribe or provider offline
                evictURLCache(consumer);
            }
        } else {
            if (CollectionUtils.isNotEmpty(providers)) {
                urls = toConfiguratorsWithoutEmpty(consumer, providers);
            }
        }

        if (urls.isEmpty()) {
            int i = path.lastIndexOf(PATH_SEPARATOR);
            String category = i < 0 ? path : path.substring(i + 1);
            URL empty = URLBuilder.from(consumer)
                    .setProtocol(EMPTY_PROTOCOL)
                    .addParameter(CATEGORY_KEY, category)
                    .build();
            urls.add(empty);
        }

        return urls;
    }

    protected ServiceAddressURL createURL(String rawProvider, URL consumerURL, Map<String, String> extraParameters) {
        boolean encoded = true;
        // use encoded value directly to avoid URLDecoder.decode allocation.
        // 直接使用encoded的URL来避免URLDecoder.decode的消耗
        int paramStartIdx = rawProvider.indexOf(ENCODED_QUESTION_MARK);
        if (paramStartIdx == -1) {// if ENCODED_QUESTION_MARK does not shown, mark as not encoded.
            encoded = false;
        }
        // 将rawProvider根据paramStartIdx分成两个部分
        String[] parts = URLStrParser.parseRawURLToArrays(rawProvider, paramStartIdx);
        if (parts.length <= 1) {
            logger.warn("Received url without any parameters " + rawProvider);
            return DubboServiceAddressURL.valueOf(rawProvider, consumerURL);
        }

        String rawAddress = parts[0];
        String rawParams = parts[1];
        boolean isEncoded = encoded;
        // 再次利用了下两个缓存，如果命中也会省去URLAddress.parse和URLParam.parse的解析消耗
        URLAddress address = stringAddress.computeIfAbsent(rawAddress, k -> URLAddress.parse(k, getDefaultURLProtocol(), isEncoded));
        address.setTimestamp(System.currentTimeMillis());

        URLParam param = stringParam.computeIfAbsent(rawParams, k -> URLParam.parse(k, isEncoded, extraParameters));
        param.setTimestamp(System.currentTimeMillis());

        ServiceAddressURL cachedURL = createServiceURL(address, param, consumerURL);
        if (isMatch(consumerURL, cachedURL)) {
            return cachedURL;
        }
        return null;
    }


    protected ServiceAddressURL createServiceURL(URLAddress address, URLParam param, URL consumerURL) {
        return new DubboServiceAddressURL(address, param, consumerURL, null);
    }

    protected URL removeParamsFromConsumer(URL consumer) {
        return consumer.removeParameters(PROVIDER_FIRST_KEYS);
    }

    private String stripOffVariableKeys(String rawProvider) {
        //  timestamp%3D 、 pid%3D
        String[] keys = getVariableKeys();
        if (keys == null || keys.length == 0) {
            return rawProvider;
        }

        // 移除上两个参数对
        for (String key : keys) {
            int idxStart = rawProvider.indexOf(key);
            if (idxStart == -1) {
                continue;
            }
            int idxEnd = rawProvider.indexOf(ENCODED_AND_MARK, idxStart);
            String part1 = rawProvider.substring(0, idxStart);
            if (idxEnd == -1) {
                rawProvider = part1;
            } else {
                String part2 = rawProvider.substring(idxEnd + ENCODED_AND_MARK.length());
                rawProvider = part1 + part2;
            }
        }

        if (rawProvider.endsWith(ENCODED_AND_MARK)) {
            rawProvider = rawProvider.substring(0, rawProvider.length() - ENCODED_AND_MARK.length());
        }
        if (rawProvider.endsWith(ENCODED_QUESTION_MARK)) {
            rawProvider = rawProvider.substring(0, rawProvider.length() - ENCODED_QUESTION_MARK.length());
        }

        return rawProvider;
    }

    private List<URL> toConfiguratorsWithoutEmpty(URL consumer, Collection<String> configurators) {
        List<URL> urls = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(configurators)) {
            for (String provider : configurators) {
                if (provider.contains(PROTOCOL_SEPARATOR_ENCODED)) {
                    URL url = URLStrParser.parseEncodedStr(provider);
                    if (UrlUtils.isMatch(consumer, url)) {
                        urls.add(url);
                    }
                }
            }
        }
        return urls;
    }

    protected Map<String, String> getExtraParameters() {
        return extraParameters;
    }

    protected String[] getVariableKeys() {
        return VARIABLE_KEYS;
    }

    protected String getDefaultURLProtocol() {
        return DUBBO;
    }

    /**
     * This method is for unit test to see if the RemovalTask has completed or not.<br />
     * <strong>Please do not call this method in other places.</strong>
     */
    @Deprecated
    protected Semaphore getSemaphore() {
        return semaphore;
    }

    protected abstract boolean isMatch(URL subscribeUrl, URL providerUrl);


    private static class RemovalTask implements Runnable {
        @Override
        public void run() {
            logger.info("Clearing cached URLs, waiting to clear size " + waitForRemove.size());
            int clearCount = 0;
            try {
                Iterator<Map.Entry<ServiceAddressURL, Long>> it = waitForRemove.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<ServiceAddressURL, Long> entry = it.next();
                    ServiceAddressURL removeURL = entry.getKey();
                    long removeTime = entry.getValue();
                    long current = System.currentTimeMillis();

                    if (current - removeTime >= cacheClearWaitingThresholdInMillis) {
                        URLAddress urlAddress = removeURL.getUrlAddress();
                        URLParam urlParam = removeURL.getUrlParam();

                        if (current - urlAddress.getTimestamp() >= cacheClearWaitingThresholdInMillis) {
                            stringAddress.remove(urlAddress.getRawAddress());
                        }
                        if (current - urlParam.getTimestamp() >= cacheClearWaitingThresholdInMillis) {
                            stringParam.remove(urlParam.getRawParam());
                        }
                        it.remove();
                        clearCount++;
                    }
                }
            } catch (Throwable t) {
                logger.error("Error occurred when clearing cached URLs", t);
            } finally {
                // 释放信号量
                semaphore.release();
            }
            logger.info("Clear cached URLs, size " + clearCount);

            // 前面刚清空一批之后，又来了新的
            if (CollectionUtils.isNotEmptyMap(waitForRemove)) {
                // move to next schedule
                if (semaphore.tryAcquire()) {
                    cacheRemovalScheduler.schedule(new RemovalTask(), cacheRemovalTaskIntervalInMillis, TimeUnit.MILLISECONDS);
                }
            }
        }
    }
}
