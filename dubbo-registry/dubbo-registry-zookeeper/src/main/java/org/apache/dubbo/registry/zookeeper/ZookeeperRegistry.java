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
package org.apache.dubbo.registry.zookeeper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLBuilder;
import org.apache.dubbo.common.URLStrParser;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.UrlUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.support.FailbackRegistry;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.zookeeper.ChildListener;
import org.apache.dubbo.remoting.zookeeper.StateListener;
import org.apache.dubbo.remoting.zookeeper.ZookeeperClient;
import org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter;
import org.apache.dubbo.rpc.RpcException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.apache.dubbo.common.constants.CommonConstants.ANY_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_SEPARATOR;
import static org.apache.dubbo.common.constants.CommonConstants.PROTOCOL_SEPARATOR_ENCODED;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.CONSUMERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DEFAULT_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.DYNAMIC_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;
import static org.apache.dubbo.common.constants.RegistryConstants.PROVIDERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.ROUTERS_CATEGORY;

/**
 * ZookeeperRegistry
 *
 */
public class ZookeeperRegistry extends FailbackRegistry {

    private final static Logger logger = LoggerFactory.getLogger(ZookeeperRegistry.class);

    private final static String DEFAULT_ROOT = "dubbo";

    private final String root;

    private final Set<String> anyServices = new ConcurrentHashSet<>();

    private final ConcurrentMap<URL, ConcurrentMap<NotifyListener, ChildListener>> zkListeners = new ConcurrentHashMap<>();

    private final ZookeeperClient zkClient;

