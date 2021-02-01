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

import org.apache.dubbo.common.serialize.Cleanable;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;
import org.apache.dubbo.remoting.buffer.ChannelBufferInputStream;
import org.apache.dubbo.remoting.buffer.ChannelBufferOutputStream;
import org.apache.dubbo.remoting.transport.AbstractCodec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Subclasses {@link org.apache.dubbo.remoting.telnet.codec.TelnetCodec} and {@link org.apache.dubbo.remoting.exchange.codec.ExchangeCodec}
 * both override all the methods declared in this class.
 *
 * 子类TelnetCodec和ExchangeCodec两者都覆盖在这个类中声明的所有方法。所以下面被标记为废弃 ，但是子类可不是废弃的，这种感觉有点奇怪
 */
// OK
@Deprecated
public class TransportCodec extends AbstractCodec {

    @Override
    public void encode(Channel channel, ChannelBuffer buffer, Object message) throws IOException {
        // ChannelBuffer -> ChannelBufferOutputStream 进去
        OutputStream output = new ChannelBufferOutputStream(buffer);
        // 根据url获取序列化扩展类实例，然后调用serialize获取序列化器，eg 返回 new FastJsonObjectOutput(output)
        // 序列化就是写出、保存，所以返回的是输出流，即将把msg灌入
        ObjectOutput objectOutput = getSerialization(channel).serialize(channel.getUrl(), output);
        // 进去
        encodeData(channel, objectOutput, message);
        // 进去
        objectOutput.flushBuffer();
        // Cleanable类似一种标记接口
        if (objectOutput instanceof Cleanable) {
            // 清理
            ((Cleanable) objectOutput).cleanup();
        }
    }

    @Override
    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        // 进去
        InputStream input = new ChannelBufferInputStream(buffer);
        ObjectInput objectInput = getSerialization(channel).deserialize(channel.getUrl(), input);
        Object object = decodeData(channel, objectInput);
        if (objectInput instanceof Cleanable) {
            ((Cleanable) objectInput).cleanup();
        }
        return object;
    }

    protected void encodeData(Channel channel, ObjectOutput output, Object message) throws IOException {
        // 进去
        encodeData(output, message);
    }

    protected Object decodeData(Channel channel, ObjectInput input) throws IOException {
        return decodeData(input);
    }

    protected void encodeData(ObjectOutput output, Object message) throws IOException {
        // 写入数据（以fastJson，就是利用其api写到output输出流，FastJsonObjectOutput内部利用的是PrintWriter writer，writer进行写的时候
        // 底层调用的是ChannelBufferOutputStream的write方法）
        output.writeObject(message);
    }

    protected Object decodeData(ObjectInput input) throws IOException {
        try {
            return input.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("ClassNotFoundException: " + StringUtils.toString(e));
        }
    }
}
