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
package org.apache.dubbo.remoting.codec;


import org.apache.dubbo.common.Version;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.io.Bytes;
import org.apache.dubbo.common.io.UnsafeByteArrayOutputStream;
import org.apache.dubbo.common.serialize.ObjectOutput;
import org.apache.dubbo.common.serialize.Serialization;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.buffer.ChannelBuffer;
import org.apache.dubbo.remoting.buffer.ChannelBuffers;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.exchange.codec.ExchangeCodec;
import org.apache.dubbo.remoting.telnet.codec.TelnetCodec;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.READONLY_EVENT;
import static org.junit.jupiter.api.Assertions.fail;

/**
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
// OK
public class ExchangeCodecTest extends TelnetCodecTest {
    // magic header.
    private static final short MAGIC = (short) 0xdabb;
    private static final byte MAGIC_HIGH = (byte) Bytes.short2bytes(MAGIC)[0];
    private static final byte MAGIC_LOW = (byte) Bytes.short2bytes(MAGIC)[1];
    Serialization serialization = getSerialization(Constants.DEFAULT_REMOTING_SERIALIZATION);

    private static Serialization getSerialization(String name) {
        Serialization serialization = ExtensionLoader.getExtensionLoader(Serialization.class).getExtension(name);
        return serialization;
    }

    private Object decode(byte[] request) throws IOException {
        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(request);
        AbstractMockChannel channel = getServerSideChannel(url);
        //decode
        Object obj = codec.decode(channel, buffer);
        return obj;
    }

    private byte[] getRequestBytes(Object obj, byte[] header) throws IOException {
        // encode request data.
        UnsafeByteArrayOutputStream bos = new UnsafeByteArrayOutputStream(1024);
        ObjectOutput out = serialization.serialize(url, bos);
        out.writeObject(obj);

        out.flushBuffer();
        bos.flush();
        bos.close();
        byte[] data = bos.toByteArray();
        // data的长度是int表示的，我们转化为字节数组，用四个字节表示，进去
        byte[] len = Bytes.int2bytes(data.length);
        // 写到头部（头部的第12~15字节这部分是存放数据/身体长度的），这样就知道数据部分为多长
        System.arraycopy(len, 0, header, 12, 4);
        // 头+体 join
        byte[] request = join(header, data);
        return request;
    }

    private byte[] assemblyDataProtocol(byte[] header) {
        Person request = new Person();
        byte[] newbuf = join(header, objectToByte(request));
        return newbuf;
    }
    //===================================================================================

    @BeforeEach
    public void setUp() throws Exception {
        codec = new ExchangeCodec();
    }

    @Test
    public void test_Decode_Error_MagicNum() throws IOException {
        HashMap<byte[], Object> inputBytes = new HashMap<byte[], Object>();
        inputBytes.put(new byte[]{0}, TelnetCodec.DecodeResult.NEED_MORE_INPUT);
        inputBytes.put(new byte[]{MAGIC_HIGH, 0}, TelnetCodec.DecodeResult.NEED_MORE_INPUT);
        inputBytes.put(new byte[]{0, MAGIC_LOW}, TelnetCodec.DecodeResult.NEED_MORE_INPUT);

        for (Map.Entry<byte[], Object> entry: inputBytes.entrySet()) {
            testDecode_assertEquals(assemblyDataProtocol(entry.getKey()), entry.getValue());
        }
        // 所有都会在和 ENTER endWith()匹配的时候失败，返回TelnetCodec.DecodeResult.NEED_MORE_INPUT
    }

    @Test
    public void test_Decode_Error_Length() throws IOException {
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, 0x02, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        // 进去
        byte[] request = getRequestBytes(person, header);
        // 进去
        Channel channel = getServerSideChannel(url);
        byte[] baddata = new byte[]{1, 2};
        // 故意在join两个字节数据
        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(join(request, baddata));
        // 解码，进去
        Response obj = (Response) codec.decode(channel, buffer);
        Assertions.assertEquals(person, obj.getResult());
        // only decode necessary bytes
        // HeapChannelBuffer(ridx=86, widx=88, cap=88) 可见当前读指针才86（读了person），还有2个字节可读，就是上面的baddata，
        // 那么他是怎么知道就刚好读一个person的呢，原因在上面的getRequestBytes里，里面把person的长度写到了header
        Assertions.assertEquals(request.length, buffer.readerIndex());
    }

    @Test
    public void test_Decode_Error_Response_Object() throws IOException {
        //00000010-response/oneway/hearbeat=true |20-stats=ok|id=0|length=0
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, 0x02, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);
        // bad object
        byte[] badbytes = new byte[]{-1, -2, -3, -4, -3, -4, -3, -4, -3, -4, -3, -4};
        // request是86个字节，bad数据并没有追加到最后，而是从request的第21个字节开始覆盖，所以这是一个错误数据，内部decode会抛异常
        System.arraycopy(badbytes, 0, request, 21, badbytes.length);

        // 进去
        Response obj = (Response) decode(request);
        // Response.CLIENT_ERROR
        Assertions.assertEquals(90, obj.getStatus());
    }

    // 不合法的序列化id
    @Test
    public void testInvalidSerializaitonId() throws Exception {
        // 0x8F = 1000 1111 ,最高位1表示request，进ExchangeCodec#decodeBody的第二个分支
        // 1000 1111  & 0001 1111(SERIALIZATION_MASK) = 1111 （十进制15），而CodecSupport里面没有缓存id为15的序列化器
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, (byte)0x8F, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        // 进去
        Object obj =  decode(header);
        Assertions.assertTrue(obj instanceof Request);
        Request request = (Request) obj;
        // 下两个值是在decode request的时候，根据id获取扩展实例 获取不到抛异常 进catch块设置的
        Assertions.assertTrue(request.isBroken());
        Assertions.assertTrue(request.getData() instanceof IOException);

        // ==========================================================================================
        // 0x1F = 0001 1111，最高位不是1，表示response，这里测试decode response的时候序列化id不对捕获异常后填充到response 是什么数据
        header = new byte[]{MAGIC_HIGH, MAGIC_LOW, (byte)0x1F, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        obj = decode(header);
        Assertions.assertTrue(obj instanceof Response);
        Response response = (Response) obj;
        Assertions.assertEquals(response.getStatus(), Response.CLIENT_ERROR);
        Assertions.assertTrue(response.getErrorMessage().contains("IOException"));
    }

    @Test
    public void test_Decode_Check_Payload() throws IOException {
        // 12~15 = 1111, byte2Int = 0000001 0000001 0000001 0000001 = 16843009,数据部分这么大，checkPayload肯定抛异常
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        byte[] request = assemblyDataProtocol(header);
        try {
            testDecode_assertEquals(request, TelnetCodec.DecodeResult.NEED_MORE_INPUT);
            fail();
        } catch (IOException expected) {
            Assertions.assertTrue(expected.getMessage().startsWith("Data length too large: " + Bytes.bytes2int(new byte[]{1, 1, 1, 1})));
        }
    }

    @Test
    public void test_Decode_Header_Need_Readmore() throws IOException {

        // 这里header字节数组的长度为11，decode内部判断，readable < HEADER_LENGTH，return DecodeResult.NEED_MORE_INPUT;
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        testDecode_assertEquals(header, TelnetCodec.DecodeResult.NEED_MORE_INPUT);
    }

    @Test
    public void test_Decode_Body_Need_Readmore() throws IOException {
        // 这里header字节数组的长度为18（16+2，后2个作为数据部分），12~15 = 0011 ，byte2Int = 0000001 0000001 = 257，
        // 内部 257 + 16 > 18 ，依然 return DecodeResult.NEED_MORE_INPUT;
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 'a', 'a'};
        testDecode_assertEquals(header, TelnetCodec.DecodeResult.NEED_MORE_INPUT);
    }

    @Test
    public void test_Decode_MigicCodec_Contain_ExchangeHeader() throws IOException {
        // 两个魔数放在2 3 位置的
        byte[] header = new byte[]{0, 0, MAGIC_HIGH, MAGIC_LOW, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        Channel channel = getServerSideChannel(url);
        ChannelBuffer buffer = ChannelBuffers.wrappedBuffer(header);
        // 最后进telnetCodec的decode方法，判断不是ENTER结尾，返回NEED_MORE_INPUT
        Object obj = codec.decode(channel, buffer);
        Assertions.assertEquals(TelnetCodec.DecodeResult.NEED_MORE_INPUT, obj);
        // If the telnet data and request data are in the same data packet, we should guarantee that the receipt of request data won't be affected by the factor that telnet does not have an end characters.
        // 如果telnet数据和请求数据在同一个数据包中，我们应该保证不会因为telnet没有结束字符而影响到请求数据的接收。
        Assertions.assertEquals(2, buffer.readerIndex());
    }

    // easy 正常的decode response
    @Test
    public void test_Decode_Return_Response_Person() throws IOException {
        // 注意下面注释
        // 00000010-response/oneway/hearbeat=false/hessian |20-stats=ok|id=0|length=0
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, 2, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);

        Response obj = (Response) decode(request);
        Assertions.assertEquals(20, obj.getStatus());
        Assertions.assertEquals(person, obj.getResult());
        System.out.println(obj);
    }

    //The status input has a problem, and the read information is wrong when the serialization is serialized.
    @Test
    public void test_Decode_Return_Response_Error() throws IOException {
        // 90 表示error
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, 2, 90, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        String errorString = "encode request data error ";
        byte[] request = getRequestBytes(errorString, header);
        // 内部解析header[3]的status值 if (status == Response.OK) 不满足，走另一个分支，直接 res.setErrorMessage(in.readUTF());
        Response obj = (Response) decode(request);
        Assertions.assertEquals(90, obj.getStatus());
        Assertions.assertEquals(errorString, obj.getErrorMessage());
    }

    @Test
    public void test_Decode_Return_Request_Event_Object() throws IOException {
        // todo need pr 0xe2他解释成的二进制为：|10011111|20-stats=ok|id=0|length=0，是不对的，正确的如下
        //|11100010|20-stats=ok|id=0|length=0
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, (byte) 0xe2, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);

        Request obj = (Request) decode(request);
        Assertions.assertEquals(person, obj.getData());
        // 11100010 ，高6 7 位为1 表示 TwoWay 和 Event
        Assertions.assertTrue(obj.isTwoWay());
        Assertions.assertTrue(obj.isEvent());
        Assertions.assertEquals(Version.getProtocolVersion(), obj.getVersion());
        System.out.println(obj);
    }

    @Test
    public void test_Decode_Return_Request_Event_String() throws IOException {
        //|11100010|20-stats=ok|id=0|length=0
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, (byte) 0xe2, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        String event = READONLY_EVENT;
        byte[] request = getRequestBytes(event, header);

        Request obj = (Request) decode(request);
        Assertions.assertEquals(event, obj.getData());
        Assertions.assertTrue(obj.isTwoWay());
        Assertions.assertTrue(obj.isEvent());
        Assertions.assertEquals(Version.getProtocolVersion(), obj.getVersion());
        System.out.println(obj);
    }

    @Test
    public void test_Decode_Return_Request_Heartbeat_Object() throws IOException {
        //|11100010|20-stats=ok|id=0|length=0
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, (byte) 0xe2, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        byte[] request = getRequestBytes(null, header);
        Request obj = (Request) decode(request);
        Assertions.assertNull(obj.getData());
        Assertions.assertTrue(obj.isTwoWay());
        // 前面getRequestBytes只有header，没有体，且isEvent，那么就是心跳包
        Assertions.assertTrue(obj.isHeartbeat());
        Assertions.assertEquals(Version.getProtocolVersion(), obj.getVersion());
        System.out.println(obj);
    }

    @Test
    public void test_Decode_Return_Request_Object() throws IOException {
        //|11100010|20-stats=ok|id=0|length=0
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, (byte) 0xe2, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);

        Request obj = (Request) decode(request);
        Assertions.assertEquals(person, obj.getData());
        Assertions.assertTrue(obj.isTwoWay());
        // 不是心跳了，前面有person作为体
        Assertions.assertFalse(obj.isHeartbeat());
        Assertions.assertEquals(Version.getProtocolVersion(), obj.getVersion());
        System.out.println(obj);
    }

    @Test
    public void test_Decode_Error_Request_Object() throws IOException {
        //00000010-response/oneway/hearbeat=true |20-stats=ok|id=0|length=0
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, (byte) 0xe2, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);
        //bad object
        byte[] badbytes = new byte[]{-1, -2, -3, -4, -3, -4, -3, -4, -3, -4, -3, -4};
        System.arraycopy(badbytes, 0, request, 21, badbytes.length);

        Request obj = (Request) decode(request);
        Assertions.assertTrue(obj.isBroken());
        Assertions.assertTrue(obj.getData() instanceof Throwable);
    }

    // 测试名称为没有序列化标记？？ 但是内部是可以正常decode的
    @Test
    public void test_Header_Response_NoSerializationFlag() throws IOException {
        //00000010-response/oneway/hearbeat=false/noset |20-stats=ok|id=0|length=0
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, (byte) 0x02, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);

        Response obj = (Response) decode(request);
        Assertions.assertEquals(20, obj.getStatus());
        Assertions.assertEquals(person, obj.getResult());
        System.out.println(obj);
    }

    // 测试名字叫响应的心跳，但是下面的obj.isHeartbeat()返回的是false
    @Test
    public void test_Header_Response_Heartbeat() throws IOException {
        //00000010-response/oneway/hearbeat=true |20-stats=ok|id=0|length=0
        byte[] header = new byte[]{MAGIC_HIGH, MAGIC_LOW, 0x02, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        Person person = new Person();
        byte[] request = getRequestBytes(person, header);

        Response obj = (Response) decode(request);
        // Assertions.assertTrue(obj.isHeartbeat());
        Assertions.assertEquals(20, obj.getStatus());
        Assertions.assertEquals(person, obj.getResult());
        System.out.println(obj);
    }

    // =======================================================================================================================
    // 下面是encode
    // =======================================================================================================================
    // =======================================================================================================================

    @Test
    public void test_Encode_Request() throws IOException {
        // 进去，内部利用抽象工厂模式创建Buffer，三种具体工厂都有自己INSTANCE单例，获取到具体的工厂后，创建具体产品：
        // buffer = factory.getBuffer(estimatedLength); 不过buffer是赋值给ChannelBuffer这个抽象产品，具体产品有堆内、堆外等。
        ChannelBuffer encodeBuffer = ChannelBuffers.dynamicBuffer(2014);
        Channel channel = getCliendSideChannel(url);

        // 前面decode 都是把结果强转为Request和response，这里是手动构建，发请求了

        Request request = new Request();
        Person person = new Person();
        request.setData(person);

        // 进去，内部会把request的数据编码到 encodeBuffer
        codec.encode(channel, encodeBuffer, request);

        // encode resault check need decode
        // 下面就是把结果封装，走decode逻辑，看和一开始的是否一致
        byte[] data = new byte[encodeBuffer.writerIndex()];
        encodeBuffer.readBytes(data);
        ChannelBuffer decodeBuffer = ChannelBuffers.wrappedBuffer(data);
        Request obj = (Request) codec.decode(channel, decodeBuffer);

        // 都一致
        Assertions.assertEquals(request.isBroken(), obj.isBroken());
        Assertions.assertEquals(request.isHeartbeat(), obj.isHeartbeat());
        Assertions.assertEquals(request.isTwoWay(), obj.isTwoWay());
        Assertions.assertEquals(person, obj.getData());
    }

    @Test
    public void test_Encode_Response() throws IOException {
        ChannelBuffer encodeBuffer = ChannelBuffers.dynamicBuffer(1024);
        Channel channel = getCliendSideChannel(url);
        Response response = new Response();
        response.setHeartbeat(true);
        response.setId(1001L);
        response.setStatus((byte) 20);
        response.setVersion("11");
        Person person = new Person();
        response.setResult(person);
        // 进去  大体逻辑和前面test程序一致
        codec.encode(channel, encodeBuffer, response);
        byte[] data = new byte[encodeBuffer.writerIndex()];
        encodeBuffer.readBytes(data);

        //encode resault check need decode
        ChannelBuffer decodeBuffer = ChannelBuffers.wrappedBuffer(data);
        Response obj = (Response) codec.decode(channel, decodeBuffer);

        Assertions.assertEquals(response.getId(), obj.getId());
        Assertions.assertEquals(response.getStatus(), obj.getStatus());
        Assertions.assertEquals(response.isHeartbeat(), obj.isHeartbeat());
        Assertions.assertEquals(person, obj.getResult());
        // encode response verson ??
//        Assertions.assertEquals(response.getProtocolVersion(), obj.getVersion());

    }

    @Test
    public void test_Encode_Error_Response() throws IOException {
        ChannelBuffer encodeBuffer = ChannelBuffers.dynamicBuffer(1024);
        Channel channel = getCliendSideChannel(url);
        Response response = new Response();
        response.setHeartbeat(true);
        response.setId(1001L);
        response.setStatus((byte) 10); // 不是200，在encodeResponse进另一个分支
        response.setVersion("11");
        String badString = "bad";
        response.setErrorMessage(badString); // 错误消息
        Person person = new Person();
        response.setResult(person);

        codec.encode(channel, encodeBuffer, response);
        byte[] data = new byte[encodeBuffer.writerIndex()];
        encodeBuffer.readBytes(data);

        //encode resault check need decode
        ChannelBuffer decodeBuffer = ChannelBuffers.wrappedBuffer(data);
        Response obj = (Response) codec.decode(channel, decodeBuffer);
        Assertions.assertEquals(response.getId(), obj.getId());
        Assertions.assertEquals(response.getStatus(), obj.getStatus());
        Assertions.assertEquals(response.isHeartbeat(), obj.isHeartbeat());
        Assertions.assertEquals(badString, obj.getErrorMessage());
        Assertions.assertNull(obj.getResult());
//        Assertions.assertEquals(response.getProtocolVersion(), obj.getVersion());
    }

    // 下两个未看

    // 未看
    @Test
    public void testMessageLengthGreaterThanMessageActualLength() throws Exception {
        Channel channel = getCliendSideChannel(url);
        Request request = new Request(1L);
        request.setVersion(Version.getProtocolVersion());
        Date date = new Date();
        request.setData(date);
        ChannelBuffer encodeBuffer = ChannelBuffers.dynamicBuffer(1024);
        codec.encode(channel, encodeBuffer, request);
        byte[] bytes = new byte[encodeBuffer.writerIndex()];
        encodeBuffer.readBytes(bytes);
        int len = Bytes.bytes2int(bytes, 12);
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        out.write(bytes, 0, 12);
        /*
         * The fill length can not be less than 256, because by default, hessian reads 256 bytes from the stream each time.
         * Refer Hessian2Input.readBuffer for more details
         */
        int padding = 512;
        out.write(Bytes.int2bytes(len + padding));
        out.write(bytes, 16, bytes.length - 16);
        for (int i = 0; i < padding; i++) {
            out.write(1);
        }
        out.write(bytes);
        /* request|1111...|request */
        ChannelBuffer decodeBuffer = ChannelBuffers.wrappedBuffer(out.toByteArray());
        Request decodedRequest = (Request) codec.decode(channel, decodeBuffer);
        Assertions.assertEquals(date, decodedRequest.getData());
        Assertions.assertEquals(bytes.length + padding, decodeBuffer.readerIndex());
        decodedRequest = (Request) codec.decode(channel, decodeBuffer);
        Assertions.assertEquals(date, decodedRequest.getData());
    }

    // 未看
    @Test
    public void testMessageLengthExceedPayloadLimitWhenEncode() throws Exception {
        Request request = new Request(1L);
        request.setData("hello");
        ChannelBuffer encodeBuffer = ChannelBuffers.dynamicBuffer(512);
        AbstractMockChannel channel = getCliendSideChannel(url.addParameter(Constants.PAYLOAD_KEY, 4));
        try {
            codec.encode(channel, encodeBuffer, request);
            Assertions.fail();
        } catch (IOException e) {
            Assertions.assertTrue(e.getMessage().startsWith("Data length too large: " + 6));
        }

        Response response = new Response(1L);
        response.setResult("hello");
        encodeBuffer = ChannelBuffers.dynamicBuffer(512);
        channel = getServerSideChannel(url.addParameter(Constants.PAYLOAD_KEY, 4));
        codec.encode(channel, encodeBuffer, response);
        Assertions.assertTrue(channel.getReceivedMessage() instanceof Response);
        Response receiveMessage = (Response) channel.getReceivedMessage();
        Assertions.assertEquals(Response.BAD_RESPONSE, receiveMessage.getStatus());
        Assertions.assertTrue(receiveMessage.getErrorMessage().contains("Data length too large: "));
    }
}
