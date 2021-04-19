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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.metadata.definition.ServiceDefinitionBuilder;
import org.apache.dubbo.metadata.definition.model.FullServiceDefinition;
import org.apache.dubbo.metadata.report.MetadataReport;
import org.apache.dubbo.metadata.report.MetadataReportFactory;
import org.apache.dubbo.metadata.report.identifier.KeyTypeEnum;
import org.apache.dubbo.metadata.report.identifier.MetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.ServiceMetadataIdentifier;
import org.apache.dubbo.metadata.report.identifier.SubscriberMetadataIdentifier;

import com.google.gson.Gson;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER_SIDE;

/**
 * 2018/10/9
 */
public class ZookeeperMetadataReportTest {
    private TestingServer zkServer;
    private ZookeeperMetadataReport zookeeperMetadataReport;
    private URL registryUrl;
    private ZookeeperMetadataReportFactory zookeeperMetadataReportFactory;

    @BeforeEach
    public void setUp() throws Exception {
        this.registryUrl = URL.valueOf("zookeeper://127.0.0.1:2181?sync.report=true"  );

        zookeeperMetadataReportFactory =  (ZookeeperMetadataReportFactory) ExtensionLoader.getExtensionLoader(MetadataReportFactory.class).getDefaultExtension();

        zookeeperMetadataReport = (ZookeeperMetadataReport) zookeeperMetadataReportFactory.getMetadataReport(registryUrl);
    }

    @AfterEach
    public void tearDown() throws Exception {
        zkServer.stop();
    }

    private void deletePath(MetadataIdentifier metadataIdentifier, ZookeeperMetadataReport zookeeperMetadataReport) {
        // 获取节点path
        String category = zookeeperMetadataReport.toRootDir() + metadataIdentifier.getUniqueKey(KeyTypeEnum.PATH);
        // 删除节点
        zookeeperMetadataReport.zkClient.delete(category);
    }

    @Test
    public void testStoreProvider() throws ClassNotFoundException, InterruptedException {
        String interfaceName = "org.apache.dubbo.metadata.store.zookeeper.ZookeeperMetadataReport4TstService";
        String version = "1.0.0.zk.md";
        String group = null;
        String application = "vic.zk.md";
        // 进去
        MetadataIdentifier providerMetadataIdentifier = storePrivider(zookeeperMetadataReport, interfaceName, version, group, application);

        // 看是否能取到zk的节点
        String fileContent = zookeeperMetadataReport.zkClient.getContent(zookeeperMetadataReport.getNodePath(providerMetadataIdentifier));
        fileContent = waitSeconds(fileContent, 3500, zookeeperMetadataReport.getNodePath(providerMetadataIdentifier));
        Assertions.assertNotNull(fileContent);

        // 删除
        deletePath(providerMetadataIdentifier, zookeeperMetadataReport);
        fileContent = zookeeperMetadataReport.zkClient.getContent(zookeeperMetadataReport.getNodePath(providerMetadataIdentifier));
        fileContent = waitSeconds(fileContent, 1000, zookeeperMetadataReport.getNodePath(providerMetadataIdentifier));
        Assertions.assertNull(fileContent);


        // 再存一次
        providerMetadataIdentifier = storePrivider(zookeeperMetadataReport, interfaceName, version, group, application);
        fileContent = zookeeperMetadataReport.zkClient.getContent(zookeeperMetadataReport.getNodePath(providerMetadataIdentifier));
        fileContent = waitSeconds(fileContent, 3500, zookeeperMetadataReport.getNodePath(providerMetadataIdentifier));
        Assertions.assertNotNull(fileContent);

        Gson gson = new Gson();
        // 把节点的值利用gson反序列化
        FullServiceDefinition fullServiceDefinition = gson.fromJson(fileContent, FullServiceDefinition.class);
        Assertions.assertEquals(fullServiceDefinition.getParameters().get("paramTest"), "zkTest");

        // 注意自己再额外对比下zk节点的值和本地MetaData缓存文件的内容是否一致
    }


