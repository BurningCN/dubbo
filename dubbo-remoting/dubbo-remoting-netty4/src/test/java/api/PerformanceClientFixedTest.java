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

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.exchange.ExchangeClient;
import org.apache.dubbo.remoting.exchange.Exchangers;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Random;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.remoting.Constants.CONNECTIONS_KEY;

// OK
public class PerformanceClientFixedTest  {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceClientTest.class);

    // 注意先去 PerformanceServerMain 启动服务端，在启动下面的测试程序
    @Test
    public void testClient() throws Exception {
        // read the parameters
        // 临时注释
//        if (PerformanceUtils.getProperty("server", null) == null) {
//            logger.warn("Please set -Dserver=127.0.0.1:9911");
//            return;
//        }
        final String server = System.getProperty("server", "127.0.0.1:9911");
        final String transporter = PerformanceUtils.getProperty(Constants.TRANSPORTER_KEY, Constants.DEFAULT_TRANSPORTER);
        final String serialization = PerformanceUtils.getProperty(Constants.SERIALIZATION_KEY, Constants.DEFAULT_REMOTING_SERIALIZATION);
        final int timeout = PerformanceUtils.getIntProperty(TIMEOUT_KEY, DEFAULT_TIMEOUT);
        //final int length = PerformanceUtils.getIntProperty("length", 1024);
        final int connectionCount = PerformanceUtils.getIntProperty(CONNECTIONS_KEY, 1);
        //final int concurrent = PerformanceUtils.getIntProperty("concurrent", 100);
        //int r = PerformanceUtils.getIntProperty("runs", 10000);
        //final int runs = r > 0 ? r : Integer.MAX_VALUE;
        //final String onerror = PerformanceUtils.getProperty("onerror", "continue");
        final String url = "exchange://" + server + "?transporter=" + transporter + "&serialization=" + serialization + "&timeout=" + timeout;

        //int idx = server.indexOf(':');
        Random rd = new Random(connectionCount);
        ArrayList<ExchangeClient> arrays = new ArrayList<ExchangeClient>();
        String oneKBlock = null;
        String messageBlock = null;
        int s = 0;
        int f = 0;
        System.out.println("initialize arrays " + url);
        while (s < connectionCount) {
            ExchangeClient client = null;
            try {
                System.out.println("open connection " + s + " " + url + arrays.size());

                // 连接
                client = Exchangers.connect(url);

                System.out.println("run after open");

                if (client.isConnected()) {
                    arrays.add(client);
                    s++;
                    System.out.println("open client success " + s);
                } else {
                    System.out.println("open client failed, try again.");
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                if (client != null && !client.isConnected()) {
                    f++;
                    System.out.println("open client failed, try again " + f);
                    client.close();
                }
            }
        }

        StringBuilder sb1 = new StringBuilder();
        Random rd2 = new Random();
        char[] numbersAndLetters = ("0123456789abcdefghijklmnopqrstuvwxyz" + "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ").toCharArray();
        int size1 = numbersAndLetters.length;
        for (int j = 0; j < 1024; j++) {
            // 填充1024个 随机字符
            sb1.append(numbersAndLetters[rd2.nextInt(size1)]);
        }
        oneKBlock = sb1.toString();

        // 发100次请求数据给服务端
        for (int j = 0; j < 101; j++) {
            try {
                String size = "10";

                int request_size = 10;
                try {
                    request_size = Integer.parseInt(size);
                } catch (Throwable t) {
                    request_size = 10;
                }

                if (messageBlock == null) {
                    StringBuilder sb = new StringBuilder();
                    // oneKBlock * request_size 倍 消息大小（10240个字符）
                    for (int i = 0; i < request_size; i++) {
                        sb.append(oneKBlock);
                    }
                    messageBlock = sb.toString();

                    System.out.println("set messageBlock to " + messageBlock);
                }
                int index = rd.nextInt(connectionCount);
                // 如果有多个客户端，随机选择一个
                ExchangeClient client = arrays.get(index);
                // 核心！这里get阻塞获取结果进去
                String output = (String) client.request(messageBlock).get();

                if (output.lastIndexOf(messageBlock) < 0) {
                    System.out.println("send messageBlock;get " + output);
                    throw new Throwable("return results invalid");
                } else {
                    // 都走这个分支
                    if (j % 100 == 0)
                        System.out.println("OK: " + j);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

}
