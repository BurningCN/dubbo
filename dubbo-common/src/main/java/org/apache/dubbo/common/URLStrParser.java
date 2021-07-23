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
package org.apache.dubbo.common;

import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.url.component.ServiceConfigURL;
import org.apache.dubbo.common.url.component.URLItemCache;

import org.eclipse.collections.impl.map.mutable.UnifiedMap;

import java.util.Collections;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY_PREFIX;
import static org.apache.dubbo.common.utils.StringUtils.EMPTY_STRING;
import static org.apache.dubbo.common.utils.StringUtils.decodeHexByte;
import static org.apache.dubbo.common.utils.Utf8Utils.decodeUtf8;

public final class URLStrParser {
    private static final Logger logger = LoggerFactory.getLogger(URLStrParser.class);
    public static final String ENCODED_QUESTION_MARK = "%3F";
    public static final String ENCODED_TIMESTAMP_KEY = "timestamp%3D";
    public static final String ENCODED_PID_KEY = "pid%3D";
    public static final String ENCODED_AND_MARK = "%26";
    private static final char SPACE = 0x20;

    private static final ThreadLocal<TempBuf> DECODE_TEMP_BUF = ThreadLocal.withInitial(() -> new TempBuf(1024));

    private URLStrParser() {
        //empty
    }

    public static URL parseDecodedStr(String decodedURLStr) {
        return parseDecodedStr(decodedURLStr, false);
    }

    /**
     * @param decodedURLStr : after {@link URL#decode} string
     *                      decodedURLStr format: protocol://username:password@host:port/path?k1=v1&k2=v2
     *                      [protocol://][username:password@][host:port]/[path][?k1=v1&k2=v2]
     */
    public static URL parseDecodedStr(String decodedURLStr, boolean modifiable) {
        Map<String, String> parameters = null;
        int pathEndIdx = decodedURLStr.indexOf('?');
        if (pathEndIdx >= 0) {
            parameters = parseDecodedParams(decodedURLStr, pathEndIdx + 1);
        } else {
            pathEndIdx = decodedURLStr.length();
        }

        String decodedBody = decodedURLStr.substring(0, pathEndIdx);
        return parseURLBody(decodedURLStr, decodedBody, parameters, modifiable);
    }

    private static Map<String, String> parseDecodedParams(String str, int from) {
        int len = str.length();
        if (from >= len) {
            return Collections.emptyMap();
        }

        TempBuf tempBuf = DECODE_TEMP_BUF.get();
        Map<String, String> params = new UnifiedMap<>();
        int nameStart = from;
        int valueStart = -1;
        int i;
        for (i = from; i < len; i++) {
            char ch = str.charAt(i);
            switch (ch) {
                case '=':
                    if (nameStart == i) {
                        nameStart = i + 1;
                    } else if (valueStart < nameStart) {
                        valueStart = i + 1;
                    }
                    break;
                case ';':
                case '&':
                    addParam(str, false, nameStart, valueStart, i, params, tempBuf);
                    nameStart = i + 1;
                    break;
                default:
                    // continue
            }
        }
        addParam(str, false, nameStart, valueStart, i, params, tempBuf);
        return params;
    }

