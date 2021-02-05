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
package org.apache.dubbo.rpc.protocol.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.DubboAppender;
import org.apache.dubbo.common.utils.LogUtil;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.exchange.ExchangeClient;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.protocol.AsyncToSyncInvoker;
import org.apache.dubbo.rpc.protocol.dubbo.support.ProtocolUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.apache.dubbo.remoting.Constants.CONNECTIONS_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.SHARE_CONNECTIONS_KEY;


public class ReferenceCountExchangeClientTest {

    public static ProxyFactory proxy = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
    private static DubboProtocol protocol = DubboProtocol.getDubboProtocol();
    Exporter<?> demoExporter;
    Exporter<?> helloExporter;
    Invoker<IDemoService> demoServiceInvoker;
    Invoker<IHelloService> helloServiceInvoker;
    IDemoService demoService;
    IHelloService helloService;
    ExchangeClient demoClient;
    ExchangeClient helloClient;
    String errorMsg = "safe guard client , should not be called ,must have a bug";

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterAll
    public static void tearDownAfterClass() {
        ProtocolUtils.closeAll();// 进去
    }

    public static Invoker<?> referInvoker(Class<?> type, URL url) {
        return (Invoker<?>) protocol.refer(type, url);// 进去
    }

    public static <T> Exporter<T> export(T instance, Class<T> type, String url) {
        return export(instance, type, URL.valueOf(url));
    }

    public static <T> Exporter<T> export(T instance, Class<T> type, URL url) {
        return protocol.export(proxy.getInvoker(instance, type, url));
    }

    @BeforeEach
    public void setUp() throws Exception {
    }

    /**
     * test connection sharing
     */
    @Test
    public void test_share_connect() {
        init(0, 1);// 进去
        Assertions.assertEquals(demoClient.getLocalAddress(), helloClient.getLocalAddress());
        Assertions.assertEquals(demoClient, helloClient);
        destoy();
    }

    /**
     * test connection not sharing
     */
    @Test
    public void test_not_share_connect() {
        init(1, 1);// 第一个参数为1，表示不使用连接复用，进去
        Assertions.assertNotSame(demoClient.getLocalAddress(), helloClient.getLocalAddress());
        Assertions.assertNotSame(demoClient, helloClient);
        destoy();
    }

    /**
     * test using multiple shared connections
     */
    @Test
    public void test_mult_share_connect() {
        // here a three shared connection is established between a consumer process and a provider process.
        // 在这里，消费者流程和提供者流程之间建立了三个共享连接。
        final int shareConnectionNum = 3;

        init(0, shareConnectionNum); // DubboProtocol内部会创建三个HeaderExchangeClient,并用Referencexxx包装 ，进去

        List<ReferenceCountExchangeClient> helloReferenceClientList = getReferenceClientList(helloServiceInvoker);
        Assertions.assertEquals(shareConnectionNum, helloReferenceClientList.size());

        List<ReferenceCountExchangeClient> demoReferenceClientList = getReferenceClientList(demoServiceInvoker);
        Assertions.assertEquals(shareConnectionNum, demoReferenceClientList.size());

        // because helloServiceInvoker and demoServiceInvoker use share connect， so client list must be equal
        Assertions.assertEquals(helloReferenceClientList, demoReferenceClientList);

        //helloReferenceClientList = {ArrayList@3205}  size = 3 ---——> // 注意这里的3 因为shareConnectionNum = 3 （init方法内部发起rpc调用的时候内部会进行一个client的选择）
        // 0 = {ReferenceCountExchangeClient@3230}
        //  url = {URL@3233} "dubbo://127.0.0.1:12345/demo?codec=dubbo&connections=0&heartbeat=60000&shareconnections=3"
        //  referenceCount = {AtomicInteger@3234} "2" // 注意这里的2  ----> 因为init里面两次refer
        //  client = {HeaderExchangeClient@3198} "HeaderExchangeClient [channel=org.apache.dubbo.remoting.transport.netty4.NettyClient [/30.25.58.202:53235 -> /30.25.58.202:12345]]"
        // 1 = {ReferenceCountExchangeClient@3231}
        //  url = {URL@3238} "dubbo://127.0.0.1:12345/demo?codec=dubbo&connections=0&heartbeat=60000&shareconnections=3"
        //  referenceCount = {AtomicInteger@3239} "2" // 注意
        //  client = {HeaderExchangeClient@3240} "HeaderExchangeClient [channel=org.apache.dubbo.remoting.transport.netty4.NettyClient [/30.25.58.202:53237 -> /30.25.58.202:12345]]"
        // 2 = {ReferenceCountExchangeClient@3232}
        //  url = {URL@3244} "dubbo://127.0.0.1:12345/demo?codec=dubbo&connections=0&heartbeat=60000&shareconnections=3"
        //  referenceCount = {AtomicInteger@3245} "2" // 注意
        //  client = {HeaderExchangeClient@3246} "HeaderExchangeClient [channel=org.apache.dubbo.remoting.transport.netty4.NettyClient [/30.25.58.202:53236 -> /30.25.58.202:12345]]"


        Assertions.assertEquals(demoClient.getLocalAddress(), helloClient.getLocalAddress());
        Assertions.assertEquals(demoClient, helloClient);

        destoy();
    }

