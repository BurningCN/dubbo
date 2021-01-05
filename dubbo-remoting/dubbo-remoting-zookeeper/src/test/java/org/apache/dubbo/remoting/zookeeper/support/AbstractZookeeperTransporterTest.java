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
package org.apache.dubbo.remoting.zookeeper.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.zookeeper.ZookeeperClient;
import org.apache.dubbo.remoting.zookeeper.curator.CuratorZookeeperTransporter;

import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.nullValue;

/**
 * AbstractZookeeperTransporterTest
 */
// OK
public class AbstractZookeeperTransporterTest {
    private TestingServer zkServer;
    private ZookeeperClient zookeeperClient;
    private AbstractZookeeperTransporter abstractZookeeperTransporter;
    private int zkServerPort;

    @BeforeEach
    public void setUp() throws Exception {
        zkServerPort = NetUtils.getAvailablePort();
        zkServer = new TestingServer(zkServerPort, true);
        zkServerPort = 2181;
        zookeeperClient = new CuratorZookeeperTransporter().connect(URL.valueOf("zookeeper://127.0.0.1:" +
                zkServerPort + "/service"));
        abstractZookeeperTransporter = new CuratorZookeeperTransporter();
    }


    @AfterEach
    public void tearDown() throws Exception {
        zkServer.stop();
    }

    @Test
    public void testZookeeperClient() {
        assertThat(zookeeperClient, not(nullValue()));
        zookeeperClient.close();
    }

