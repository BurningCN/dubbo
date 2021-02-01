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
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;

import java.util.Date;

import static org.apache.dubbo.common.constants.CommonConstants.HEARTBEAT_EVENT;

/**
 * NettyClientHandler
 */
// OK
@io.netty.channel.ChannelHandler.Sharable
public class NettyClientHandler extends ChannelDuplexHandler {
    private static final Logger logger = LoggerFactory.getLogger(NettyClientHandler.class);

    private final URL url;

    private final ChannelHandler handler; // handler为NettyClient

    // gx
    public NettyClientHandler(URL url, ChannelHandler handler) {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        this.url = url;
        this.handler = handler;
    }
    // NettyChannel [channel=[id: 0x3b3b6129, L:/30.25.58.152:55544 - R:/30.25.58.152:56780]] 。L表示客户端本地，R表示服务器远端，前者的ip:port是内核分配的；后者是port是自己选定的，ip是多宿的
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        handler.connected(channel); // handler是NettyClient 进去
        if (logger.isInfoEnabled()) {
            logger.info("The connection of " + channel.getLocalAddress() + " -> " + channel.getRemoteAddress() + " is established.");
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        try {
            handler.disconnected(channel);
        } finally {
            NettyChannel.removeChannel(ctx.channel());
        }

        if (logger.isInfoEnabled()) {
            logger.info("The connection of " + channel.getLocalAddress() + " -> " + channel.getRemoteAddress() + " is disconnected.");
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        handler.received(channel, msg); // handler为NettyClient
        // 调用栈如下，第一个为最后一个流转到的 ，然后AllChannelHandler就是提交任务给线程池了，就走DecodeHandler(HeaderExchangeHandler(requestHandler))的逻辑了
        //received:71, AllChannelHandler (org.apache.dubbo.remoting.transport.dispatcher.all)
        //received:95, HeartbeatHandler (org.apache.dubbo.remoting.exchange.support.header)
        //received:46, MultiMessageHandler (org.apache.dubbo.remoting.transport)
        //received:149, AbstractPeer (org.apache.dubbo.remoting.transport)
        //channelRead:87, NettyClientHandler (org.apache.dubbo.remoting.transport.netty4)
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        super.write(ctx, msg, promise);
        final NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
        final boolean isRequest = msg instanceof Request;

        // We add listeners to make sure our out bound event is correct.
        // If our out bound event has an error (in most cases the encoder fails),
        // we need to have the request return directly instead of blocking the invoke process.
        //添加监听器以确保out bound事件是正确的。如果我们的out bound事件有错误(在大多数情况下编码器失败)，我们需要直接返回请求，而不是阻塞调用进程。
        promise.addListener(future -> {
            if (future.isSuccess()) {
                // if our future is success, mark the future to sent.
                // handler为NettyClient，sent是 AbstractPeer 的 ，分析下下面的栈帧
                handler.sent(channel, msg);
                return;
                // sent的栈帧如下，第一个是最后流转到的，进去看这个即可
                //sent:138, DefaultFuture (org.apache.dubbo.remoting.exchange.support)
                //sent:164, HeaderExchangeHandler (org.apache.dubbo.remoting.exchange.support.header)
                //sent:55, AbstractChannelHandlerDelegate (org.apache.dubbo.remoting.transport)
                //sent:66, WrappedChannelHandler (org.apache.dubbo.remoting.transport.dispatcher)
                //sent:63, HeartbeatHandler (org.apache.dubbo.remoting.exchange.support.header)
                //sent:55, AbstractChannelHandlerDelegate (org.apache.dubbo.remoting.transport)
                //sent:141, AbstractPeer (org.apache.dubbo.remoting.transport)
            }

            Throwable t = future.cause();
            if (t != null && isRequest) {
                Request request = (Request) msg;
                // 进去
                Response response = buildErrorResponse(request, t);
                handler.received(channel, response);
            }
        });
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        System.out.println(new Date()+"客户端空闲事件触发");
        // send heartbeat when read idle.
        if (evt instanceof IdleStateEvent) {
            try {
                NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);
                if (logger.isDebugEnabled()) {
                    // 超时了，赶紧发心跳给服务端
                    logger.debug("IdleStateEvent triggered, send heartbeat to channel " + channel);
                }
                Request req = new Request();// 进去
                req.setVersion(Version.getProtocolVersion());
                req.setTwoWay(true);
                req.setEvent(HEARTBEAT_EVENT);// 进去 HEARTBEAT_EVENT 为null
                channel.send(req);// send方法是NettyChannel的父父类AbstractPeer的 进去
            } finally {
                NettyChannel.removeChannelIfDisconnected(ctx.channel());
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
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

    public void handshakeCompleted(SslHandlerInitializer.HandshakeCompletionEvent evt) {
        // TODO
    }

    /**
     * build a bad request's response
     *
     * @param request the request
     * @param t       the throwable. In most cases, serialization fails.
     * @return the response
     */
    private static Response buildErrorResponse(Request request, Throwable t) {
        Response response = new Response(request.getId(), request.getVersion());
        response.setStatus(Response.BAD_REQUEST);
        response.setErrorMessage(StringUtils.toString(t));
        return response;
    }
}
