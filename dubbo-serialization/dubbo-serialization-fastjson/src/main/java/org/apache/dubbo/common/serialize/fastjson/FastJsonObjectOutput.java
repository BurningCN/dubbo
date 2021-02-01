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
package org.apache.dubbo.common.serialize.fastjson;

import org.apache.dubbo.common.serialize.ObjectOutput;

import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.io.*;

/**
 * FastJson object output implementation
 */
// OK
public class FastJsonObjectOutput implements ObjectOutput {

    private final PrintWriter writer;

    // 传过来的一般是ByteArrayOutputStream，流连接到的目的里面是没值的，需要往里面灌入
    public FastJsonObjectOutput(OutputStream out) {
        // 进去
        this(new OutputStreamWriter(out));
    }

    public FastJsonObjectOutput(Writer writer) {
        this.writer = new PrintWriter(writer);
    }


    // 下面各种基本数据类型，统一进 writeObject(Object obj)方法
    @Override
    public void writeBool(boolean v) throws IOException {
        writeObject(v);
    }

    @Override
    public void writeByte(byte v) throws IOException {
        writeObject(v);
    }

    @Override
    public void writeShort(short v) throws IOException {
        writeObject(v);
    }

    @Override
    public void writeInt(int v) throws IOException {
        writeObject(v);
    }

    @Override
    public void writeLong(long v) throws IOException {
        writeObject(v);
    }

    @Override
    public void writeFloat(float v) throws IOException {
        writeObject(v);
    }

    @Override
    public void writeDouble(double v) throws IOException {
        writeObject(v);
    }

    @Override
    public void writeUTF(String v) throws IOException {
        writeObject(v);
    }

    @Override
    public void writeBytes(byte[] b) throws IOException {
        // PrintWriter的api学习下，相当于除了字节数组，直接利用PrintWriter写入，其他的数据类型都进writeObject方法
        writer.println(new String(b));
    }

    @Override
    public void writeBytes(byte[] b, int off, int len) throws IOException {
        writer.println(new String(b, off, len));
    }

    @Override
    public void writeObject(Object obj) throws IOException {
        // fastJson相关api
        SerializeWriter out = new SerializeWriter();
        JSONSerializer serializer = new JSONSerializer(out);
        serializer.config(SerializerFeature.WriteEnumUsingToString, true);
        serializer.write(obj);
        // 输出到writer
        out.writeTo(writer);
        out.close(); // for reuse SerializeWriter buf
        // 空行+flush
        writer.println();
        writer.flush();
    }

    @Override
    public void flushBuffer() throws IOException {
        writer.flush();
    }

}
