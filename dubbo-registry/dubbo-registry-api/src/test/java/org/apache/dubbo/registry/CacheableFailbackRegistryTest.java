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
package org.apache.dubbo.registry;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLStrParser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CacheableFailbackRegistryTest {

    static String service;
    static URL serviceUrl;
    static URL registryUrl;
    static String urlStr;
    MockCacheableRegistryImpl registry;

    @BeforeAll
    static void setProperty() {
        System.setProperty("dubbo.application.url.cache.task.interval", "0");
        System.setProperty("dubbo.application.url.cache.clear.waiting", "0");
    }

    @BeforeEach
    public void setUp() throws Exception {
        service = "org.apache.dubbo.test.DemoService";
        serviceUrl = URL.valueOf("dubbo://127.0.0.1/org.apache.dubbo.test.DemoService?category=providers");
        registryUrl = URL.valueOf("http://1.2.3.4:9090/registry?check=false&file=N/A");
        urlStr = "dubbo%3A%2F%2F172.19.4.113%3A20880%2Forg.apache.dubbo.demo.DemoService%3Fside%3Dprovider%26timeout%3D3000";
    }

    @AfterEach
    public void tearDown() {
        registry.getStringUrls().clear();
        registry.getStringAddress().clear();
        registry.getStringParam().clear();
    }

    @Test
    public void testFullURLCache() {
        final AtomicReference<Integer> resCount = new AtomicReference<>(0);
        registry = new MockCacheableRegistryImpl(registryUrl);
        URL url = URLStrParser.parseEncodedStr(urlStr);

        NotifyListener listener = urls -> resCount.set(urls.size());

        // 添加一个url，这个相当于provider 方法的是mock类专有的方法
        registry.addChildren(url);
        // 内部会走到 MockCacheableRegistryImpl#doSubscribe方法，进去看看，第一次（toUrlsWithoutEmpty方法）肯定没有命中缓存，新构建
        registry.subscribe(serviceUrl, listener);
        assertEquals(1, registry.getStringUrls().get(serviceUrl).size());
        assertEquals(1, resCount.get());

        registry.addChildren(url);
        // 此时 MockCacheableRegistryImpl#doSubscribe方法 会命中缓存
        registry.subscribe(serviceUrl, listener);
        assertEquals(1, registry.getStringUrls().get(serviceUrl).size());
        assertEquals(1, resCount.get());

        // 添加了一个新的provider
        URL url1 = url.addParameter("k1", "v1");
        registry.addChildren(url1);
        // 内部会有一个命中，有一个没有命中
        registry.subscribe(serviceUrl, listener);
        assertEquals(2, registry.getStringUrls().get(serviceUrl).size());
        assertEquals(2, resCount.get());

        URL url2 = url1.setHost("192.168.1.1");
        registry.addChildren(url2);
        // 内部会有2个命中，有一个没有命中
        registry.subscribe(serviceUrl, listener);
        assertEquals(3, registry.getStringUrls().get(serviceUrl).size());
        assertEquals(3, resCount.get());
    }

    @Test
    public void testURLAddressCache() {
        final AtomicReference<Integer> resCount = new AtomicReference<>(0);
        registry = new MockCacheableRegistryImpl(registryUrl);
        URL url = URLStrParser.parseEncodedStr(urlStr);

        NotifyListener listener = urls -> resCount.set(urls.size());

        registry.addChildren(url);
        registry.subscribe(serviceUrl, listener);
        // 注意这里调用的是getStringAddress，前面的方法调用的是getStringUrl
        assertEquals(1, registry.getStringAddress().size());
        assertEquals(1, resCount.get());

        URL url1 = url.addParameter("k1", "v1");
        registry.addChildren(url1);
        registry.subscribe(serviceUrl, listener);
        // 可以发现仅仅是加了新参数，虽然走了createUrl，但是内部还是会命中stringAddress缓存的
        assertEquals(1, registry.getStringAddress().size());
        assertEquals(2, resCount.get());

        URL url2 = url1.setHost("192.168.1.1");
        registry.addChildren(url2);
        registry.subscribe(serviceUrl, listener);
        // 因为ip/address变了，所以这次就没有命中了
        assertEquals(2, registry.getStringAddress().size());
        assertEquals(3, resCount.get());
    }

    @Test
    public void testURLParamCache() {
        final AtomicReference<Integer> resCount = new AtomicReference<>(0);
        registry = new MockCacheableRegistryImpl(registryUrl);
        URL url = URLStrParser.parseEncodedStr(urlStr);

        NotifyListener listener = urls -> resCount.set(urls.size());

        registry.addChildren(url);
        registry.subscribe(serviceUrl, listener);
        assertEquals(1, registry.getStringParam().size());
        assertEquals(1, resCount.get());

        URL url1 = url.addParameter("k1", "v1");
        registry.addChildren(url1);
        registry.subscribe(serviceUrl, listener);
        assertEquals(2, registry.getStringParam().size());
        assertEquals(2, resCount.get());

        URL url2 = url1.setHost("192.168.1.1");
        registry.addChildren(url2);
        registry.subscribe(serviceUrl, listener);
        // 没有命中stringAddress，但是命中了stringParam
        assertEquals(2, registry.getStringParam().size());
        assertEquals(3, resCount.get());
    }

    @Test
    public void testRemove() throws Exception {
        final AtomicReference<Integer> resCount = new AtomicReference<>(0);
        registry = new MockCacheableRegistryImpl(registryUrl);
        URL url = URLStrParser.parseEncodedStr(urlStr);

        NotifyListener listener = urls -> resCount.set(urls.size());

        registry.addChildren(url);
        // 内部会存到相应的缓存
        registry.subscribe(serviceUrl, listener);
        assertEquals(1, registry.getStringUrls().get(serviceUrl).size());
        assertEquals(1, registry.getStringAddress().size());
        assertEquals(1, registry.getStringParam().size());
        assertEquals(1, resCount.get());

        registry.clearChildren();
        URL url1 = url.addParameter("k1", "v1");
        registry.addChildren(url1);
        // 无法命中stringUrl，会走createUrl，命中stringAddress，未命中stringParam
        // 内部 ServiceAddressURL cachedURL = oldURLs.remove(rawProvider); 无法删除前面一段的元素，因为会触发evict
        registry.subscribe(serviceUrl, listener);
        assertEquals(1, registry.getStringUrls().get(serviceUrl).size());
        assertEquals(1, resCount.get());

        // After RemovalTask
        assertEquals(1, registry.getStringParam().size());
        // 注意如下（stringParam有两份，分别是上面两段，而stringAdress只有一份）
        // StringAddress will be deleted because the related stringUrls cache has been deleted.
        assertEquals(0, registry.getStringAddress().size());

        registry.clearChildren();
        URL url2 = url1.setHost("192.168.1.1");
        registry.addChildren(url2);
        registry.subscribe(serviceUrl, listener);
        assertEquals(1, registry.getStringUrls().get(serviceUrl).size());
        assertEquals(1, resCount.get());

        // After RemovalTask
        assertEquals(1, registry.getStringAddress().size());
        // StringParam will be deleted because the related stringUrls cache has been deleted.
        assertEquals(0, registry.getStringParam().size());
    }

}
