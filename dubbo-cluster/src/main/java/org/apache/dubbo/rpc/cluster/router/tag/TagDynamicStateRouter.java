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
package org.apache.dubbo.rpc.cluster.router.tag;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigChangeType;
import org.apache.dubbo.common.config.configcenter.ConfigChangedEvent;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.RouterChain;
import org.apache.dubbo.rpc.cluster.router.state.AbstractStateRouter;
import org.apache.dubbo.rpc.cluster.router.state.BitList;
import org.apache.dubbo.rpc.cluster.router.state.RouterCache;
import org.apache.dubbo.rpc.cluster.router.tag.model.TagRouterRule;
import org.apache.dubbo.rpc.cluster.router.tag.model.TagRuleParser;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.TAG_KEY;
import static org.apache.dubbo.rpc.Constants.FORCE_USE_TAG;

/**
 * TagDynamicStateRouter, "application.tag-router"
 */
// 和 static的区别就是实现了 ConfigurationListener
public class TagDynamicStateRouter extends AbstractStateRouter implements ConfigurationListener {
    public static final String NAME = "TAG_ROUTER";
    private static final int TAG_ROUTER_DEFAULT_PRIORITY = 100;
    private static final Logger logger = LoggerFactory.getLogger(TagDynamicStateRouter.class);
    private static final String RULE_SUFFIX = ".tag-router";
    private static final String NO_TAG = "noTag";

    private TagRouterRule tagRouterRule;
    private String application;

    public TagDynamicStateRouter(URL url, RouterChain chain) {
        super(url, chain);
        this.priority = TAG_ROUTER_DEFAULT_PRIORITY;
    }

    @Override
    public synchronized void process(ConfigChangedEvent event) {
        if (logger.isDebugEnabled()) {
            logger.debug("Notification of tag rule, change type is: " + event.getChangeType() + ", raw rule is:\n " +
                event.getContent());
        }

        try {
            if (event.getChangeType().equals(ConfigChangeType.DELETED)) {
                this.tagRouterRule = null;
            } else {
                this.tagRouterRule = TagRuleParser.parse(event.getContent());
            }
        } catch (Exception e) {
            logger.error("Failed to parse the raw tag router rule and it will not take effect, please check if the " +
                "rule matches with the template, the raw rule is:\n ", e);
        }
    }

    // todo 没必要 和父类都一样
    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public <T> BitList<Invoker<T>> route(BitList<Invoker<T>> invokers, RouterCache<T> cache, URL url,
        Invocation invocation) throws RpcException {


        // 地址的元数据信息，注意set处，其实拿到之后就是调用了一些取属性的方法，如下的 isForce
        final TagRouterRule tagRouterRuleCopy = (TagRouterRule)cache.getAddrMetadata();

        String tag = StringUtils.isEmpty(invocation.getAttachment(TAG_KEY)) ? url.getParameter(TAG_KEY) :
            invocation.getAttachment(TAG_KEY);

        ConcurrentMap<String, BitList<Invoker<T>>> addrPool = cache.getAddrPool();

        if (StringUtils.isEmpty(tag)) {
            return invokers.intersect(addrPool.get(NO_TAG), invokers.getUnmodifiableList());
        } else {
            BitList<Invoker<T>> result = addrPool.get(tag);

            if (CollectionUtils.isNotEmpty(result) || (tagRouterRuleCopy != null && tagRouterRuleCopy.isForce())
                || isForceUseTag(invocation)) {
                return invokers.intersect(result, invokers.getUnmodifiableList());
            } else {
                invocation.setAttachment(TAG_KEY, NO_TAG);
                return invokers;
            }
        }
    }

    private boolean isForceUseTag(Invocation invocation) {
        return Boolean.valueOf(invocation.getAttachment(FORCE_USE_TAG, url.getParameter(FORCE_USE_TAG, "false")));
    }

    @Override
    public boolean isRuntime() {
        return tagRouterRule != null && tagRouterRule.isRuntime();
    }

    @Override
    public boolean isEnable() {
        return true;
    }

    @Override
    public boolean isForce() {
        return tagRouterRule != null && tagRouterRule.isForce();
    }

    @Override
    public String getName() {
        return "TagDynamic";
    }

    @Override
    public boolean shouldRePool() {
        return false;
    }