    /**
     * @param fullURLStr  : fullURLString
     * @param decodedBody : format: [protocol://][username:password@][host:port]/[path]
     * @param parameters  :
     * @return URL
     */
    private static URL parseURLBody(String fullURLStr, String decodedBody, Map<String, String> parameters, boolean modifiable) {
        int starIdx = 0, endIdx = decodedBody.length();
        // ignore the url content following '#'
        int poundIndex = decodedBody.indexOf('#');
        if (poundIndex != -1) {
            endIdx = poundIndex;
        }

        String protocol = null;
        int protoEndIdx = decodedBody.indexOf("://");
        if (protoEndIdx >= 0) {
            if (protoEndIdx == 0) {
                throw new IllegalStateException("url missing protocol: \"" + fullURLStr + "\"");
            }
            protocol = decodedBody.substring(0, protoEndIdx);
            starIdx = protoEndIdx + 3;
        } else {
            // case: file:/path/to/file.txt  两个分支，体现http网络的url是带有://的，文件url是:/格式的，不过他们的前面都叫做协议protocol
            protoEndIdx = decodedBody.indexOf(":/");
            if (protoEndIdx >= 0) {
                if (protoEndIdx == 0) {
                    throw new IllegalStateException("url missing protocol: \"" + fullURLStr + "\"");
                }
                protocol = decodedBody.substring(0, protoEndIdx);
                starIdx = protoEndIdx + 1;
            }
        }

        String path = null;
        int pathStartIdx = indexOf(decodedBody, '/', starIdx, endIdx);
        if (pathStartIdx >= 0) {
            // path = "path/to/file.txt"
            path = decodedBody.substring(pathStartIdx + 1, endIdx);
            endIdx = pathStartIdx;
        }

        String username = null;
        String password = null;
        int pwdEndIdx = lastIndexOf(decodedBody, '@', starIdx, endIdx);
        if (pwdEndIdx > 0) {
            int passwordStartIdx = indexOf(decodedBody, ':', starIdx, pwdEndIdx);
            if (passwordStartIdx != -1) {//tolerate incomplete user pwd input, like '1234@'
                username = decodedBody.substring(starIdx, passwordStartIdx);
                password = decodedBody.substring(passwordStartIdx + 1, pwdEndIdx);
            } else {
                username = decodedBody.substring(starIdx, pwdEndIdx);
            }
            starIdx = pwdEndIdx + 1;
        }

        String host = null;
        int port = 0;
        int hostEndIdx = lastIndexOf(decodedBody, ':', starIdx, endIdx);
        if (hostEndIdx > 0 && hostEndIdx < decodedBody.length() - 1) {
            if (lastIndexOf(decodedBody, '%', starIdx, endIdx) > hostEndIdx) {
                // ipv6 address with scope id
                // e.g. fe80:0:0:0:894:aeec:f37d:23e1%en0
                // see https://howdoesinternetwork.com/2013/ipv6-zone-id
                // ignore
            } else {
                port = Integer.parseInt(decodedBody.substring(hostEndIdx + 1, endIdx));
                endIdx = hostEndIdx;
            }
        }

        if (endIdx > starIdx) {
            host = decodedBody.substring(starIdx, endIdx);
        }

        // check cache
        protocol = URLItemCache.intern(protocol);
        path = URLItemCache.checkPath(path);

        return new ServiceConfigURL(protocol, username, password, host, port, path, parameters);
    }

    public static URL parseEncodedStr(String encodedURLStr) {
        return parseEncodedStr(encodedURLStr, false);
    }

    public static String[] parseRawURLToArrays(String rawURLStr, int pathEndIdx) {
        String[] parts = new String[2];
        int paramStartIdx = pathEndIdx + 3;//skip ENCODED_QUESTION_MARK
        if (pathEndIdx == -1) {
            pathEndIdx = rawURLStr.indexOf('?');
            if (pathEndIdx == -1) {
                // url with no params, decode anyway
                rawURLStr = URL.decode(rawURLStr);
            } else {
                paramStartIdx = pathEndIdx + 1;
            }
        }
        if (pathEndIdx >= 0) {
            parts[0] = rawURLStr.substring(0, pathEndIdx);
            parts[1] = rawURLStr.substring(paramStartIdx);
        } else {
            parts = new String[]{rawURLStr};
        }
        return parts;
    }

    public static Map<String, String> parseParams(String rawParams, boolean encoded) {
        if (encoded) {
            return parseEncodedParams(rawParams, 0);
        }
        return parseDecodedParams(rawParams, 0);
    }

    /**
     * @param encodedURLStr : after {@link URL#encode(String)} string
     *                      encodedURLStr after decode format: protocol://username:password@host:port/path?k1=v1&k2=v2
     *                      [protocol://][username:password@][host:port]/[path][?k1=v1&k2=v2]
     */
    // file%3A%2Fpath%2Fto%2Ffile.txt
    public static URL parseEncodedStr(String encodedURLStr, boolean modifiable) {
        Map<String, String> parameters = null;
        int pathEndIdx = encodedURLStr.toUpperCase().indexOf("%3F");// '?'
        if (pathEndIdx >= 0) {
            // 处理参数部分
            parameters = parseEncodedParams(encodedURLStr, pathEndIdx + 3);
        } else {
            pathEndIdx = encodedURLStr.length();
        }

        //decodedBody format: [protocol://][username:password@][host:port]/[path]
        String decodedBody = decodeComponent(encodedURLStr, 0, pathEndIdx, false, DECODE_TEMP_BUF.get());
        // 按照前面的案例 此时上面的decodedBody如右边所示  file:/path/to/file.txt
        // 在比如 dubbo%3A%2F%2F192.168.1.1 -> dubbo://192.168.1.1
        return parseURLBody(encodedURLStr, decodedBody, parameters, modifiable);
    }

