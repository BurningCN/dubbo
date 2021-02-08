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


import org.apache.dubbo.common.Parameters;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.ExchangeClient;
import org.apache.dubbo.remoting.exchange.ExchangeHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.dubbo.remoting.Constants.RECONNECT_KEY;
import static org.apache.dubbo.remoting.Constants.SEND_RECONNECT_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.LAZY_CONNECT_INITIAL_STATE_KEY;

/**
 * dubbo protocol support class.
 */
// OK
@SuppressWarnings("deprecation")
final class ReferenceCountExchangeClient implements ExchangeClient {

    private final URL url;
    private final AtomicInteger referenceCount = new AtomicInteger(0);

    private ExchangeClient client;

    // ReferenceCountExchangeClient 内部定义了一个引用计数变量 referenceCount，每当该对象被引用一次 referenceCount 都会进行自增。
    // 每当 close 方法被调用时，referenceCount 进行自减。ReferenceCountExchangeClient 内部仅实现了一个引用计数的功能，其他方法并无复杂
    // 逻辑，均是直接调用被装饰对象的相关方法。所以这里就不多说了，继续向下分析，这次是 HeaderExchangeClient。
    // gx
    public ReferenceCountExchangeClient(ExchangeClient client) {
        this.client = client;
        // 引用计数自增
        referenceCount.incrementAndGet();
        this.url = client.getUrl();
    }

    @Override
    public void reset(URL url) {
        client.reset(url);
    }

    @Override
    public CompletableFuture<Object> request(Object request) throws RemotingException {
        // 直接调用被装饰对象的同签名方法
        return client.request(request);
    }

    @Override
    public URL getUrl() {
        return client.getUrl();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return client.getRemoteAddress();
    }

    @Override
    public ChannelHandler getChannelHandler() {
        return client.getChannelHandler();
    }

    @Override
    public CompletableFuture<Object> request(Object request, int timeout) throws RemotingException {
        // 直接调用被装饰对象的同签名方法
        return client.request(request, timeout);
    }

    @Override
    public CompletableFuture<Object> request(Object request, ExecutorService executor) throws RemotingException {
        return client.request(request, executor);
    }

    @Override
    public CompletableFuture<Object> request(Object request, int timeout, ExecutorService executor) throws RemotingException {
        return client.request(request, timeout, executor);// 进去
    }

    @Override
    public boolean isConnected() {
        return client.isConnected();
    }

    @Override
    public void reconnect() throws RemotingException {
        client.reconnect();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return client.getLocalAddress();
    }

    @Override
    public boolean hasAttribute(String key) {
        return client.hasAttribute(key);
    }

    @Override
    public void reset(Parameters parameters) {
        client.reset(parameters);
    }

    @Override
    public void send(Object message) throws RemotingException {
        client.send(message);
    }

    @Override
    public ExchangeHandler getExchangeHandler() {
        return client.getExchangeHandler();
    }

    @Override
    public Object getAttribute(String key) {
        return client.getAttribute(key);
    }

    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        client.send(message, sent);
    }

    @Override
    public void setAttribute(String key, Object value) {
        client.setAttribute(key, value);
    }

    @Override
    public void removeAttribute(String key) {
        client.removeAttribute(key);
    }

    /**
     * close() is not idempotent any longer 不再是幂等的，因为需要做一些引用计数的操作
     */
    @Override
    public void close() {
        close(0); // 进去
    }

    @Override
    public void close(int timeout) {
        // referenceCount 自减 只有没有任何人引用了，才会进入分支，关闭client，这就是连接复用
        if (referenceCount.decrementAndGet() <= 0) {
            if (timeout == 0) {
                client.close();

            } else {
                client.close(timeout);
            }
            // 进去
            replaceWithLazyClient();
        }
    }

    @Override
    public void startClose() {
        client.startClose();
    }

    /**
     * when closing the client, the client needs to be set to LazyConnectExchangeClient, and if a new call is made,
     * the client will "resurrect".
     * 关闭客户端时，客户端需要设置为LazyConnectExchangeClient，如果有新的调用，
     * 客户端将“复活”。
     *
     * @return
     */
    private void replaceWithLazyClient() {
        // this is a defensive operation to avoid client is closed by accident, the initial state of the client is false
        // 这是一种防御操作，以避免客户端被意外关闭，客户端的初始状态为false
        URL lazyUrl = url.addParameter(LAZY_CONNECT_INITIAL_STATE_KEY, Boolean.TRUE)
                .addParameter(RECONNECT_KEY, Boolean.FALSE)
                .addParameter(SEND_RECONNECT_KEY, Boolean.TRUE.toString())
                .addParameter(LazyConnectExchangeClient.REQUEST_WITH_WARNING_KEY, true);
        // dubbo://127.0.0.1:12345/demo?codec=dubbo&connect.lazy.initial.state=true&connections=0&heartbeat=300000&lazyclient_request_with_warning=true&reconnect=false&send.reconnect=true&shareconnections=1&timeout=600000
        /**
         * the order of judgment in the if statement cannot be changed.
         * if语句中的判断顺序不能改变。
         */
        if (!(client instanceof LazyConnectExchangeClient) || client.isClosed()) {
            // 进去
            client = new LazyConnectExchangeClient(lazyUrl, client.getExchangeHandler());
            // 此时 ReferenceCountClient 变成LazyConnectClient，实际的客户端没有了，如果触发对该类ReferenceCountClient的请求时候，会跳转到LazyConnectClient
        }
    }

    @Override
    public boolean isClosed() {
        return client.isClosed();
    }

    /**
     * The reference count of current ExchangeClient, connection will be closed if all invokers destroyed.
     * 如果销毁了所有调用程序，则当前ExchangeClient, connection的引用计数将被关闭。
     */
    /** 引用计数自增，该方法由外部调用 */
    public void incrementAndGetCount() {
        referenceCount.incrementAndGet();
    }
}

