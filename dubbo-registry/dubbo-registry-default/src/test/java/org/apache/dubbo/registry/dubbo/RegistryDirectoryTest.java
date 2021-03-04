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
package org.apache.dubbo.registry.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.LogUtil;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.registry.NotifyListener;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;
import org.apache.dubbo.registry.integration.RegistryDirectory;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.cluster.RouterChain;
import org.apache.dubbo.rpc.cluster.loadbalance.LeastActiveLoadBalance;
import org.apache.dubbo.rpc.cluster.loadbalance.RoundRobinLoadBalance;
import org.apache.dubbo.rpc.cluster.router.script.ScriptRouterFactory;
import org.apache.dubbo.rpc.cluster.support.wrapper.MockClusterInvoker;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.protocol.InvokerWrapper;
import org.apache.dubbo.rpc.service.GenericService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.script.ScriptEngineManager;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.apache.dubbo.common.constants.CommonConstants.$INVOKE;
import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.APPLICATION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.CONSUMER_SIDE;
import static org.apache.dubbo.common.constants.CommonConstants.DISABLED_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ENABLED_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.LOADBALANCE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SIDE_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CATEGORY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.CONFIGURATORS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.EMPTY_PROTOCOL;
import static org.apache.dubbo.common.constants.RegistryConstants.PROVIDERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.ROUTERS_CATEGORY;
import static org.apache.dubbo.common.constants.RegistryConstants.ROUTE_PROTOCOL;
import static org.apache.dubbo.registry.Constants.CONSUMER_PROTOCOL;
import static org.apache.dubbo.rpc.Constants.MOCK_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.INVOCATION_NEED_MOCK;
import static org.apache.dubbo.rpc.cluster.Constants.MOCK_PROTOCOL;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.ROUTER_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.RULE_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.TYPE_KEY;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings({"rawtypes", "unchecked"})
public class RegistryDirectoryTest {

    private static boolean isScriptUnsupported = new ScriptEngineManager().getEngineByName("javascript") == null;
    RegistryFactory registryFactory = ExtensionLoader.getExtensionLoader(RegistryFactory.class).getAdaptiveExtension();
    Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
    String service = DemoService.class.getName();
    RpcInvocation invocation = new RpcInvocation();
    URL noMeaningUrl = URL.valueOf("notsupport:/" + service + "?refer=" + URL.encode("interface=" + service));
    URL SERVICEURL = URL.valueOf("dubbo://127.0.0.1:9091/" + service + "?lazy=true&side=consumer&application=mockName");
    URL SERVICEURL2 = URL.valueOf("dubbo://127.0.0.1:9092/" + service + "?lazy=true&side=consumer&application=mockName");
    URL SERVICEURL3 = URL.valueOf("dubbo://127.0.0.1:9093/" + service + "?lazy=true&side=consumer&application=mockName");
    URL SERVICEURL_DUBBO_NOPATH = URL.valueOf("dubbo://127.0.0.1:9092" + "?lazy=true&side=consumer&application=mockName");

    private Registry registry = Mockito.mock(Registry.class);

    @BeforeEach
    public void setUp() {
        ApplicationModel.setApplication("RegistryDirectoryTest");
    }

    private RegistryDirectory getRegistryDirectory(URL url) {
        RegistryDirectory registryDirectory = new RegistryDirectory(URL.class, url);
        registryDirectory.setProtocol(protocol);
        registryDirectory.setRegistry(registry);
        registryDirectory.setRouterChain(RouterChain.buildChain(url));
        Map<String, String> queryMap = StringUtils.parseQueryString(url.getParameterAndDecoded(REFER_KEY));
        URL subscribeUrl = new URL(CONSUMER_PROTOCOL, "10.20.30.40", 0, url.getServiceInterface(), queryMap);
        registryDirectory.subscribe(subscribeUrl);
        // asert empty
        List invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(0, invokers.size());
        Assertions.assertFalse(registryDirectory.isAvailable());
        return registryDirectory;
    }

    private RegistryDirectory getRegistryDirectory() {
        return getRegistryDirectory(noMeaningUrl);
    }

