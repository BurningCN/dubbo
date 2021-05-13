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
package org.apache.dubbo.metadata.store.zookeeper;

import com.google.gson.Gson;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.metadata.MappingChangedEvent;
import org.apache.dubbo.metadata.MappingListener;
import org.apache.dubbo.metadata.MetadataInfo;
import org.apache.dubbo.metadata.report.identifier.BaseMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.KeyTypeEnum;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;
import org.apache.dubbo.metadata.report.support.AbstractMetadataReport;
import org.apache.dubbo.remoting.zookeeper.ChildListener;
import org.apache.dubbo.remoting.zookeeper.ZookeeperClient;
import org.apache.dubbo.remoting.zookeeper.ZookeeperTransporter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_SEPARATOR;

/**
 * ZookeeperMetadataReport
 */
// 和 ZookeeperRegistry 差不多
public class ZookeeperMetadataReport extends AbstractMetadataReport {

    // todo need pr 可以去除
    private final static Logger logger = LoggerFactory.getLogger(ZookeeperMetadataReport.class);

    private final String root;

    final ZookeeperClient zkClient;

    private Gson gson = new Gson();

    private Map<String, ChildListener> listenerMap = new ConcurrentHashMap<>();

    public ZookeeperMetadataReport(URL url, ZookeeperTransporter zookeeperTransporter) {
        // 进去
        super(url);
        if (url.isAnyHost()) {
            throw new IllegalStateException("registry address == null");
        }

        String group = url.getParameter(GROUP_KEY, DEFAULT_ROOT);
        if (!group.startsWith(PATH_SEPARATOR)) {
            group = PATH_SEPARATOR + group;
        }
        // 默认为/dubbo
        this.root = group;
        // 这里是很有可能拿到复用的zkClient的，因为config-center以及发起了一次
        zkClient = zookeeperTransporter.connect(url);
    }

    String toRootDir() {
        if (root.equals(PATH_SEPARATOR)) {
            return root;
        }
        return root + PATH_SEPARATOR;
    }

    @Override
    protected void doStoreProviderMetadata(MetadataIdentifier providerMetadataIdentifier, String serviceDefinitions) {
        storeMetadata(providerMetadataIdentifier, serviceDefinitions);
    }

    @Override
    protected void doStoreConsumerMetadata(MetadataIdentifier consumerMetadataIdentifier, String value) {
        storeMetadata(consumerMetadataIdentifier, value);
    }

    @Override
    protected void doSaveMetadata(ServiceMetadataIdentifier metadataIdentifier, URL url) {
        // 此时节点path的值为url的信息（storeMetadata方法存储的是serviceDefinition的信息）
        zkClient.create(getNodePath(metadataIdentifier), URL.encode(url.toFullString()), false);
    }

    @Override
    protected void doRemoveMetadata(ServiceMetadataIdentifier metadataIdentifier) {
        zkClient.delete(getNodePath(metadataIdentifier));
    }

    @Override
    protected List<String> doGetExportedURLs(ServiceMetadataIdentifier metadataIdentifier) {
        String content = zkClient.getContent(getNodePath(metadataIdentifier));
        if (StringUtils.isEmpty(content)) {
            return Collections.emptyList();
        }
        return new ArrayList<String>(Arrays.asList(URL.decode(content)));
    }

    @Override
    protected void doSaveSubscriberData(SubscriberMetadataIdentifier subscriberMetadataIdentifier, String urls) {
        zkClient.create(getNodePath(subscriberMetadataIdentifier), urls, false);
    }

    @Override
    protected String doGetSubscribedURLs(SubscriberMetadataIdentifier subscriberMetadataIdentifier) {
        return zkClient.getContent(getNodePath(subscriberMetadataIdentifier));
    }

    @Override
    public String getServiceDefinition(MetadataIdentifier metadataIdentifier) {
        return zkClient.getContent(getNodePath(metadataIdentifier));
    }

    // v就是gson.toJson(serviceDefinition);
    private void storeMetadata(MetadataIdentifier metadataIdentifier, String v) {
        // getNodePath
        // provider-eg /dubbo/metadata/samples.servicediscovery.demo.DemoService/provider/demo-provider
        // consumer-eg /dubbo/metadata/samples.servicediscovery.demo.DemoService/consumer/demo-consumer
        zkClient.create(getNodePath(metadataIdentifier), v, false);
    }

    // toRootDir() 默认为 /dubbo/
    String getNodePath(BaseMetadataIdentifier metadataIdentifier) {
        // /dubbo/metadata/{interface}/{version}/{side}/{appName}
        // eg /dubbo/metadata/my.metadata.zookeeper.ZookeeperMetadataReport4TstService/1.0.0.zk.md/provider/vic.zk.md
        return toRootDir() + metadataIdentifier.getUniqueKey(KeyTypeEnum.PATH);
        // 如果 参数为SubscriberMetadataIdentifier，
        // 则eg /dubbo/metadata/demo-provider/AB6F0B7C2429C8828F640F853B65E1E1

    }