    /**
     * test counter won't count down incorrectly when invoker is destroyed for multiple times
     * 当调用程序被销毁多次时，测试计数器不会错误地计数
     */
    @Test
    public void test_multi_destory() {
        init(0, 1);
        DubboAppender.doStart();
        DubboAppender.clear();
        demoServiceInvoker.destroy();
        demoServiceInvoker.destroy();
        Assertions.assertEquals("hello", helloService.hello());
        Assertions.assertEquals(0, LogUtil.findMessage(errorMsg), "should not  warning message");
        LogUtil.checkNoError();
        DubboAppender.doStop();
        destoy();
    }

    /**
     * Test against invocation still succeed even if counter has error
     */
    @Test
    public void test_counter_error() {
        init(0, 1);
        DubboAppender.doStart();
        DubboAppender.clear();

        // because the two interfaces are initialized, the ReferenceCountExchangeClient reference counter is 2
        ReferenceCountExchangeClient client = getReferenceClient(helloServiceInvoker);

        // close once, counter counts down from 2 to 1, no warning occurs
        client.close();
        Assertions.assertEquals("hello", helloService.hello());
        Assertions.assertEquals(0, LogUtil.findMessage(errorMsg), "should not warning message");

        // generally a client can only be closed once, here it is closed twice, counter is incorrect
        client.close();

        // wait close done.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Assertions.fail();
        }

        // due to the effect of LazyConnectExchangeClient, the client will be "revived" whenever there is a call.
        Assertions.assertEquals("hello", helloService.hello());
        Assertions.assertEquals(1, LogUtil.findMessage(errorMsg), "should warning message");

        // output one error every 5000 invocations.
        Assertions.assertEquals("hello", helloService.hello());
        Assertions.assertEquals(1, LogUtil.findMessage(errorMsg), "should warning message");

        DubboAppender.doStop();

        // status switch to available once invoke again
        Assertions.assertTrue(helloServiceInvoker.isAvailable(), "client status available");

        /**
         * This is the third time to close the same client. Under normal circumstances,
         * a client value should be closed once (that is, the shutdown operation is irreversible).
         * After closing, the value of the reference counter of the client has become -1.
         *
         * But this is a bit special, because after the client is closed twice, there are several calls to helloService,
         * that is, the client inside the ReferenceCountExchangeClient is actually active, so the third shutdown here is still effective,
         * let the resurrection After the client is really closed.
         */
        client.close();

