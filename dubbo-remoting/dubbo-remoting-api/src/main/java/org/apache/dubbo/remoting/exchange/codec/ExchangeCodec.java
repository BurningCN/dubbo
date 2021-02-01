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
package org.apache.dubbo.remoting.exchange.codec;

import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.io.Bytes;
import org.apache.dubbo.common.io.StreamUtils;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.serialize.Cleanable;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.serialize.Serialization;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;
import org.apache.dubbo.remoting.buffer.ChannelBufferInputStream;
import org.apache.dubbo.remoting.buffer.ChannelBufferOutputStream;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.support.DefaultFuture;
import org.apache.dubbo.remoting.telnet.codec.TelnetCodec;
import org.apache.dubbo.remoting.transport.CodecSupport;
import org.apache.dubbo.remoting.transport.ExceedPayloadLimitException;

import java.io.IOException;
import java.io.InputStream;

/**
 * ExchangeCodec.
 */
// OK
public class ExchangeCodec extends TelnetCodec {

    // header length.
    protected static final int HEADER_LENGTH = 16;
    // magic header.
    protected static final short MAGIC = (short) 0xdabb; //意为dubbo
    protected static final byte MAGIC_HIGH = Bytes.short2bytes(MAGIC)[0];
    protected static final byte MAGIC_LOW = Bytes.short2bytes(MAGIC)[1];
    // message flag.
    protected static final byte FLAG_REQUEST = (byte) 0x80;  // 1000 0000
    protected static final byte FLAG_TWOWAY = (byte) 0x40;   // 0100 0000
    protected static final byte FLAG_EVENT = (byte) 0x20;    // 0010 0000
    protected static final int SERIALIZATION_MASK = 0x1f;    // 0001 1111（十进制：16+15 = 31）
    private static final Logger logger = LoggerFactory.getLogger(ExchangeCodec.class);

    public Short getMagicCode() {
        return MAGIC;
    }

    @Override
    public void encode(Channel channel, ChannelBuffer buffer, Object msg) throws IOException {
        if (msg instanceof Request) {
            // 对 Request 对象进行编码 进去
            encodeRequest(channel, buffer, (Request) msg);
        } else if (msg instanceof Response) {
            // 对 Response 对象进行编码
            encodeResponse(channel, buffer, (Response) msg);
        } else {
            super.encode(channel, buffer, msg);
        }
    }

    // 重写了父类TelnetCodec的decode方法
    @Override
    public Object decode(Channel channel, ChannelBuffer buffer) throws IOException {
        int readable = buffer.readableBytes();
        // 创建消息头字节数组
        byte[] header = new byte[Math.min(readable, HEADER_LENGTH)];
        // buffer = 头 + 体 这里读出头到header变量中
        buffer.readBytes(header);
        // 进去
        return decode(channel, buffer, readable, header);
    }

