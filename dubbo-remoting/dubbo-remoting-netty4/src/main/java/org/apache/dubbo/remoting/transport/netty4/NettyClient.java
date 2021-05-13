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
package org.apache.dubbo.remoting.transport.netty4;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.transport.AbstractClient;
import org.apache.dubbo.remoting.transport.dispatcher.ChannelHandlers;
import org.apache.dubbo.remoting.utils.UrlUtils;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.proxy.Socks5ProxyHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.InetSocketAddress;
import java.util.Date;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.dubbo.common.constants.CommonConstants.SSL_ENABLED_KEY;
import static org.apache.dubbo.remoting.transport.netty4.NettyEventLoopFactory.eventLoopGroup;
import static org.apache.dubbo.remoting.transport.netty4.NettyEventLoopFactory.socketChannelClass;

/**
 * NettyClient.
 */
// OK
public class NettyClient extends AbstractClient {

    private static final Logger logger = LoggerFactory.getLogger(NettyClient.class);
    /**
     * netty client bootstrap
     */
    // client一个EventLoopGroup即可，这个是workerGroup
    private static final EventLoopGroup NIO_EVENT_LOOP_GROUP = eventLoopGroup(Constants.DEFAULT_IO_THREADS, "NettyClientWorker");

    private static final String SOCKS_PROXY_HOST = "socksProxyHost";

    private static final String SOCKS_PROXY_PORT = "socksProxyPort";

    private static final String DEFAULT_SOCKS_PROXY_PORT = "1080";

    private Bootstrap bootstrap;

    /**
     * current channel. Each successful invocation of {@link NettyClient#doConnect()} will
     * replace this with new channel and close old channel.
     * <b>volatile, please copy reference to use.</b>
     */
    // 上面解释了volatile的原因，因为doConnect是别的线程触发的（EventLoopGroup回调NettyClientHandler）然后更新channel，
    // 而别的线程，比如业务线程需要channel拿来发消息，这里利用volatile保证多线程的可见性
    private volatile Channel channel;

    /**
     * The constructor of NettyClient.
     * It wil init and start netty.
     */
    public NettyClient(final URL url, final ChannelHandler handler) throws RemotingException {
    	// you can customize name and type of client thread pool by THREAD_NAME_KEY and THREADPOOL_KEY in CommonConstants.
    	// the handler will be wrapped: MultiMessageHandler->HeartbeatHandler->handler
       // 原来是这样super(url, wrapChannelHandler(url, handler)); 为了和NettyServer统一 我改成了如下
        // server直接给url添加了THREAD_NAME_KEY参数，这里没有
    	super(url,  ChannelHandlers.wrap(handler, url));

    }

    /**
     * Init bootstrap
     *
     * @throws Throwable
     */
    @Override
    protected void doOpen() throws Throwable {
        // 进去
        final NettyClientHandler nettyClientHandler = new NettyClientHandler(getUrl(), this);
        bootstrap = new Bootstrap();
        bootstrap.group(NIO_EVENT_LOOP_GROUP)
                .option(ChannelOption.SO_KEEPALIVE, true) // 一般是客户端指定长链接
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                //.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, getTimeout())
                .channel(socketChannelClass());

        bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.max(3000, getConnectTimeout()));
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {

            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                // 进去
                int heartbeatInterval = UrlUtils.getHeartbeat(getUrl());
                System.out.println(heartbeatInterval+"客户端读写空闲最大时间");
                if (getUrl().getParameter(SSL_ENABLED_KEY, false)) {
                    ch.pipeline().addLast("negotiation", SslHandlerInitializer.sslClientHandler(getUrl(), nettyClientHandler));
                }

                NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyClient.this);
                ch.pipeline()//.addLast("logging",new LoggingHandler(LogLevel.INFO))//for debug
                        .addLast("decoder", adapter.getDecoder())
                        .addLast("encoder", adapter.getEncoder())
                        // 读空闲（服务端是读写空闲）且客户端直接使用url的heartbeat参数值（没有的话默认60s），而服务端则是调用getIdleTimeout方法（如果url配置heartbeat参数的话，默认是3倍）