        // client has been replaced with lazy client. lazy client is fetched from referenceclientmap, and since it's
        // been invoked once, it's close status is false
        Assertions.assertFalse(client.isClosed(), "client status close");
        Assertions.assertFalse(helloServiceInvoker.isAvailable(), "client status close");
        destoy();
    }

    @SuppressWarnings("unchecked")
    private void init(int connections, int shareConnections) {
        Assertions.assertTrue(connections >= 0);
        Assertions.assertTrue(shareConnections >= 1);

        int port = NetUtils.getAvailablePort();
        port = 12345;
        // 下两个提供方url用的一个port
        URL demoUrl = URL.valueOf("dubbo://127.0.0.1:" + port + "/demo?" + CONNECTIONS_KEY + "=" + connections + "&" + SHARE_CONNECTIONS_KEY + "=" + shareConnections);
        URL helloUrl = URL.valueOf("dubbo://127.0.0.1:" + port + "/hello?" + CONNECTIONS_KEY + "=" + connections + "&" + SHARE_CONNECTIONS_KEY + "=" + shareConnections);

        demoExporter = export(new DemoServiceImpl(), IDemoService.class, demoUrl);// 进去
        helloExporter = export(new HelloServiceImpl(), IHelloService.class, helloUrl);// 进去
        // helloExporter如下，注意exporterMap

        // helloExporter = {DubboExporter@3115} "interface org.apache.dubbo.rpc.protocol.dubbo.ReferenceCountExchangeClientTest$IHelloService -> dubbo://127.0.0.1:62796/hello?connections=0&shareconnections=1"
        // key = "hello:62796"
        // exporterMap = {ConcurrentHashMap@2261}  size = 2
        //  "demo:62796" -> {DubboExporter@2273} "interface org.apache.dubbo.rpc.protocol.dubbo.ReferenceCountExchangeClientTest$IDemoService -> dubbo://127.0.0.1:62796/demo?connections=0&shareconnections=1"
        //  "hello:62796" -> {DubboExporter@3115} "interface org.apache.dubbo.rpc.protocol.dubbo.ReferenceCountExchangeClientTest$IHelloService -> dubbo://127.0.0.1:62796/hello?connections=0&shareconnections=1"
        // logger = {FailsafeLogger@3999}
        // invoker = {JavassistProxyFactory$1@3105} "interface org.apache.dubbo.rpc.protocol.dubbo.ReferenceCountExchangeClientTest$IHelloService -> dubbo://127.0.0.1:62796/hello?connections=0&shareconnections=1"
        // unexported = false

        demoUrl= demoUrl.addParameter("timeout",50000); // 这里是我特地加的，可能当前电脑比较慢，导致DefaultFuture经常超时

        demoServiceInvoker = (Invoker<IDemoService>) referInvoker(IDemoService.class, demoUrl);// 进去
        // demoServiceInvoker类型为AsyncToSyncInvoker，内部的invoker属性为DubboInvoker， getProxy进去
        demoService = proxy.getProxy(demoServiceInvoker);
        // demoService类型为proxy0，内部含有InvokerInvocationHandler属性，而InvokerInvocationHandler含有AsyncToSyncInvoker
        // demo进去 直接进InvokerInvocationHandler的invoke方法
        // 交互逻辑非常复杂，要关注服务端的编解码、客户端的编解码，服务端是最终怎么调用到本地服务的方法的，客户端最终是怎么拿到结果的，相关逻辑打断点测试
        Assertions.assertEquals("demo", demoService.demo());

        helloServiceInvoker = (Invoker<IHelloService>) referInvoker(IHelloService.class, helloUrl);
        helloService = proxy.getProxy(helloServiceInvoker);
        Assertions.assertEquals("hello", helloService.hello());

        demoClient = getClient(demoServiceInvoker);// 进去
        helloClient = getClient(helloServiceInvoker);// 进去
    }

    private void destoy() {
        demoServiceInvoker.destroy();
        helloServiceInvoker.destroy();
        demoExporter.getInvoker().destroy();
        helloExporter.getInvoker().destroy();
    }

    private ExchangeClient getClient(Invoker<?> invoker) {
        if (invoker.getUrl().getParameter(CONNECTIONS_KEY, 1) == 1) {
            return getInvokerClient(invoker);
        } else {
            ReferenceCountExchangeClient client = getReferenceClient(invoker);// 进去
            try {
                // 获取ReferenceCountExchangeClient的ExchangeClient client属性值
                Field clientField = ReferenceCountExchangeClient.class.getDeclaredField("client");
                clientField.setAccessible(true);
                return (ExchangeClient) clientField.get(client);
            } catch (Exception e) {
                e.printStackTrace();
                Assertions.fail(e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }

    private ReferenceCountExchangeClient getReferenceClient(Invoker<?> invoker) {
        return getReferenceClientList(invoker).get(0);// 进去
    }

    private List<ReferenceCountExchangeClient> getReferenceClientList(Invoker<?> invoker) {
        List<ExchangeClient> invokerClientList = getInvokerClientList(invoker);// 进去

        // 筛选出为ReferenceCountExchangeClient填充到返回结果集中
        List<ReferenceCountExchangeClient> referenceCountExchangeClientList = new ArrayList<>(invokerClientList.size());
        for (ExchangeClient exchangeClient : invokerClientList) {
            Assertions.assertTrue(exchangeClient instanceof ReferenceCountExchangeClient);
            referenceCountExchangeClientList.add((ReferenceCountExchangeClient) exchangeClient);
        }

        return referenceCountExchangeClientList;
    }

    private ExchangeClient getInvokerClient(Invoker<?> invoker) {
        return getInvokerClientList(invoker).get(0);
    }

    private List<ExchangeClient> getInvokerClientList(Invoker<?> invoker) {
        @SuppressWarnings("rawtypes") DubboInvoker dInvoker = (DubboInvoker) ((AsyncToSyncInvoker) invoker).getInvoker();// 进去
        try {
            // 获取DubboInvoker的ExchangeClient[] clients属性
            Field clientField = DubboInvoker.class.getDeclaredField("clients");
            clientField.setAccessible(true);
            ExchangeClient[] clients = (ExchangeClient[]) clientField.get(dInvoker);

            List<ExchangeClient> clientList = new ArrayList<ExchangeClient>(clients.length);
            for (ExchangeClient client : clients) {
                clientList.add(client);
            }

            // sorting makes it easy to compare between lists
            Collections.sort(clientList, Comparator.comparing(c -> Integer.valueOf(Objects.hashCode(c))));

            return clientList;

        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public interface IDemoService {
        String demo();
    }

    public interface IHelloService {
        String hello();
    }

    public class DemoServiceImpl implements IDemoService {
        public String demo() {
            return "demo";
        }
    }

    public class HelloServiceImpl implements IHelloService {
        public String hello() {
            return "hello";
        }
    }
}
