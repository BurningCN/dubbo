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

package org.apache.dubbo.remoting.buffer;

import java.nio.ByteBuffer;

public final class ChannelBuffers {

    // 堆缓冲区
    public static final ChannelBuffer EMPTY_BUFFER = new HeapChannelBuffer(0);

    private ChannelBuffers() {
    }

    public static ChannelBuffer dynamicBuffer() {
        return dynamicBuffer(256);
    }

    public static ChannelBuffer dynamicBuffer(int capacity) {
        // 进去
        return new DynamicChannelBuffer(capacity);
    }

    public static ChannelBuffer dynamicBuffer(int capacity,
                                              ChannelBufferFactory factory) {
        return new DynamicChannelBuffer(capacity, factory);
    }

    public static ChannelBuffer buffer(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity can not be negative");
        }
        if (capacity == 0) {
            return EMPTY_BUFFER;
        }
        // 进去
        return new HeapChannelBuffer(capacity);
    }

    public static ChannelBuffer wrappedBuffer(byte[] array, int offset, int length) {
        if (array == null) {
            throw new NullPointerException("array == null");
        }
        byte[] dest = new byte[length];
        System.arraycopy(array, offset, dest, 0, length);
        return wrappedBuffer(dest);
    }

    public static ChannelBuffer wrappedBuffer(byte[] array) {
        if (array == null) {
            throw new NullPointerException("array == null");
        }
        if (array.length == 0) {
            return EMPTY_BUFFER;
        }
        return new HeapChannelBuffer(array);
    }

    public static ChannelBuffer wrappedBuffer(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            return EMPTY_BUFFER;
        }
        if (buffer.hasArray()) {
            return wrappedBuffer(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        } else {
            return new ByteBufferBackedChannelBuffer(buffer);
        }
    }

    public static ChannelBuffer directBuffer(int capacity) {
        if (capacity == 0) {
            return EMPTY_BUFFER;
        }

        // 借助的实际还是netty的api，ByteBufferBackedChannelBuffer进去
        ChannelBuffer buffer = new ByteBufferBackedChannelBuffer(
                ByteBuffer.allocateDirect(capacity));
        buffer.clear();
        return buffer;
    }

    public static boolean equals(ChannelBuffer bufferA, ChannelBuffer bufferB) {

        final int aLen = bufferA.readableBytes();
        // 可读字节数要相等
        if (aLen != bufferB.readableBytes()) {
            return false;
        }

        final int byteCount = aLen & 7; // 7 = 0111 结果最大为7 ？ 后面for循环只比较7个字节？

        int aIndex = bufferA.readerIndex();
        int bIndex = bufferB.readerIndex();

        for (int i = byteCount; i > 0; i--) {
            // 每个字节都要相等
            if (bufferA.getByte(aIndex) != bufferB.getByte(bIndex)) {
                return false;
            }
            aIndex++;
            bIndex++;
        }

        return true;
    }

    public static int hasCode(ChannelBuffer buffer){
        final int aLen = buffer.readableBytes();
        final int byteCount = aLen & 7;

        int hashCode = 1;
        int arrayIndex = buffer.readerIndex();

        for (int i = byteCount; i > 0; i--) {
            hashCode = 31 * hashCode + buffer.getByte(arrayIndex++);
        }

        if (hashCode == 0) {
            hashCode = 1;
        }

        return hashCode;
    }

    public static int compare(ChannelBuffer bufferA, ChannelBuffer bufferB) {

        final int aLen = bufferA.readableBytes();
        final int bLen = bufferB.readableBytes();
        // 取两个buffer的最小可读字节数
        final int minLength = Math.min(aLen, bLen);

        // 取各自的读指针
        int aIndex = bufferA.readerIndex();
        int bIndex = bufferB.readerIndex();

        // 然后挨个进行字节比较，双指针比较，很easy
        for (int i = minLength; i > 0; i--) {
            byte va = bufferA.getByte(aIndex);
            byte vb = bufferB.getByte(bIndex);
            if (va > vb) {
                return 1;
            } else if (va < vb) {
                return -1;
            }
            // 一轮比较厚，双指针++
            aIndex++;
            bIndex++;
        }

        // 如果前面每个字节都相等，那么根据可读字节长度比较

        return aLen - bLen;
    }

}