    @Test
    public void test_Constructor_WithErrorParam() {
        try {
            new RegistryDirectory(null, null);
            fail();
        } catch (IllegalArgumentException e) {

        }
        try {
            // null url
            new RegistryDirectory(null, noMeaningUrl);
            fail();
        } catch (IllegalArgumentException e) {

        }
        try {
            // no servicekey
            new RegistryDirectory(RegistryDirectoryTest.class, URL.valueOf("dubbo://10.20.30.40:9090"));
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void test_Constructor_CheckStatus() throws Exception {
        URL url = URL.valueOf("notsupported://10.20.30.40/" + service + "?a=b").addParameterAndEncoded(REFER_KEY,
                "foo=bar");
        RegistryDirectory reg = getRegistryDirectory(url);
        Field field = reg.getClass().getSuperclass().getSuperclass().getDeclaredField("queryMap");
        field.setAccessible(true);
        Map<String, String> queryMap = (Map<String, String>) field.get(reg);
        Assertions.assertEquals("bar", queryMap.get("foo"));
        Assertions.assertEquals(url.setProtocol(CONSUMER_PROTOCOL).clearParameters().addParameter("foo", "bar"), reg.getConsumerUrl());
    }

    @Test
    public void testNotified_Normal() {
        RegistryDirectory registryDirectory = getRegistryDirectory();// 进去
        test_Notified2invokers(registryDirectory);// 进去
        test_Notified1invokers(registryDirectory);// 进去（这个+下面的 和上面test_Notified2invokers逻辑的基本相似）
        test_Notified3invokers(registryDirectory);// 进去
        testforbid(registryDirectory);// 进去
    }

    /**
     * Test push only router
     */
    @Test
    public void testNotified_Normal_withRouters() {
        LogUtil.start();
        RegistryDirectory registryDirectory = getRegistryDirectory();
        test_Notified1invokers(registryDirectory);// 进去
        test_Notified_only_routers(registryDirectory);// 进去
        Assertions.assertTrue(registryDirectory.isAvailable());
        Assertions.assertTrue(LogUtil.checkNoError(), "notify no invoker urls ,should not error");
        LogUtil.stop();
        test_Notified2invokers(registryDirectory);// 进去

    }

    @Test
    public void testNotified_WithError() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        List<URL> serviceUrls = new ArrayList<URL>();
        // ignore error log
        URL badurl = URL.valueOf("notsupported://127.0.0.1/" + service);
        serviceUrls.add(badurl);
        serviceUrls.add(SERVICEURL);

        registryDirectory.notify(serviceUrls);// 进去 ，内部会在处理badurl的时候，发现其协议"notsupported"不是所允许的扩展类型，会忽略该url，因此下面调用list返回的个数为1
        Assertions.assertTrue(registryDirectory.isAvailable());
        List invokers = registryDirectory.list(invocation);// 进去
        Assertions.assertEquals(1, invokers.size());
    }

    @Test
    public void testNotified_WithDuplicateUrls() {
        // 这个测试程序就不说了，前面分析过了，详见 test_Notified2invokers 方法

        List<URL> serviceUrls = new ArrayList<URL>();
        // ignore error log
        serviceUrls.add(SERVICEURL);
        serviceUrls.add(SERVICEURL);

        RegistryDirectory registryDirectory = getRegistryDirectory();
        registryDirectory.notify(serviceUrls);
        List invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(1, invokers.size());
    }

    // forbid
    private void testforbid(RegistryDirectory registryDirectory) {
        invocation = new RpcInvocation();
        List<URL> serviceUrls = new ArrayList<URL>();
        serviceUrls.add(new URL(EMPTY_PROTOCOL, ANYHOST_VALUE, 0, service, CATEGORY_KEY, PROVIDERS_CATEGORY)); // 注意是empty://
        registryDirectory.notify(serviceUrls);// 进去
        Assertions.assertFalse(registryDirectory.isAvailable(),
                "invokers size=0 ,then the registry directory is not available");
        try {
            registryDirectory.list(invocation);
            fail("forbid must throw RpcException");
        } catch (RpcException e) {
            Assertions.assertEquals(RpcException.FORBIDDEN_EXCEPTION, e.getCode());
        }
    }

    //The test call is independent of the path of the registry url 测试调用独立于注册中心url的路径
    @Test
    public void test_NotifiedDubbo1() {
        URL errorPathUrl = URL.valueOf("notsupport:/" + "xxx" + "?refer=" + URL.encode("interface=" + service));
        RegistryDirectory registryDirectory = getRegistryDirectory(errorPathUrl);
        List<URL> serviceUrls = new ArrayList<URL>();
        URL Dubbo1URL = URL.valueOf("dubbo://127.0.0.1:9098?lazy=true");
        serviceUrls.add(Dubbo1URL.addParameter("methods", "getXXX"));
        registryDirectory.notify(serviceUrls);// 进去
        Assertions.assertTrue(registryDirectory.isAvailable());

        invocation = new RpcInvocation();

        List<Invoker<DemoService>> invokers = registryDirectory.list(invocation);// 进去
        Assertions.assertEquals(1, invokers.size());

        invocation.setMethodName("getXXX");// 制定方法名没啥用，因为内部并没有ConditionRouter做过滤，所以下面invokers.size()还是1，和前面一样
        invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(1, invokers.size());
        // invokers的invoker实例的生成逻辑见RegistryDirectory中new InvokerDelegate 处，且注意传给其的url是对providerUrl经过merge的，详见其mergeUrl方法
        Assertions.assertEquals(DemoService.class.getName(), invokers.get(0).getUrl().getPath());
    }

    // notify one invoker
    private void test_Notified_only_routers(RegistryDirectory registryDirectory) {
        List<URL> serviceUrls = new ArrayList<URL>();
        serviceUrls.add(URL.valueOf("empty://127.0.0.1/?category=routers"));// 注意empty:// 和category参数对，notify内部不会吧route url添加到对应的容器
        registryDirectory.notify(serviceUrls);
    }

    // notify one invoker
    private void test_Notified1invokers(RegistryDirectory registryDirectory) {

        List<URL> serviceUrls = new ArrayList<URL>();
        serviceUrls.add(SERVICEURL.addParameter("methods", "getXXX1").addParameter(APPLICATION_KEY, "mockApplicationName"));// .addParameter("refer.autodestroy", "true")
        registryDirectory.notify(serviceUrls);
        Assertions.assertTrue(registryDirectory.isAvailable());

        invocation = new RpcInvocation();

        List invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(1, invokers.size());

        invocation.setMethodName("getXXX");
        invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(1, invokers.size());

        invocation.setMethodName("getXXX1");
        invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(1, invokers.size());

        invocation.setMethodName("getXXX2");
        invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(1, invokers.size());
    }

    // 2 invokers===================================
    private void test_Notified2invokers(RegistryDirectory registryDirectory) {
        List<URL> serviceUrls = new ArrayList<URL>();
        serviceUrls.add(SERVICEURL.addParameter("methods", "getXXX1"));
        serviceUrls.add(SERVICEURL2.addParameter("methods", "getXXX1,getXXX2"));// 注意这个和下面的一样
        serviceUrls.add(SERVICEURL2.addParameter("methods", "getXXX1,getXXX2"));

        registryDirectory.notify(serviceUrls);// 进去
        Assertions.assertTrue(registryDirectory.isAvailable());

        invocation = new RpcInvocation();

        List invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers.size()); // 前面有两个相同的SERVICEURL2，前面notify方法内部会去重，所以最后只有两个

        invocation.setMethodName("getXXX"); // 这里和下面指定的getXXX和getXXX1没啥用，list内部逻辑是一样的（主要是没有ConditionRoute），都是返回这两个invoker
        invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers.size());

