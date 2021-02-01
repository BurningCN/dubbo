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
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.*;
import org.apache.dubbo.remoting.exchange.support.header.HeaderExchanger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

// OK
public class HeartbeatHandlerTest {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatHandlerTest.class);

    private ExchangeServer server;
    private ExchangeClient client;

    @AfterEach
    public void after() throws Exception {
        if (client != null) {
            client.close();
            client = null;
        }

        if (server != null) {
            server.close();
            server = null;
        }

        // wait for timer to finish
        Thread.sleep(2000);
    }

    @Test
    public void testServerHeartbeat() throws Exception {
        URL serverURL = URL.valueOf("telnet://localhost:" + NetUtils.getAvailablePort(56780))
                .addParameter(Constants.EXCHANGER_KEY, HeaderExchanger.NAME) // 在Exchange$Adaptive会根据这个选择HeaderExchanger进行bind或者connect
                .addParameter(Constants.TRANSPORTER_KEY, "netty4")
                .addParameter(Constants.HEARTBEAT_KEY, 1000);// 1000*3会作为服务端的读写空闲时间，1000会作为客户端的读空闲时间

        // telnet://localhost:56780?exchanger=header&heartbeat=1000&transporter=netty4

        CountDownLatch connect = new CountDownLatch(1);
        CountDownLatch disconnect = new CountDownLatch(1);
        TestHeartbeatHandler handler = new TestHeartbeatHandler(connect, disconnect);// 进去
        server = Exchangers.bind(serverURL, handler);// 进去
        System.out.println("Server bind successfully");

        FakeChannelHandlers.setTestingChannelHandlers();// 进去
        //serverURL = serverURL.removeParameter(Constants.HEARTBEAT_KEY);// 无用，下面的addParameter会覆盖

        // Let the client not reply to the heartbeat, and turn off automatic reconnect to simulate the client dropped.
        // 让客户端不响应心跳，并关闭自动重新连接以模拟客户机丢失。

        // 这里client是600s，前面server是1s，server（NettyServerHandler）就会检测到空闲超时，内部会关闭channel。
        // 如果不想这样，那么把下面一行去掉，客户端在1s内如果没有从服务器读取数据的话，就会触发NettyClientHandler的userEventTrigger方法，内
        // 部判断如果是空闲超时时间，则会channel.send 发数据--->这个测试场景自己试一下，把前面的1000设置为5000，不过还是会关闭，原因在于
        // NettyCodecAdapter的InternalDecoder使用telnetCodec.decode的时候返回 NEED_MORE_INPUT，原因是NettyClientHandler的
        // userEventTriggered内部发送的Request不是ENTER结尾的)
        serverURL = serverURL.addParameter(Constants.HEARTBEAT_KEY, 600 * 1000); // *1
        serverURL = serverURL.addParameter(Constants.RECONNECT_KEY, false); // telnet://localhost:56780?exchanger=header&heartbeat=600000&reconnect=false&transporter=netty4

        client = Exchangers.connect(serverURL);// 进去
        disconnect.await();
        //System.in.read(); // *2
        Assertions.assertTrue(handler.disconnectCount > 0);
        System.out.println("disconnect count " + handler.disconnectCount);

        // ☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆上面测试本身结束，如下我设计了一个非常关键的实验☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆
        // ☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆上面测试本身结束，如下我设计了一个非常关键的实验☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆
        // ☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆上面测试本身结束，如下我设计了一个非常关键的实验☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆

        // 实验条件：将前面的*1标记处注释掉，*2打开，此时 1000*3会作为服务端的读写空闲时间，1000会作为客户端的读空闲时间，我在某些地方加了输出，测试程序启动后输出如下：

        //Fri Jan 22 19:48:57 CST 2021服务端绑定成功    ------5
        //Server bind successfully
        //1000客户端读写空闲最大时间
        //Fri Jan 22 19:48:58 CST 2021客户端连接成功       ----1
        //3000服务端读写空闲最大时间
        //Fri Jan 22 19:48:59 CST 2021客户端空闲事件触发    ----2
        //Fri Jan 22 19:49:00 CST 2021客户端空闲事件触发    ----3
        //Fri Jan 22 19:49:01 CST 2021服务端空闲事件发生 ------6
        //Fri Jan 22 19:49:01 CST 2021客户端空闲事件触发    ----4

        // 可见客户端连接成功后(标记1)，1s发现没有读服务器数据，读空闲了，所以会触发NettyClientHandle的 userEventTriggered（可见标记2），内部会发数据给server
        // 且每隔1s触发了一次，一共发生3次（2、3、4标记），为啥发生三次呢？因为服务端的读写空闲为3s，如果一直没有收到客户端的读写请求，就会触发
        // NettyServerHandler的userEventTriggered，内部会channel.close，但是我们直到NettyClientHandle的 userEventTriggered内部是
        // 会构造request数据，并channel.send的（最后跟到channel.WAF），client不是在NettyClientHandle的 userEventTriggered发了吗？
        // 的确发了，但是因为服务端解码器NettyCodecAdapter的InternalDecoder使用telnetCodec.decode，而Request不是ENTER结尾的，
        // 所以解码会返回NEED_MORE_INPUT，导致无法传递到IdleStateHandler！！！也就无法传递到NettyServerHandler！！！（pipeline的addLast顺序）
        // 所以会触发服务端空闲检测，最终关闭服务。而前面将url的RECONNECT_KEY设置为了false，不会自动重连（内部有一个定时任务），下面的测试程序默认是
        // 开启自动重连，我们去看下输出的是什么。


        // ☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆上面测试本身结束，又是一个非常关键的实验☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆\
        // ☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆上面测试本身结束，又是一个非常关键的实验☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆\
        // ☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆上面测试本身结束，又是一个非常关键的实验☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆\

        // 如果把前面的的yrl改成exchange://，就会正常解码(去看下decode和NettyServerHandler的channelRead方法)，得到正常的心跳，输出如下：
        //Mon Jan 25 11:48:29 CST 2021服务端绑定成功
        //Server bind successfully
        //1000客户端读写空闲最大时间
        //Mon Jan 25 11:48:29 CST 2021客户端连接成功
        //3000服务端读写空闲最大时间
        //Mon Jan 25 11:48:30 CST 2021客户端空闲事件触发
        //Mon Jan 25 11:48:31 CST 2021客户端空闲事件触发
        //Mon Jan 25 11:48:32 CST 2021客户端空闲事件触发
        //Mon Jan 25 11:48:33 CST 2021客户端空闲事件触发
        //Mon Jan 25 11:48:34 CST 2021客户端空闲事件触发
        //Mon Jan 25 11:48:35 CST 2021客户端空闲事件触发
        //Mon Jan 25 11:48:36 CST 2021客户端空闲事件触发
        //Mon Jan 25 11:48:37 CST 2021客户端空闲事件触发
        //Mon Jan 25 11:48:38 CST 2021客户端空闲事件触发
        //Mon Jan 25 11:48:39 CST 2021客户端空闲事件触发
        //Mon Jan 25 11:48:40 CST 2021客户端空闲事件触发
        //Mon Jan 25 11:48:41 CST 2021客户端空闲事件触发

    }

    @Test
    public void testHeartbeat() throws Exception {
        URL serverURL = URL.valueOf("telnet://localhost:" + NetUtils.getAvailablePort(56785))
                .addParameter(Constants.EXCHANGER_KEY, HeaderExchanger.NAME)
                .addParameter(Constants.TRANSPORTER_KEY, "netty4")
                .addParameter(Constants.HEARTBEAT_KEY, 1000);
        CountDownLatch connect = new CountDownLatch(1);
        CountDownLatch disconnect = new CountDownLatch(1);
        TestHeartbeatHandler handler = new TestHeartbeatHandler(connect, disconnect);
        server = Exchangers.bind(serverURL, handler);
        System.out.println("Server bind successfully");

        client = Exchangers.connect(serverURL);
        connect.await();
        System.in.read(); // 1
        System.err.println("++++++++++++++ disconnect count " + handler.disconnectCount);
        System.err.println("++++++++++++++ connect count " + handler.connectCount);
        Assertions.assertEquals(0, handler.disconnectCount);
        Assertions.assertEquals(1, handler.connectCount);

        // ☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆针对上上个测试程序最后的实验，配合的另实验如下☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆☆
        // 测试条件，将前面标记1放开，输出如下

        //Fri Jan 22 20:19:13 CST 2021服务端绑定成功
        //Server bind successfully
        //1000客户端读写空闲最大时间
        //Fri Jan 22 20:19:13 CST 2021客户端连接成功
        //3000服务端读写空闲最大时间
        //Fri Jan 22 20:19:14 CST 2021客户端空闲事件触发
        //Fri Jan 22 20:19:15 CST 2021客户端空闲事件触发
        //Fri Jan 22 20:19:16 CST 2021服务端空闲事件发生  ---->内部关闭客户端channel
        //1000客户端读写空闲最大时间
        //Fri Jan 22 20:19:17 CST 2021客户端连接成功    ---->自动重连
        //3000服务端读写空闲最大时间
        //Fri Jan 22 20:19:18 CST 2021客户端空闲事件触发
        //Fri Jan 22 20:19:19 CST 2021客户端空闲事件触发
        //Fri Jan 22 20:19:20 CST 2021服务端空闲事件发生 ---->内部关闭客户端channel
        //1000客户端读写空闲最大时间
        //Fri Jan 22 20:19:21 CST 2021客户端连接成功   --->自动重连
        //3000服务端读写空闲最大时间
        //Fri Jan 22 20:19:22 CST 2021客户端空闲事件触发
        //Fri Jan 22 20:19:23 CST 2021客户端空闲事件触发
        //Fri Jan 22 20:19:24 CST 2021服务端空闲事件发生
        //...................
    }

    @Test
    public void testClientHeartbeat() throws Exception {
        FakeChannelHandlers.setTestingChannelHandlers();
        URL serverURL = URL.valueOf("telnet://localhost:" + NetUtils.getAvailablePort(56790))
                .addParameter(Constants.EXCHANGER_KEY, HeaderExchanger.NAME)
                .addParameter(Constants.TRANSPORTER_KEY, "netty4");
        CountDownLatch connect = new CountDownLatch(1);
        CountDownLatch disconnect = new CountDownLatch(1);
        TestHeartbeatHandler handler = new TestHeartbeatHandler(connect, disconnect);
        server = Exchangers.bind(serverURL, handler);
        System.out.println("Server bind successfully");

        FakeChannelHandlers.resetChannelHandlers();
        serverURL = serverURL.addParameter(Constants.HEARTBEAT_KEY, 1000);
        client = Exchangers.connect(serverURL);
        connect.await();
        Assertions.assertTrue(handler.connectCount > 0);
        System.out.println("connect count " + handler.connectCount);
    }

    class TestHeartbeatHandler implements ExchangeHandler {

        public int disconnectCount = 0;
        public int connectCount = 0;
        private CountDownLatch connectCountDownLatch;
        private CountDownLatch disconnectCountDownLatch;

        public TestHeartbeatHandler(CountDownLatch connectCountDownLatch, CountDownLatch disconnectCountDownLatch) {
            this.connectCountDownLatch = connectCountDownLatch;
            this.disconnectCountDownLatch = disconnectCountDownLatch;
        }

        public CompletableFuture<Object> reply(ExchangeChannel channel, Object request) throws RemotingException {
            return CompletableFuture.completedFuture(request);
        }

        @Override
        public void connected(Channel channel) throws RemotingException {
            ++connectCount;
            connectCountDownLatch.countDown();
        }

        @Override
        public void disconnected(Channel channel) throws RemotingException {
            ++disconnectCount;
            disconnectCountDownLatch.countDown();
        }

        @Override
        public void sent(Channel channel, Object message) throws RemotingException {

        }

        @Override
        public void received(Channel channel, Object message) throws RemotingException {
            logger.error(this.getClass().getSimpleName() + message.toString());
        }

        @Override
        public void caught(Channel channel, Throwable exception) throws RemotingException {
            exception.printStackTrace();
        }

        public String telnet(Channel channel, String message) throws RemotingException {
            return message;
        }
    }

}
