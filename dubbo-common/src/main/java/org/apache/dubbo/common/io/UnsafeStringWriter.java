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
package org.apache.dubbo.common.io;

import java.io.IOException;
import java.io.Writer;

/**
 * Thread-unsafe StringWriter.
 */
public class UnsafeStringWriter extends Writer {
    // 都委托给这个了
    private StringBuilder mBuffer;

    public UnsafeStringWriter() {
        // 注意这里的lock 是Writer类的属性，还有这种连等的写法
        lock = mBuffer = new StringBuilder();
    }

    public UnsafeStringWriter(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative buffer size");
        }

        lock = mBuffer = new StringBuilder();
    }

    @Override
    public void write(int c) {
        mBuffer.append((char) c);
    }

    @Override
    public void write(char[] cs) throws IOException {
        mBuffer.append(cs, 0, cs.length);
    }

    // 这个是必须重写的（调用的相关方法可以传入字符串、字符数组、字符序列）
    @Override
    public void write(char[] cs, int off, int len) throws IOException {
        if ((off < 0) || (off > cs.length) || (len < 0) ||
                ((off + len) > cs.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        }

        if (len > 0) {
            mBuffer.append(cs, off, len);
        }
    }

    @Override
    public void write(String str) {
        mBuffer.append(str);
    }

    @Override
    public void write(String str, int off, int len) {
        mBuffer.append(str, off, off + len);
    }

    // write的重载方法传入的都是字符串，append的重载...都是字符序列CharSequence
    // 且后者实际调用的还是write方法
    @Override
    public Writer append(CharSequence csq) {
        if (csq == null) {
            // 调用write方法
            write("null");
        } else {
            write(csq.toString());
        }
        return this;
    }

    @Override
    public Writer append(CharSequence csq, int start, int end) {
        // 保存"null"
        CharSequence cs = (csq == null ? "null" : csq);
        write(cs.subSequence(start, end).toString());
        return this;
    }

    @Override
    public Writer append(char c) {
        mBuffer.append(c);
        return this;
    }

    @Override
    public void close() {
    }

    @Override
    public void flush() {
    }

    @Override
    public String toString() {
        return mBuffer.toString();
    }
}