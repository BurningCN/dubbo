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
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.transport.netty4.SslHandlerInitializer.HandshakeCompletionEvent;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;

import java.net.InetSocketAddress;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NettyServerHandler.
 */
// OK
// extends ChannelDuplexHandler，复用的、双向的出入站处理器，关注读写、连接、取消连接、关闭等事件，主要是作为服务端的childHandler的一员，
// 在各种事件到来固有的两个步骤（1）NettyChannel.getOrAddChannel(ctx.channel(), url, handler) ，缓存到NettyChannel内部也有一个
// Static即类持有/实例共享的CHANNEL_MAP变量中，同时还缓存到本类的channels map 属性中。如果是连接不活跃、空闲超时、抛异常会从CHANNEL_MAP移除
// （2）传递事件给通过构造函数传进来的handler，特指NettyServer，比如handler.connected(channel) ，connected是其父类AbstractServer的方法。
@io.netty.channel.ChannelHandler.Sharable
public class NettyServerHandler extends ChannelDuplexHandler { // 和rmq的一样，双向/复用处理器，读写、连接、取消连接、关闭等事件
    private static final Logger logger = LoggerFactory.getLogger(NettyServerHandler.class);
    /**
     * the cache for alive worker channel.
     * <ip:port, dubbo channel>
     */
    private final Map<String, Channel> channels = new ConcurrentHashMap<String, Channel>();

    private final URL url;

    private final ChannelHandler handler;

    // gx
    public NettyServerHandler(URL url, ChannelHandler handler) {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        this.url = url;
        this.handler = handler;
    }

