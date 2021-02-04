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
package my.common.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Miscellaneous io utility methods.
 * Mainly for internal use within the framework.
 *
 * @author william.liangf
 * @since 2.0.7
 */
// miscellaneous [ˌmɪsəˈleɪniəs] adj. 混杂的，各种各样的；多方面的，多才多艺的
// OK
public class IOUtils {
    private static final int BUFFER_SIZE = 1024 * 8;
    public static final int EOF = -1;

    private IOUtils() {
    }

    /**
     * write.
     *
     * @param is InputStream instance.
     * @param os OutputStream instance.
     * @return count.
     * @throws IOException If an I/O error occurs
     */
    public static long write(InputStream is, OutputStream os) throws IOException {
        // 进去
        return write(is, os, BUFFER_SIZE);
    }

    /**
     * write.
     *
     * @param is         InputStream instance.
     * @param os         OutputStream instance.
     * @param bufferSize buffer size.
     * @return count.
     * @throws IOException If an I/O error occurs
     */
    public static long write(InputStream is, OutputStream os, int bufferSize) throws IOException {
        byte[] buff = new byte[bufferSize];
        // 进去
        return write(is, os, buff);
    }

    /**
     * write.
     *
     * @param input  InputStream instance.
     * @param output OutputStream instance.
     * @param buffer buffer byte array
     * @return count.
     * @throws IOException If an I/O error occurs
     */
    public static long write(final InputStream input, final OutputStream output, final byte[] buffer) throws IOException {
        long count = 0;
        int n;
        // EOF为-1
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            // 统计写入的字符
            count += n;
        }
        return count;
    }

    /**
     * read string.
     *
     * @param reader Reader instance.
     * @return String.
     * @throws IOException If an I/O error occurs
     */
    public static String read(Reader reader) throws IOException {
        // try-with-resource语法
        try (StringWriter writer = new StringWriter()) {
            // 一次写入（两个都是字符流），进去
            write(reader, writer);
            // 获取完全的字符串内容
            return writer.getBuffer().toString();
        }
    }

    /**
     * write string.
     *
     * @param writer Writer instance.
     * @param string String.
     * @throws IOException If an I/O error occurs
     */
    public static long write(Writer writer, String string) throws IOException {
        try (Reader reader = new StringReader(string)) {
            return write(reader, writer);
        }
    }

    /**
     * write.
     *
     * @param reader Reader.
     * @param writer Writer.
     * @return count.
     * @throws IOException If an I/O error occurs
     */
    public static long write(Reader reader, Writer writer) throws IOException {
        return write(reader, writer, BUFFER_SIZE);
    }

    /**
     * write.
     *
     * @param reader     Reader.
     * @param writer     Writer.
     * @param bufferSize buffer size.
     * @return count.
     * @throws IOException If an I/O error occurs
     */
    public static long write(Reader reader, Writer writer, int bufferSize) throws IOException {
        int read;
        long total = 0;
        // 字符数组-->字节流的缓冲拷贝用的字节数组
        char[] buf = new char[bufferSize];
        while ((read = reader.read(buf)) != -1) {
            writer.write(buf, 0, read);
            total += read;
        }
        return total;
    }

    /**
     * read lines.
     *
     * @param file file.
     * @return lines.
     * @throws IOException If an I/O error occurs
     */
    public static String[] readLines(File file) throws IOException {
        if (file == null || !file.exists() || !file.canRead()) {
            return new String[0];
        }

        // 进去
        return readLines(new FileInputStream(file));
    }

    /**
     * read lines.
     *
     * @param is input stream.
     * @return lines.
     * @throws IOException If an I/O error occurs
     */
    public static String[] readLines(InputStream is) throws IOException {
        List<String> lines = new ArrayList<String>();
        // 转换流
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            // 读一行
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            // list->array之间的互转看下面main函数，如果不加new String[] 会返回 Object[]
            return lines.toArray(new String[0]);
        }
    }

    public static void main(String[] args) {
        List<String> list = new ArrayList<>();
        list.add("1");list.add("2");list.add("3");
        String[] strings = list.toArray(new String[0]);
        List<String> strings1 = Arrays.asList(strings);
    }

    /**
     * write lines.
     *
     * @param os    output stream.
     * @param lines lines.
     * @throws IOException If an I/O error occurs
     */
    public static void writeLines(OutputStream os, String[] lines) throws IOException {
        // 转换流
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(os))) {
            for (String line : lines) {
                // println写一行后换行
                writer.println(line);
            }
            // 打印输出流一般都需要flush
            writer.flush();
        }
    }

    /**
     * write lines.
     *
     * @param file  file.
     * @param lines lines.
     * @throws IOException If an I/O error occurs
     */
    public static void writeLines(File file, String[] lines) throws IOException {
        if (file == null) {
            throw new IOException("File is null.");
        }
        // 进去
        writeLines(new FileOutputStream(file), lines);
    }

    /**
     * append lines.
     *
     * @param file  file.
     * @param lines lines.
     * @throws IOException If an I/O error occurs
     */
    public static void appendLines(File file, String[] lines) throws IOException {
        if (file == null) {
            throw new IOException("File is null.");
        }
        writeLines(new FileOutputStream(file, true), lines);
    }

}