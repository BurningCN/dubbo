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
package org.apache.dubbo.remoting.telnet.codec;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;
import org.apache.dubbo.remoting.transport.codec.TransportCodec;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.apache.dubbo.remoting.Constants.CHARSET_KEY;
import static org.apache.dubbo.remoting.Constants.DEFAULT_CHARSET;

/**
 * TelnetCodec
 */
// OK
public class TelnetCodec extends TransportCodec {

    private static final Logger logger = LoggerFactory.getLogger(TelnetCodec.class);

    private static final String HISTORY_LIST_KEY = "telnet.history.list";

    private static final String HISTORY_INDEX_KEY = "telnet.history.index";

    private static final byte[] UP = new byte[]{27, 91, 65};

    private static final byte[] DOWN = new byte[]{27, 91, 66};

    private static final List<?> ENTER = Arrays.asList(
            new byte[]{'\r', '\n'} /* Windows Enter */,
            new byte[]{'\n'} /* Linux Enter */);

    private static final List<?> EXIT = Arrays.asList(
            new byte[]{3} /* Windows Ctrl+C */,
            new byte[]{-1, -12, -1, -3, 6} /* Linux Ctrl+C */,
            new byte[]{-1, -19, -1, -3, 6} /* Linux Pause */);

    private static Charset getCharset(Channel channel) {
        if (channel != null) {
            // 从channel的属性中取CHARSET_KEY的值
            Object attribute = channel.getAttribute(CHARSET_KEY);
            if (attribute instanceof String) {
                try {
                    // 获取字符集实例
                    return Charset.forName((String) attribute);
                } catch (Throwable t) {
                    logger.warn(t.getMessage(), t);
                }
            } else if (attribute instanceof Charset) {
                return (Charset) attribute;
            }
            URL url = channel.getUrl();
            if (url != null) {
                // 从url的参数中取CHARSET_KEY的值
                String parameter = url.getParameter(CHARSET_KEY);
                if (StringUtils.isNotEmpty(parameter)) {
                    try {
                        return Charset.forName(parameter);
                    } catch (Throwable t) {
                        logger.warn(t.getMessage(), t);
                    }
                }
            }
        }
        try {
            return Charset.forName(DEFAULT_CHARSET);
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
        return Charset.defaultCharset();
    }

    private static String toString(byte[] message, Charset charset) throws UnsupportedEncodingException {
        // 准备临时容器
        byte[] copy = new byte[message.length];
        int index = 0;
        for (int i = 0; i < message.length; i++) {
            byte b = message[i];
            if (b == '\b') { // backspace
                if (index > 0) {
                    index--;
                }
                if (i > 2 && message[i - 2] < 0) { // double byte char
                    if (index > 0) {
                        index--;
                    }
                }
            } else if (b == 27) { // escape
                if (i < message.length - 4 && message[i + 4] == 126) {
                    i = i + 4;
                } else if (i < message.length - 3 && message[i + 3] == 126) {
                    i = i + 3;
                } else if (i < message.length - 2) {
                    i = i + 2;
                }
            } else if (b == -1 && i < message.length - 2
                    && (message[i + 1] == -3 || message[i + 1] == -5)) { // handshake
                i = i + 2;
            } else {
                // 主要走这个逻辑
                copy[index++] = message[i];
            }
        }
        if (index == 0) {
            return "";
        }
        // 变字符串，指定字符编码方式的名称
        return new String(copy, 0, index, charset.name()).trim();
    }

    // 判断两个字节数组完全相等
    private static boolean isEquals(byte[] message, byte[] command) throws IOException {
        return message.length == command.length && endsWith(message, command);
    }

    private static boolean endsWith(byte[] message, byte[] command) throws IOException {
        if (message.length < command.length) {
            return false;
        }
        // 计算offset，避免不必要的字节匹配
        int offset = message.length - command.length;
        // 从后往前匹配
        for (int i = command.length - 1; i >= 0; i--) {
            if (message[offset + i] != command[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void encode(Channel channel, ChannelBuffer buffer, Object message) throws IOException {
        if (message instanceof String) {
            if (isClientSide(channel)) {
                message = message + "\r\n";
            }
            byte[] msgData = ((String) message).getBytes(getCharset(channel).name());
            buffer.writeBytes(msgData);
        } else {
            super.encode(channel, buffer, message);
        }
    }

    // 重写了父类TransportCodec的decode方法
    @Override
    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        // buffer的数据拷贝到一个临时的字节数组message
        int readable = buffer.readableBytes();
        byte[] message = new byte[readable];
        buffer.readBytes(message);
        // 进去
        return decode(channel, buffer, readable, message);
    }

    // buffer参数没有用到
    @SuppressWarnings("unchecked")
    protected Object decode(Channel channel, ChannelBuffer buffer, int readable, byte[] message) throws IOException {
        // 进去
        if (isClientSide(channel)) {
            // getCharset、toString 进去
            return toString(message, getCharset(channel));
        }
        checkPayload(channel, readable);
        if (message == null || message.length == 0) {
            // msg为空，表明没有输入，还解码个der
            return DecodeResult.NEED_MORE_INPUT;
        }

        // 最后一个字节是'\b'
        if (message[message.length - 1] == '\b') { // Windows backspace echo
            try {
                // double byte char 意思就是说Windows最后必须两个\b
                boolean doublechar = message.length >= 3 && message[message.length - 3] < 0;
                // new String(new byte[]{32, 32, 8, 8})是"\b\b"，new byte[]{32, 8}是 "\b"
                // 可以看下AbstractMockChannel的send方法和getReceivedMessage
                channel.send(new String(doublechar ? new byte[]{32, 32, 8, 8} : new byte[]{32, 8}, getCharset(channel).name()));
            } catch (RemotingException e) {
                throw new IOException(StringUtils.toString(e));
            }
            return DecodeResult.NEED_MORE_INPUT;
        }

        for (Object command : EXIT) {
            // 传入Telnet的decode方法的msg消息字节数组比如位new byte[]{3}，和EXIT的其一是匹配的，表示要关闭channel，进去
            if (isEquals(message, (byte[]) command)) {
                if (logger.isInfoEnabled()) {
                    // 日志
                    logger.info(new Exception("Close channel " + channel + " on exit command: " + Arrays.toString((byte[]) command)));
                }
                // 关闭通道
                channel.close();
                return null;
            }
        }

        // UP、DOWN表示channel的history的上、下一条消息
        boolean up = endsWith(message, UP);
        boolean down = endsWith(message, DOWN);
        if (up || down) {
            // 获取list
            LinkedList<String> history = (LinkedList<String>) channel.getAttribute(HISTORY_LIST_KEY);
            if (CollectionUtils.isEmpty(history)) {
                return DecodeResult.NEED_MORE_INPUT;
            }
            // 获取记录的索引值
            Integer index = (Integer) channel.getAttribute(HISTORY_INDEX_KEY);
            Integer old = index;
            if (index == null) {
                index = history.size() - 1;
            } else {
                if (up) {
                    index = index - 1;
                    if (index < 0) {
                        index = history.size() - 1;
                    }
                } else {
                    index = index + 1;
                    if (index > history.size() - 1) {
                        index = 0;
                    }
                }
            }
            if (old == null || !old.equals(index)) {
                // 更新索引值
                channel.setAttribute(HISTORY_INDEX_KEY, index);
                // 取
                String value = history.get(index);
                if (old != null && old >= 0 && old < history.size()) {
                    String ov = history.get(old);
                    StringBuilder buf = new StringBuilder();
                    for (int i = 0; i < ov.length(); i++) {
                        buf.append("\b");
                    }
                    for (int i = 0; i < ov.length(); i++) {
                        buf.append(" ");
                    }
                    for (int i = 0; i < ov.length(); i++) {
                        buf.append("\b");
                    }
                    value = buf.toString() + value;
                }
                try {
                    // 发送到channel
                    channel.send(value);
                } catch (RemotingException e) {
                    throw new IOException(StringUtils.toString(e));
                }
            }
            return DecodeResult.NEED_MORE_INPUT;
        }
        for (Object command : EXIT) {
            // 完全匹配，表示退出，即关闭channel
            if (isEquals(message, (byte[]) command)) {
                if (logger.isInfoEnabled()) {
                    logger.info(new Exception("Close channel " + channel + " on exit command " + command));
                }
                channel.close();
                return null;
            }
        }
        byte[] enter = null;
        for (Object command : ENTER) {
            // 是否以ENTER换行符结尾，如果是服务端的数据，那么一定得换行符结尾，比如/n结尾，具体参考ENTER，否则表示未输入完，NEED_MORE_INPUT
            // 进去
            if (endsWith(message, (byte[]) command)) {
                enter = (byte[]) command;
                break;
            }
        }
        if (enter == null) {
            // 如果是服务端的数据，那么一定得换行符结尾，比如/n结尾，具体参考ENTER，否则表示未输入完，NEED_MORE_INPUT
            return DecodeResult.NEED_MORE_INPUT;
        }

        // decode后的消息存到channel的属性（history）中。用以下次发送UP、DOWN命令的时候取出来

        LinkedList<String> history = (LinkedList<String>) channel.getAttribute(HISTORY_LIST_KEY);
        Integer index = (Integer) channel.getAttribute(HISTORY_INDEX_KEY);
        channel.removeAttribute(HISTORY_INDEX_KEY);
        if (CollectionUtils.isNotEmpty(history) && index != null && index >= 0 && index < history.size()) {
            String value = history.get(index);
            if (value != null) {
                // 开辟空间存放原index位置的值和msg

                byte[] b1 = value.getBytes();
                byte[] b2 = new byte[b1.length + message.length];
                System.arraycopy(b1, 0, b2, 0, b1.length);
                System.arraycopy(message, 0, b2, b1.length, message.length);
                message = b2;
            }
        }
        String result = toString(message, getCharset(channel));
        if (result.trim().length() > 0) {
            if (history == null) {
                history = new LinkedList<String>();
                channel.setAttribute(HISTORY_LIST_KEY, history);
            }
            if (history.isEmpty()) {
                history.addLast(result);
            } else if (!result.equals(history.getLast())) {
                history.remove(result);
                history.addLast(result);
                if (history.size() > 10) {
                    history.removeFirst();
                }
            }
        }
        // 返回解码后的数据
        return result;
    }

}
