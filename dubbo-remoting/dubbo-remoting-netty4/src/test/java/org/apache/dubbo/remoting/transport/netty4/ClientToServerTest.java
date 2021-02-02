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

import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.ExchangeChannel;
import org.apache.dubbo.remoting.exchange.ExchangeServer;
import org.apache.dubbo.remoting.exchange.support.Replier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

/**
 * ClientToServer
 */
// OK
public abstract class ClientToServerTest {

    protected static final String LOCALHOST = "127.0.0.1";

    protected ExchangeServer server;

    protected ExchangeChannel client;

    protected WorldHandler handler = new WorldHandler();

    protected abstract ExchangeServer newServer(int port, Replier<?> receiver) throws RemotingException;

    protected abstract ExchangeChannel newClient(int port) throws RemotingException;

    @BeforeEach
    protected void setUp() throws Exception {
        int port = NetUtils.getAvailablePort();
        server = newServer(port, handler);// 进去  注意handler
        client = newClient(port);// 进去
    }

    @AfterEach
    protected void tearDown() {
        try {
            if (server != null)
                server.close();
        } finally {
            if (client != null)
                client.close();
        }
    }

    // 通过Exchanges.bind和connect进行cs连接。注意传给bind的是 Replier<World>实例，内部会用ExchangeHandlerAdapater包装，然后client.request(new World("world"))，发请求，Replier的reply就会在服务端得到调用。然后回写数据给client，client的AllChannelHandler内部会构建received事件的相关任务，然后给DefaultFuture进行Complete。
    @Test
    public void testFuture() throws Exception {
        // 进去 为了打断点，防止DefaultFuture超时抛异常，这里设置一个较大的超时时间1000000（原来程序没有该参数）
        // 注意两个核心断点处，一个是WorldHandler的reply方法，一个是DefaultFuture的received方法
        CompletableFuture<Object> future = client.request(new World("world"),1000000);
        // 阻塞获取结果
        Hello result = (Hello) future.get();
        Assertions.assertEquals("hello,world", result.getName());
    }


}