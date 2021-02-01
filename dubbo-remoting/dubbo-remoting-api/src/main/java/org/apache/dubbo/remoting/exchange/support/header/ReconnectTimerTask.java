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

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Client;

/**
 * ReconnectTimerTask
 */
// OK
public class ReconnectTimerTask extends AbstractTimerTask {

    private static final Logger logger = LoggerFactory.getLogger(ReconnectTimerTask.class);

    private final int idleTimeout;

    // gx
    public ReconnectTimerTask(ChannelProvider channelProvider, Long heartbeatTimeoutTick, int idleTimeout) {
        // 进去，channelProvider 里面含有HeaderExchangeClient实例
        super(channelProvider, heartbeatTimeoutTick);
        this.idleTimeout = idleTimeout;
    }

    // gx 模板方法，被父类调用。这里的channel就是上面构造函数的channelProvider里的内容，即HeaderExchangeClient实例
    @Override
    protected void doTask(Channel channel) {
        try {
            Long lastRead = lastRead(channel);// 进去
            Long now = now();// 进去

            // Rely on reconnect timer to reconnect when AbstractClient.doConnect fails to init the connection
            // 当AbstractClient.doConnect 初始化连接失败 依赖重新连接计时器进行重连
            if (!channel.isConnected()) {
                try {
                    logger.info("Initial connection to " + channel);
                    // 核心！进去
                    ((Client) channel).reconnect();
                } catch (Exception e) {
                    logger.error("Fail to connect to " + channel, e);
                }
            // check pong at client
            } else if (lastRead != null && now - lastRead > idleTimeout) {
                // 上面的idleTimeout就是NettyClient的IdleStateHandler的读空闲最大时间，这里不是因为连接失败而重连。是已经连接了，但是为了
                // 防止客户端读空闲超时被服务端关闭，所以发起重连。看如下日志
                logger.warn("Reconnect to channel " + channel + ", because heartbeat read idle time out: "
                        + idleTimeout + "ms");
                try {
                    ((Client) channel).reconnect();
                } catch (Exception e) {
                    logger.error(channel + "reconnect failed during idle time.", e);
                }
            }
        } catch (Throwable t) {
            logger.warn("Exception when reconnect to remote channel " + channel.getRemoteAddress(), t);
        }
    }
}
