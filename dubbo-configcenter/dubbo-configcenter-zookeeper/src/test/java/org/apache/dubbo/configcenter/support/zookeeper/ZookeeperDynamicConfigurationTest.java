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
package org.apache.dubbo.configcenter.support.zookeeper;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.config.configcenter.ConfigChangedEvent;
import org.apache.dubbo.common.config.configcenter.ConfigurationListener;
import org.apache.dubbo.common.config.configcenter.DynamicConfiguration;
import org.apache.dubbo.common.config.configcenter.DynamicConfigurationFactory;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.NetUtils;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TODO refactor using mockito
 */
// OK
public class ZookeeperDynamicConfigurationTest {
    private static CuratorFramework client;
    private static URL configUrl;
    private static int zkServerPort = NetUtils.getAvailablePort();
    private static TestingServer zkServer;
    private static DynamicConfiguration configuration;

    @BeforeAll
    public static void setUp() throws Exception {
        zkServer = new TestingServer(zkServerPort, true);

        zkServerPort = 2181;
        // 使用的是Curator客户端
        client = CuratorFrameworkFactory.newClient("127.0.0.1:" + zkServerPort, 60 * 1000, 60 * 1000,
                new ExponentialBackoffRetry(1000, 3));
        // 启动，发起连接
        client.start();

        try {
            // 写数据到zk，进去
            setData("/dubbo/config/dubbo/dubbo.properties", "The content from dubbo.properties");
            setData("/dubbo/config/dubbo/service:version:group.configurators", "The content from configurators");
            setData("/dubbo/config/appname", "The content from higer level node");
            setData("/dubbo/config/dubbo/appname.tag-router", "The content from appname tagrouters");
            setData("/dubbo/config/dubbo/never.change.DemoService.configurators", "Never change value from configurators");
        } catch (Exception e) {
            e.printStackTrace();
        }


        // 构建URL
        configUrl = URL.valueOf("zookeeper://127.0.0.1:" + zkServerPort);

        // URL的协议值就是SPI扩展名，获取对应的扩展实例（此模块下，SPI文件里面仅配置了zk），getDynamicConfiguration进去，最后会进ZKDynamicConfiguration的构造函数
        configuration = ExtensionLoader.getExtensionLoader(DynamicConfigurationFactory.class).getExtension(configUrl.getProtocol())
                .getDynamicConfiguration(configUrl);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        zkServer.stop();
    }

    // easy
    private static void setData(String path, String data) throws Exception {
        if (client.checkExists().forPath(path) == null) {
            client.create().creatingParentsIfNeeded().forPath(path);
        }
        client.setData().forPath(path, data.getBytes());
    }

    @Test
    public void testGetConfig() throws Exception {
        // BeforeAll 测试程序里面提前写了数据到zk ，即setData("/dubbo/config/dubbo/dubbo.properties", "The content from dubbo.properties");
        // 所以能取到，进去看看实际的key是怎么变成上面的/dubbo/config/dubbo/dubbo.properties（其实是rootPath + group + key）
        Assertions.assertEquals("The content from dubbo.properties", configuration.getConfig("dubbo.properties", "dubbo"));
    }

    @Test
    public void testAddListener() throws Exception {
        CountDownLatch latch = new CountDownLatch(4);
        // 进去
        TestListener listener1 = new TestListener(latch);
        TestListener listener2 = new TestListener(latch);
        TestListener listener3 = new TestListener(latch);
        TestListener listener4 = new TestListener(latch);
        // 添加对path的监听器，进去
        configuration.addListener("service:version:group.configurators", listener1);
        configuration.addListener("service:version:group.configurators", listener2);
        configuration.addListener("appname.tag-router", listener3);
        configuration.addListener("appname.tag-router", listener4);

        // 下面设置节点的值后，listener1和2监听器就收到监听了
        setData("/dubbo/config/dubbo/service:version:group.configurators", "new value1");
        Thread.sleep(100);
        // listener3和4收到监听
        setData("/dubbo/config/dubbo/appname.tag-router", "new value2");
        Thread.sleep(100);



        setData("/dubbo/config/appname", "new value3");

        Thread.sleep(5000);

        latch.await();

        // 对前面的验证

        // 进去
        Assertions.assertEquals(1, listener1.getCount("service:version:group.configurators"));
        Assertions.assertEquals(1, listener2.getCount("service:version:group.configurators"));
        Assertions.assertEquals(1, listener3.getCount("appname.tag-router"));
        Assertions.assertEquals(1, listener4.getCount("appname.tag-router"));

        // 进去
        Assertions.assertEquals("new value1", listener1.getValue());
        Assertions.assertEquals("new value1", listener2.getValue());
        Assertions.assertEquals("new value2", listener3.getValue());
        Assertions.assertEquals("new value2", listener4.getValue());
    }

    @Test
    public void testPublishConfig() {
        String key = "user-service";
        String group = "org.apache.dubbo.service.UserService";
        String content = "test";

        // 进去
        assertTrue(configuration.publishConfig(key, group, content));
        // 进去
        assertEquals("test", configuration.getProperties(key, group));

        // 上下两部都是借助Curator客户端，添加节点和获取节点值
    }

    @Test
    public void testGetConfigKeysAndContents() {

        String group = "mapping";
        String key = "org.apache.dubbo.service.UserService";
        String content = "app1";

        String key2 = "org.apache.dubbo.service.UserService2";

        assertTrue(configuration.publishConfig(key, group, content));
        assertTrue(configuration.publishConfig(key2, group, content));

        // 获取同一个组的配置信息，进去（内部client.getChildren().forPath(path);）
        Set<String> configKeys = configuration.getConfigKeys(group);

        assertEquals(new TreeSet(asList(key, key2)), configKeys);
    }

    // 需要实现接口
    private class TestListener implements ConfigurationListener {
        private CountDownLatch latch;
        private String value;
        private Map<String, Integer> countMap = new HashMap<>();

        public TestListener(CountDownLatch latch) {
            this.latch = latch;
        }


        // 指定监听回调函数
        @Override
        public void process(ConfigChangedEvent event) {
            System.out.println(this + ": " + event);
            // 统计每个event出现的次数
            Integer count = countMap.computeIfAbsent(event.getKey(), k -> new Integer(0));
            countMap.put(event.getKey(), ++count);

            value = event.getContent();
            latch.countDown();
        }

        public int getCount(String key) {
            return countMap.get(key);
        }

        public String getValue() {
            return value;
        }
    }

}
