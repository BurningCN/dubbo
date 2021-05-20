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
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ExecutorUtil;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.RemotingServer;
import org.apache.dubbo.remoting.transport.AbstractServer;
import org.apache.dubbo.remoting.transport.dispatcher.ChannelHandlers;
import org.apache.dubbo.remoting.utils.UrlUtils;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.InetSocketAddress;
import java.util.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.dubbo.common.constants.CommonConstants.IO_THREADS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.SSL_ENABLED_KEY;

/**
 * NettyServer.
 */
// OK
// extends AbstractServer 。
public class NettyServer extends AbstractServer /*implements RemotingServer */{ // 这里我给注释了感觉没用，可以和NettyClient对比下

    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
    /**
     * the cache for alive worker channel.
     * <ip:port, dubbo channel>
     */
    private Map<String, Channel> channels;
    /**
     * netty server bootstrap.
     */
    private ServerBootstrap bootstrap;
    /**
     * the boss channel that receive connections and dispatch these to worker channel.
     */
	private io.netty.channel.Channel channel;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyServer(URL url, ChannelHandler handler) throws RemotingException {
        // you can customize name and type of client thread pool by THREAD_NAME_KEY and THREADPOOL_KEY in CommonConstants.
        // 可以通过CommonConstants中的THREAD_NAME_KEY和THREADPOOL_KEY自定义client线程池的名称和类型。
        // the handler will be wrapped: MultiMessageHandler->HeartbeatHandler->handler
        // setThreadName、wrap、super方法都进去
        // 添加了 threadname=DubboServerHandler-30.25.58.158:20880 参数
        super(ExecutorUtil.setThreadName(url, SERVER_THREAD_POOL_NAME), ChannelHandlers.wrap(handler, url));
    }

