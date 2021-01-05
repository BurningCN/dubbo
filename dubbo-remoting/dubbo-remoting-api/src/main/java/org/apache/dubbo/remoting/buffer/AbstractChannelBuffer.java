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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

// OK
public abstract class AbstractChannelBuffer implements ChannelBuffer {

    // 仅仅存储了读写指针，capacity在不同的子类实现不同，暴露给外界的是capacity()方法

    private int readerIndex;

    private int writerIndex;

    private int markedReaderIndex;

    private int markedWriterIndex;

    // getReaderIndex
    @Override
    public int readerIndex() {
        return readerIndex;
    }

    // setReaderIndex
    @Override
    public void readerIndex(int readerIndex) {
        if (readerIndex < 0 || readerIndex > writerIndex) {
            throw new IndexOutOfBoundsException();
        }
        this.readerIndex = readerIndex;
    }

    @Override
    public int writerIndex() {
        return writerIndex;
    }

    @Override
    public void writerIndex(int writerIndex) {
        if (writerIndex < readerIndex || writerIndex > capacity()) {
            throw new IndexOutOfBoundsException();
        }
        this.writerIndex = writerIndex;
    }

    @Override
    public void setIndex(int readerIndex, int writerIndex) {
        // 这个条件看出了三者的关系
        if (readerIndex < 0 || readerIndex > writerIndex || writerIndex > capacity()) {
            throw new IndexOutOfBoundsException();
        }
        this.readerIndex = readerIndex;
        this.writerIndex = writerIndex;
    }

    @Override
    public void clear() {
        readerIndex = writerIndex = 0;
    }


    @Override
    public boolean readable() {
        // 进去
        return readableBytes() > 0;
    }


    @Override
    public boolean writable() {
        // 进去
        return writableBytes() > 0;
    }

    @Override
    public int readableBytes() {
        return writerIndex - readerIndex;
    }

    @Override
    public int writableBytes() {
        return capacity() - writerIndex;
    }

    @Override
    public void markReaderIndex() {
        markedReaderIndex = readerIndex;
    }

    @Override
    public void resetReaderIndex() {
        readerIndex(markedReaderIndex);
    }

    @Override
    public void markWriterIndex() {
        markedWriterIndex = writerIndex;
    }

    @Override
    public void resetWriterIndex() {
        writerIndex = markedWriterIndex;
    }

    @Override
    public void discardReadBytes() {
        if (readerIndex == 0) {
            return;
        }
        // 进去
        setBytes(0, this, readerIndex, writerIndex - readerIndex);
        writerIndex -= readerIndex;
        markedReaderIndex = Math.max(markedReaderIndex - readerIndex, 0);
        markedWriterIndex = Math.max(markedWriterIndex - readerIndex, 0);
        readerIndex = 0;
    }