    @Override
    protected Object decode(Channel channel, ChannelBuffer buffer, int readable, byte[] header) throws IOException {
        // check magic number.
        // header的前两个字节是魔数，如果不满足，进if分支，最后super，调用TelnetCodec的方法
        if (readable > 0 && header[0] != MAGIC_HIGH
                || readable > 1 && header[1] != MAGIC_LOW) {
            int length = header.length;
            if (header.length < readable) {
                header = Bytes.copyOf(header, readable);
                buffer.readBytes(header, length, readable - length);
            }
            // 循环 找到 魔数的位置
            for (int i = 1; i < header.length - 1; i++) {
                // 这两个字节为魔数
                if (header[i] == MAGIC_HIGH && header[i + 1] == MAGIC_LOW) {
                    // readerIndex移动
                    buffer.readerIndex(buffer.readerIndex() - header.length + i);
                    // 把魔数前面的数据拷贝到新的header
                    header = Bytes.copyOf(header, i);
                    break;
                }
            }
            // 进去，内部把header当做msg，解码，buffer传进去了，但是没有用到
            // 通过 telnet 命令行发送的数据包不包含消息头，所以这里调用 TelnetCodec 的 decode 方法对数据包进行解码 - 官方源码文档
            return super.decode(channel, buffer, readable, header);
        }
        // check length. // 检测可读数据量是否少于消息头长度，若小于则立即返回 DecodeResult.NEED_MORE_INPUT
        if (readable < HEADER_LENGTH) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        // get data length.（第12~15字节存放了体的长度）
        int len = Bytes.bytes2int(header, 12);
        // 检测消息体长度是否超出限制，超出则抛出异常
        checkPayload(channel, len);

        int tt = len + HEADER_LENGTH;
        // 检测可读的字节数是否小于实际的字节数
        if (readable < tt) {
            return DecodeResult.NEED_MORE_INPUT;
        }

        // limit input stream.
        ChannelBufferInputStream is = new ChannelBufferInputStream(buffer, len);

        try {
            // 继续进行解码工作 进去
            return decodeBody(channel, is, header);

            // 上面方法通过检测消息头中的魔数是否与规定的魔数相等，提前拦截掉非常规数据包，比如通过 telnet 命令行发出的数据包。接着再对消息体长度，
            // 以及可读字节数进行检测。最后调用 decodeBody 方法进行后续的解码工作，ExchangeCodec 中实现了 decodeBody 方法，但因其子类
            // DubboCodec 覆写了该方法，所以在运行时 DubboCodec 中的 decodeBody 方法会被调用。下面我们来看一下该方法的代码。
        } finally {
            if (is.available() > 0) {
                try {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Skip input stream " + is.available());
                    }
                    StreamUtils.skipUnusedStream(is);
                } catch (IOException e) {
                    logger.warn(e.getMessage(), e);
                }
            }
        }
    }
    /**
     *      header的组成
     *
     *         byte 16
     *         0-1 magic code
     *         2 flag
         *         8 - 1-request/0-response
         *         7 - two way
         *         6 - heartbeat
         *         1-5 serialization id
     *         3 status
         *         20 ok
         *         90 error?
     *         4-11 id (long)
     *         12 -15 datalength
     */
    protected Object decodeBody(Channel channel, InputStream is, byte[] header) throws IOException {
        // 一个字节，可以拥有八个标记位，具体怎么分配的，看上面header 的组成。
        // proto是序列化器id，即serialization.getContentTypeId()，可以看CodecSupport
        byte flag = header[2], proto = (byte) (flag & SERIALIZATION_MASK);
        // get request id.
        long id = Bytes.bytes2long(header, 4);

        // 高8位不是1，就是响应数据
        if ((flag & FLAG_REQUEST) == 0) {
            // decode response.
            Response res = new Response(id);
            if ((flag & FLAG_EVENT) != 0) {
                res.setEvent(true);
            }
            // get status. status = 20 表示 ok
            byte status = header[3];
            res.setStatus(status);
            try { // 根据proto（序列化id）、url、is获取序列化扩展实例的序列化器，进去
                ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                if (status == Response.OK) { // 正好Response.OK = 20
                    Object data;
                    if (res.isHeartbeat()) {
                        data = decodeHeartbeatData(channel, in);
                    } else if (res.isEvent()) {
                        data = decodeEventData(channel, in);
                    } else { // getRequestData 、decodeResponseData 进去
                        data = decodeResponseData(channel, in, getRequestData(id));
                    } // 填充到结果中
                    res.setResult(data);
                } else {
                    res.setErrorMessage(in.readUTF());
                }
            } catch (Throwable t) {
                // 前面decodeXX(比如decodeResponseData)内部调用readObject反序列化抛异常的话会进这里，
                // 或者CodecSupport.deserialize内部检查id不对抛异常也会进这里
                res.setStatus(Response.CLIENT_ERROR); // 90
                res.setErrorMessage(StringUtils.toString(t));
            }
            return res;

            // 高8位是1，就是发来的请求
        } else {
            // decode request.
            Request req = new Request(id);
            req.setVersion(Version.getProtocolVersion());
            req.setTwoWay((flag & FLAG_TWOWAY) != 0);
            if ((flag & FLAG_EVENT) != 0) {
                req.setEvent(true);
            }
            try {
                // 进去
                ObjectInput in = CodecSupport.deserialize(channel.getUrl(), is, proto);
                Object data;
                // 根据请求的类型走不通的解码逻辑
                if (req.isHeartbeat()) {
                    data = decodeHeartbeatData(channel, in);
                } else if (req.isEvent()) {
                    data = decodeEventData(channel, in);
                } else {
                    data = decodeRequestData(channel, in);
                }
                req.setData(data);
            } catch (Throwable t) {
                // 前面CodecSupport.deserialize根据id找不到序列化扩展实例也会抛异常，走到这里，或者另三个decodeXX抛异常
                // bad request
                req.setBroken(true);
                req.setData(t);
            }
            return req;
        }
    }

    protected Object getRequestData(long id) {
        DefaultFuture future = DefaultFuture.getFuture(id);
        if (future == null) {
            return null;
        }
        Request req = future.getRequest();
        if (req == null) {
            return null;
        }
        return req.getData();
    }

    // 对请求数据进行编码，编码成规定的协议格式，主要是Header+Body。
    // 先把Header的16个字节该设置的设置好，多个标记位利用或操作，然后往参数buffer写入身体部分（实现移动写指针，空出16字节），
    // 再把写入的字节数填充到头部，然后移动写指针到空处开头，写入头部，再还原写指针。
    protected void encodeRequest(Channel channel, ChannelBuffer buffer, Request req) throws IOException {
        Serialization serialization = getSerialization(channel);
        // header.
        byte[] header = new byte[HEADER_LENGTH];
        // set magic number.
        Bytes.short2bytes(MAGIC, header);

        // set request and serialization flag.
        // 或操作使得两个标记位结合
        header[2] = (byte) (FLAG_REQUEST | serialization.getContentTypeId());

        if (req.isTwoWay()) {
            header[2] |= FLAG_TWOWAY;// 或操作
        }
        if (req.isEvent()) {
            header[2] |= FLAG_EVENT;// 或操作
        }

        // set request id.
        Bytes.long2bytes(req.getId(), header, 4);

        // encode request data.
        int savedWriteIndex = buffer.writerIndex();
        // 移动写指针，空出16个字节
        buffer.writerIndex(savedWriteIndex + HEADER_LENGTH);
        ChannelBufferOutputStream bos = new ChannelBufferOutputStream(buffer);
        ObjectOutput out = serialization.serialize(channel.getUrl(), bos);
        if (req.isEvent()) {
            encodeEventData(channel, out, req.getData());
        } else {
            // 把request的数据写到out(bos)，注意前面16个字节空出来了，还没写header
            encodeRequestData(channel, out, req.getData(), req.getVersion());
        }
        out.flushBuffer();
        if (out instanceof Cleanable) {
            ((Cleanable) out).cleanup();
        }
        bos.flush();
        bos.close();
        // 获取写入数据（身体）的字节数
        int len = bos.writtenBytes();
        checkPayload(channel, len);
        // 填充到header
        Bytes.int2bytes(len, header, 12);

        // 前面16个字节空出来了，还没写header ，这里开始写入，先绝对定位头部的/空出的起始位置
        // write header
        buffer.writerIndex(savedWriteIndex);
        buffer.writeBytes(header); // write header.
        // 绝对定位还原写指针
        buffer.writerIndex(savedWriteIndex + HEADER_LENGTH + len);
    }

    // 大体逻辑参考 encodeRequest 基本一致
    protected void encodeResponse(Channel channel, ChannelBuffer buffer, Response res) throws IOException {
        // 获取 buffer 当前的写位置
        int savedWriteIndex = buffer.writerIndex();
        try {
            Serialization serialization = getSerialization(channel);
            // header.  // 创建消息头字节数组，长度为 16
            byte[] header = new byte[HEADER_LENGTH];
            // set magic number.  // 设置魔数
            Bytes.short2bytes(MAGIC, header);
            // set request and serialization flag.    // 设置数据包类型（Request/Response）和序列化器编号
            header[2] = serialization.getContentTypeId();
            if (res.isHeartbeat()) {
                header[2] |= FLAG_EVENT;// 设置事件标识
            }
            // set response status.
            byte status = res.getStatus();
            header[3] = status;
            // set request id. // 设置请求编号，8个字节，从第4个字节开始设置
            Bytes.long2bytes(res.getId(), header, 4);

            // 更新 writerIndex，为消息头预留 16 个字节的空间
            buffer.writerIndex(savedWriteIndex + HEADER_LENGTH);
            ChannelBufferOutputStream bos = new ChannelBufferOutputStream(buffer);
            // 创建序列化器，比如 Hessian2ObjectOutput
            ObjectOutput out = serialization.serialize(channel.getUrl(), bos);
            // encode response data or error message.
            if (status == Response.OK) {
                if (res.isHeartbeat()) {
                    // 对事件数据进行序列化操作
                    encodeEventData(channel, out, res.getResult());
                } else {
                    // 对请求数据进行序列化操作 进去
                    encodeResponseData(channel, out, res.getResult(), res.getVersion());
                }
            } else {
                out.writeUTF(res.getErrorMessage());
            }
            out.flushBuffer();
            if (out instanceof Cleanable) {
                ((Cleanable) out).cleanup();
            }
            bos.flush();
            bos.close();

            // 获取写入的字节数，也就是消息体长度
            int len = bos.writtenBytes();
            checkPayload(channel, len);
            // 将消息体长度写入到消息头中
            Bytes.int2bytes(len, header, 12);
            // 将 buffer 指针移动到 savedWriteIndex，为写消息头做准备
            buffer.writerIndex(savedWriteIndex);
            // 从 savedWriteIndex 下标处写入消息头
            buffer.writeBytes(header);
            // 设置新的 writerIndex，writerIndex = 原写下标 + 消息头长度 + 消息体长度
            buffer.writerIndex(savedWriteIndex + HEADER_LENGTH + len);

            // 以上就是请求对象的编码过程，该过程首先会通过位运算将消息头写入到 header 数组中。然后对 Request 对象的 data 字段执行序列化操作，
            // 序列化后的数据最终会存储到 ChannelBuffer 中。序列化操作执行完后，可得到数据序列化后的长度 len，紧接着将 len 写入到 header
            // 指定位置处。最后再将消息头字节数组 header 写入到 ChannelBuffer 中，整个编码过程就结束了。本节的最后，我们再来看一下 Request
            // 对象的 data 字段序列化过程，也就是 encodeRequestData 方法的逻辑，如下（该类的倒数第2个方法）
        } catch (Throwable t) {
            // clear buffer
            buffer.writerIndex(savedWriteIndex);
            // send error message to Consumer, otherwise, Consumer will wait till timeout.
            if (!res.isEvent() && res.getStatus() != Response.BAD_RESPONSE) {
                Response r = new Response(res.getId(), res.getVersion());
                r.setStatus(Response.BAD_RESPONSE);

                if (t instanceof ExceedPayloadLimitException) {
                    logger.warn(t.getMessage(), t);
                    try {
                        r.setErrorMessage(t.getMessage());
                        channel.send(r);
                        return;
                    } catch (RemotingException e) {
                        logger.warn("Failed to send bad_response info back: " + t.getMessage() + ", cause: " + e.getMessage(), e);
                    }
                } else {
                    // FIXME log error message in Codec and handle in caught() of IoHanndler?
                    logger.warn("Fail to encode response: " + res + ", send bad_response info instead, cause: " + t.getMessage(), t);
                    try {
                        r.setErrorMessage("Failed to send response: " + res + ", cause: " + StringUtils.toString(t));
                        channel.send(r);
                        return;
                    } catch (RemotingException e) {
                        logger.warn("Failed to send bad_response info back: " + res + ", cause: " + e.getMessage(), e);
                    }
                }
            }

            // Rethrow exception
            if (t instanceof IOException) {
                throw (IOException) t;
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                throw new RuntimeException(t.getMessage(), t);
            }
        }
    }

    @Override
    protected Object decodeData(ObjectInput in) throws IOException {
        return decodeRequestData(in);
    }

    @Deprecated
    protected Object decodeHeartbeatData(ObjectInput in) throws IOException {
        return decodeEventData(null, in);
    }

    protected Object decodeRequestData(ObjectInput in) throws IOException {
        try {
            return in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(StringUtils.toString("Read object failed.", e));
        }
    }

    protected Object decodeResponseData(ObjectInput in) throws IOException {
        try {
            return in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException(StringUtils.toString("Read object failed.", e));
        }
    }

    @Override
    protected void encodeData(ObjectOutput out, Object data) throws IOException {
        encodeRequestData(out, data);
    }

    private void encodeEventData(ObjectOutput out, Object data) throws IOException {
        out.writeEvent(data);
    }

    @Deprecated
    protected void encodeHeartbeatData(ObjectOutput out, Object data) throws IOException {
        encodeEventData(out, data);
    }

    protected void encodeRequestData(ObjectOutput out, Object data) throws IOException {
        out.writeObject(data);
    }

    protected void encodeResponseData(ObjectOutput out, Object data) throws IOException {
        out.writeObject(data);
    }

    @Override
    protected Object decodeData(Channel channel, ObjectInput in) throws IOException {
        return decodeRequestData(channel, in);
    }

    protected Object decodeEventData(Channel channel, ObjectInput in) throws IOException {
        try {
            return in.readEvent();
        } catch (IOException | ClassNotFoundException e) {
            throw new IOException(StringUtils.toString("Decode dubbo protocol event failed.", e));
        }
    }

    @Deprecated
    protected Object decodeHeartbeatData(Channel channel, ObjectInput in) throws IOException {
        return decodeEventData(channel, in);
    }

    protected Object decodeRequestData(Channel channel, ObjectInput in) throws IOException {
        return decodeRequestData(in);
    }

    protected Object decodeResponseData(Channel channel, ObjectInput in) throws IOException {
        return decodeResponseData(in);
    }

    protected Object decodeResponseData(Channel channel, ObjectInput in, Object requestData) throws IOException {
        return decodeResponseData(channel, in);
    }

    @Override
    protected void encodeData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeRequestData(channel, out, data);
    }

    private void encodeEventData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeEventData(out, data);
    }

    @Deprecated
    protected void encodeHeartbeatData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeHeartbeatData(out, data);
    }

    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeRequestData(out, data);
    }


    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data) throws IOException {
        encodeResponseData(out, data);
    }

    // 看 DubboCodec 重写的
    protected void encodeRequestData(Channel channel, ObjectOutput out, Object data, String version) throws IOException {
        encodeRequestData(out, data);
    }


    // 看 DubboCodec 重写的
    protected void encodeResponseData(Channel channel, ObjectOutput out, Object data, String version) throws IOException {
        encodeResponseData(out, data);
    }


}