    // 这里的url为registryURL
    public ZookeeperRegistry(URL url, ZookeeperTransporter zookeeperTransporter) {
        super(url);
        if (url.isAnyHost()) {
            throw new IllegalStateException("registry address == null");
        }
        // 获取组名，默认为 dubbo
        String group = url.getParameter(GROUP_KEY, DEFAULT_ROOT);
        if (!group.startsWith(PATH_SEPARATOR)) {
            // group = "/" + group
            group = PATH_SEPARATOR + group;
        }
        this.root = group;
        // 创建 Zookeeper 客户端，默认为 CuratorZookeeperTransporter
        zkClient = zookeeperTransporter.connect(url);
        // 添加状态监听器
        zkClient.addStateListener((state) -> {
            if (state == StateListener.RECONNECTED) {
                logger.warn("Trying to fetch the latest urls, in case there're provider changes during connection loss.\n" +
                        " Since ephemeral ZNode will not get deleted for a connection lose, " +
                        "there's no need to re-register url of this instance.");
                // 试图获取最新的url，以防在连接丢失期间provider发生变化,因为临时的ZNode不会因为连接丢失而被删除,不需要重新注册这个实例的url
                ZookeeperRegistry.this.fetchLatestAddresses();
            } else if (state == StateListener.NEW_SESSION_CREATED) {
                logger.warn("Trying to re-register urls and re-subscribe listeners of this instance to registry...");
                try {
                    // 上面日志，re-register +  re-subscribe 就在recover方法里面
                    ZookeeperRegistry.this.recover();
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            } else if (state == StateListener.SESSION_LOST) {
                logger.warn("Url of this instance will be deleted from registry soon. " +
                        "Dubbo client will try to re-register once a new session is created.");
            } else if (state == StateListener.SUSPENDED) {

            } else if (state == StateListener.CONNECTED) {

            }
        });
        // 在上面的代码代码中，我们重点关注 ZookeeperTransporter 的 connect 方法调用，这个方法用于创建 Zookeeper 客户端。创建好 Zookeeper
        // 客户端，意味着注册中心的创建过程就结束了。接下来，再来分析一下 Zookeeper 客户端的创建过程。前面说过，这里的 zookeeperTransporter
        // 类型为自适应拓展类，因此 connect 方法会在被调用时决定加载什么类型的 ZookeeperTransporter 拓展，默认为 CuratorZookeeperTransporter。
        // 下面我们到 CuratorZookeeperTransporter 中看一看
    }

    @Override
    public boolean isAvailable() {
        return zkClient.isConnected();
    }

    @Override
    public void destroy() {
        super.destroy();
        try {
            zkClient.close();
        } catch (Exception e) {
            logger.warn("Failed to close zookeeper client " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void doRegister(URL url) {
        try {
            // 通过 Zookeeper 客户端创建节点，节点路径由 toUrlPath 方法生成，路径格式如下:（注意这个结构非常重要，牢牢记住）
            //      /${group}/${serviceInterface}/providers/${url} 比如 /dubbo/org.apache.dubbo.DemoService/providers/dubbo%3A%2F%2F127.0.0.1......
            // toUrlPath 、create都进去，第二个参数根据url的dynamic参数值判定节点是否是临时节点（dynamic=true就是临时节点）
            zkClient.create(toUrlPath(url), url.getParameter(DYNAMIC_KEY, true));
            // 到这里仅仅是provider对应的节点创建好了 ，没有内容（但是节点path含有完整的url，相当于provider的信息都在path上）
        } catch (Throwable e) {
            throw new RpcException("Failed to register " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void doUnregister(URL url) {
        try {
            zkClient.delete(toUrlPath(url));
        } catch (Throwable e) {
            throw new RpcException("Failed to unregister " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void doSubscribe(final URL url, final NotifyListener listener) {// url是provider的信息，比如 provider://30.25.58.102:20880/....
        try {
            if (ANY_VALUE.equals(url.getServiceInterface())) { // "*".equals(x)
                String root = toRootPath();
                ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.computeIfAbsent(url, k -> new ConcurrentHashMap<>());
                ChildListener zkListener = listeners.computeIfAbsent(listener, k -> (parentPath, currentChilds) -> {
                    for (String child : currentChilds) {
                        child = URL.decode(child);
                        if (!anyServices.contains(child)) {
                            anyServices.add(child);
                            subscribe(url.setPath(child).addParameters(INTERFACE_KEY, child,
                                    Constants.CHECK_KEY, String.valueOf(false)), k);
                        }
                    }
                });
                zkClient.create(root, false);
                List<String> services = zkClient.addChildListener(root, zkListener);
                if (CollectionUtils.isNotEmpty(services)) {
                    for (String service : services) {
                        service = URL.decode(service);
                        anyServices.add(service);
                        subscribe(url.setPath(service).addParameters(INTERFACE_KEY, service,
                                Constants.CHECK_KEY, String.valueOf(false)), listener);
                    }
                }
            } else {
                List<URL> urls = new ArrayList<>();
                // 获取要监听的父节点 进去
                for (String path : toCategoriesPath(url)) {
                    // subscribe方法已经存储了url->Set<NotifyListener>的映射，这里又做了一次映射，不过是 url -> [{NotifyListener,ChildListener},...]的映射
                    // 之所以需要做映射的原因是CuratorWatcherImpl#process调用的是ChildListener#childChanged方法，直接交互的是ChildListener，
                    // 而不是我们业务方的NotifyListener。所以需要和ChildListener做一次映射，即节点变更后 调用 ChildListener#childChanged方法，
                    // 其内部调用notify(url, listener, urls);（这里的listener是NotifyListener）
                    ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.computeIfAbsent(url, k -> new ConcurrentHashMap<>());
                    // ChildListener接口去看下，其方法是两个参数无返回值，所以k->右边两个参数
                    ChildListener zkListener = listeners.computeIfAbsent(listener, k -> (parentPath, currentChilds) -> ZookeeperRegistry.this.notify(url, k, toUrlsWithEmpty(url, parentPath, currentChilds)));
                    // 创建持久节点，eg /dubbo/org.apache.dubbo.demo.DemoService/configurators
                    zkClient.create(path, false);
                    // 对节点添加监听，返回的path下的子节点
                    List<String> children = zkClient.addChildListener(path, zkListener);
                    if (children != null) {
                        // 进去
                        urls.addAll(toUrlsWithEmpty(url, path, children));
                    }
                }
                // 进去
                notify(url, listener, urls);
            }
        } catch (Throwable e) {
            throw new RpcException("Failed to subscribe " + url + " to zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    @Override
    public void doUnsubscribe(URL url, NotifyListener listener) {
        ConcurrentMap<NotifyListener, ChildListener> listeners = zkListeners.get(url);
        if (listeners != null) {
            ChildListener zkListener = listeners.get(listener);
            if (zkListener != null) {
                if (ANY_VALUE.equals(url.getServiceInterface())) {
                    String root = toRootPath();
                    zkClient.removeChildListener(root, zkListener);
                } else {
                    // 比如消费者就是
                    //result = {String[3]@5919}
                    // 0 = "/dubbo/samples.annotation.api.HelloService/providers"
                    // 1 = "/dubbo/samples.annotation.api.HelloService/configurators"
                    // 2 = "/dubbo/samples.annotation.api.HelloService/routers"

                    //提供者就是
                    //  /dubbo/samples.annotation.api.HelloService/configurators
                    for (String path : toCategoriesPath(url)) {
                        // 和前面 subscribe的 zkClient.addChildListener(path, zkListener); 对应
                        zkClient.removeChildListener(path, zkListener);
                    }
                }
            }
        }
    }

    // 这里是直接从zk获取最新的提供者列表，父类是从缓存中获取
    @Override
    public List<URL> lookup(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("lookup url == null");
        }
        try {
            List<String> providers = new ArrayList<>();
            for (String path : toCategoriesPath(url)) {
                List<String> children = zkClient.getChildren(path);
                if (children != null) {
                    providers.addAll(children);
                }
            }
            return toUrlsWithoutEmpty(url, providers);
        } catch (Throwable e) {
            throw new RpcException("Failed to lookup " + url + " from zookeeper " + getUrl() + ", cause: " + e.getMessage(), e);
        }
    }

    private String toRootDir() {
        if (root.equals(PATH_SEPARATOR)) {
            return root;
        }
        return root + PATH_SEPARATOR;
    }

    private String toRootPath() {
        return root;
    }

    private String toServicePath(URL url) {
        String name = url.getServiceInterface(); // dubbo/org.apache.dubbo.demo.DemoService
        if (ANY_VALUE.equals(name)) {
            return toRootPath();
        }
        return toRootDir() + URL.encode(name);// toRootDir()  = /duubo/
        // eg /dubbo/org.apache.dubbo.demo.DemoService
    }

    private String[] toCategoriesPath(URL url) {
        String[] categories;
        if (ANY_VALUE.equals(url.getParameter(CATEGORY_KEY))) {
            categories = new String[]{PROVIDERS_CATEGORY, CONSUMERS_CATEGORY, ROUTERS_CATEGORY, CONFIGURATORS_CATEGORY};
        } else {
            // DEFAULT_CATEGORY = "providers"  取category参数的值，比如url如果是RegistryProtocol的registry.subscribe(overrideSubscribeUrl..)一路传过来的，那么该参数值就是configurators
            categories = url.getParameter(CATEGORY_KEY, new String[]{DEFAULT_CATEGORY});
        }
        String[] paths = new String[categories.length];
        for (int i = 0; i < categories.length; i++) {
            // eg  /dubbo/org.apache.dubbo.demo.DemoService/configurators  -- >一会返回path的时候要创建这些节点的
            paths[i] = toServicePath(url) + PATH_SEPARATOR + categories[i];
        }

        return paths;
    }

    private String toCategoryPath(URL url) {
        // toServicePath进去
        return toServicePath(url) + PATH_SEPARATOR + url.getParameter(CATEGORY_KEY, DEFAULT_CATEGORY);// DEFAULT_CATEGORY= "provider"
    }

    private String toUrlPath(URL url) {
        // toCategoryPath进去
        return toCategoryPath(url) + PATH_SEPARATOR + URL.encode(url.toFullString());
    }

    private List<URL> toUrlsWithoutEmpty(URL consumer, List<String> providers) {
        List<URL> urls = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(providers)) {
            for (String provider : providers) {
                //     String PROTOCOL_SEPARATOR_ENCODED = URL.encode(PROTOCOL_SEPARATOR);
                // 这里为啥是包含URL.encode("://")呢？这是因为提供者注册到zk的url信息就是encoded的，详见该类的doRegister方法
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

    private List<URL> toUrlsWithEmpty(URL consumer, String path, List<String> providers) {
        List<URL> urls = toUrlsWithoutEmpty(consumer, providers);
        // todo need pr 下面可以替换为 CollectionUtils.isEmpty(urlList)
        if (urls == null || urls.isEmpty()) {
            int i = path.lastIndexOf(PATH_SEPARATOR);
            String category = i < 0 ? path : path.substring(i + 1);
            // 构建一个空协议，empty://consumerInfo?xxxx&category=yyy
            URL empty = URLBuilder.from(consumer)
                    .setProtocol(EMPTY_PROTOCOL)
                    .addParameter(CATEGORY_KEY, category)
                    .build();
            urls.add(empty);
        }
        return urls;
    }

    /**
     * When zookeeper connection recovered from a connection loss, it need to fetch the latest provider list.
     * re-register watcher is only a side effect and is not mandate.
     * zookeeper连接丢失后恢复，需要获取最新的提供商列表。
     * *重新注册观察者只是一个副作用，不是强制的。 订阅方法内部除了订阅本身的作用外，还会向zk取数据，并调用notify
     */

    private void fetchLatestAddresses() {
        // 为什么重新订阅就是所谓的 fetchLatestAddresses 呢 ？ 这是因为订阅内部会有获取订阅节点的子节点列表+notify的过程，订阅后会立马获取订阅节点下的childs，这些childs就是提供者列表
        // subscribe
        Map<URL, Set<NotifyListener>> recoverSubscribed = new HashMap<URL, Set<NotifyListener>>(getSubscribed());
        if (!recoverSubscribed.isEmpty()) {
            if (logger.isInfoEnabled()) {
                logger.info("Fetching the latest urls of " + recoverSubscribed.keySet());
            }
            for (Map.Entry<URL, Set<NotifyListener>> entry : recoverSubscribed.entrySet()) {
                URL url = entry.getKey();
                for (NotifyListener listener : entry.getValue()) {
                    addFailedSubscribed(url, listener);
                }
            }
        }
    }

}
