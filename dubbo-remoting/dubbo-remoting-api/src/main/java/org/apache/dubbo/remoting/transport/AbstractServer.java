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
package org.apache.dubbo.remoting.transport;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.utils.ExecutorUtil;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.RemotingServer;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.remoting.Constants.ACCEPTS_KEY;
import static org.apache.dubbo.remoting.Constants.DEFAULT_ACCEPTS;
import static org.apache.dubbo.remoting.Constants.DEFAULT_IDLE_TIMEOUT;
import static org.apache.dubbo.remoting.Constants.IDLE_TIMEOUT_KEY;

/**
 * AbstractServer
 */
// OK
public abstract class AbstractServer extends AbstractEndpoint implements RemotingServer {

    // gx
    protected static final String SERVER_THREAD_POOL_NAME = "DubboServerHandler";
    private static final Logger logger = LoggerFactory.getLogger(AbstractServer.class);
    ExecutorService executor;
    private InetSocketAddress localAddress;
    private InetSocketAddress bindAddress;
    private int accepts;
    private int idleTimeout;
    // 去看下默认扩展构造器
    private ExecutorRepository executorRepository = ExtensionLoader.getExtensionLoader(ExecutorRepository.class).getDefaultExtension();

    public AbstractServer(URL url, ChannelHandler handler) throws RemotingException {
        // 传进来的handler是Wrap的：MultiMessageHandler(HeartbeatHandler(hanlder/ExchangeHandler))，最里层的就是我们的业务处理器，
        // 比如 DubboProtocol 的 成员ExchangeHandler requestHandler
        // 进去
        super(url, handler); //eg  url :telnet://localhost:56780?codec=exchange&exchanger=header&heartbeat=1000&threadname=DubboServerHandler-localhost:56780&transporter=netty4
        localAddress = getUrl().toInetSocketAddress();

        // 获取 ip 和端口
        String bindIp = getUrl().getParameter(Constants.BIND_IP_KEY, getUrl().getHost());
        int bindPort = getUrl().getParameter(Constants.BIND_PORT_KEY, getUrl().getPort());
        if (url.getParameter(ANYHOST_KEY, false) || NetUtils.isInvalidLocalHost(bindIp)) {
            // 如果满足上面的条件，即anyhost = true或者host为127.0.0.1、localhost、0.0.0.0等，就设置 ip 为 0.0.0.0，表示服务端的socket不选择ip，一个机器可以多ip/多宿的，这样其他客户端可以指定多个服务端ip，都能接收到
            bindIp = ANYHOST_VALUE;
        }
        bindAddress = new InetSocketAddress(bindIp, bindPort);
        // 获取最大可接受连接数
        this.accepts = url.getParameter(ACCEPTS_KEY, DEFAULT_ACCEPTS);
        // 该值在NettyServer子类用到（这句话不对，子类用的不是这个，去看下int idleTimeout = UrlUtils.getIdleTimeout(getUrl()) ，从url取的key不一样）
        this.idleTimeout = url.getParameter(IDLE_TIMEOUT_KEY, DEFAULT_IDLE_TIMEOUT);
        try {
            // 调用模板方法 doOpen 启动服务器
            // 上面代码多为赋值代码，不需要多讲。我们重点关注 doOpen 抽象方法，该方法需要子类实现，去看下NettyServer的doOpen
            doOpen();
            if (logger.isInfoEnabled()) {
                logger.info("Start " + getClass().getSimpleName() + " bind " + getBindAddress() + ", export " + getLocalAddress());
            }
        } catch (Throwable t) {
            throw new RemotingException(url.toInetSocketAddress(), null, "Failed to bind " + getClass().getSimpleName()
                    + " on " + getLocalAddress() + ", cause: " + t.getMessage(), t);
        }
        //  创建executor
        executor = executorRepository.createExecutorIfAbsent(url);
    }

    protected abstract void doOpen() throws Throwable;

    protected abstract void doClose() throws Throwable;

    @Override
    public void reset(URL url) {
        if (url == null) {
            return;
        }
        try {
            if (url.hasParameter(ACCEPTS_KEY)) {
                int a = url.getParameter(ACCEPTS_KEY, 0);
                if (a > 0) {
                    this.accepts = a;
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        try {
            if (url.hasParameter(IDLE_TIMEOUT_KEY)) {
                int t = url.getParameter(IDLE_TIMEOUT_KEY, 0);
                if (t > 0) {
                    this.idleTimeout = t;
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        // 进去
        executorRepository.updateThreadpool(url, executor);
        // 更新父类的url
        super.setUrl(getUrl().addParameters(url.getParameters()));
    }

    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        Collection<Channel> channels = getChannels(); // 去看NettyServer的getChannels方法
        for (Channel channel : channels) {
            if (channel.isConnected()) {
                channel.send(message, sent); // 发消息
            }
        }
    }

    @Override
    public void close() {
        if (logger.isInfoEnabled()) {
            logger.info("Close " + getClass().getSimpleName() + " bind " + getBindAddress() + ", export " + getLocalAddress());
        }
        // 进去
        ExecutorUtil.shutdownNow(executor, 100);
        try {
            super.close();
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            doClose();
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public void close(int timeout) {
        ExecutorUtil.gracefulShutdown(executor, timeout);
        close();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    public InetSocketAddress getBindAddress() {
        return bindAddress;
    }

    public int getAccepts() {
        return accepts;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    // gx 主要是NettyServeHandler#ChannelActive触发
    @Override
    public void connected(Channel ch) throws RemotingException {
        // If the server has entered the shutdown process, reject any new connection
        if (this.isClosing() || this.isClosed()) {// 这里的this是NettyServer实例，注意有两个关闭状态：Closing和Closed，都进去看下
            // 日志
            logger.warn("Close new channel " + ch + ", cause: server is closing or has been closed. For example, receive a new connect request while in shutdown process.");
            ch.close();
            return;
        }

        Collection<Channel> channels = getChannels();
        if (accepts > 0 && channels.size() > accepts) {
            // 日志
            logger.error("Close channel " + ch + ", cause: The server " + ch.getLocalAddress() + " connections greater than max config " + accepts);
            ch.close();
            return;
        }
        super.connected(ch);
    }

    @Override
    public void disconnected(Channel ch) throws RemotingException {
        Collection<Channel> channels = getChannels(); // 进去
        if (channels.isEmpty()) { // 日志
            logger.warn("All clients has disconnected from " + ch.getLocalAddress() + ". You can graceful shutdown now.");
        }
        super.disconnected(ch);// 进去
    }

}