    @Override
    public void ensureWritableBytes(int writableBytes) {
        // 默认非动态缓冲区，空间不够抛异常，去看下动态缓冲区重写的
        if (writableBytes > writableBytes()) {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public void getBytes(int index, byte[] dst) {
        // 进去
        getBytes(index, dst, 0, dst.length);
    }

    @Override
    public void getBytes(int index, ChannelBuffer dst) {
        // 进去
        getBytes(index, dst, dst.writableBytes());
    }

    @Override
    public void getBytes(int index, ChannelBuffer dst, int length) {
        if (length > dst.writableBytes()) {
            throw new IndexOutOfBoundsException();
        }
        // 进去
        getBytes(index, dst, dst.writerIndex(), length);
        // 更新dst的写指针
        dst.writerIndex(dst.writerIndex() + length);
    }

    @Override
    public void setBytes(int index, byte[] src) {
        setBytes(index, src, 0, src.length);
    }

    @Override
    public void setBytes(int index, ChannelBuffer src) {
        // 进去
        setBytes(index, src, src.readableBytes());
    }

    @Override
    public void setBytes(int index, ChannelBuffer src, int length) {
        if (length > src.readableBytes()) {
            throw new IndexOutOfBoundsException();
        }
        // 进去
        setBytes(index, src, src.readerIndex(), length);
        src.readerIndex(src.readerIndex() + length);
    }

    @Override
    public byte readByte() {
        if (readerIndex == writerIndex) {
            throw new IndexOutOfBoundsException();
        }
        return getByte(readerIndex++);
    }

    @Override
    public ChannelBuffer readBytes(int length) {
        checkReadableBytes(length);
        if (length == 0) {
            return ChannelBuffers.EMPTY_BUFFER;
        }
        ChannelBuffer buf = factory().getBuffer(length);
        buf.writeBytes(this, readerIndex, length);
        readerIndex += length;
        return buf;
    }

    @Override
    public void readBytes(byte[] dst, int dstIndex, int length) {
        checkReadableBytes(length);
        getBytes(readerIndex, dst, dstIndex, length);
        readerIndex += length;
    }

    @Override
    public void readBytes(byte[] dst) {
        readBytes(dst, 0, dst.length);
    }

    @Override
    public void readBytes(ChannelBuffer dst) {
        readBytes(dst, dst.writableBytes());
    }

    @Override
    public void readBytes(ChannelBuffer dst, int length) {
        if (length > dst.writableBytes()) {
            throw new IndexOutOfBoundsException();
        }
        readBytes(dst, dst.writerIndex(), length);
        dst.writerIndex(dst.writerIndex() + length);
    }

    @Override
    public void readBytes(ChannelBuffer dst, int dstIndex, int length) {
        checkReadableBytes(length);
        getBytes(readerIndex, dst, dstIndex, length);
        readerIndex += length;
    }

    @Override
    public void readBytes(ByteBuffer dst) {
        int length = dst.remaining();
        checkReadableBytes(length);
        getBytes(readerIndex, dst);
        readerIndex += length;
    }

    @Override
    public void readBytes(OutputStream out, int length) throws IOException {
        checkReadableBytes(length);
        getBytes(readerIndex, out, length);
        readerIndex += length;
    }

    @Override
    public void skipBytes(int length) {
        int newReaderIndex = readerIndex + length;
        if (newReaderIndex > writerIndex) {
            throw new IndexOutOfBoundsException();
        }
        readerIndex = newReaderIndex;
    }

    @Override
    public void writeByte(int value) {
        setByte(writerIndex++, value);
    }

    @Override
    public void writeBytes(byte[] src, int srcIndex, int length) {
        // 将字节数组的内容写到buf，同时写指针++。进去
        setBytes(writerIndex, src, srcIndex, length);

        writerIndex += length;
    }

    @Override
    public void writeBytes(byte[] src) {
        writeBytes(src, 0, src.length);
    }

    @Override
    public void writeBytes(ChannelBuffer src) {
        writeBytes(src, src.readableBytes());
    }

    @Override
    public void writeBytes(ChannelBuffer src, int length) {
        if (length > src.readableBytes()) {
            throw new IndexOutOfBoundsException();
        }
        writeBytes(src, src.readerIndex(), length);
        src.readerIndex(src.readerIndex() + length);
    }

    @Override
    public void writeBytes(ChannelBuffer src, int srcIndex, int length) {
        setBytes(writerIndex, src, srcIndex, length);
        writerIndex += length;
    }

    @Override
    public void writeBytes(ByteBuffer src) {
        int length = src.remaining();
        setBytes(writerIndex, src);
        writerIndex += length;
    }

    @Override
    public int writeBytes(InputStream in, int length) throws IOException {
        int writtenBytes = setBytes(writerIndex, in, length);
        if (writtenBytes > 0) {
            writerIndex += writtenBytes;
        }
        return writtenBytes;
    }

    @Override
    public ChannelBuffer copy() {
        // 生成自身的一个拷贝
        return copy(readerIndex, readableBytes());
    }

    @Override
    public ByteBuffer toByteBuffer() {
        // ChannelBuffer转化为ByteBuffer，进去
        return toByteBuffer(readerIndex, readableBytes());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChannelBuffer
                // 进去
                && ChannelBuffers.equals(this, (ChannelBuffer) o);
    }

    @Override
    public int hashCode() {
        return ChannelBuffers.hasCode(this);
    }

    @Override
    public int compareTo(ChannelBuffer that) {
        // 类似Integr.compare等方法，不少都是这种命名方式（或者str1.compareTo(str2)），进去
        return ChannelBuffers.compare(this, that);
    }

    @Override
    public String toString() {
        // 三个重要指针输出下即可
        return getClass().getSimpleName() + '(' +
                "ridx=" + readerIndex + ", " +
                "widx=" + writerIndex + ", " +
                "cap=" + capacity() +
                ')';
    }

    protected void checkReadableBytes(int minimumReadableBytes) {
        if (readableBytes() < minimumReadableBytes) {
            throw new IndexOutOfBoundsException();
        }
    }
}
