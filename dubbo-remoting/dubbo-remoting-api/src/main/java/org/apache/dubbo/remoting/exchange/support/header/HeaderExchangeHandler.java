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
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.ExecutionException;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.ExchangeChannel;
import org.apache.dubbo.remoting.exchange.ExchangeHandler;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.support.DefaultFuture;
import org.apache.dubbo.remoting.transport.ChannelHandlerDelegate;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;

import static org.apache.dubbo.common.constants.CommonConstants.READONLY_EVENT;


/**
 * ExchangeReceiver
 */
// OK
// 注意 有个 AbstractChannelHandlerDelegate 抽象类，该类没有直接继承，而是 注意直接实现 ChannelHandlerDelegate 接口了，因为全部方法都重
// 写了，没必要extends了。不过我感觉最好继承好点
public class HeaderExchangeHandler implements ChannelHandlerDelegate {

    protected static final Logger logger = LoggerFactory.getLogger(HeaderExchangeHandler.class);

    private final ExchangeHandler handler;

    // gx 传进来ExchangeHandler/ExchangeHandlerAdapter的实现类，大部分是匿名类/lambda
    public HeaderExchangeHandler(ExchangeHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        this.handler = handler;
    }

    // gx
    static void handleResponse(Channel channel, Response response) throws RemotingException {
        if (response != null && !response.isHeartbeat()) {
            DefaultFuture.received(channel, response);// 进去
        }
    }

    private static boolean isClientSide(Channel channel) {
        InetSocketAddress address = channel.getRemoteAddress();
        URL url = channel.getUrl();
        return url.getPort() == address.getPort() &&
                NetUtils.filterLocalHost(url.getIp())
                        .equals(NetUtils.filterLocalHost(address.getAddress().getHostAddress()));
    }

    void handlerEvent(Channel channel, Request req) throws RemotingException {
        if (req.getData() != null && req.getData().equals(READONLY_EVENT)) {
            // 客户端接收到服务端这个，表示服务端准备关闭了，进入closing状态，所以会告诉该client，此链接的channel不能发消息了，加一个这个属性CHANNEL_ATTRIBUTE_READONLY_KEY
            // 看下 CHANNEL_ATTRIBUTE_READONLY_KEY 的调用点
            channel.setAttribute(Constants.CHANNEL_ATTRIBUTE_READONLY_KEY, Boolean.TRUE);
        }
    }

    void handleRequest(final ExchangeChannel channel, Request req) throws RemotingException {
        Response res = new Response(req.getId(), req.getVersion());
        // 检测请求是否合法，不合法则返回状态码为 BAD_REQUEST 的响应
        if (req.isBroken()) {
            Object data = req.getData();

            String msg;
            if (data == null) {
                msg = null;
            } else if (data instanceof Throwable) {
                msg = StringUtils.toString((Throwable) data);
            } else {
                msg = data.toString();
            }
            res.setErrorMessage("Fail to decode request due to: " + msg);
            // 设置 BAD_REQUEST 状态
            res.setStatus(Response.BAD_REQUEST);

            channel.send(res);
            return;
        }
        // 获取 data 字段值，也就是 RpcInvocation 对象，这里一般是DecodeableRpcInvocation
        Object msg = req.getData();
        try {
            // 这里的handler就是我们的业务handler（除了ExchangeHandler/Adapter，也有可能是ExchangeHandlerDispatcher），进去
            // handler可以就单纯的理解为DubboProtocol 的 requestHandler
            CompletionStage<Object> future = handler.reply(channel, msg);
            future.whenComplete((appResult, t) -> {
                try {
                    if (t == null) {
                        res.setStatus(Response.OK);// 设置 OK 状态码
                        res.setResult(appResult);// 设置调用结果
                    } else {
                        res.setStatus(Response.SERVICE_ERROR);
                        res.setErrorMessage(StringUtils.toString(t));
                    }
                    // 进去 发给对端，channel为HeaderExchangeChannel
                    channel.send(res);
                } catch (RemotingException e) {
                    logger.warn("Send result to consumer failed, channel is " + channel + ", msg is " + e);
                }
            });
        } catch (Throwable e) {
            res.setStatus(Response.SERVICE_ERROR);// 若调用过程出现异常，则设置 SERVICE_ERROR，表示服务端异常
            res.setErrorMessage(StringUtils.toString(e));
            channel.send(res);
        }
        // 到这里，我们看到了比较清晰的请求和响应逻辑。对于双向通信，HeaderExchangeHandler 首先向后进行调用，得到调用结果。然后将调用结果封装到
        // Response 对象中，最后再将该对象返回给服务消费方。如果请求不合法，或者调用失败，则将错误信息封装到 Response 对象中，并返回给服务消费方。
        // 接下来我们继续向后分析，把剩余的调用过程分析完。下面分析定义在 DubboProtocol 类中的匿名类对象逻辑，如下，去看 DubboProtocol 的 requestHandler
    }

