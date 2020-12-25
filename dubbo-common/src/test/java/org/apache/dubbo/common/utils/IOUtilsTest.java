/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dubbo.common.utils;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

// OK
public class IOUtilsTest {

    private static String TEXT = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890想";
    private InputStream is;
    private OutputStream os;
    private Reader reader;
    private Writer writer;

    @BeforeEach
    public void setUp() throws Exception {
        // 字节流
        is = new ByteArrayInputStream(TEXT.getBytes(StandardCharsets.UTF_8));
        os = new ByteArrayOutputStream();
        // 字符流
        reader = new StringReader(TEXT);
        writer = new StringWriter();
    }

    @AfterEach
    public void tearDown() throws Exception {
        is.close();
        os.close();
        reader.close();
        writer.close();
    }

    @Test
    public void testWrite1() throws Exception {
        // 字节流的数据拷贝，返回拷贝的字节数，和TEXT.length()等，一个字符占多少个字节取决于什么编码方式，比如上面的TEXT.getBytes(StandardCharsets.UTF_8)
        // UTF-8就是一个数字、英文字符占一个字节，一个汉字两个字节。write方法进去
        assertThat((int) IOUtils.write(is, os, 16), equalTo(TEXT.length()));
    }

    @Test
    public void testWrite2() throws Exception {
        // 字符流的数据拷贝，进去
        assertThat((int) IOUtils.write(reader, writer, 16), equalTo(TEXT.length()));
    }

    @Test
    public void testWrite3() throws Exception {
        // 字符串写到字符输出流，进去
        assertThat((int) IOUtils.write(writer, TEXT), equalTo(TEXT.length()));
    }

    @Test
    public void testWrite4() throws Exception {
        // 和前面testWrite1区别就是少了缓冲区大小参数，内部会用默认的缓冲区大小8K
        assertThat((int) IOUtils.write(is, os), equalTo(TEXT.length()));
    }

    @Test
    public void testWrite5() throws Exception {
        // 和前面一样，不带缓冲区大小参数
        assertThat((int) IOUtils.write(reader, writer), equalTo(TEXT.length()));
    }

    @Test
    public void testLines(@TempDir Path tmpDir) throws Exception {
        File file = tmpDir.getFileName().toAbsolutePath().toFile();
        // 字符串数组写到文件，进去
        IOUtils.writeLines(file, new String[]{TEXT});
        // 进去
        String[] lines = IOUtils.readLines(file);
        assertThat(lines.length, equalTo(1));
        assertThat(lines[0], equalTo(TEXT));
        tmpDir.getFileName().toAbsolutePath().toFile().delete();
    }

    @Test
    public void testReadLines() throws Exception {
        String[] lines = IOUtils.readLines(is);
        assertThat(lines.length, equalTo(1));
        assertThat(lines[0], equalTo(TEXT));
    }

    @Test
    public void testWriteLines() throws Exception {
        IOUtils.writeLines(os, new String[]{TEXT});
        ByteArrayOutputStream bos = (ByteArrayOutputStream) os;
        // toByteArray api
        assertThat(new String(bos.toByteArray()), equalTo(TEXT + System.lineSeparator()));
    }

    @Test
    public void testRead() throws Exception {
        assertThat(IOUtils.read(reader), equalTo(TEXT));
    }

    @Test
    public void testAppendLines(@TempDir Path tmpDir) throws Exception {
        File file = tmpDir.getFileName().toAbsolutePath().toFile();
        IOUtils.appendLines(file, new String[]{"a", "b", "c"});
        String[] lines = IOUtils.readLines(file);
        assertThat(lines.length, equalTo(3));
        assertThat(lines[0], equalTo("a"));
        assertThat(lines[1], equalTo("b"));
        assertThat(lines[2], equalTo("c"));
        tmpDir.getFileName().toAbsolutePath().toFile().delete();
    }
}