    // eg dubbo://127.0.0.1?test=中文测试 --->
    // dubbo%3A%2F%2F127.0.0.1%3Ftest%3D%E4%B8%AD%E6%96%87%E6%B5%8B%E8%AF%95
    // from = 26(t --- %3F后面的t)
    private static Map<String, String> parseEncodedParams(String str, int from) {
        int len = str.length();
        if (from >= len) {
            return Collections.emptyMap();
        }

        TempBuf tempBuf = DECODE_TEMP_BUF.get();
        Map<String, String> params = new UnifiedMap<>();
        int nameStart = from;
        int valueStart = -1;
        int i;
        for (i = from; i < len; i++) {
            // 以前面为案例，前四次循环分别扫过test，不做任何处理，当前i指向的字符是%
            //%3D处理后，后面的是中文测试的编码信息，后面整个都是val部分
            char ch = str.charAt(i);
            if (ch == '%') {// %3D
                if (i + 3 > len) {
                    throw new IllegalArgumentException("unterminated escape sequence at index " + i + " of: " + str);
                }
                // i位置字符是%，后面两个3D(hex) = 65(b) = '='
                ch = (char) decodeHexByte(str, i + 1);
                // 跳过3D。准备处理=的右半部分
                i += 2;
            }

            switch (ch) {
                case '=':
                    if (nameStart == i) {
                        nameStart = i + 1;
                    } else if (valueStart < nameStart) {
                        // =的下一个索引位开始就是val的开始部分
                        valueStart = i + 1;
                    }
                    break;
                case ';':
                case '&':
                    addParam(str, true, nameStart, valueStart, i - 2, params, tempBuf);
                    nameStart = i + 1;
                    break;
                default:
                    // continue
            }
        }
        addParam(str, true, nameStart, valueStart, i, params, tempBuf);
        return params;
    }

    private static boolean addParam(String str, boolean isEncoded, int nameStart, int valueStart, int valueEnd, Map<String, String> params,
                                    TempBuf tempBuf) {
        if (nameStart >= valueEnd) {
            return false;
        }

        if (valueStart <= nameStart) {
            valueStart = valueEnd + 1;
        }

        if (isEncoded) {
            // valueStart处于k%3Dv的v起始位置，-3就是截止到k的结尾位置，这样 [nameStart,valueStart - 3)这部分就是k本身的值
            String name = decodeComponent(str, nameStart, valueStart - 3, false, tempBuf);
            String value;
            if (valueStart == valueEnd) {
                value = name;
            } else {
                // 处理value部分
                value = decodeComponent(str, valueStart, valueEnd, false, tempBuf);
            }
            URLItemCache.putParams(params, name, value);
            // compatible with lower versions registering "default." keys
            if (name.startsWith(DEFAULT_KEY_PREFIX)) {
                params.putIfAbsent(name.substring(DEFAULT_KEY_PREFIX.length()), value);
            }
        } else {
            String name = str.substring(nameStart, valueStart - 1);
            String value;
            if (valueStart == valueEnd) {
                value = name;
            } else {
                value = str.substring(valueStart, valueEnd);
            }
            URLItemCache.putParams(params, name, value);
            // compatible with lower versions registering "default." keys
            if (name.startsWith(DEFAULT_KEY_PREFIX)) {
                params.putIfAbsent(name.substring(DEFAULT_KEY_PREFIX.length()), value);
            }
        }
        return true;
    }

