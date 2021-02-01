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
package org.apache.dubbo.remoting.transport.dispatcher;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.support.DefaultFuture;
import org.apache.dubbo.remoting.transport.ChannelHandlerDelegate;

import java.util.concurrent.ExecutorService;

// OK
// 和AbstractChannelHandlerDelegate都是 ChannelHandlerDelegate 接口的直接子类
public  class WrappedChannelHandler implements ChannelHandlerDelegate {

    protected static final Logger logger = LoggerFactory.getLogger(WrappedChannelHandler.class);

    protected final ChannelHandler handler;

    protected final URL url;

    // gx handler为 DecodeHandler
    public WrappedChannelHandler(ChannelHandler handler, URL url) {
        this.handler = handler;
        this.url = url;
    }

    public void close() {

    }

    // 以下方法被子类重写或不重写，主要决定发生事件之后是交给executor，还是在原有的io线程执行
    @Override
    public void connected(Channel channel) throws RemotingException {
        handler.connected(channel);
    }

    @Override
    public void disconnected(Channel channel) throws RemotingException {
        handler.disconnected(channel);
    }

    @Override
    public void sent(Channel channel, Object message) throws RemotingException {
        handler.sent(channel, message);
    }

    @Override
    public void received(Channel channel, Object message) throws RemotingException {
        handler.received(channel, message);
    }

    @Override
    public void caught(Channel channel, Throwable exception) throws RemotingException {
        handler.caught(channel, exception);
    }

    // gx
    protected void sendFeedback(Channel channel, Request request, Throwable t) throws RemotingException {
        if (request.isTwoWay()) {
            // 注意
            String msg = "Server side(" + url.getIp() + "," + url.getPort()
                    + ") thread pool is exhausted, detail msg:" + t.getMessage();
            Response response = new Response(request.getId(), request.getVersion());
            response.setStatus(Response.SERVER_THREADPOOL_EXHAUSTED_ERROR); // 状态码
            response.setErrorMessage(msg);// 状态消息
            // 返回包含错误信息的 Response 对象 回发给channel client
            channel.send(response);
            return;
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

    public URL getUrl() {
        return url;
    }

    /**
     * Currently, this method is mainly customized to facilitate the thread model on consumer side.
     * 1. Use ThreadlessExecutor, aka., delegate callback directly to the thread initiating the call.
     * 2. Use shared executor to execute the callback.
     *
     * *目前这个方法主要是定制化的，方便消费者端的线程模型。
     * * 1。使用ThreadlessExecutor,又名。，将callback直接委托给发起调用的线程。
     * * 2。使用共享执行器来执行回调。
     *
     * @param msg
     * @return
     */
    public ExecutorService getPreferredExecutorService(Object msg) {
        if (msg instanceof Response) {
            Response response = (Response) msg;
            // 进去
            DefaultFuture responseFuture = DefaultFuture.getFuture(response.getId());
            // a typical scenario is the response returned after timeout, the timeout response may has completed the future
            // 一个典型的场景是响应超时后返回，超时响应可能已经完成了未来
            if (responseFuture == null) {
                return getSharedExecutorService();
            } else {
                ExecutorService executor = responseFuture.getExecutor();
                if (executor == null || executor.isShutdown()) {
                    executor = getSharedExecutorService();
                }
                return executor;
            }
        } else {
            return getSharedExecutorService();
        }
    }

    /**
     * get the shared executor for current Server or Client
     *
     * @return
     */
    public ExecutorService getSharedExecutorService() {
        ExecutorRepository executorRepository =
                ExtensionLoader.getExtensionLoader(ExecutorRepository.class).getDefaultExtension();
        ExecutorService executor = executorRepository.getExecutor(url);// 进去 ，内部缓存是直接能拿到的，因为比如NettyServer在doOpen的时候会创建线程池并填充到缓存
        if (executor == null) {
            executor = executorRepository.createExecutorIfAbsent(url);
        }
        return executor;
    }

    @Deprecated
    public ExecutorService getExecutorService() {
        return getSharedExecutorService();// 进去
    }


}