                        // 那么这1s内如果没有从服务器读取数据的话，就会触发NettyClient的userEventTrigger方法，内部判断如果是空闲超时时间，
                        // 则会channel.send 发数据。s端会给自己的IdleStateHandler配置**读写空闲的**最大时间，到达最大空闲时间前收到这个
                        // 数据的话，则会重置，如果一直没有收到，NettyServerHandler内部则会channel.close关闭和客户端的连接。之所以c端仅
                        // 配置读空闲，是因为本身正常send数据的话，s端收到也会重置
                        .addLast("client-idle-handler", new IdleStateHandler(heartbeatInterval, 0, 0, MILLISECONDS))
                        .addLast("handler", nettyClientHandler);

                String socksProxyHost = ConfigUtils.getProperty(SOCKS_PROXY_HOST);
                if(socksProxyHost != null) {
                    int socksProxyPort = Integer.parseInt(ConfigUtils.getProperty(SOCKS_PROXY_PORT, DEFAULT_SOCKS_PROXY_PORT));
                    Socks5ProxyHandler socks5ProxyHandler = new Socks5ProxyHandler(new InetSocketAddress(socksProxyHost, socksProxyPort));
                    ch.pipeline().addFirst(socks5ProxyHandler);
                }
            }
        });
    }

    @Override
    protected void doConnect() throws Throwable {
        long start = System.currentTimeMillis();
        // getConnectAddress 进去
        ChannelFuture future = bootstrap.connect(getConnectAddress());
        try {
            // 等待连接超时(server是直接 syncUninterruptibly)
            boolean ret = future.awaitUninterruptibly(getConnectTimeout(), MILLISECONDS);

            if (ret && future.isSuccess()) {
                System.out.println(new Date()+"客户端连接成功");
                Channel newChannel = future.channel();
                try {
                    // Close old channel
                    // copy reference
                    Channel oldChannel = NettyClient.this.channel;
                    if (oldChannel != null) {
                        try {
                            if (logger.isInfoEnabled()) {
                                logger.info("Close old netty channel " + oldChannel + " on create new netty channel " + newChannel);
                            }
                            oldChannel.close();
                        } finally {
                            NettyChannel.removeChannelIfDisconnected(oldChannel);
                        }
                    }
                } finally {
                    if (NettyClient.this.isClosed()) {
                        try {
                            if (logger.isInfoEnabled()) {
                                logger.info("Close new netty channel " + newChannel + ", because the client closed.");
                            }
                            newChannel.close();
                        } finally {
                            NettyClient.this.channel = null;
                            NettyChannel.removeChannelIfDisconnected(newChannel);
                        }
                    } else {
                        NettyClient.this.channel = newChannel;
                    }
                }
                // 连接出现异常
            } else if (future.cause() != null) {
                throw new RemotingException(this, "client(url: " + getUrl() + ") failed to connect to server "
                        + getRemoteAddress() + ", error message is:" + future.cause().getMessage(), future.cause());
            } else {
                // 连接超时
                throw new RemotingException(this, "client(url: " + getUrl() + ") failed to connect to server "
                        + getRemoteAddress() + " client-side timeout "
                        + getConnectTimeout() + "ms (elapsed: " + (System.currentTimeMillis() - start) + "ms) from netty client "
                        + NetUtils.getLocalHost() + " using dubbo version " + Version.getVersion());
            }
        } finally {
            // just add new valid channel to NettyChannel's cache
            if (!isConnected()) {
                //future.cancel(true);
            }
        }
    }

    @Override
    protected void doDisConnect() throws Throwable {
        try {
            NettyChannel.removeChannelIfDisconnected(channel);
        } catch (Throwable t) {
            logger.warn(t.getMessage());
        }
    }

    @Override
    protected void doClose() throws Throwable {
        // can't shutdown nioEventLoopGroup because the method will be invoked when closing one channel but not a client,
        // but when and how to close the nioEventLoopGroup ?
        // nioEventLoopGroup.shutdownGracefully();
    }

    @Override
    protected org.apache.dubbo.remoting.Channel getChannel() {
        Channel c = channel;
        if (c == null) {
            return null;
        }
        return NettyChannel.getOrAddChannel(c, getUrl(), this);
    }

    Channel getNettyChannel() {
        return channel;
    }

    @Override
    public boolean canHandleIdle() {
        return true;
    }
}
