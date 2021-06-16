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
package org.apache.dubbo.config.cache;

import org.apache.dubbo.cache.Cache;
import org.apache.dubbo.cache.CacheFactory;
import org.apache.dubbo.cache.support.threadlocal.ThreadLocalCache;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.MethodConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.RpcInvocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CacheTest
 */
public class CacheTest {

    @BeforeEach
    public void setUp() {
//        ApplicationModel.getConfigManager().clear();
    }

    @AfterEach
    public void tearDown() {
//        ApplicationModel.getConfigManager().clear();
    }

    private void testCache(String type) throws Exception {
        ApplicationConfig applicationConfig = new ApplicationConfig("cache-test");
        // 影响 loadRegistries 方法 ，方法会返回空集合 （注意export的走的分支）
        RegistryConfig registryConfig = new RegistryConfig("N/A");
        // 正好这里设置的是injvm，不会表露到registry，而上面也没设置有效的registry （不过还是会暴露两次，只是两次都是injvm协议暴露的）
        ProtocolConfig protocolConfig = new ProtocolConfig("injvm");
        ServiceConfig<CacheService> service = new ServiceConfig<CacheService>();
        service.setApplication(applicationConfig);
        service.setRegistry(registryConfig);
        service.setProtocol(protocolConfig);
        service.setInterface(CacheService.class.getName());
        service.setRef(new CacheServiceImpl());
        service.export();
        try {
            ReferenceConfig<CacheService> reference = new ReferenceConfig<CacheService>();
            reference.setApplication(applicationConfig);
            reference.setInterface(CacheService.class);
            // 这里直接设置url表示点对点调用（注意refer过程进入的分支），且协议是injvm，且注意后两个参数（含有cache=true参数，在调用
            // getActivateExtension的时候会激活CacheFilter，因为其类上面含有注解@Activate(group = {CONSUMER, PROVIDER}, value = CACHE_KEY)）
            reference.setUrl("injvm://127.0.0.1?scope=remote&cache=true");

            MethodConfig method = new MethodConfig();
            method.setName("findCache");
            // 注意这里
            method.setCache(type);
            // 给Referenc添加了method参数，注意refer的过程是怎么处理的
            reference.setMethods(Arrays.asList(method));

            // 在触发refer的时候 ( invoker = REF_PROTOCOL.refer(interfaceClass, urls.get(0)) )，url如下
            // injvm://127.0.0.1/org.apache.dubbo.config.cache.CacheService?application=cache-test&cache=true&findCache.cache=lru&interface=org.apache.dubbo.config.cache.CacheService&pid=18788&register.ip=30.25.58.142&remote.application=&scope=remote&side=consumer&sticky=false
            /*
            parameters = {Collections$UnmodifiableMap@2909}  size = 10
                "cache" -> "true"
                "side" -> "consumer"
                "remote.application" -> null
                "application" -> "cache-test"
                "register.ip" -> "30.25.58.142"
                "scope" -> "remote"
                "findCache.cache" -> "lru"
                "sticky" -> "false"
                "pid" -> "19446"
                "interface" -> "org.apache.dubbo.config.cache.CacheService"
            methodParameters = {Collections$UnmodifiableMap@2910}  size = 3
                "findCache" -> {HashMap@2929}  size = 1
                    key = "findCache"
                    value = {HashMap@2929}  size = 1
                    "cache" -> "lru"
                "remote" -> {HashMap@2931}  size = 1
                    key = "remote"
                    value = {HashMap@2931}  size = 1
                    "application" -> null
                "register" -> {HashMap@2933}  size = 1
                    key = "register"
                    value = {HashMap@2933}  size = 1
                    "ip" -> "30.25.58.142"*/

            // 注意跟踪下refer的过程（从ProtocolFilterWrapper开始），很easy，没有涉及注册中心，最终走到InjvmProtocol
            CacheService cacheService = reference.get();
            try {
                // // 验证缓存，多次调用返回相同的结果（实际上是服务端每次调用返回值都会增加）
                // verify cache, same result is returned for multiple invocations (in fact, the return value increases
                // on every invocation on the server side)
                String fix = null;
                for (int i = 0; i < 3; i++) {
                    // 断点到 CacheFilter#invoke方法看看过程
                    String result = cacheService.findCache("0");
                    assertTrue(fix == null || fix.equals(result));
                    fix = result;
                    Thread.sleep(100);
                }

                if ("lru".equals(type)) {
                    // default cache.size is 1000 for LRU, should have cache expired if invoke more than 1001 times
                    for (int n = 0; n < 1001; n++) {
                        String pre = null;
                        for (int i = 0; i < 10; i++) {
                            String result = cacheService.findCache(String.valueOf(n));
                            assertTrue(pre == null || pre.equals(result));
                            pre = result;
                        }
                    }

                    // verify if the first cache item is expired in LRU cache
                    String result = cacheService.findCache("0");
                    assertFalse(fix == null || fix.equals(result));
                }
            } finally {
                reference.destroy();
            }
        } finally {
            service.unexport();
        }
    }

    @Test
    public void testCacheLru() throws Exception {
        testCache("lru");
    }

    @Test
    public void testCacheThreadlocal() throws Exception {
        testCache("threadlocal");
    }

    @Test
    public void testCacheProvider() throws Exception {
        CacheFactory cacheFactory = ExtensionLoader.getExtensionLoader(CacheFactory.class).getAdaptiveExtension();

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("findCache.cache", "threadlocal");
        URL url = new URL("dubbo", "127.0.0.1", 29582, "org.apache.dubbo.config.cache.CacheService", parameters);

        Invocation invocation = new RpcInvocation("findCache", CacheService.class.getName(), "", new Class[]{String.class}, new String[]{"0"}, null, null, null);

        Cache cache = cacheFactory.getCache(url, invocation);
        assertTrue(cache instanceof ThreadLocalCache);
    }

}
