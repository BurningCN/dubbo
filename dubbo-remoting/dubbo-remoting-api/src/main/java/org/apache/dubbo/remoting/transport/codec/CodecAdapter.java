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

package org.apache.dubbo.remoting.transport.codec;

import org.apache.dubbo.common.io.UnsafeByteArrayInputStream;
import org.apache.dubbo.common.io.UnsafeByteArrayOutputStream;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Codec;
import org.apache.dubbo.remoting.Codec2;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;

import java.io.IOException;

// OK
// 标准的适配器模式，实现目标接口，含有被适配器对象（对象组合的方式）
// 这里主要是兼容旧版本
public class CodecAdapter implements Codec2 {

    private Codec codec;

    public CodecAdapter(Codec codec) {
        Assert.notNull(codec, "codec == null");
        this.codec = codec;
    }

    @Override
    public void encode(Channel channel, ChannelBuffer buffer, Object message)
            throws IOException {
        // 进去
        UnsafeByteArrayOutputStream os = new UnsafeByteArrayOutputStream(1024);
        // 旧版本的codec的encode的参数和新版的不一致（区别在第二个参数），但是外层套了壳，做了兼容，这就是适配器模式，使得旧版本也能使用/适配了旧版本
        // 把msg的数据写到os
        codec.encode(channel, os, message);
        // 把os的数据写到buffer
        buffer.writeBytes(os.toByteArray());
    }

    @Override
    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        byte[] bytes = new byte[buffer.readableBytes()];
        int savedReaderIndex = buffer.readerIndex();
        // 把buffer的数据读到临时容器bytes中
        buffer.readBytes(bytes);
        // 输入流
        UnsafeByteArrayInputStream is = new UnsafeByteArrayInputStream(bytes);
        // 解码，接收返回结果
        Object result = codec.decode(channel, is);
        // 手动调整buffer的读指针
        buffer.readerIndex(savedReaderIndex + is.position());
        return result == Codec.NEED_MORE_INPUT ? DecodeResult.NEED_MORE_INPUT : result;
    }

    // 从上两个方法可见，Codec接口（没有2）的encode和decode的参数没有buffer，而是io输入输出流，也说明buffer既可以作为输入也可以作为输出，这点比io流更好。

    public Codec getCodec() {
        return codec;
    }
}