    @Test
    public void testGetURLBackupAddress() {
        URL url = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort + "/org.apache.dubbo.registry.RegistryService?backup=127.0.0.1:" + 9099 + "&application=metadatareport-local-xml-provider2&dubbo=2.0.2&interface=org.apache.dubbo.registry.RegistryService&pid=47418&specVersion=2.7.0-SNAPSHOT&timestamp=1547102428828");
        // 进去
        List<String> stringList = abstractZookeeperTransporter.getURLBackupAddress(url);
        // 两个地址
        Assertions.assertEquals(stringList.size(), 2);
        Assertions.assertEquals(stringList.get(0), "127.0.0.1:" + zkServerPort);
        Assertions.assertEquals(stringList.get(1), "127.0.0.1:9099");
    }

    // no back up
    @Test
    public void testGetURLBackupAddressNoBack() {
        URL url = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort + "/org.apache.dubbo.registry.RegistryService?application=metadatareport-local-xml-provider2&dubbo=2.0.2&interface=org.apache.dubbo.registry.RegistryService&pid=47418&specVersion=2.7.0-SNAPSHOT&timestamp=1547102428828");
        List<String> stringList = abstractZookeeperTransporter.getURLBackupAddress(url);
        Assertions.assertEquals(stringList.size(), 1);
        Assertions.assertEquals(stringList.get(0), "127.0.0.1:" + zkServerPort);
    }

    // 测试 FetchAndUpdateZookeeperClientCache
    @Test
    public void testFetchAndUpdateZookeeperClientCache() throws Exception {
        int zkServerPort2 = NetUtils.getAvailablePort();
        TestingServer zkServer2 = new TestingServer(zkServerPort2, true);

        int zkServerPort3 = NetUtils.getAvailablePort();
        TestingServer zkServer3 = new TestingServer(zkServerPort3, true);

        URL url = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort + "/org.apache.dubbo.registry.RegistryService?backup=127.0.0.1:" + zkServerPort3 + ",127.0.0.1:" + zkServerPort2 + "&application=metadatareport-local-xml-provider2&dubbo=2.0.2&interface=org.apache.dubbo.registry.RegistryService&pid=47418&specVersion=2.7.0-SNAPSHOT&timestamp=1547102428828");
        // 进去
        ZookeeperClient newZookeeperClient = abstractZookeeperTransporter.connect(url);
        // just for connected --- > 这句话的意思就是如果前面连接上了，下面的语句一定可以正常执行不会抛异常
        newZookeeperClient.getContent("/dubbo/test");
        // 前面url一共是有三个地址的。所以缓存map的大小也是3，key是各个address，val是同一个zkClient，如下：
        //"127.0.0.1:49258" -> {CuratorZookeeperClient@2479}
        //"127.0.0.1:49255" -> {CuratorZookeeperClient@2479}
        //"127.0.0.1:2181" -> {CuratorZookeeperClient@2479}
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().size(), 3);
        // 获取map的key为"127.0.0.1:2181"的val，肯定是等于newZookeeperClient的
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().get("127.0.0.1:" + zkServerPort), newZookeeperClient);

        URL url2 = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort + "/org.apache.dubbo.metadata.store.MetadataReport?address=zookeeper://127.0.0.1:2181&application=metadatareport-local-xml-provider2&cycle-report=false&interface=org.apache.dubbo.metadata.store.MetadataReport&retry-period=4590&retry-times=23&sync-report=true");
        // 进去
        checkFetchAndUpdateCacheNotNull(url2);
        URL url3 = URL.valueOf("zookeeper://127.0.0.1:8778/org.apache.dubbo.metadata.store.MetadataReport?backup=127.0.0.1:" + zkServerPort3 + "&address=zookeeper://127.0.0.1:2181&application=metadatareport-local-xml-provider2&cycle-report=false&interface=org.apache.dubbo.metadata.store.MetadataReport&retry-period=4590&retry-times=23&sync-report=true");
        checkFetchAndUpdateCacheNotNull(url3);

        zkServer2.stop();
        zkServer3.stop();
    }

    // easy
    private void checkFetchAndUpdateCacheNotNull(URL url) {
        List<String> addressList = abstractZookeeperTransporter.getURLBackupAddress(url);
        ZookeeperClient zookeeperClient = abstractZookeeperTransporter.fetchAndUpdateZookeeperClientCache(addressList);
        Assertions.assertNotNull(zookeeperClient);
    }

    @Test
    public void testRepeatConnect() {
        URL url = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort + "/org.apache.dubbo.registry.RegistryService?application=metadatareport-local-xml-provider2&dubbo=2.0.2&interface=org.apache.dubbo.registry.RegistryService&pid=47418&specVersion=2.7.0-SNAPSHOT&timestamp=1547102428828");
        URL url2 = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort + "/org.apache.dubbo.metadata.store.MetadataReport?address=zookeeper://127.0.0.1:2181&application=metadatareport-local-xml-provider2&cycle-report=false&interface=org.apache.dubbo.metadata.store.MetadataReport&retry-period=4590&retry-times=23&sync-report=true");
        ZookeeperClient newZookeeperClient = abstractZookeeperTransporter.connect(url);
        //just for connected
        newZookeeperClient.getContent("/dubbo/test");
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().size(), 1);
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().get("127.0.0.1:" + zkServerPort), newZookeeperClient);
        Assertions.assertTrue(newZookeeperClient.isConnected());

        // 内部如果发现url的addressList能从缓存的map取到其一匹配的entry，直接返回zkClient，不会创建新的zkClient，进去
        ZookeeperClient newZookeeperClient2 = abstractZookeeperTransporter.connect(url2);
        //just for connected
        newZookeeperClient2.getContent("/dubbo/test");
        Assertions.assertEquals(newZookeeperClient, newZookeeperClient2);
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().size(), 1);
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().get("127.0.0.1:" + zkServerPort), newZookeeperClient);
    }

    @Test
    public void testNotRepeatConnect() throws Exception {
        int zkServerPort2 = NetUtils.getAvailablePort();
        TestingServer zkServer2 = new TestingServer(zkServerPort2, true);

        URL url = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort + "/org.apache.dubbo.registry.RegistryService?application=metadatareport-local-xml-provider2&dubbo=2.0.2&interface=org.apache.dubbo.registry.RegistryService&pid=47418&specVersion=2.7.0-SNAPSHOT&timestamp=1547102428828");
        URL url2 = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort2 + "/org.apache.dubbo.metadata.store.MetadataReport?address=zookeeper://127.0.0.1:2181&application=metadatareport-local-xml-provider2&cycle-report=false&interface=org.apache.dubbo.metadata.store.MetadataReport&retry-period=4590&retry-times=23&sync-report=true");
        ZookeeperClient newZookeeperClient = abstractZookeeperTransporter.connect(url);
        //just for connected
        newZookeeperClient.getContent("/dubbo/test");
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().size(), 1);
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().get("127.0.0.1:" + zkServerPort), newZookeeperClient);

        ZookeeperClient newZookeeperClient2 = abstractZookeeperTransporter.connect(url2);
        //just for connected
        newZookeeperClient2.getContent("/dubbo/test");
        Assertions.assertNotEquals(newZookeeperClient, newZookeeperClient2);
        // 这里map里面有两个entry，k v都不一样，如下：
        //"127.0.0.1:49542" -> {CuratorZookeeperClient@2534}
        //"127.0.0.1:2181" -> {CuratorZookeeperClient@2276}
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().size(), 2);
        // 第二个entry
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().get("127.0.0.1:" + zkServerPort2), newZookeeperClient2);

        zkServer2.stop();
    }

    @Test
    public void testRepeatConnectForBackUpAdd() throws Exception {
        int zkServerPort2 = NetUtils.getAvailablePort();
        TestingServer zkServer2 = new TestingServer(zkServerPort2, true);

        int zkServerPort3 = NetUtils.getAvailablePort();
        TestingServer zkServer3 = new TestingServer(zkServerPort3, true);

        URL url = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort + "/org.apache.dubbo.registry.RegistryService?backup=127.0.0.1:" + zkServerPort2 + "&application=metadatareport-local-xml-provider2&dubbo=2.0.2&interface=org.apache.dubbo.registry.RegistryService&pid=47418&specVersion=2.7.0-SNAPSHOT&timestamp=1547102428828");
        URL url2 = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort2 + "/org.apache.dubbo.metadata.store.MetadataReport?backup=127.0.0.1:" + zkServerPort3 + "&address=zookeeper://127.0.0.1:2181&application=metadatareport-local-xml-provider2&cycle-report=false&interface=org.apache.dubbo.metadata.store.MetadataReport&retry-period=4590&retry-times=23&sync-report=true");
        ZookeeperClient newZookeeperClient = abstractZookeeperTransporter.connect(url);
        //just for connected
        newZookeeperClient.getContent("/dubbo/test");
        //"127.0.0.1:49664" -> {CuratorZookeeperClient@2529}
        //"127.0.0.1:2181" -> {CuratorZookeeperClient@2529}
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().size(), 2);
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().get("127.0.0.1:" + zkServerPort), newZookeeperClient);

        // 这里用url2发起连接，根据测试方法名称就知道啥意思了，这里说的是url的://后面的address和url的backup参数的值是同一个值，同一个address
        // 两个addressList有交集，所以会从ZookeeperClientMap匹配到entry，返回先前建立好的zkClient
        ZookeeperClient newZookeeperClient2 = abstractZookeeperTransporter.connect(url2);
        //just for connected
        newZookeeperClient2.getContent("/dubbo/test");
        Assertions.assertEquals(newZookeeperClient, newZookeeperClient2);
        //"127.0.0.1:49664" -> {CuratorZookeeperClient@2529}
        //"127.0.0.1:49667" -> {CuratorZookeeperClient@2529}
        //"127.0.0.1:2181" -> {CuratorZookeeperClient@2529}
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().size(), 3);
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().get("127.0.0.1:" + zkServerPort2), newZookeeperClient2);

        zkServer2.stop();
        zkServer3.stop();
    }

    // easy
    @Test
    public void testRepeatConnectForNoMatchBackUpAdd() throws Exception {
        int zkServerPort2 = NetUtils.getAvailablePort();
        TestingServer zkServer2 = new TestingServer(zkServerPort2, true);

        int zkServerPort3 = NetUtils.getAvailablePort();
        TestingServer zkServer3 = new TestingServer(zkServerPort3, true);

        URL url = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort + "/org.apache.dubbo.registry.RegistryService?backup=127.0.0.1:" + zkServerPort3 + "&application=metadatareport-local-xml-provider2&dubbo=2.0.2&interface=org.apache.dubbo.registry.RegistryService&pid=47418&specVersion=2.7.0-SNAPSHOT&timestamp=1547102428828");
        URL url2 = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort2 + "/org.apache.dubbo.metadata.store.MetadataReport?address=zookeeper://127.0.0.1:2181&application=metadatareport-local-xml-provider2&cycle-report=false&interface=org.apache.dubbo.metadata.store.MetadataReport&retry-period=4590&retry-times=23&sync-report=true");
        ZookeeperClient newZookeeperClient = abstractZookeeperTransporter.connect(url);
        //just for connected
        newZookeeperClient.getContent("/dubbo/test");
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().size(), 2);
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().get("127.0.0.1:" + zkServerPort), newZookeeperClient);

        ZookeeperClient newZookeeperClient2 = abstractZookeeperTransporter.connect(url2);
        //just for connected
        newZookeeperClient2.getContent("/dubbo/test");
        Assertions.assertNotEquals(newZookeeperClient, newZookeeperClient2);
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().size(), 3);
        Assertions.assertEquals(abstractZookeeperTransporter.getZookeeperClientMap().get("127.0.0.1:" + zkServerPort2), newZookeeperClient2);

        zkServer2.stop();
        zkServer3.stop();
    }

    @Test
    public void testSameHostWithDifferentUser() throws Exception {
        int zkPort1 = NetUtils.getAvailablePort();
        int zkPort2 = NetUtils.getAvailablePort();
        try (TestingServer zkServer1 = new TestingServer(zkPort1, true)) {
            try (TestingServer zkServer2 = new TestingServer(zkPort2, true)) {
                URL url1 = URL.valueOf("zookeeper://us1:pw1@127.0.0.1:" + zkPort1 + "/path1");
                URL url2 = URL.valueOf("zookeeper://us2:pw2@127.0.0.1:" + zkPort1 + "/path2");

                ZookeeperClient client1 = abstractZookeeperTransporter.connect(url1);
                ZookeeperClient client2 = abstractZookeeperTransporter.connect(url2);

                // 不同的用户名密码，发起的连接肯定不是一个
                assertThat(client1, not(client2));
            }
        }
    }
}
