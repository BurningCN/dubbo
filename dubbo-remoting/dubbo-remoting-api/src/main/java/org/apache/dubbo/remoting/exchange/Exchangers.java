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
package org.apache.dubbo.remoting.exchange;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.support.ExchangeHandlerDispatcher;
import org.apache.dubbo.remoting.exchange.support.Replier;
import org.apache.dubbo.remoting.transport.ChannelHandlerAdapter;

/**
 * Exchanger facade. (API, Static, ThreadSafe)
 */
// OK
// 门面模式。提供bind和connect方法，参数都是url+ChannelHandler，分别返回ExchangeServer和ExchangeClient。bind和connect的逻辑分别是
// getExchanger(url).bind(url, handler) 和 getExchanger(url).connect(url, handler)，getExchanger根据spi机制默认获取的是
// HeaderExchanger，走他的bind和connect。static{}块调用Version类的checkDuplicate，门面肯定一般都是一份，所以不应该有多个重复的类（比如引入的依赖、jar有了重复的）
// 整个是这样的 Exchanges -> HeaderExchanger -> Transporters -> NettyTransporter -> new NettyServer/Client
public class Exchangers {

    static {
        // check duplicate jar package 进去
        Version.checkDuplicate(Exchangers.class);
    }

    private Exchangers() {
    }

    public static ExchangeServer bind(String url, Replier<?> replier) throws RemotingException {
        return bind(URL.valueOf(url), replier);
    }

    public static ExchangeServer bind(URL url, Replier<?> replier) throws RemotingException {
        return bind(url, new ChannelHandlerAdapter(), replier);// 进去
    }

    public static ExchangeServer bind(String url, ChannelHandler handler, Replier<?> replier) throws RemotingException {
        return bind(URL.valueOf(url), handler, replier);
    }

    public static ExchangeServer bind(URL url, ChannelHandler handler, Replier<?> replier) throws RemotingException {
        return bind(url, new ExchangeHandlerDispatcher(replier, handler));// ExchangeHandlerDispatcher 、 bind 进去
    }

    public static ExchangeServer bind(String url, ExchangeHandler handler) throws RemotingException {
        return bind(URL.valueOf(url), handler);
    }
    // ExchangeServer、 ExchangeHandler、Exchanger、
    // ExchangeServer = Exchanger.bind(url, ExchangeHandler);
    public static ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        } // 添加codec参数（如果没有的话，一般是有的，codec=dubbo）
        url = url.addParameterIfAbsent(Constants.CODEC_KEY, "exchange");
        // 获取 Exchanger，默认为 HeaderExchanger。
        // 紧接着调用 HeaderExchanger 的 bind 方法创建 ExchangeServer 实例，看一下 HeaderExchanger 的 bind 方法
        return getExchanger(url).bind(url, handler);
    }

    public static ExchangeClient connect(String url) throws RemotingException {
        return connect(URL.valueOf(url));
    }

    public static ExchangeClient connect(URL url) throws RemotingException {
        // 进去
        return connect(url, new ChannelHandlerAdapter(), null);
    }

    public static ExchangeClient connect(String url, Replier<?> replier) throws RemotingException {
        return connect(URL.valueOf(url), new ChannelHandlerAdapter(), replier);
    }

    public static ExchangeClient connect(URL url, Replier<?> replier) throws RemotingException {
        return connect(url, new ChannelHandlerAdapter(), replier);
    }

    public static ExchangeClient connect(String url, ChannelHandler handler, Replier<?> replier) throws RemotingException {
        return connect(URL.valueOf(url), handler, replier);
    }

    public static ExchangeClient connect(URL url, ChannelHandler handler, Replier<?> replier) throws RemotingException {
        // ExchangeHandlerDispatcher、connect进去
        return connect(url, new ExchangeHandlerDispatcher(replier, handler));
    }

    public static ExchangeClient connect(String url, ExchangeHandler handler) throws RemotingException {
        return connect(URL.valueOf(url), handler);
    }

    // 
    public static ExchangeClient connect(URL url, ExchangeHandler handler) throws RemotingException {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
//        url = url.addParameterIfAbsent(Constants.CODEC_KEY, "exchange");
        return getExchanger(url).connect(url, handler);
    }

    public static Exchanger getExchanger(URL url) {
        // url.getParameter("exchanger", "header"); 一般都是取的默认的header
        String type = url.getParameter(Constants.EXCHANGER_KEY, Constants.DEFAULT_EXCHANGER);
        // 进去
        return getExchanger(type);
    }

    public static Exchanger getExchanger(String type) {
        // 获取扩展实例
        return ExtensionLoader.getExtensionLoader(Exchanger.class).getExtension(type);
    }

}
