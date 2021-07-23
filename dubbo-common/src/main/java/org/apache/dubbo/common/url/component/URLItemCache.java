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
package org.apache.dubbo.common.url.component;

import org.apache.dubbo.common.utils.LRUCache;

import java.util.Map;

public class URLItemCache {
    // thread safe with limited size, by default 1000
    private static final Map<String, String> PARAM_KEY_CACHE = new LRUCache<>(10000);
    private static final Map<String, String> PARAM_VALUE_CACHE = new LRUCache<>(50000);
    private static final Map<String, String> PATH_CACHE = new LRUCache<>(10000);
    private static final Map<String, String> REVISION_CACHE = new LRUCache<>(10000);

    public static void putParams(Map<String, String> params, String key, String value) {
        String cachedKey = PARAM_KEY_CACHE.get(key);
        if (cachedKey == null) {
            cachedKey = key;
            PARAM_KEY_CACHE.put(key, key);
        }
        String cachedValue = PARAM_VALUE_CACHE.get(value);
        if (cachedValue == null) {
            cachedValue = value;
            PARAM_VALUE_CACHE.put(value, value);
        }

        params.put(cachedKey, cachedValue);
    }

    public static String checkPath(String _path) {
        if (_path == null) {
            return _path;
        }
        String cachedPath = PATH_CACHE.putIfAbsent(_path, _path);
        if (cachedPath != null) {
            return cachedPath;
        }
        return _path;
    }

    public static String checkRevision(String _revision) {
        if (_revision == null) {
            return _revision;
        }
        String revision = REVISION_CACHE.putIfAbsent(_revision, _revision);
        if (revision != null) {
            return revision;
        }
        return _revision;
    }

    public static String intern(String _protocol) {
        if (_protocol == null) {
            return _protocol;
        }
        /**
         * 返回字符串对象的规范表示。
         * <p>
         * 字符串池，最初为空，由类 {@code String} 私下维护。
         * <p>
         * 当调用 intern 方法时，如果池中已经包含一个字符串等于这个 {@code String} 对象，由 {@link #equals(Object)} 方法确定，则返回池中的字符串。否则，此 {@code String} 对象将添加到池中，并返回对此 {@code String} 对象的引用。
         * <p>
         * 对于任意两个字符串 {@code s} 和 {@code t}，{@code s.intern() == t.intern()} 是 {@code true} 当且仅当 {@code s .equals(t)} 是 {@code true}。
         * <p>
         * 所有文字字符串和字符串值常量表达式都是实习生的。字符串文字在 <cite>The Java&trade; 的第 3.10.5 节中定义。语言规范</cite>。
         * @return 与此字符串具有相同内容的字符串，但保证来自唯一字符串池。
         */
        return _protocol.intern();
    }

    public static void putParamsIntern(Map<String, String> params, String key, String value) {
        if (key == null || value == null) {
            params.put(key, value);
            return;
        }
        key = key.intern();
        value = value.intern();
        params.put(key, value);
    }
}