    /**
     * Init and start netty server
     *
     * @throws Throwable
     */
    // 我们来看关键的doOpen模板方法，父类的构造方法再进行一些初始化操作之后会调用该方法，方法主要是标准的Netty服务端程序，
    // bootstrap 、 bossGroup+workerGroup 这两个利用前面的NettyEventLoopFactory进行eventLoopGroup的创建（Epoll型还是NIO型），
    // 然后就是范式如下：.... 省略.... 。childHandler主要是通过ChannelInitializer的initChannel(SocketChannel ch)方法，
    // 里面利用ch.pipeline()添加addLast各种handler，包括adapter.getDecoder()、adapter.getEncoder()、IdleStateHandler、NettyServerHandler。
    // 这里学习到空闲状态处理器检测到读写空闲超时后（从url取的空闲超时时间）会产生事件传递给NettyServerHandler，其有一个userEventTriggered方法会得到触发。
    // 注意顺序，入站顺序adapter.getDecoder - >  IdleStateHandler ->NettyServerHandler，出站顺序adapter.getEncoder ->IdleStateHandler -> NettyServerHandler
    @Override
    protected void doOpen() throws Throwable { // 以下代码就是netty server的范式代码，自己看下即可
        bootstrap = new ServerBootstrap();

        // eventLoopGroup 指定线程个数和线程名称，进去
        bossGroup = NettyEventLoopFactory.eventLoopGroup(1, "NettyServerBoss"); // boss 一个线程接受连接即可
        workerGroup = NettyEventLoopFactory.eventLoopGroup(
                // 默认  Math.min(Runtime.getRuntime().availableProcessors() + 1, 32);
                // worker的线程数取决于当前系统的核数
                getUrl().getPositiveParameter(IO_THREADS_KEY, Constants.DEFAULT_IO_THREADS),
                "NettyServerWorker");

        // 进去
        final NettyServerHandler nettyServerHandler = new NettyServerHandler(getUrl(), this);
        channels = nettyServerHandler.getChannels();

        bootstrap.group(bossGroup, workerGroup)
                .channel(NettyEventLoopFactory.serverSocketChannelClass())// 进去，epoll和nio
                .option(ChannelOption.SO_REUSEADDR, Boolean.TRUE)// 连接复用
                .childOption(ChannelOption.TCP_NODELAY, Boolean.TRUE)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // FIXME: should we use getTimeout()?
                        // 进去
                        int idleTimeout = UrlUtils.getIdleTimeout(getUrl());;
                        System.out.println(idleTimeout+"服务端读写空闲最大时间");
                        // 进去
                        NettyCodecAdapter adapter = new NettyCodecAdapter(getCodec(), getUrl(), NettyServer.this);
                        if (getUrl().getParameter(SSL_ENABLED_KEY, false)) {
                            ch.pipeline().addLast("negotiation",
                                    SslHandlerInitializer.sslServerHandler(getUrl(), nettyServerHandler)); // ssl
                        }
                        ch.pipeline()
                                .addLast("decoder", adapter.getDecoder()) // 进去
                                .addLast("encoder", adapter.getEncoder()) // 进去
                                // 客户端配置的读空闲，服务端是读写空闲。且客户端直接使用url的heartbeat参数值（没有的话默认60s），而服务端则是调用getIdleTimeout方法（如果url配置heartbeat参数的话，默认是3倍）
                                // 空闲状态处理器检测到读写空闲超时后（从url取的空闲超时时间）会产生事件(源码内部调用fireUserEventTriggered)传递给NettyServerHandler，其有一个userEventTriggered方法会得到触发
                                .addLast("server-idle-handler", new IdleStateHandler(0, 0, idleTimeout, MILLISECONDS))
                                .addLast("handler", nettyServerHandler); // 进去
                    }
                    // 注意顺序，入站顺序 adapter.getDecoder - >  IdleStateHandler ->NettyServerHandler，出站顺序adapter.getEncoder ->IdleStateHandler -> NettyServerHandler
                });
        // bind getBindAddress进去，其属性赋值处注意下
        ChannelFuture channelFuture = bootstrap.bind(getBindAddress());
        channelFuture.syncUninterruptibly(); // Uninterruptibly 不间断地；连续地
        channel = channelFuture.channel(); // NioServerSocketChannel
        System.out.println(new Date()+"服务端绑定成功");

    }

    // doClose方法比较简单：channel.close()（这里的channel是channelFuture.channel()返回的） + getChannels()遍历close + boss/workerGroup.shutdownGracefully().syncUninterruptibly();
    @Override
    protected void doClose() throws Throwable {
        try {
            if (channel != null) {
                // unbind.
                channel.close();
            }
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            Collection<org.apache.dubbo.remoting.Channel> channels = getChannels();
            if (channels != null && channels.size() > 0) {
                for (org.apache.dubbo.remoting.Channel channel : channels) {
                    try {
                        channel.close();
                    } catch (Throwable e) {
                        logger.warn(e.getMessage(), e);
                    }
                }
            }
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            if (bootstrap != null) {
                bossGroup.shutdownGracefully().syncUninterruptibly();
                workerGroup.shutdownGracefully().syncUninterruptibly();
            }
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            if (channels != null) {
                channels.clear();
            }
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public Collection<Channel> getChannels() {
        Collection<Channel> chs = new ArrayList<>(this.channels.size());
        chs.addAll(this.channels.values());
        // check of connection status is unnecessary since we are using channels in NettyServerHandler
        // 不需要检查连接状态，因为我们在NettyServerHandler中使用通道
//        for (Channel channel : this.channels.values()) {
//            if (channel.isConnected()) {
//                chs.add(channel);
//            } else {
//                channels.remove(NetUtils.toAddressString(channel.getRemoteAddress()));
//            }
//        }
        return chs;
    }

    @Override
    public Channel getChannel(InetSocketAddress remoteAddress) {
        return channels.get(NetUtils.toAddressString(remoteAddress));
    }

    @Override
    public boolean canHandleIdle() {
        return true;
    }

    // 所谓的isBound就是看channel是否Active
    @Override
    public boolean isBound() {
        return channel.isActive();
    }

}
