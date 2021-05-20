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
package org.apache.dubbo.remoting.exchange.support.header;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.timer.HashedWheelTimer;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Client;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.ExchangeChannel;
import org.apache.dubbo.remoting.exchange.ExchangeClient;
import org.apache.dubbo.remoting.exchange.ExchangeHandler;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.remoting.Constants.HEARTBEAT_CHECK_TICK;
import static org.apache.dubbo.remoting.Constants.LEAST_HEARTBEAT_DURATION;
import static org.apache.dubbo.remoting.Constants.TICKS_PER_WHEEL;
import static org.apache.dubbo.remoting.utils.UrlUtils.getHeartbeat;
import static org.apache.dubbo.remoting.utils.UrlUtils.getIdleTimeout;

/**
 * DefaultMessageClient
 */
// OK
public class HeaderExchangeClient implements ExchangeClient {

    private final Client client; // client为NettyClient
    private final ExchangeChannel channel;// channel为HeaderExchangeChannel

    private static final HashedWheelTimer IDLE_CHECK_TIMER = new HashedWheelTimer(
            new NamedThreadFactory("dubbo-client-idleCheck", true), 1, TimeUnit.SECONDS, TICKS_PER_WHEEL);
    private HeartbeatTimerTask heartBeatTimerTask;
    private ReconnectTimerTask reconnectTimerTask;

    // gx
    public HeaderExchangeClient(Client client, boolean startTimer) {
        // client为NettyClient
        Assert.notNull(client, "Client can't be null");
        this.client = client;
        // 创建 HeaderExchangeChannel 对象 进去
        this.channel = new HeaderExchangeChannel(client);

        if (startTimer) {
            URL url = client.getUrl();
            startReconnectTask(url);// 开启重连检测定时器 进去
            startHeartBeatTask(url);// 开启心跳检测定时器 进去
        }
    }


    @Override
    public CompletableFuture<Object> request(Object request) throws RemotingException {
        //  直接 HeaderExchangeChannel 对象的同签名方法 进去
        return channel.request(request);
    }

    // HeaderExchangeClient 中很多方法只有一行代码，即调用 HeaderExchangeChannel 对象的同签名方法。那 HeaderExchangeClient 有什么用
    // 处呢？答案是封装了一些关于心跳检测的逻辑。心跳检测并非本文所关注的点，因此就不多说了，继续向下看 HeaderExchangeChannel 。

    @Override
    public URL getUrl() {
        return channel.getUrl();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return channel.getRemoteAddress();
    }

    @Override
    public CompletableFuture<Object> request(Object request, int timeout) throws RemotingException {
        // 直接 HeaderExchangeChannel 对象的同签名方法
        return channel.request(request, timeout);
    }


    @Override
    public CompletableFuture<Object> request(Object request, ExecutorService executor) throws RemotingException {
        return channel.request(request, executor);
    }

    @Override
    public CompletableFuture<Object> request(Object request, int timeout, ExecutorService executor) throws RemotingException {
        return channel.request(request, timeout, executor);// 进去
    }

    @Override
    public ChannelHandler getChannelHandler() {
        return channel.getChannelHandler();
    }

    @Override
    public boolean isConnected() {
        return channel.isConnected();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    @Override
    public ExchangeHandler getExchangeHandler() {
        return channel.getExchangeHandler();
    }

    @Override
    public void send(Object message) throws RemotingException {
        channel.send(message);
    }

    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        channel.send(message, sent);
    }

    @Override
    public boolean isClosed() {
        return channel.isClosed();
    }

    @Override
    public void close() {
        doClose();
        channel.close();
    }

    @Override
    public void close(int timeout) {
        // Mark the client into the closure process
        startClose();// 进去  服务端的HeaderExchangeServer也有startClose
        doClose();// 进去 服务端的HeaderExchangeServer也有doClose
        channel.close(timeout);  // 进去 服务端的HeaderExchangeServer也有关闭所有客户端连接channel的过程
    }

    @Override
    public void startClose() {
        // 这里是 HeaderExchangeChannel
        channel.startClose();
    }

    @Override
    public void reset(URL url) {
        client.reset(url);
        // FIXME, should cancel and restart timer tasks if parameters in the new URL are different?
    }

    @Override
    @Deprecated
    public void reset(org.apache.dubbo.common.Parameters parameters) {
        reset(getUrl().addParameters(parameters.getParameters()));
    }

    @Override
    public void reconnect() throws RemotingException {
        client.reconnect();
    }

    @Override
    public Object getAttribute(String key) {
        return channel.getAttribute(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        channel.setAttribute(key, value);
    }

    @Override
    public void removeAttribute(String key) {
        channel.removeAttribute(key);
    }

    @Override
    public boolean hasAttribute(String key) {
        return channel.hasAttribute(key);
    }

    // 可以先看 startReconnectTask
    private void startHeartBeatTask(URL url) {
        // 进去 ，不过默认NettyClient是返回true的，即canHandleIdle，表示客户端能自己处理心跳（利用空闲检测）
        if (!client.canHandleIdle()) {
            AbstractTimerTask.ChannelProvider cp = () -> Collections.singletonList(HeaderExchangeClient.this);
            int heartbeat = getHeartbeat(url);// 进去 下面startReconnectTask是 getIdleTimeout
            long heartbeatTick = calculateLeastDuration(heartbeat);
            this.heartBeatTimerTask = new HeartbeatTimerTask(cp, heartbeatTick, heartbeat);// 进去
            IDLE_CHECK_TIMER.newTimeout(heartBeatTimerTask, heartbeatTick, TimeUnit.MILLISECONDS);
        }
    }

    private void startReconnectTask(URL url) {
        // 进去
        if (shouldReconnect(url)) {
            // ChannelProvider函数接口去看下，进去
            AbstractTimerTask.ChannelProvider cp = () -> Collections.singletonList(HeaderExchangeClient.this);
            int idleTimeout = getIdleTimeout(url);// 进去
            long heartbeatTimeoutTick = calculateLeastDuration(idleTimeout);// 进去
            // 上两个参数作用：第一个值是第二个值的3倍（看calculateLeastDuration方法）。
            // 第二个是定时间隔heartbeatTimeoutTick发起ReConnect(ReconnectTimerTask的doTask方法)，即控制周期的/间隔的
            // 第一个参数是NettyClient的IdleStateHandler的读空闲最大时间，doTask内部检测到now和lastRead相差这么久的话，也会Reconnect，防止被服务端关闭

            // ReconnectTimerTask进去
            this.reconnectTimerTask = new ReconnectTimerTask(cp, heartbeatTimeoutTick, idleTimeout);
            // newTimeout 调度指定的TimerTask在指定的延迟后一次性执行
            IDLE_CHECK_TIMER.newTimeout(reconnectTimerTask, heartbeatTimeoutTick, TimeUnit.MILLISECONDS);
        }
    }

    private void doClose() {
        if (heartBeatTimerTask != null) {
            heartBeatTimerTask.cancel();// 进去
        }

        if (reconnectTimerTask != null) {
            reconnectTimerTask.cancel();// 进去
        }
    }

    /**
     * Each interval cannot be less than 1000ms.
     */
    private long calculateLeastDuration(int time) {
        if (time / HEARTBEAT_CHECK_TICK <= 0) {
            return LEAST_HEARTBEAT_DURATION;
        } else {
            return time / HEARTBEAT_CHECK_TICK;
        }
    }

    private boolean shouldReconnect(URL url) {
        return url.getParameter(Constants.RECONNECT_KEY, true);
    }

    @Override
    public String toString() {
        return "HeaderExchangeClient [channel=" + channel + "]";
    }
}