        invocation.setMethodName("getXXX1");
        invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers.size());
    }

    // 3 invoker notifications===================================
    private void test_Notified3invokers(RegistryDirectory registryDirectory) {
        List<URL> serviceUrls = new ArrayList<URL>();
        serviceUrls.add(SERVICEURL.addParameter("methods", "getXXX1"));
        serviceUrls.add(SERVICEURL2.addParameter("methods", "getXXX1,getXXX2"));
        serviceUrls.add(SERVICEURL3.addParameter("methods", "getXXX1,getXXX2,getXXX3"));

        registryDirectory.notify(serviceUrls);
        Assertions.assertTrue(registryDirectory.isAvailable());

        invocation = new RpcInvocation();

        List invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(3, invokers.size());

        invocation.setMethodName("getXXX");
        invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(3, invokers.size());

        invocation.setMethodName("getXXX1");
        invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(3, invokers.size());

        invocation.setMethodName("getXXX2");
        invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(3, invokers.size());

        invocation.setMethodName("getXXX3");
        invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(3, invokers.size());
    }

    @Test
    public void testParametersMerge() {
        RegistryDirectory registryDirectory = getRegistryDirectory();

        URL regurl = noMeaningUrl.addParameter("test", "reg").addParameterAndEncoded(REFER_KEY,
                "key=query&" + LOADBALANCE_KEY + "=" + LeastActiveLoadBalance.NAME);
        // notsupport:///org.apache.dubbo.registry.dubbo.RegistryDirectoryTest$DemoService?refer=key=query&loadbalance=leastactive&test=reg
        RegistryDirectory<RegistryDirectoryTest> registryDirectory2 = new RegistryDirectory(
                RegistryDirectoryTest.class,
                regurl);// 进去
        registryDirectory2.setProtocol(protocol); // 注意前后两个 RegistryDirectory 实例

        List<URL> serviceUrls = new ArrayList<URL>();
        // The parameters of the inspection registry need to be cleared  需要清除检查注册表参数
        {
            serviceUrls.clear();
            serviceUrls.add(SERVICEURL.addParameter("methods", "getXXX1"));

            // notify内部会生成invoker，且注意provider url(就是上面的 serviceUrls) 会和 RegistryDirectory 实例的queryMap属性（在AbstractDirectory中）
            // 进行合并(注意是queryMap的信息合并到provider url 上，当然不仅仅merge queryMap，还有其他信息也会合并到provider url上，详见merge方法)
            registryDirectory.notify(serviceUrls);

            invocation = new RpcInvocation();
            List invokers = registryDirectory.list(invocation);

            Invoker invoker = (Invoker) invokers.get(0);
            URL url = invoker.getUrl();
            // 这里为null，因为registryDirectory的queryMap没有"key"参数项（registryDirectory2有 - -，不过这段代码块并没有和registryDirectory2交互，后面会有交互）
            Assertions.assertNull(url.getParameter("key"));
        }
        // The parameters of the provider for the inspection service need merge 检查服务的提供者的参数需要合并
        {
            serviceUrls.clear();
            serviceUrls.add(SERVICEURL.addParameter("methods", "getXXX2").addParameter("key", "provider"));

            registryDirectory.notify(serviceUrls);
            invocation = new RpcInvocation();
            List invokers = registryDirectory.list(invocation);

            Invoker invoker = (Invoker) invokers.get(0);
            URL url = invoker.getUrl();
            Assertions.assertEquals("provider", url.getParameter("key"));// provider url自己的参数，肯定能取出来
        }
        // The parameters of the test service query need to be with the providermerge.
        {
            serviceUrls.clear();
            serviceUrls.add(SERVICEURL.addParameter("methods", "getXXX3").addParameter("key", "provider"));
            registryDirectory2.setRegistry(registry);
            registryDirectory2.setRouterChain(RouterChain.buildChain(noMeaningUrl));
            registryDirectory2.subscribe(noMeaningUrl);
            // notify内部会生成invoker，且注意provider url(就是上面的 serviceUrls) 会和 前面的registryDirectory2的
            // regUrl进行合并(注意是regUrl的信息合并到provider url 上)
            registryDirectory2.notify(serviceUrls);
            invocation = new RpcInvocation();
            List invokers = registryDirectory2.list(invocation);

            Invoker invoker = (Invoker) invokers.get(0);
            URL url = invoker.getUrl();
            // 前面notify内部做了merge，所以regUrl的一些信息比如queryMap就合并到了这个url上，虽然regUrl和SERVICEURL(provider url)
            // 都有"key"参数对，但是queryMap会覆盖原provider url的
            Assertions.assertEquals("query", url.getParameter("key"));
        }

        {
            serviceUrls.clear();
            serviceUrls.add(SERVICEURL.addParameter("methods", "getXXX1"));
            registryDirectory.notify(serviceUrls);

            invocation = new RpcInvocation();
            List invokers = registryDirectory.list(invocation);

            Invoker invoker = (Invoker) invokers.get(0);
            URL url = invoker.getUrl();
            Assertions.assertFalse(url.getParameter(Constants.CHECK_KEY, false));
        }
        {
            serviceUrls.clear();
            serviceUrls.add(SERVICEURL.addParameter(LOADBALANCE_KEY, RoundRobinLoadBalance.NAME));
            registryDirectory2.notify(serviceUrls);

            invocation = new RpcInvocation();
            invocation.setMethodName("get");
            List invokers = registryDirectory2.list(invocation);

            Invoker invoker = (Invoker) invokers.get(0);
            URL url = invoker.getUrl();
            // url的methodParameters没有"get"项（url的参数如果有类似这种a.b=c，那么就会作为methodParameters的项，key为a表示方法名为a，value
            // 也是一个map，kv分别为bc） 内部发现找不到会直接从url本身的参数中查找loadbalance的值
            Assertions.assertEquals(LeastActiveLoadBalance.NAME, url.getMethodParameter("get", LOADBALANCE_KEY));
        }
        //test geturl
        {
            Assertions.assertNull(registryDirectory2.getUrl().getParameter("mock"));
            serviceUrls.clear();
            serviceUrls.add(SERVICEURL.addParameter(MOCK_KEY, "true"));
            registryDirectory2.notify(serviceUrls);

            Assertions.assertEquals("true", ((InvokerWrapper<?>) registryDirectory2.getInvokers().get(0)).getUrl().getParameter("mock"));
        }
    }

    /**
     * When destroying, RegistryDirectory should: 1. be disconnected from Registry 2. destroy all invokers
     */
    @Test
    public void testDestroy() {
        RegistryDirectory registryDirectory = getRegistryDirectory();

        List<URL> serviceUrls = new ArrayList<URL>();
        serviceUrls.add(SERVICEURL.addParameter("methods", "getXXX1"));
        serviceUrls.add(SERVICEURL2.addParameter("methods", "getXXX1,getXXX2"));
        serviceUrls.add(SERVICEURL3.addParameter("methods", "getXXX1,getXXX2,getXXX3"));

        registryDirectory.notify(serviceUrls);
        List<Invoker> invokers = registryDirectory.list(invocation);
        Assertions.assertTrue(registryDirectory.isAvailable());// 进去
        Assertions.assertTrue(invokers.get(0).isAvailable());// 进去

        registryDirectory.destroy(); // 进去
        Assertions.assertFalse(registryDirectory.isAvailable());// 进去
        Assertions.assertFalse(invokers.get(0).isAvailable());// 进去

        registryDirectory.destroy();// 进去

        List<Invoker<RegistryDirectoryTest>> cachedInvokers = registryDirectory.getInvokers();
        Map<URL, Invoker<RegistryDirectoryTest>> urlInvokerMap = registryDirectory.getUrlInvokerMap();

        Assertions.assertNull(cachedInvokers);
        Assertions.assertEquals(0, urlInvokerMap.size());
        // List<U> urls = mockRegistry.getSubscribedUrls();

        RpcInvocation inv = new RpcInvocation();
        try {
            registryDirectory.list(inv);// 进去
            fail();
        } catch (RpcException e) {
            Assertions.assertTrue(e.getMessage().contains("already destroyed"));
        }
    }

    @Test
    public void testDestroy_WithDestroyRegistry() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        CountDownLatch latch = new CountDownLatch(1);
        registryDirectory.setRegistry(new MockRegistry(latch));// 注意MockRegistry的unSubscribe方法
        registryDirectory.subscribe(URL.valueOf("consumer://" + NetUtils.getLocalHost() + "/DemoService?category=providers"));
        registryDirectory.destroy();// 进去
        Assertions.assertEquals(0, latch.getCount());
    }

    @Test
    public void testDestroy_WithDestroyRegistry_WithError() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        registryDirectory.setRegistry(new MockRegistry(true));// 不知道干啥的，
        registryDirectory.destroy();
    }

    @Test
    public void testDubbo1UrlWithGenericInvocation() { // 不看

        RegistryDirectory registryDirectory = getRegistryDirectory();

        List<URL> serviceUrls = new ArrayList<URL>();
        URL serviceURL = SERVICEURL_DUBBO_NOPATH.addParameter("methods", "getXXX1,getXXX2,getXXX3");
        serviceUrls.add(serviceURL);

        registryDirectory.notify(serviceUrls);

        // Object $invoke(String method, String[] parameterTypes, Object[] args) throws GenericException;
        invocation = new RpcInvocation($INVOKE, GenericService.class.getName(), "", new Class[]{String.class, String[].class, Object[].class},
                new Object[]{"getXXX1", "", new Object[]{}});

        List<Invoker> invokers = registryDirectory.list(invocation);

        Assertions.assertEquals(1, invokers.size());
//        Assertions.assertEquals(
//                serviceURL.setPath(service).addParameters("check", "false", "interface", DemoService.class.getName(), REMOTE_APPLICATION_KEY, serviceURL.getParameter(APPLICATION_KEY))
//                , invokers.get(0).getUrl()
//        );

    }

    /**
     * When the first arg of a method is String or Enum, Registry server can do parameter-value-based routing.
     */
    @Disabled("Parameter routing is not available at present.")
    @Test
    public void testParmeterRoute() {// 不看
        RegistryDirectory registryDirectory = getRegistryDirectory();
        List<URL> serviceUrls = new ArrayList<URL>();
        serviceUrls.add(SERVICEURL.addParameter("methods", "getXXX1.napoli"));
        serviceUrls.add(SERVICEURL2.addParameter("methods", "getXXX1.MORGAN,getXXX2"));
        serviceUrls.add(SERVICEURL3.addParameter("methods", "getXXX1.morgan,getXXX2,getXXX3"));

        registryDirectory.notify(serviceUrls);

        invocation = new RpcInvocation($INVOKE, GenericService.class.getName(), "",
                new Class[]{String.class, String[].class, Object[].class},
                new Object[]{"getXXX1", new String[]{"Enum"}, new Object[]{Param.MORGAN}});

        List invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(1, invokers.size());
    }

    /**
     * Empty notify cause forbidden, non-empty notify cancels forbidden state
     * 空通知导致禁用，非空通知取消禁用状态
     */
    @Test
    public void testEmptyNotifyCauseForbidden() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        List invokers = null;

        List<URL> serviceUrls = new ArrayList<URL>();
        registryDirectory.notify(serviceUrls); // 容器为空 // 进去

        RpcInvocation inv = new RpcInvocation();
        try {
            invokers = registryDirectory.list(inv);// 返回的size = 0
        } catch (RpcException e) {
            // 实际并没有进这个catch块，不过我们注意下 FORBIDDEN_EXCEPTION 的生成点，在doList的第一步判定禁用的话（forbidden=true）会构建该异常
            // refreshInvoker的一个分支是置forbidden=true的唯一处
            Assertions.assertEquals(RpcException.FORBIDDEN_EXCEPTION, e.getCode());
            Assertions.assertFalse(registryDirectory.isAvailable());
        }

        serviceUrls.add(SERVICEURL.addParameter("methods", "getXXX1"));
        serviceUrls.add(SERVICEURL2.addParameter("methods", "getXXX1,getXXX2"));
        serviceUrls.add(SERVICEURL3.addParameter("methods", "getXXX1,getXXX2,getXXX3"));

        registryDirectory.notify(serviceUrls);
        inv.setMethodName("getXXX2");
        invokers = registryDirectory.list(inv);
        Assertions.assertTrue(registryDirectory.isAvailable());
        Assertions.assertEquals(3, invokers.size());
    }

    /**
     * 1. notify twice, the second time notified router rules should completely replace the former one. 2. notify with
     * no router url, do nothing to current routers 3. notify with only one router url, with router=clean, clear all
     * current routers
     1. 通知两次，第二次通知的路由器规则应该完全取代前一个。
     2. 没有路由器url的通知，对当前的路由器什么都不做
     3.只使用一个路由器url通知，使用router=clean，清除所有当前的路由器
     */
    @Test
    public void testNotifyRouterUrls() { // 这个不看
        if (isScriptUnsupported) return;
        RegistryDirectory registryDirectory = getRegistryDirectory();
        URL routerurl = URL.valueOf(ROUTE_PROTOCOL + "://127.0.0.1:9096/");
        URL routerurl2 = URL.valueOf(ROUTE_PROTOCOL + "://127.0.0.1:9097/");

        List<URL> serviceUrls = new ArrayList<URL>();
        // without ROUTER_KEY, the first router should not be created.
        serviceUrls.add(routerurl.addParameter(CATEGORY_KEY, ROUTERS_CATEGORY).addParameter(TYPE_KEY, "javascript").addParameter(ROUTER_KEY, "notsupported").addParameter(RULE_KEY, "function test1(){}"));
        serviceUrls.add(routerurl2.addParameter(CATEGORY_KEY, ROUTERS_CATEGORY).addParameter(TYPE_KEY, "javascript").addParameter(ROUTER_KEY,
                ScriptRouterFactory.NAME).addParameter(RULE_KEY,
                "function test1(){}"));

        //0 = {URL@3806} "route://127.0.0.1:9096?category=routers&router=notsupported&rule=function test1(){}&type=javascript"
        //1 = {URL@3807} "route://127.0.0.1:9097?category=routers&router=script&rule=function test1(){}&type=javascript"

        // FIXME
        /*registryDirectory.notify(serviceUrls);
        RouterChain routerChain = registryDirectory.getRouterChain();
        //default invocation selector
        Assertions.assertEquals(1 + 1, routers.size());
        Assertions.assertTrue(ScriptRouter.class == routers.get(1).getClass() || ScriptRouter.class == routers.get(0).getClass());

        registryDirectory.notify(new ArrayList<URL>());
        routers = registryDirectory.getRouters();
        Assertions.assertEquals(1 + 1, routers.size());
        Assertions.assertTrue(ScriptRouter.class == routers.get(1).getClass() || ScriptRouter.class == routers.get(0).getClass());

        serviceUrls.clear();
        serviceUrls.add(routerurl.addParameter(Constants.ROUTER_KEY, Constants.ROUTER_TYPE_CLEAR));
        registryDirectory.notify(serviceUrls);
        routers = registryDirectory.getRouters();
        Assertions.assertEquals(0 + 1, routers.size());*/
    }

    /**
     * Test whether the override rule have a high priority
     * Scene: first push override , then push invoker
     * 测试覆盖规则是否具有高优先级
     * 场景:首先push override，然后push调用
     */
    @Test
    public void testNotifyoverrideUrls_beforeInvoker() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        List<URL> overrideUrls = new ArrayList<URL>();
        overrideUrls.add(URL.valueOf("override://0.0.0.0?timeout=1&connections=5"));
        registryDirectory.notify(overrideUrls);
        //The registry is initially pushed to override only, and the directory state should be false because there is no invoker.
        // 注册表最初只被推入以覆盖，并且目录状态应该为false，因为没有调用程序。
        Assertions.assertFalse(registryDirectory.isAvailable());//状态为false，原因看上面注释，进去


        List<URL> serviceUrls = new ArrayList<URL>();
        serviceUrls.add(SERVICEURL.addParameter("timeout", "1000"));
        serviceUrls.add(SERVICEURL2.addParameter("timeout", "1000").addParameter("connections", "10"));

        registryDirectory.notify(serviceUrls);
        // After pushing two provider, the directory state is restored to true
        Assertions.assertTrue(registryDirectory.isAvailable());//状态为true，原因看上面注释，进去

        //Start validation of parameter values

        invocation = new RpcInvocation();

        List<Invoker<?>> invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers.size());

        // overrideUrls的参数 timeout和connections 覆盖了两个provider url的参数
        Assertions.assertEquals("1", invokers.get(0).getUrl().getParameter("timeout"), "override rute must be first priority");
        Assertions.assertEquals("5", invokers.get(0).getUrl().getParameter("connections"), "override rute must be first priority");
    }

    /**
     * Test whether the override rule have a high priority
     * Scene: first push override , then push invoker
     */
    @Test
    public void testNotifyoverrideUrls_afterInvoker() {
        RegistryDirectory registryDirectory = getRegistryDirectory();

        //After pushing two provider, the directory state is restored to true
        List<URL> serviceUrls = new ArrayList<URL>();
        serviceUrls.add(SERVICEURL.addParameter("timeout", "1000"));
        serviceUrls.add(SERVICEURL2.addParameter("timeout", "1000").addParameter("connections", "10"));

        registryDirectory.notify(serviceUrls);
        Assertions.assertTrue(registryDirectory.isAvailable());

        List<URL> overrideUrls = new ArrayList<URL>();
        overrideUrls.add(URL.valueOf("override://0.0.0.0?timeout=1&connections=5"));
        registryDirectory.notify(overrideUrls);// 进去 因为是override协议，所以会进configurators容器，并且merge方法会示使用configurators容器的元素对provider url进行覆盖

        //Start validation of parameter values

        invocation = new RpcInvocation();

        List<Invoker<?>> invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers.size());

        // 全部被覆盖为overrideUrls的参数对
        Assertions.assertEquals("1", invokers.get(0).getUrl().getParameter("timeout"), "override rute must be first priority");
        Assertions.assertEquals("5", invokers.get(0).getUrl().getParameter("connections"), "override rute must be first priority");
    }

    /**
     * Test whether the override rule have a high priority
     * Scene: push override rules with invoker
     */
    @Test
    public void testNotifyoverrideUrls_withInvoker() {
        RegistryDirectory registryDirectory = getRegistryDirectory();

        List<URL> durls = new ArrayList<URL>();
        durls.add(SERVICEURL.addParameter("timeout", "1000"));
        durls.add(SERVICEURL2.addParameter("timeout", "1000").addParameter("connections", "10"));
        durls.add(URL.valueOf("override://0.0.0.0?timeout=1&connections=5"));

        registryDirectory.notify(durls); // 和前面test方法逻辑差不多，只是这里是把override url 和普通的provider url合在一起了，内部在分组的时候还是会填充configurators容器的
        Assertions.assertTrue(registryDirectory.isAvailable());

        //Start validation of parameter values

        invocation = new RpcInvocation();

        List<Invoker<?>> invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers.size());

        Assertions.assertEquals("1", invokers.get(0).getUrl().getParameter("timeout"), "override rute must be first priority");
        Assertions.assertEquals("5", invokers.get(0).getUrl().getParameter("connections"), "override rute must be first priority");
    }

    /**
     * Test whether the override rule have a high priority
     * Scene: the rules of the push are the same as the parameters of the provider
     * Expectation: no need to be re-referenced
     */
    @Test
    public void testNotifyoverrideUrls_Nouse() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        invocation = new RpcInvocation();

        List<URL> durls = new ArrayList<URL>();
        durls.add(SERVICEURL.addParameter("timeout", "1"));// One is the same, one is different ，这句话注释是和下面的 override url 的参数对比较的
        durls.add(SERVICEURL2.addParameter("timeout", "1").addParameter("connections", "5"));
        registryDirectory.notify(durls);

        List<Invoker<?>> invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers.size());

        Map<String, Invoker<?>> map = new HashMap<>();
        map.put(invokers.get(0).getUrl().getAddress(), invokers.get(0));
        map.put(invokers.get(1).getUrl().getAddress(), invokers.get(1));

        durls = new ArrayList<URL>();
        durls.add(URL.valueOf("override://0.0.0.0?timeout=1&connections=5"));// override url
        // 上面的 override url 和 SERVICEURL 参数 对比发现，timeout参数对是相同的，但是connections参数在override url有，而在SERVICEURL没有，所以
        // 在RegistryDirectory#overrideWithConfigurators方法会使用configurator对象进行覆盖，并返回新的URL对象，也就导致后面list方法调用返回的
        // invokers集合和前面list返回的invokers集合有一个invoker是不同的，即 SERVICEURL对应的那个invoker，可以看最后的断言比较
        registryDirectory.notify(durls);
        Assertions.assertTrue(registryDirectory.isAvailable());

        invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers.size());

        Map<String, Invoker<?>> map2 = new HashMap<>();
        map2.put(invokers.get(0).getUrl().getAddress(), invokers.get(0));
        map2.put(invokers.get(1).getUrl().getAddress(), invokers.get(1));

        //The parameters are different and must be rereferenced.
        Assertions.assertNotSame(map.get(SERVICEURL.getAddress()), map2.get(SERVICEURL.getAddress()),
                "object should not same");

        //The parameters can not be rereferenced
        Assertions.assertSame(map.get(SERVICEURL2.getAddress()), map2.get(SERVICEURL2.getAddress()),
                "object should not same");
    }

    // 😡😡😡😡😡 下面暂时未看，可以在写RPC的时候有时间的话，再来看下 😡😡😡😡😡
    // 😡😡😡😡😡 下面暂时未看，可以在写RPC的时候有时间的话，再来看下 😡😡😡😡😡
    // 😡😡😡😡😡 下面暂时未看，可以在写RPC的时候有时间的话，再来看下 😡😡😡😡😡

    /**
     * Test override rules for a certain provider
     */
    @Test
    public void testNofityOverrideUrls_Provider() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        invocation = new RpcInvocation();

        List<URL> durls = new ArrayList<URL>();
        durls.add(SERVICEURL.setHost("10.20.30.140").addParameter("timeout", "1").addParameter(SIDE_KEY, CONSUMER_SIDE));//One is the same, one is different
        durls.add(SERVICEURL2.setHost("10.20.30.141").addParameter("timeout", "2").addParameter(SIDE_KEY, CONSUMER_SIDE));
        registryDirectory.notify(durls);

        durls = new ArrayList<URL>();
        durls.add(URL.valueOf("override://0.0.0.0?timeout=3"));
        durls.add(URL.valueOf("override://10.20.30.141:9092?timeout=4"));
        registryDirectory.notify(durls);

        List<Invoker<?>> invokers = registryDirectory.list(invocation);
        URL aUrl = invokers.get(0).getUrl();
        URL bUrl = invokers.get(1).getUrl();
        Assertions.assertEquals(aUrl.getHost().equals("10.20.30.140") ? "3" : "4", aUrl.getParameter("timeout"));
        Assertions.assertEquals(bUrl.getHost().equals("10.20.30.141") ? "4" : "3", bUrl.getParameter("timeout"));
    }

    /**
     * Test cleanup override rules, and sent remove rules and other override rules
     * Whether the test can be restored to the providerUrl when it is pushed
     */
    @Test
    public void testNofityOverrideUrls_Clean1() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        invocation = new RpcInvocation();

        List<URL> durls = new ArrayList<URL>();
        durls.add(SERVICEURL.setHost("10.20.30.140").addParameter("timeout", "1"));
        registryDirectory.notify(durls);

        durls = new ArrayList<URL>();
        durls.add(URL.valueOf("override://0.0.0.0?timeout=1000"));
        registryDirectory.notify(durls);

        durls = new ArrayList<URL>();
        durls.add(URL.valueOf("override://0.0.0.0?timeout=3"));
        durls.add(URL.valueOf("override://0.0.0.0"));
        registryDirectory.notify(durls);

        List<Invoker<?>> invokers = registryDirectory.list(invocation);
        Invoker<?> aInvoker = invokers.get(0);
        //Need to be restored to the original providerUrl
        Assertions.assertEquals("3", aInvoker.getUrl().getParameter("timeout"));
    }

    /**
     * The test clears the override rule and only sends the override cleanup rules
     * Whether the test can be restored to the providerUrl when it is pushed
     */
    @Test
    public void testNofityOverrideUrls_CleanOnly() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        invocation = new RpcInvocation();

        List<URL> durls = new ArrayList<URL>();
        durls.add(SERVICEURL.setHost("10.20.30.140").addParameter("timeout", "1"));
        registryDirectory.notify(durls);
        Assertions.assertNull(((InvokerWrapper<?>) registryDirectory.getInvokers().get(0)).getUrl().getParameter("mock"));

        //override
        durls = new ArrayList<URL>();
        durls.add(URL.valueOf("override://0.0.0.0?timeout=1000&mock=fail"));
        registryDirectory.notify(durls);
        List<Invoker<?>> invokers = registryDirectory.list(invocation);
        Invoker<?> aInvoker = invokers.get(0);
        Assertions.assertEquals("1000", aInvoker.getUrl().getParameter("timeout"));
        Assertions.assertEquals("fail", ((InvokerWrapper<?>) registryDirectory.getInvokers().get(0)).getUrl().getParameter("mock"));

        //override clean
        durls = new ArrayList<URL>();
        durls.add(URL.valueOf("override://0.0.0.0/dubbo.test.api.HelloService"));
        registryDirectory.notify(durls);
        invokers = registryDirectory.list(invocation);
        aInvoker = invokers.get(0);
        //Need to be restored to the original providerUrl
        Assertions.assertEquals("1", aInvoker.getUrl().getParameter("timeout"));

        Assertions.assertNull(((InvokerWrapper<?>) registryDirectory.getInvokers().get(0)).getUrl().getParameter("mock"));
    }

    /**
     * Test the simultaneous push to clear the override and the override for a certain provider
     * See if override can take effect
     */
    @Test
    public void testNofityOverrideUrls_CleanNOverride() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        invocation = new RpcInvocation();

        List<URL> durls = new ArrayList<URL>();
        durls.add(SERVICEURL.setHost("10.20.30.140").addParameter("timeout", "1"));
        registryDirectory.notify(durls);

        durls = new ArrayList<URL>();
        durls.add(URL.valueOf("override://0.0.0.0?timeout=3"));
        durls.add(URL.valueOf("override://0.0.0.0"));
        durls.add(URL.valueOf("override://10.20.30.140:9091?timeout=4"));
        registryDirectory.notify(durls);

        List<Invoker<?>> invokers = registryDirectory.list(invocation);
        Invoker<?> aInvoker = invokers.get(0);
        Assertions.assertEquals("4", aInvoker.getUrl().getParameter("timeout"));
    }

    /**
     * Test override disables all service providers through enable=false
     * Expectation: all service providers can not be disabled through override.
     */
    @Test
    public void testNofityOverrideUrls_disabled_allProvider() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        invocation = new RpcInvocation();

        List<URL> durls = new ArrayList<URL>();
        durls.add(SERVICEURL.setHost("10.20.30.140"));
        durls.add(SERVICEURL.setHost("10.20.30.141"));
        registryDirectory.notify(durls);

        durls = new ArrayList<URL>();
        durls.add(URL.valueOf("override://0.0.0.0?" + ENABLED_KEY + "=false"));
        registryDirectory.notify(durls);

        List<Invoker<?>> invokers = registryDirectory.list(invocation);
        //All service providers can not be disabled through override.
        Assertions.assertEquals(2, invokers.size());
    }

    /**
     * Test override disables a specified service provider through enable=false
     * It is expected that a specified service provider can be disable.
     */
    @Test
    public void testNofityOverrideUrls_disabled_specifiedProvider() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        invocation = new RpcInvocation();

        List<URL> durls = new ArrayList<URL>();
        durls.add(SERVICEURL.setHost("10.20.30.140"));
        durls.add(SERVICEURL.setHost("10.20.30.141"));
        registryDirectory.notify(durls);

        durls = new ArrayList<URL>();
        durls.add(URL.valueOf("override://10.20.30.140:9091?" + DISABLED_KEY + "=true"));
        registryDirectory.notify(durls);

        List<Invoker<?>> invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(1, invokers.size());
        Assertions.assertEquals("10.20.30.141", invokers.get(0).getUrl().getHost());

        durls = new ArrayList<URL>();
        durls.add(URL.valueOf("empty://0.0.0.0?" + DISABLED_KEY + "=true&" + CATEGORY_KEY + "=" + CONFIGURATORS_CATEGORY));
        registryDirectory.notify(durls);
        List<Invoker<?>> invokers2 = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers2.size());
    }

    /**
     * Test override disables a specified service provider through enable=false
     * It is expected that a specified service provider can be disable.
     */
    @Test
    public void testNofity_To_Decrease_provider() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        invocation = new RpcInvocation();

        List<URL> durls = new ArrayList<URL>();
        durls.add(SERVICEURL.setHost("10.20.30.140"));
        durls.add(SERVICEURL.setHost("10.20.30.141"));
        registryDirectory.notify(durls);

        List<Invoker<?>> invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers.size());

        durls = new ArrayList<URL>();
        durls.add(SERVICEURL.setHost("10.20.30.140"));
        registryDirectory.notify(durls);
        List<Invoker<?>> invokers2 = registryDirectory.list(invocation);
        Assertions.assertEquals(1, invokers2.size());
        Assertions.assertEquals("10.20.30.140", invokers2.get(0).getUrl().getHost());

        durls = new ArrayList<URL>();
        durls.add(URL.valueOf("empty://0.0.0.0?" + DISABLED_KEY + "=true&" + CATEGORY_KEY + "=" + CONFIGURATORS_CATEGORY));
        registryDirectory.notify(durls);
        List<Invoker<?>> invokers3 = registryDirectory.list(invocation);
        Assertions.assertEquals(1, invokers3.size());
    }

    /**
     * Test override disables a specified service provider through enable=false
     * It is expected that a specified service provider can be disable.
     */
    @Test
    public void testNofity_disabled_specifiedProvider() {
        RegistryDirectory registryDirectory = getRegistryDirectory();
        invocation = new RpcInvocation();

        // Initially disable
        List<URL> durls = new ArrayList<URL>();
        durls.add(SERVICEURL.setHost("10.20.30.140").addParameter(ENABLED_KEY, "false"));
        durls.add(SERVICEURL.setHost("10.20.30.141"));
        registryDirectory.notify(durls);

        List<Invoker<?>> invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(1, invokers.size());
        Assertions.assertEquals("10.20.30.141", invokers.get(0).getUrl().getHost());

        //Enabled by override rule
        durls = new ArrayList<URL>();
        durls.add(URL.valueOf("override://10.20.30.140:9091?" + DISABLED_KEY + "=false"));
        registryDirectory.notify(durls);
        List<Invoker<?>> invokers2 = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers2.size());
    }

    @Test
    public void testNotifyRouterUrls_Clean() {
        if (isScriptUnsupported) return;
        RegistryDirectory registryDirectory = getRegistryDirectory();
        URL routerurl = URL.valueOf(ROUTE_PROTOCOL + "://127.0.0.1:9096/").addParameter(ROUTER_KEY,
                "javascript").addParameter(RULE_KEY,
                "function test1(){}").addParameter(ROUTER_KEY,
                "script"); // FIX
        // BAD

        List<URL> serviceUrls = new ArrayList<URL>();
        // without ROUTER_KEY, the first router should not be created.
        serviceUrls.add(routerurl);
        registryDirectory.notify(serviceUrls);
        // FIXME
       /* List routers = registryDirectory.getRouters();
        Assertions.assertEquals(1 + 1, routers.size());

        serviceUrls.clear();
        serviceUrls.add(routerurl.addParameter(Constants.ROUTER_KEY, Constants.ROUTER_TYPE_CLEAR));
        registryDirectory.notify(serviceUrls);
        routers = registryDirectory.getRouters();
        Assertions.assertEquals(0 + 1, routers.size());*/
    }

    /**
     * Test mock provider distribution
     */
    @Test
    public void testNotify_MockProviderOnly() {
        RegistryDirectory registryDirectory = getRegistryDirectory();

        List<URL> serviceUrls = new ArrayList<URL>();
        serviceUrls.add(SERVICEURL.addParameter("methods", "getXXX1"));
        serviceUrls.add(SERVICEURL2.addParameter("methods", "getXXX1,getXXX2"));
        serviceUrls.add(SERVICEURL.setProtocol(MOCK_PROTOCOL));

        registryDirectory.notify(serviceUrls);
        Assertions.assertTrue(registryDirectory.isAvailable());
        invocation = new RpcInvocation();

        List invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers.size());

        RpcInvocation mockinvocation = new RpcInvocation();
        mockinvocation.setAttachment(INVOCATION_NEED_MOCK, "true");
        invokers = registryDirectory.list(mockinvocation);
        Assertions.assertEquals(1, invokers.size());
    }

    // mock protocol

    //Test the matching of protocol and select only the matched protocol for refer
    @Test
    public void test_Notified_acceptProtocol0() {
        URL errorPathUrl = URL.valueOf("notsupport:/xxx?refer=" + URL.encode("interface=" + service));
        RegistryDirectory registryDirectory = getRegistryDirectory(errorPathUrl);
        List<URL> serviceUrls = new ArrayList<URL>();
        URL dubbo1URL = URL.valueOf("dubbo://127.0.0.1:9098?lazy=true&methods=getXXX");
        URL dubbo2URL = URL.valueOf("injvm://127.0.0.1:9099?lazy=true&methods=getXXX");
        serviceUrls.add(dubbo1URL);
        serviceUrls.add(dubbo2URL);
        registryDirectory.notify(serviceUrls);

        invocation = new RpcInvocation();

        List<Invoker<DemoService>> invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers.size());
    }

    //Test the matching of protocol and select only the matched protocol for refer
    @Test
    public void test_Notified_acceptProtocol1() {
        URL errorPathUrl = URL.valueOf("notsupport:/xxx");
        errorPathUrl = errorPathUrl.addParameterAndEncoded(REFER_KEY, "interface=" + service + "&protocol=dubbo");
        RegistryDirectory registryDirectory = getRegistryDirectory(errorPathUrl);
        List<URL> serviceUrls = new ArrayList<URL>();
        URL dubbo1URL = URL.valueOf("dubbo://127.0.0.1:9098?lazy=true&methods=getXXX");
        URL dubbo2URL = URL.valueOf("injvm://127.0.0.1:9098?lazy=true&methods=getXXX");
        serviceUrls.add(dubbo1URL);
        serviceUrls.add(dubbo2URL);
        registryDirectory.notify(serviceUrls);

        invocation = new RpcInvocation();

        List<Invoker<DemoService>> invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(1, invokers.size());
    }

    //Test the matching of protocol and select only the matched protocol for refer
    @Test
    public void test_Notified_acceptProtocol2() {
        URL errorPathUrl = URL.valueOf("notsupport:/xxx");
        errorPathUrl = errorPathUrl.addParameterAndEncoded(REFER_KEY, "interface=" + service + "&protocol=dubbo,injvm");
        RegistryDirectory registryDirectory = getRegistryDirectory(errorPathUrl);
        List<URL> serviceUrls = new ArrayList<URL>();
        URL dubbo1URL = URL.valueOf("dubbo://127.0.0.1:9098?lazy=true&methods=getXXX");
        URL dubbo2URL = URL.valueOf("injvm://127.0.0.1:9099?lazy=true&methods=getXXX");
        serviceUrls.add(dubbo1URL);
        serviceUrls.add(dubbo2URL);
        registryDirectory.notify(serviceUrls);

        invocation = new RpcInvocation();

        List<Invoker<DemoService>> invokers = registryDirectory.list(invocation);
        Assertions.assertEquals(2, invokers.size());
    }

    @Test
    public void test_Notified_withGroupFilter() {
        URL directoryUrl = noMeaningUrl.addParameterAndEncoded(REFER_KEY, "interface" + service + "&group=group1,group2");
        RegistryDirectory directory = this.getRegistryDirectory(directoryUrl);
        URL provider1 = URL.valueOf("dubbo://10.134.108.1:20880/" + service + "?methods=getXXX&group=group1&mock=false&application=mockApplication");
        URL provider2 = URL.valueOf("dubbo://10.134.108.1:20880/" + service + "?methods=getXXX&group=group2&mock=false&application=mockApplication");

        List<URL> providers = new ArrayList<>();
        providers.add(provider1);
        providers.add(provider2);
        directory.notify(providers);

        invocation = new RpcInvocation();
        invocation.setMethodName("getXXX");
        List<Invoker<DemoService>> invokers = directory.list(invocation);

        Assertions.assertEquals(2, invokers.size());
        Assertions.assertTrue(invokers.get(0) instanceof MockClusterInvoker);
        Assertions.assertTrue(invokers.get(1) instanceof MockClusterInvoker);

        directoryUrl = noMeaningUrl.addParameterAndEncoded(REFER_KEY, "interface" + service + "&group=group1");
        directory = this.getRegistryDirectory(directoryUrl);
        directory.notify(providers);

        invokers = directory.list(invocation);

        Assertions.assertEquals(2, invokers.size());
        Assertions.assertFalse(invokers.get(0) instanceof MockClusterInvoker);
        Assertions.assertFalse(invokers.get(1) instanceof MockClusterInvoker);
    }

    enum Param {
        MORGAN,
    }

    private interface DemoService {
    }

    private static class MockRegistry implements Registry {

        CountDownLatch latch;
        boolean destroyWithError;

        public MockRegistry(CountDownLatch latch) {
            this.latch = latch;
        }

        public MockRegistry(boolean destroyWithError) {
            this.destroyWithError = destroyWithError;
        }

        @Override
        public void register(URL url) {

        }

        @Override
        public void unregister(URL url) {

        }

        @Override
        public void subscribe(URL url, NotifyListener listener) {

        }

        @Override
        public void unsubscribe(URL url, NotifyListener listener) {
            if (latch != null) latch.countDown();
        }

        @Override
        public List<URL> lookup(URL url) {
            return null;
        }

        public URL getUrl() {
            return null;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void destroy() {
            if (destroyWithError) {
                throw new RpcException("test exception ignore.");
            }
        }
    }
}