    @Override
    public void connected(Channel channel) throws RemotingException {// Channel为NettyChannel
        ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        // 对于server来说，handler为实际的/最终的目标业务handler，比如HeartbeatHandlerTest$TestHeartbeatHandler，对于client则为 ExchangeHandlerDispatcher
        handler.connected(exchangeChannel);
    }

    @Override
    public void disconnected(Channel channel) throws RemotingException {
        ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);// 注意这里返回的是HeaderExchangeChannel，不是NettyChannel，进去
        try {
            // 对于server来说，handler为实际的/最终的目标业务handler，比如HeartbeatHandlerTest$TestHeartbeatHandler，对于client则为 ExchangeHandlerDispatcher
            handler.disconnected(exchangeChannel);
        } finally {
            DefaultFuture.closeChannel(channel);// 进去
            HeaderExchangeChannel.removeChannel(channel);// 进去
        }
    }

    @Override
    public void sent(Channel channel, Object message) throws RemotingException {
        Throwable exception = null;
        try {
            ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
            // 进去 handler为requestHandler
            handler.sent(exchangeChannel, message);
        } catch (Throwable t) {
            exception = t;
            HeaderExchangeChannel.removeChannelIfDisconnected(channel);
        }
        if (message instanceof Request) {
            Request request = (Request) message;
            DefaultFuture.sent(channel, request);// 进去
        }
        if (exception != null) {
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            } else if (exception instanceof RemotingException) {
                throw (RemotingException) exception;
            } else {
                throw new RemotingException(channel.getLocalAddress(), channel.getRemoteAddress(),
                        exception.getMessage(), exception);
            }
        }
    }
    // 在DecodeHandler的received得到触发
    @Override
    public void received(Channel channel, Object message) throws RemotingException {
        final ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        if (message instanceof Request) {
            // 处理请求对象
            Request request = (Request) message;

            // 处理事件
            if (request.isEvent()) {
                handlerEvent(channel, request);// 进去

            // 处理普通的请求
            } else {
                if (request.isTwoWay()) {
                    // 双向通信
                    // 向后调用服务，并得到调用结果,内部调用结果返回给服务消费端 进去
                    handleRequest(exchangeChannel, request);
                } else {
                    // 如果是单向通信，仅向后调用指定服务即可，无需返回调用结果 进去
                    handler.received(exchangeChannel, request.getData());
                }
            }
        // 处理响应对象，服务消费方会执行此处逻辑，后面分析
        } else if (message instanceof Response) {
            handleResponse(channel, (Response) message);// 进去
        } else if (message instanceof String) {
            if (isClientSide(channel)) {
                Exception e = new Exception("Dubbo client can not supported string message: " + message + " in channel: " + channel + ", url: " + channel.getUrl());
                logger.error(e.getMessage(), e);
            } else { // telnet 相关，忽略
                String echo = handler.telnet(channel, (String) message);
                if (echo != null && echo.length() > 0) {
                    channel.send(echo);
                }
            }
        } else {
            handler.received(exchangeChannel, message);
        }
    }

    @Override
    public void caught(Channel channel, Throwable exception) throws RemotingException {
        if (exception instanceof ExecutionException) {
            ExecutionException e = (ExecutionException) exception;
            Object msg = e.getRequest();
            if (msg instanceof Request) {
                Request req = (Request) msg;
                if (req.isTwoWay() && !req.isHeartbeat()) {
                    Response res = new Response(req.getId(), req.getVersion());
                    res.setStatus(Response.SERVER_ERROR);
                    res.setErrorMessage(StringUtils.toString(e));
                    channel.send(res);
                    return;
                }
            }
        }
        ExchangeChannel exchangeChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        try {
            handler.caught(exchangeChannel, exception);
        } finally {
            // 进去
            HeaderExchangeChannel.removeChannelIfDisconnected(channel);
        }
    }

    @Override
    public ChannelHandler getHandler() {
        if (handler instanceof ChannelHandlerDelegate) {
            return ((ChannelHandlerDelegate) handler).getHandler();
        } else {
            return handler;
        }
    }
}