    @Override
    public void publishAppMetadata(SubscriberMetadataIdentifier identifier, MetadataInfo metadataInfo) {
        String path = getNodePath(identifier);
        if (StringUtils.isBlank(zkClient.getContent(path))) {
            // kv分别为 eg
            /*
             /dubbo/metadata/demo-provider/AB6F0B7C2429C8828F640F853B65E1E1

             metadataInfo = {MetadataInfo@4140} "metadata{app='demo-provider',revision='AB6F0B7C2429C8828F640F853B65E1E1',services={demo-provider/org.apache.dubbo.metadata.MetadataService:1.0.0:dubbo=service{name='org.apache.dubbo.metadata.MetadataService',group='demo-provider',version='1.0.0',protocol='dubbo',params={deprecated=false, dubbo=2.0.2, version=1.0.0, group=demo-provider},consumerParams=null}, samples.servicediscovery.demo.DemoService:dubbo=service{name='samples.servicediscovery.demo.DemoService',group='null',version='null',protocol='dubbo',params={deprecated=false, weight=12, dubbo=2.0.2},consumerParams=null}}}"
                app = "demo-provider"
                revision = "AB6F0B7C2429C8828F640F853B65E1E1"
                services = {HashMap@4156}  size = 2
                    "demo-provider/org.apache.dubbo.metadata.MetadataService:1.0.0:dubbo" -> {MetadataInfo$ServiceInfo@5250} "service{name='org.apache.dubbo.metadata.MetadataService',group='demo-provider',version='1.0.0',protocol='dubbo',params={deprecated=false, dubbo=2.0.2, version=1.0.0, group=demo-provider},consumerParams=null}"
                    "samples.servicediscovery.demo.DemoService:dubbo" -> {MetadataInfo$ServiceInfo@5252} "service{name='samples.servicediscovery.demo.DemoService',group='null',version='null',protocol='dubbo',params={deprecated=false, weight=12, dubbo=2.0.2},consumerParams=null}"
                extendParams = {HashMap@4161}  size = 1
                    "REGISTRY_CLUSTER" -> "org.apache.dubbo.config.RegistryConfig"
                reported = {AtomicBoolean@4162} "false"
            */
            zkClient.create(path, gson.toJson(metadataInfo), false);
        }
    }

    @Override
    public void registerServiceAppMapping(String serviceKey, String application, URL url) {
        // eg /dubbo/mapping/{serviceInterface}/testApp
        String path = toRootDir() + serviceKey + PATH_SEPARATOR + application;
        if (StringUtils.isBlank(zkClient.getContent(path))) {
            Map<String, String> value = new HashMap<>();
            value.put("timestamp", String.valueOf(System.currentTimeMillis()));
            // 节点值为当前时间戳，永久节点
            zkClient.create(path, gson.toJson(value), false);
        }
    }


    // 和前面的publishAppMetadata相反，这个是给消费者服务的。第二个参数没有用
    @Override
    public MetadataInfo getAppMetadata(SubscriberMetadataIdentifier identifier, Map<String, String> instanceMetadata) {
        String content = zkClient.getContent(getNodePath(identifier));
        return gson.fromJson(content, MetadataInfo.class);
    }

    // 和前面的registerServiceAppMapping相反，这个是给消费者服务的。
    // 专门配合MetadataServiceNameMapping的方法
    @Override
    public Set<String> getServiceAppMapping(String serviceKey, MappingListener listener, URL url) {
        Set<String>  appNameSet = new HashSet<>();
        // eg /dubbo/mapping/samples.servicediscovery.demo.DemoService
        String path = toRootDir() + serviceKey;

        List<String> appNameList;

        if (null == listenerMap.get(path)) {
            zkClient.create(path, false);
            // 进去
            appNameList = addServiceMappingListener(path, serviceKey, listener);
        } else {
            // 获取path下的所有app名称，因为之前注册的path就是 /dubbo/mapping/{serviceInterface}/testApp 或者 /dubbo/cofing/mapping/{serviceInterface}/testApp
            appNameList = zkClient.getChildren(path);
        }

        if (!CollectionUtils.isEmpty(appNameList)) {
            appNameSet.addAll(appNameList);
        }

        //appNameSet = {HashSet@4406}  size = 1
        // 0 = "demo-provider"
        return appNameSet;
    }

    // 这里的listener为 DefaultMappingListener
    private List<String> addServiceMappingListener(String path, String serviceKey, MappingListener listener) {
        ChildListener zkListener = (path1, children) -> {
            // 当父path /dubbo/mapping/samples.servicediscovery.demo.DemoService 的子节点变更，就会收到通知，走这段逻辑
            MappingChangedEvent event = new MappingChangedEvent();
            // serviceKey eg mapping/samples.servicediscovery.demo.DemoService
            event.setServiceKey(serviceKey);
            // 父path下的所有子节点，表示appName
            event.setApps(null != children ? new HashSet<>(children) : null);
            // 进 DefaultMappingListener 的onEvent
            listener.onEvent(event);
        };
        // 内部添加监听的同时，查节点下的子节点，比如返回demo-provider
        List<String> childNodes = zkClient.addChildListener(path, zkListener);
        listenerMap.put(path, zkListener);
        return childNodes;
        //childListeners = {ConcurrentHashMap@4450}  size = 1  注意这个
        // "/dubbo/mapping/samples.servicediscovery.demo.DemoService" -> {ConcurrentHashMap@4471}  size = 1
        //listeners = {ConcurrentHashMap@4451}  size = 4
        // "/dubbo/config/dubbo/samples.servicediscovery.demo.DemoService::.condition-router" -> {ConcurrentHashMap@4461}  size = 1
        // "/dubbo/config/MIGRATION/demo-consumer.migration" -> {ConcurrentHashMap@4463}  size = 1
        // "/dubbo/config/dubbo/demo-consumer.condition-router" -> {ConcurrentHashMap@4465}  size = 1
        // "/dubbo/config/dubbo/demo-consumer.configurators" -> {ConcurrentHashMap@4467}  size = 1
    }
}