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
import org.apache.dubbo.remoting.Codec2;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.io.IOException;
import java.util.List;

/**
 * NettyCodecAdapter.
 */
// OK
// 见名知意，实现编解码的，作为childHandler的，利用netty提供的编解码MessageToByteEncoder和ByteToMessageDecoder，将其委托给codec
// （一般是dubboExchangeCodec）。几个关键属性：codec 、 encoder = new InternalEncoder()、decoder = new InternalDecoder() ，
// 以InternalEncoder内部类为例，继承MessageToByteEncoder，encode方法内部最后调用codec.encode(channel, buffer, msg)用以编码。
// 解码方法不知道为何是循环解码的？大概是和rmq的ByteBuffer很像，rmq是while(byteBuffer.hasRemaining()){}，这里是while(byteBuf.isReadable)
final public class NettyCodecAdapter {

    private final ChannelHandler encoder = new InternalEncoder();

    private final ChannelHandler decoder = new InternalDecoder();

    private final Codec2 codec;

    private final URL url;

    private final org.apache.dubbo.remoting.ChannelHandler handler;

    public NettyCodecAdapter(Codec2 codec, URL url, org.apache.dubbo.remoting.ChannelHandler handler) {
        this.codec = codec;
        this.url = url;
        this.handler = handler;
    }

    public ChannelHandler getEncoder() {
        return encoder;
    }

    public ChannelHandler getDecoder() {
        return decoder;
    }

    private class InternalEncoder extends MessageToByteEncoder { // 这里可以加泛型


        // 将msg编码到out   具体的编解码交给Dubbo的序列化模块里的各个不同的序列化实例（rmq也有，不过直接用的fastJson）
        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
            org.apache.dubbo.remoting.buffer.ChannelBuffer buffer = new NettyBackedChannelBuffer(out);
            Channel ch = ctx.channel();
            NettyChannel channel = NettyChannel.getOrAddChannel(ch, url, handler); // handler是NettyServer
            codec.encode(channel, buffer, msg);// 一般是DubboCountCodec、ExchangeCodec等 // 进去
        }
    }

    private class InternalDecoder extends ByteToMessageDecoder {

        // 将input解码填充到out
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf input, List<Object> out) throws Exception {

            ChannelBuffer message = new NettyBackedChannelBuffer(input);

            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url, handler);

            // decode object.  循环解码，一点点填充到out
            do {
                int saveReaderIndex = message.readerIndex();
                Object msg = codec.decode(channel, message); // 一般是DubboCountCodec // 进去
                if (msg == Codec2.DecodeResult.NEED_MORE_INPUT) {
                    message.readerIndex(saveReaderIndex);
                    break;
                } else {
                    //is it possible to go here ?
                    if (saveReaderIndex == message.readerIndex()) {
                        throw new IOException("Decode without read data.");
                    }
                    if (msg != null) {
                         // 一点点填充到out
                        out.add(msg);
                    }
                }
            } while (message.readable()); //  rmq的ByteBuffer很像，他是while(hasRemaining()){}，这里是readable，底层是buffer.isReadable
        }
    }
}