    private static String decodeComponent(String s, int from, int toExcluded, boolean isPath, TempBuf tempBuf) {
        int len = toExcluded - from;
        if (len <= 0) {
            return EMPTY_STRING;
        }

        // eg  file%3A%2Fpath%2Fto%2Ffile.txt
        // escaped 转义的
        int firstEscaped = -1;
        for (int i = from; i < toExcluded; i++) {
            char c = s.charAt(i);
            // 找到第一个%或者+就停止  上面的案例 firstEscaped = 4
            if (c == '%' || c == '+' && !isPath) {
                firstEscaped = i;
                break;
            }
        }
        if (firstEscaped == -1) {
            return s.substring(from, toExcluded);
        }

        // Each encoded byte takes 3 characters (e.g. "%20")
        int decodedCapacity = (toExcluded - firstEscaped) / 3;
        byte[] buf = tempBuf.byteBuf(decodedCapacity); // 返回的buf大小要么是1024要么是decodedCapacity
        char[] charBuf = tempBuf.charBuf(len);// 返回的charBuf大小要么是1024要么是len

        // 将s字符串的[from,firstEscaped)内容复制到charBuf中（上面的案例此时charBuf的内容就是"file"）
        // getChars(int srcBegin, int srcEnd, char dst[], int dstBegin)
        s.getChars(from, firstEscaped, charBuf, 0);

        // charBuf用完了0~ firstEscaped - from - 1部分的空间，接着往下走，移动指针
        int charBufIdx = firstEscaped - from;
        return decodeUtf8Component(s, firstEscaped, toExcluded, isPath, buf, charBuf, charBufIdx);
    }

    private static String decodeUtf8Component(String str, int firstEscaped, int toExcluded, boolean isPath, byte[] buf,
                                              char[] charBuf, int charBufIdx) {
        int bufIdx;
        //          file:/path/to/file.txt
        // eg str = file%3A%2Fpath%2Fto%2Ffile.txt 此时 firstEscaped = 4，toExcluded = 30
        for (int i = firstEscaped; i < toExcluded; i++) {
            char c = str.charAt(i);
            if (c != '%') {
                // %3A%2F在上次do-while循环(下面的)完毕后，再次进入这里，此时 path 会依次填充到 charBuf
                charBuf[charBufIdx++] = c != '+' || isPath ? c : SPACE;
                continue;
            }

            // 每次do-while的时候bufIdx重置，其实就是为了重用buf（不过不是clear式的然后赋值，而是直接从bufIdx从0开始直接覆盖上次的旧值）
            bufIdx = 0;
            do {
                if (i + 3 > toExcluded) {
                    throw new IllegalArgumentException("unterminated escape sequence at index " + i + " of: " + str);
                }
                // i+1 就是前面案例%3A的"3"索引位置 ，decodeHexByte方法内部就会处理3A这两个字符
                buf[bufIdx++] = decodeHexByte(str, i + 1);
                i += 3;
                // 按照前面案例，循环接着处理%2F，然后停止（hex27->b47->char / ）此时buf含有 : 和 /代表的字节
            } while (i < toExcluded && str.charAt(i) == '%');
            i--;

            // 几个参数，表示要消费buf的[0,bufIdx)的值 处理并填充到 从"charBufIdx索引位开始的"charBuf容器中
            charBufIdx += decodeUtf8(buf, 0, bufIdx, charBuf, charBufIdx);
            // 第1次来到这里的时候，此时charBuf的内容为"file:/"
            // 第2次来到这里的时候，此时charBuf的内容为"file:/path/"
            // 第3次来到这里的时候，此时charBuf的内容为"file:/path/to/"
            // 第4次来到这里的时候，此时charBuf的内容为"file:/path/to/file.text"
        }
        // 因为charBuf的实际大小最少1024，所以内部并没有填满，只指定有数据的部分即可
        return new String(charBuf, 0, charBufIdx);
    }

    // 函数就是在str中的[from,toExclude)找到ch字符所在的下标
    private static int indexOf(String str, char ch, int from, int toExclude) {
        from = Math.max(from, 0);
        toExclude = Math.min(toExclude, str.length());
        if (from > toExclude) {
            return -1;
        }

        for (int i = from; i < toExclude; i++) {
            if (str.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOf(String str, char ch, int from, int toExclude) {
        from = Math.max(from, 0);
        toExclude = Math.min(toExclude, str.length() - 1);
        if (from > toExclude) {
            return -1;
        }

        for (int i = toExclude; i >= from; i--) {
            if (str.charAt(i) == ch) {
                return i;
            }
        }
        return -1;
    }

    private static final class TempBuf {

        private final char[] chars;

        private final byte[] bytes;

        TempBuf(int bufSize) {
            this.chars = new char[bufSize];
            this.bytes = new byte[bufSize];
        }

        public char[] charBuf(int size) {
            char[] chars = this.chars;
            if (size <= chars.length) {
                return chars;
            }
            return new char[size];
        }

        public byte[] byteBuf(int size) {
            byte[] bytes = this.bytes;
            if (size <= bytes.length) {
                return bytes;
            }
            return new byte[size];
        }
    }
}
