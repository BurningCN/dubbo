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
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.Transporters;
import org.apache.dubbo.remoting.exchange.ExchangeClient;
import org.apache.dubbo.remoting.exchange.ExchangeHandler;
import org.apache.dubbo.remoting.exchange.ExchangeServer;
import org.apache.dubbo.remoting.exchange.Exchanger;
import org.apache.dubbo.remoting.transport.DecodeHandler;

/**
 * DefaultMessenger
 *
 *
 */
// OK
// 整个是这样的 Exchanges -> HeaderExchanger -> Transporters -> NettyTransporter -> new NettyServer/Client
public class HeaderExchanger implements Exchanger {

    public static final String NAME = "header";


    // connect和下面的bind对称
    @Override
    public ExchangeClient connect(URL url, ExchangeHandler handler) throws RemotingException {
        // HeaderExchangeClient包装Client/NettyClient
        return new HeaderExchangeClient(Transporters.connect(url, new DecodeHandler(new HeaderExchangeHandler(handler))), true);
    }

    // ExchangeServer = Exchanger.bind(url, ExchangeHandler);
    // Exchanger默认使用HeaderExchanger，其bind逻辑如下：
    // 1.
    //  (1) RemotingServer = Transporters.bind(url,ChannelHandler... handlers) ->
    //  (2) getTransporter().bind(url, handler)  ->
    //  (3) NettyTransporter.bind -> new NettyServer(url, handler) - > AbstractServer的构造方法 -> doOpen模板方法，子类实现
    // 2.上面拿到的RemotingServer传给HeaderExchangeServer包装后返回

    // 接口 ExchangeServer(RemotingServer server)
    // 实现 HeaderExchangeServer(NettyServer server)

    // handler参数比如说是DubboProtocol的requestHandler属性
    @Override
    public ExchangeServer bind(URL url, ExchangeHandler handler) throws RemotingException {
        // 创建 HeaderExchangeServer 实例，该方法包含了多个逻辑，分别如下：
        //   1. new HeaderExchangeHandler(handler)
        //	 2. new DecodeHandler(new HeaderExchangeHandler(handler))
        //   3. Transporters.bind(url, new DecodeHandler(new HeaderExchangeHandler(handler)))
        // HeaderExchanger 的 bind 方法包含的逻辑比较多，但目前我们仅需关心 Transporters 的 bind 方法逻辑即可 ，进去
        return new HeaderExchangeServer(Transporters.bind(url, new DecodeHandler(new HeaderExchangeHandler(handler))));
    }

}
