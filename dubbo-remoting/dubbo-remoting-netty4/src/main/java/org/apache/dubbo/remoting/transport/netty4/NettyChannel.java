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
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.transport.AbstractChannel;
import org.apache.dubbo.remoting.utils.PayloadDropper;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * NettyChannel maintains the cache of channel.
 */
// OK
// 主要是缓存netty的channel的。
// （1）几个重要属性：缓存对象为ConcurrentMap<Channel, NettyChannel> <u>CHANNEL_MAP</u>。入缓存的时候，
// 如果channel.isActive的话，把nettyChannel的AtomicBoolean <u>active设置为true</u>，从缓存remove的nettyChannel.markActive(false)。
// Map<String, Object> <u>attributes</u> 属性存放一些kv，没有在netty的channel api来添加属性。Channel channel属性，即Netty的channel。
// （2）send方法核心操作为ChannelFuture future = channel.writeAndFlush(message) +  future.await( url.getTimeout) + Throwable cause = future.cause()。
// close方法主要是清空attributes + channel.close()。
final class NettyChannel extends AbstractChannel {

    private static final Logger logger = LoggerFactory.getLogger(NettyChannel.class);
    /**
     * the cache for netty channel and dubbo channel
     */
    private static final ConcurrentMap<Channel, NettyChannel> CHANNEL_MAP = new ConcurrentHashMap<Channel, NettyChannel>();
    /**
     * netty channel
     */
    private final Channel channel;

    private final Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();

    private final AtomicBoolean active = new AtomicBoolean(false);

    /**
     * The constructor of NettyChannel.
     * It is private so NettyChannel usually create by {@link NettyChannel#getOrAddChannel(Channel, URL, ChannelHandler)}
     *
     * @param channel netty channel
     * @param url
     * @param handler dubbo handler that contain netty handler
     */
    private NettyChannel(Channel channel, URL url, ChannelHandler handler) {
        super(url, handler);// 进去
        if (channel == null) {
            throw new IllegalArgumentException("netty channel == null;");
        }
        this.channel = channel;
    }

    /**
     * Get dubbo channel by netty channel through channel cache.
     * Put netty channel into it if dubbo channel don't exist in the cache.
     *
     * @param ch      netty channel
     * @param url
     * @param handler dubbo handler that contain netty's handler
     * @return
     */
    static NettyChannel getOrAddChannel(Channel ch, URL url, ChannelHandler handler) {
        if (ch == null) {
            return null;
        }
        // 尝试从集合中获取 NettyChannel 实例
        NettyChannel ret = CHANNEL_MAP.get(ch);
        if (ret == null) {
            // 如果 ret = null，则创建一个新的 NettyChannel 实例 进去
            NettyChannel nettyChannel = new NettyChannel(ch, url, handler);
            if (ch.isActive()) {
                nettyChannel.markActive(true);// 进去
                // 将 <Channel, NettyChannel> 键值对存入 channelMap 集合中
                ret = CHANNEL_MAP.putIfAbsent(ch, nettyChannel);
            }
            if (ret == null) {
                ret = nettyChannel;
            }
        }
        return ret;
        // 获取到 NettyChannel 实例后，即可进行后续的调用。下面看一下 NettyChannel 的 send 方法。
    }

    /**
     * Remove the inactive channel.
     *
     * @param ch netty channel
     */
    static void removeChannelIfDisconnected(Channel ch) {
        if (ch != null && !ch.isActive()) {
            NettyChannel nettyChannel = CHANNEL_MAP.remove(ch);
            if (nettyChannel != null) {
                nettyChannel.markActive(false);
            }
        }
    }