    @Test
    public void testConsumer() throws ClassNotFoundException, InterruptedException {
        String interfaceName = "org.apache.dubbo.metadata.store.zookeeper.ZookeeperMetadataReport4TstService";
        String version = "1.0.0.zk.md";
        String group = null;
        String application = "vic.zk.md";
        MetadataIdentifier consumerMetadataIdentifier = storeConsumer(zookeeperMetadataReport, interfaceName, version, group, application);

        String fileContent = zookeeperMetadataReport.zkClient.getContent(zookeeperMetadataReport.getNodePath(consumerMetadataIdentifier));
        fileContent = waitSeconds(fileContent, 3500, zookeeperMetadataReport.getNodePath(consumerMetadataIdentifier));
        Assertions.assertNotNull(fileContent);

        deletePath(consumerMetadataIdentifier, zookeeperMetadataReport);
        fileContent = zookeeperMetadataReport.zkClient.getContent(zookeeperMetadataReport.getNodePath(consumerMetadataIdentifier));
        fileContent = waitSeconds(fileContent, 1000, zookeeperMetadataReport.getNodePath(consumerMetadataIdentifier));
        Assertions.assertNull(fileContent);

        consumerMetadataIdentifier = storeConsumer(zookeeperMetadataReport, interfaceName, version, group, application);
        fileContent = zookeeperMetadataReport.zkClient.getContent(zookeeperMetadataReport.getNodePath(consumerMetadataIdentifier));
        fileContent = waitSeconds(fileContent, 3000, zookeeperMetadataReport.getNodePath(consumerMetadataIdentifier));
        Assertions.assertNotNull(fileContent);
        Assertions.assertEquals(fileContent, "{\"paramConsumerTest\":\"zkCm\"}");
    }

    @Test
    public void testDoSaveMetadata() throws ExecutionException, InterruptedException {
        String interfaceName = "org.apache.dubbo.metadata.store.zookeeper.ZookeeperMetadataReport4TstService";
        String version = "1.0.0";
        String group = null;
        String application = "etc-metadata-report-consumer-test";
        String revision = "90980";
        String protocol = "xxx";
        // xxx://30.25.58.119:8989/my.metadata.zookeeper.ZookeeperMetadataReport4TstService?application=etc-metadata-report-consumer-test&paramTest=etcdTest&version=1.0.0
        URL url = generateURL(interfaceName, version, group, application);
        ServiceMetadataIdentifier serviceMetadataIdentifier = new ServiceMetadataIdentifier(interfaceName, version,
                group, "provider", revision, protocol);
        // 进去
        zookeeperMetadataReport.doSaveMetadata(serviceMetadataIdentifier, url);

        String fileContent = zookeeperMetadataReport.zkClient.getContent(zookeeperMetadataReport.getNodePath(serviceMetadataIdentifier));
        Assertions.assertNotNull(fileContent);

        // 节点的内容就是对url.toFullString()进行encode的
        Assertions.assertEquals(fileContent, URL.encode(url.toFullString()));
    }

    @Test
    public void testDoRemoveMetadata() throws ExecutionException, InterruptedException {
        String interfaceName = "org.apache.dubbo.metadata.store.zookeeper.ZookeeperMetadataReport4TstService";
        String version = "1.0.0";
        String group = null;
        String application = "etc-metadata-report-consumer-test";
        String revision = "90980";
        String protocol = "xxx";
        URL url = generateURL(interfaceName, version, group, application);
        ServiceMetadataIdentifier serviceMetadataIdentifier = new ServiceMetadataIdentifier(interfaceName, version,
                group, "provider", revision, protocol);
        zookeeperMetadataReport.doSaveMetadata(serviceMetadataIdentifier, url);
        String fileContent = zookeeperMetadataReport.zkClient.getContent(zookeeperMetadataReport.getNodePath(serviceMetadataIdentifier));

        Assertions.assertNotNull(fileContent);


        // 注意
        zookeeperMetadataReport.doRemoveMetadata(serviceMetadataIdentifier);

        fileContent = zookeeperMetadataReport.zkClient.getContent(zookeeperMetadataReport.getNodePath(serviceMetadataIdentifier));
        Assertions.assertNull(fileContent);
    }

    @Test
    public void testDoGetExportedURLs() throws ExecutionException, InterruptedException {
        String interfaceName = "org.apache.dubbo.metadata.store.zookeeper.ZookeeperMetadataReport4TstService";
        String version = "1.0.0";
        String group = null;
        String application = "etc-metadata-report-consumer-test";
        String revision = "90980";
        String protocol = "xxx";
        URL url = generateURL(interfaceName, version, group, application);
        ServiceMetadataIdentifier serviceMetadataIdentifier = new ServiceMetadataIdentifier(interfaceName, version,
                group, "provider", revision, protocol);
        zookeeperMetadataReport.doSaveMetadata(serviceMetadataIdentifier, url);

        // 注意
        List<String> r = zookeeperMetadataReport.doGetExportedURLs(serviceMetadataIdentifier);
        Assertions.assertTrue(r.size() == 1);

        String fileContent = r.get(0);
        Assertions.assertNotNull(fileContent);

        Assertions.assertEquals(fileContent, url.toFullString());
    }