    public Map<String, Channel> getChannels() {
        return channels;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 进去，注意其NettyChannel的toString方法，比如下面返回的
        // channel： NettyChannel [channel=[id: 0x3317ff9f, L:/30.25.58.152:56780 - R:/30.25.58.152:63761]]
        // HeartbeatTest的serverUrl绑定的就是56780，所以左边的L(Local)就是本地服务的address，端口的确是56780；
        // 右边R表示远端，即客户端的，客户端的端口63761是在调用bootstrap.connect内核分配的的临时可用端口
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        if (channel != null) {
            // 缓存 channel ，key为远端/客户端的ip:port，value是NettyChannel，NettyChannel内部包装实际的ctx.channel()
            //channels = {ConcurrentHashMap@4725}  size = 1
            // "30.25.58.158:50914" -> {NettyChannel@4929} "NettyChannel [channel=[id: 0x5cc6d0a9, L:/30.25.58.158:20880 - R:/30.25.58.158:50914]]"
            channels.put(NetUtils.toAddressString((InetSocketAddress) ctx.channel().remoteAddress()), channel);
        }
        // 回调"监听"类的方法，这个handler是NettyServer，connected是其父类AbstractServer的方法，进去
        handler.connected(channel);

        if (logger.isInfoEnabled()) {
            logger.info("The connection of " + channel.getRemoteAddress() + " -> " + channel.getLocalAddress() + " is established.");
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        try {
            channels.remove(NetUtils.toAddressString((InetSocketAddress) ctx.channel().remoteAddress()));
            handler.disconnected(channel);
        } finally {
            // NettyChannel内部也有一个Static即类持有/实例共享的CHANNEL_MAP缓存，进去也给remove下，进去
            NettyChannel.removeChannel(ctx.channel());
        }

        if (logger.isInfoEnabled()) {
            logger.info("The connection of " + channel.getRemoteAddress() + " -> " + channel.getLocalAddress() + " is disconnected.");
        }
    }

    // 关键方法
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        // 经过 adapter.getDecoder() 处理后，此时msg就是解码后的正常消息了，传给如下我们的handler,handler是nettyServer，其还包装了
        // 关键的业务handler，这个业务handler也是包装结构为：MultiMessageHandler ->HeartbeatHandler ->AllChannelHandler ->
        // MultiMessageHandler ->HeartbeatHandler->handler(handler比如DubboProtocol的new ExchangeHandlerAdapter)
        // 至于业务handler的赋值链路为：NettyServer -> AbstractServer->AbstractEndpoint->AbstractPeer。下面的received就去AbstractPeer的看下
        handler.received(channel, msg);
        // 栈帧如下，下面第一个为最后一个步入的逻辑，然后AllChannelHandler就是提交任务给线程池了，就走DecodeHandler(HeaderExchangeHandler(requestHandler))的逻辑了
        //received:71, AllChannelHandler (org.apache.dubbo.remoting.transport.dispatcher.all)
        //received:95, HeartbeatHandler (org.apache.dubbo.remoting.exchange.support.header)
        //received:46, MultiMessageHandler (org.apache.dubbo.remoting.transport)
        //received:149, AbstractPeer (org.apache.dubbo.remoting.transport)
        //channelRead:117, NettyServerHandler (org.apache.dubbo.remoting.transport.netty4)

// ================下面是官方文档doc的部分文档========

        // 逻辑比较简单。首先根据一些信息获取 NettyChannel 实例，然后将 NettyChannel 实例以及
        // Request 对象向下传递。下面再来看看 AllChannelHandler 的逻辑，在详细分析代码之前，我们先来了解一下 Dubbo 中的线程派发模型。
        // ##### 2.3.2.1 线程派发模型
        //
        // Dubbo 将底层通信框架中接收请求的线程称为 IO 线程。如果一些事件处理逻辑可以很快执行完，比如只在内存打一个标记，此时直接在 IO 线程上执
        // 行该段逻辑即可。但如果事件的处理逻辑比较耗时，比如该段逻辑会发起数据库查询或者 HTTP 请求。此时我们就不应该让事件处理逻辑在 IO 线程上执
        // 行，而是应该派发到线程池中去执行。原因也很简单，IO 线程主要用于接收请求，如果 IO 线程被占满，将导致它不能接收新的请求。
        //
        // 以上就是线程派发的背景，下面我们再来通过 Dubbo 调用图，看一下线程派发器所处的位置。
        // .....省略图.....(看图的话去看原md文件)
        // 如上图，红框中的 Dispatcher 就是线程派发器。需要说明的是，Dispatcher 真实的职责创建具有线程派发能力的 ChannelHandler，比如 AllChannelHandler、MessageOnlyChannelHandler 和 ExecutionChannelHandler 等，其本身并不具备线程派发能力。Dubbo 支持 5 种不同的线程派发策略，下面通过一个表格列举一下。
        //
        //| 策略       | 用途                                                         |
        //| ---------- | ------------------------------------------------------------ |
        //| all        | 所有消息都派发到线程池，包括请求，响应，连接事件，断开事件等 |
        //| direct     | 所有消息都不派发到线程池，全部在 IO 线程上直接执行           |
        //| message    | 只有**请求**和**响应**消息派发到线程池，其它消息均在 IO 线程上执行 |
        //| execution  | 只有**请求**消息派发到线程池，不含响应。其它消息均在 IO 线程上执行 |
        //| connection | 在 IO 线程上，将连接断开事件放入队列，有序逐个执行，其它消息派发到线程池 |
        //
        //默认配置下，Dubbo 使用 `all` 派发策略，即将所有的消息都派发到线程池中。下面我们来分析一下 AllChannelHandler 的代码。
    }


    // 关键方法
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
       // 注意这里是调用super先把数据写给对端
        super.write(ctx, msg, promise);
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);

        // handler见上面channelRead的内部对其的解释

        // 这里叫做sent，表示已经发送，即上面super，相当于此时handler做的是一个后置处理逻辑
        handler.sent(channel, msg);
    }
   // IdleStatHandler检测到空闲超时后，会调用fireUserEventTriggered方法，从而传到这里
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

        System.out.println(new Date()+"服务端空闲事件发生");
        // server will close channel when server don't receive any heartbeat from client util timeout.
        // 当服务器没有从客户端接收到任何心跳时，服务器将关闭通道。
        if (evt instanceof IdleStateEvent) {
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
            try {
                // 日志
                logger.info("IdleStateEvent triggered, close channel " + channel);
                channel.close(); // close之后会进前面的channelInactive方法
            } finally {
                // 进去
                NettyChannel.removeChannelIfDisconnected(ctx.channel());
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        try {
            handler.caught(channel, cause);
        } finally {
            NettyChannel.removeChannelIfDisconnected(ctx.channel());
        }
    }

    public void handshakeCompleted(HandshakeCompletionEvent evt) {
        // TODO
    }
}