    static void removeChannel(Channel ch) {
        if (ch != null) {
            NettyChannel nettyChannel = CHANNEL_MAP.remove(ch);
            if (nettyChannel != null) {
                nettyChannel.markActive(false);
            }
        }
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) channel.localAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) channel.remoteAddress();
    }

    @Override
    public boolean isConnected() {
        return !isClosed() && active.get();
    }

    public boolean isActive() {
        return active.get();
    }

    public void markActive(boolean isActive) {
        active.set(isActive);
    }

    /**
     * Send message by netty and whether to wait the completion of the send.
     *
     * @param message message that need send.
     * @param sent    whether to ack async-sent
     * @throws RemotingException throw RemotingException if wait until timeout or any exception thrown by method body that surrounded by try-catch.
     */
    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        // whether the channel is closed 进去
        super.send(message, sent);

        boolean success = true;
        int timeout = 0;
        try {
            // 核心，注意去看NettCodecAdapter的encode方法
            ChannelFuture future = channel.writeAndFlush(message);
            // sent 的值源于 <dubbo:method sent="true/false" /> 中 sent 的配置值，有两种配置值：
            //   1. true: 等待消息发出，消息发送失败将抛出异常
            //   2. false: 不等待消息发出，将消息放入 IO 队列，即刻返回
            // 默认情况下 sent = false；
            if (sent) {
                // wait timeout ms
                timeout = getUrl().getPositiveParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT);
                // 等待消息发出，若在规定时间没能发出，success 会被置为 false
                success = future.await(timeout);
            }
            Throwable cause = future.cause();
            if (cause != null) {
                throw cause;
            }
        } catch (Throwable e) {
            removeChannelIfDisconnected(channel);
            throw new RemotingException(this, "Failed to send message " + PayloadDropper.getRequestWithoutData(message) + " to " + getRemoteAddress() + ", cause: " + e.getMessage(), e);
        }
        // 若 success 为 false，这里抛出异常
        if (!success) {
            throw new RemotingException(this, "Failed to send message " + PayloadDropper.getRequestWithoutData(message) + " to " + getRemoteAddress()
                    + "in timeout(" + timeout + "ms) limit");
        }
        // 经历多次调用，到这里请求数据的发送过程就结束了，过程漫长。为了便于大家阅读代码，这里以 DemoService 为例，将 sayHello 方法的整个调用路径贴出来。
        // proxy0#sayHello(String)
        //  —> InvokerInvocationHandler#invoke(Object, Method, Object[])
        //    —> MockClusterInvoker#invoke(Invocation)
        //      —> AbstractClusterInvoker#invoke(Invocation)
        //        —> FailoverClusterInvoker#doInvoke(Invocation, List<Invoker<T>>, LoadBalance)
        //          —> Filter#invoke(Invoker, Invocation)  // 包含多个 Filter 调用
        //            —> ListenerInvokerWrapper#invoke(Invocation)
        //              —> AbstractInvoker#invoke(Invocation)
        //                —> DubboInvoker#doInvoke(Invocation)
        //                  —> ReferenceCountExchangeClient#request(Object, int)
        //                    —> HeaderExchangeClient#request(Object, int)
        //                      —> HeaderExchangeChannel#request(Object, int)
        //                        —> AbstractPeer#send(Object)
        //                          —> AbstractClient#send(Object, boolean)
        //                            —> NettyChannel#send(Object, boolean)
        //                              —> NioClientSocketChannel#write(Object)
        // 在 Netty 中，出站数据在发出之前还需要进行编码操作，接下来我们来分析一下请求数据的编码逻辑。

        // Dubbo 数据包分为消息头和消息体，消息头用于存储一些元信息，比如魔数（Magic），数据包类型（Request/Response），消息体长度
        // （Data Length）等。消息体中用于存储具体的调用消息，比如方法名称，参数列表等。下面简单列举一下消息头的内容。
        // ...........数据包结构图、消息头内容.......
        // ...........数据包结构图、消息头内容.......
        // ...........数据包结构图、消息头内容.......
        // 了解了 Dubbo 数据包格式，接下来我们就可以探索编码过程了。这次我们开门见山，直接分析编码逻辑所在类 ，我们去看下 ExchangeCodec
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            removeChannelIfDisconnected(channel);
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            attributes.clear();
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            if (logger.isInfoEnabled()) {
                logger.info("Close netty channel " + channel);
            }
            channel.close();
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    @Override
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        // The null value is unallowed in the ConcurrentHashMap.
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }

    @Override
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((channel == null) ? 0 : channel.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }

        // FIXME: a hack to make org.apache.dubbo.remoting.exchange.support.DefaultFuture.closeChannel work
        if (obj instanceof NettyClient) {
            NettyClient client = (NettyClient) obj;
            return channel.equals(client.getNettyChannel());
        }

        if (getClass() != obj.getClass()) {
            return false;
        }
        NettyChannel other = (NettyChannel) obj;
        if (channel == null) {
            if (other.channel != null) {
                return false;
            }
        } else if (!channel.equals(other.channel)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "NettyChannel [channel=" + channel + "]";
    }

}