    // 被routeChain调用有效果，被内部方法调用没有用
    @Override
    public <T> RouterCache<T> pool(List<Invoker<T>> invokers) {

        RouterCache<T> routerCache = new RouterCache<>();
        ConcurrentHashMap<String, BitList<Invoker<T>>> addrPool = new ConcurrentHashMap<>();

        final TagRouterRule tagRouterRuleCopy = tagRouterRule;


        // tagRouterRuleCopy 为 null或者无效、不允许，则参数的invokers都是可用的，整体作为 noTag 一种分类，映射到BitList
        if (tagRouterRuleCopy == null || !tagRouterRuleCopy.isValid() || !tagRouterRuleCopy.isEnabled()) {
            BitList<Invoker<T>> noTagList = new BitList<>(invokers, true);

            for (int index = 0; index < invokers.size(); index++) {
                noTagList.addIndex(index);
            }
            addrPool.put(NO_TAG, noTagList);
            routerCache.setAddrPool(addrPool);
            return routerCache;
        }

        List<String> tagNames = tagRouterRuleCopy.getTagNames();
        Map<String, List<String>> tagnameToAddresses = tagRouterRuleCopy.getTagnameToAddresses();

        // 根据 tag 分类
        for (String tag : tagNames) {
            List<String> addresses = tagnameToAddresses.get(tag);
            BitList<Invoker<T>> list = new BitList<>(invokers, true);

            // 该tag的地址为空，则invokers都是允许的
            if (CollectionUtils.isEmpty(addresses)) {
                list.addAll(invokers);
            } else {
                for (int index = 0; index < invokers.size(); index++) {
                    Invoker<T> invoker = invokers.get(index);
                    // 地址匹配的话，则该invoker允许，映射到BitList
                    if (addressMatches(invoker.getUrl(), addresses)) {
                        list.addIndex(index);
                    }
                }
            }

            addrPool.put(tag, list);
        }

        List<String> addresses = tagRouterRuleCopy.getAddresses();
        BitList<Invoker<T>> noTagList = new BitList<>(invokers, true);

        for (int index = 0; index < invokers.size(); index++) {
            Invoker<T> invoker = invokers.get(index);
            if (addressNotMatches(invoker.getUrl(), addresses)) {
                noTagList.addIndex(index);
            }
        }
        addrPool.put(NO_TAG, noTagList);
        routerCache.setAddrPool(addrPool);
        routerCache.setAddrMetadata(tagRouterRuleCopy);

        return routerCache;
    }

    private boolean addressMatches(URL url, List<String> addresses) {
        return addresses != null && checkAddressMatch(addresses, url.getHost(), url.getPort());
    }

    private boolean addressNotMatches(URL url, List<String> addresses) {
        return addresses == null || !checkAddressMatch(addresses, url.getHost(), url.getPort());
    }

    private boolean checkAddressMatch(List<String> addresses, String host, int port) {
        for (String address : addresses) {
            try {
                if (NetUtils.matchIpExpression(address, host, port)) {
                    return true;
                }
                if ((ANYHOST_VALUE + ":" + port).equals(address)) {
                    return true;
                }
            } catch (UnknownHostException e) {
                logger.error("The format of ip address is invalid in tag route. Address :" + address, e);
            } catch (Exception e) {
                logger.error("The format of ip address is invalid in tag route. Address :" + address, e);
            }
        }
        return false;
    }

    public void setApplication(String app) {
        this.application = app;
    }

    @Override
    public <T> void notify(List<Invoker<T>> invokers) {
        if (CollectionUtils.isEmpty(invokers)) {
            return;
        }

        Invoker<T> invoker = invokers.get(0);
        URL url = invoker.getUrl();
        String providerApplication = url.getRemoteApplication();

        if (StringUtils.isEmpty(providerApplication)) {
            logger.error("TagRouter must getConfig from or subscribe to a specific application, but the application " +
                "in this TagRouter is not specified.");
            return;
        }

        synchronized (this) {
            // 如果最新过来的invokers这一批的app和当前的application不一致，那么需要重新监听新的path
            if (!providerApplication.equals(application)) {
                if (!StringUtils.isEmpty(application)) {
                    ruleRepository.removeListener(application + RULE_SUFFIX, this);
                }
                String key = providerApplication + RULE_SUFFIX;
                ruleRepository.addListener(key, this);
                application = providerApplication;
                String rawRule = ruleRepository.getRule(key, DynamicConfiguration.DEFAULT_GROUP);
                if (StringUtils.isNotEmpty(rawRule)) {
                    this.process(new ConfigChangedEvent(key, DynamicConfiguration.DEFAULT_GROUP, rawRule));
                }
            }
        }
        // todo 可以删掉
        pool(invokers);
    }

}