    @Test
    public void testDoSaveSubscriberData() throws ExecutionException, InterruptedException {
        String interfaceName = "org.apache.dubbo.metadata.store.zookeeper.ZookeeperMetadataReport4TstService";
        String version = "1.0.0";
        String group = null;
        String application = "etc-metadata-report-consumer-test";
        String revision = "90980";
        String protocol = "xxx";
        URL url = generateURL(interfaceName, version, group, application);
        SubscriberMetadataIdentifier subscriberMetadataIdentifier = new SubscriberMetadataIdentifier(application, revision);
        Gson gson = new Gson();
        String r = gson.toJson(Arrays.asList(url));
        zookeeperMetadataReport.doSaveSubscriberData(subscriberMetadataIdentifier, r);

        String fileContent = zookeeperMetadataReport.zkClient.getContent(zookeeperMetadataReport.getNodePath(subscriberMetadataIdentifier));

        Assertions.assertNotNull(fileContent);

        Assertions.assertEquals(fileContent, r);
    }

    // 这个测试程序不用看，和上面的代码一致
    @Test
    public void testDoGetSubscribedURLs() throws ExecutionException, InterruptedException {
        String interfaceName = "org.apache.dubbo.metadata.store.zookeeper.ZookeeperMetadataReport4TstService";
        String version = "1.0.0";
        String group = null;
        String application = "etc-metadata-report-consumer-test";
        String revision = "90980";
        String protocol = "xxx";
        URL url = generateURL(interfaceName, version, group, application);
        SubscriberMetadataIdentifier subscriberMetadataIdentifier = new SubscriberMetadataIdentifier(application, revision);
        Gson gson = new Gson();
        String r = gson.toJson(Arrays.asList(url));
        zookeeperMetadataReport.doSaveSubscriberData(subscriberMetadataIdentifier, r);

        String fileContent = zookeeperMetadataReport.zkClient.getContent(zookeeperMetadataReport.getNodePath(subscriberMetadataIdentifier));

        Assertions.assertNotNull(fileContent);

        Assertions.assertEquals(fileContent, r);
    }


    private MetadataIdentifier storePrivider(MetadataReport zookeeperMetadataReport, String interfaceName, String version, String group, String application) throws ClassNotFoundException, InterruptedException {
        URL url = URL.valueOf("xxx://" + NetUtils.getLocalAddress().getHostName() + ":4444/" + interfaceName + "?paramTest=zkTest&version=" + version + "&application="
                + application + (group == null ? "" : "&group=" + group));

        MetadataIdentifier providerMetadataIdentifier = new MetadataIdentifier(interfaceName, version, group, PROVIDER_SIDE, application);
        Class interfaceClass = Class.forName(interfaceName);
        FullServiceDefinition fullServiceDefinition = ServiceDefinitionBuilder.buildFullDefinition(interfaceClass, url.getParameters());

        zookeeperMetadataReport.storeProviderMetadata(providerMetadataIdentifier, fullServiceDefinition);
        Thread.sleep(2000);
        return providerMetadataIdentifier;
    }

    private MetadataIdentifier storeConsumer(MetadataReport zookeeperMetadataReport, String interfaceName, String version, String group, String application) throws ClassNotFoundException, InterruptedException {
        URL url = URL.valueOf("xxx://" + NetUtils.getLocalAddress().getHostName() + ":4444/" + interfaceName + "?version=" + version + "&application="
                + application + (group == null ? "" : "&group=" + group));

        MetadataIdentifier consumerMetadataIdentifier = new MetadataIdentifier(interfaceName, version, group, CONSUMER_SIDE, application);
        Class interfaceClass = Class.forName(interfaceName);

        Map<String, String> tmp = new HashMap<>();
        tmp.put("paramConsumerTest", "zkCm");
        zookeeperMetadataReport.storeConsumerMetadata(consumerMetadataIdentifier, tmp);
        Thread.sleep(2000);

        return consumerMetadataIdentifier;
    }

    private String waitSeconds(String value, long moreTime, String path) throws InterruptedException {
        if (value == null) {
            Thread.sleep(moreTime);
            return zookeeperMetadataReport.zkClient.getContent(path);
        }
        return value;
    }

    private URL generateURL(String interfaceName, String version, String group, String application) {
        URL url = URL.valueOf("xxx://" + NetUtils.getLocalAddress().getHostName() + ":8989/" + interfaceName +
                "?paramTest=etcdTest&version=" + version + "&application="
                + application + (group == null ? "" : "&group=" + group));
        return url;
    }
}
