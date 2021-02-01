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
package api;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.exchange.ExchangeClient;
import org.apache.dubbo.remoting.exchange.Exchangers;
import org.apache.dubbo.remoting.exchange.support.ExchangeHandlerAdapter;
import org.apache.dubbo.remoting.exchange.support.header.HeaderExchanger;
import org.junit.jupiter.api.Test;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * ChanelHandlerTest
 * <p>
 * mvn clean test -Dtest=*PerformanceClientTest -Dserver=10.20.153.187:9911
 */
// OK
// 上面的mvn命令记下，可以指定系统属性
public class ChanelHandlerTest  {

    private static final Logger logger = LoggerFactory.getLogger(ChanelHandlerTest.class);

    public static ExchangeClient initClient(String url) {
        // Create client and build connection
        ExchangeClient exchangeClient = null;
        // 进去  （Peformance : 性能；绩效；表演；执行）
        PeformanceTestHandler handler = new PeformanceTestHandler(url);
        boolean run = true;
        while (run) {
            try {
                exchangeClient = Exchangers.connect(url, handler);
            } catch (Throwable t) {
                // 进catch块，原因是没有NettyTransporter(因为当前处于api模块)

                if (t != null && t.getCause() != null && t.getCause().getClass() != null && (t.getCause().getClass() == java.net.ConnectException.class
                        || t.getCause().getClass() == java.net.ConnectException.class)) {

                } else {
                    t.printStackTrace();
                }

                try {
                    // 睡眠50ms再次重试
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (exchangeClient != null) {
                run = false;
            }
        }
        return exchangeClient;
    }

    public static void closeClient(ExchangeClient client) {
        if (client.isConnected()) {
            client.close();
        }
    }

    // 注意先去 PerformanceServerMain 启动服务端，在启动下面client的测试程序
    @Test
    public void testClient() throws Throwable {
        // read server info from property
        // 下面我给临时注释掉
//        if (PerformanceUtils.getProperty("server", null) == null) {
//            logger.warn("Please set -Dserver=127.0.0.1:9911");
//            return;
//        }
        final String server = System.getProperty("server", "localhost:9911");
        final String transporter = PerformanceUtils.getProperty(Constants.TRANSPORTER_KEY, Constants.DEFAULT_TRANSPORTER);
        final String serialization = PerformanceUtils.getProperty(Constants.SERIALIZATION_KEY, Constants.DEFAULT_REMOTING_SERIALIZATION);
        final int timeout = PerformanceUtils.getIntProperty(TIMEOUT_KEY, DEFAULT_TIMEOUT);
        int sleep = PerformanceUtils.getIntProperty("sleep", 6 * 1000); // 原来是60 * 1000 * 60

        // exchange://127.0.0.1:9911?transporter=netty&serialization=hessian2&timeout=1000
        final String url = "exchange://" + server + "?transporter=" + transporter + "&serialization="
                + serialization + "&timeout=" + timeout+"&heartbeat="+1000; // 原来没有&heartbeat="+1000
        ExchangeClient exchangeClient = initClient(url);
        Thread.sleep(sleep);
        closeClient(exchangeClient);
    }


    // 一般业务的最终Handler就是 ExchangeHandler 实例，不过一般是通过 ExchangeHandlerAdapter 实现，用以传给Exchanges.connect/bind
    // 比如 DubboProtocol的ExchangeHandler requestHandler = new ExchangeHandlerAdapter()、
    // 比如 HeartbeatTest的TestHeartbeatHandler，这个是直接实现ExchangeHandler接口的
    static class PeformanceTestHandler extends ExchangeHandlerAdapter {
        String url = "";

        /**
         * @param url
         */
        public PeformanceTestHandler(String url) {
            this.url = url;
        }

        @Override
        public void connected(Channel channel) throws RemotingException {
            System.out.println("connected event,chanel;" + channel);
            // connected event,chanel;NettyChannel [channel=[id: 0x21b19aac, L:/30.25.58.197:62002 - R:/30.25.58.197:9911]]
        }

        @Override
        public void disconnected(Channel channel) throws RemotingException {
            System.out.println("disconnected event,chanel;" + channel);
            initClient(url);
        }

        /* (non-Javadoc)
         * @see org.apache.dubbo.remoting.transport.support.ChannelHandlerAdapter#caught(org.apache.dubbo.remoting.Channel, java.lang.Throwable)
         */
        @Override
        public void caught(Channel channel, Throwable exception) throws RemotingException {
//            System.out.println("caught event:"+exception);
        }


    }
}
