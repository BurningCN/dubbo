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
package org.apache.dubbo.common.url.component.param;

import org.apache.dubbo.common.extension.ExtensionLoader;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Global Param Cache Table
 * Not support method parameters
 * 全局参数缓存表
 * 不支持方法参数
 */
public final class DynamicParamTable {
    private static final List<String> KEYS = new CopyOnWriteArrayList<>();
    private static final List<ParamValue> VALUES = new CopyOnWriteArrayList<>();
    private static final Map<String, Integer> KEY2INDEX = new HashMap<>(64);

    // 禁止实例化
    private DynamicParamTable() {
        throw new IllegalStateException();
    }

    static {
        init();
    }

    public static Integer getKeyIndex(boolean enabled, String key) {
        if (!enabled) {
            return null;
        }
        return KEY2INDEX.get(key);
        //KEY2INDEX = {HashMap@2033}  size = 12
        // "release" -> {Integer@2049} 10
        // "dubbo" -> {Integer@2051} 9
        // "pid" -> {Integer@2053} 4
        // "interface" -> {Integer@2055} 3
        // "threadpool" -> {Integer@2057} 5
        // "path" -> {Integer@2059} 11
        // "group" -> {Integer@2061} 6
        // "anyhost" -> {Integer@2063} 12
        // "side" -> {Integer@2065} 2
        // "version" -> {Integer@2067} 1
        // "metadata-type" -> {Integer@2069} 7
        // "application" -> {Integer@2071} 8
    }

    public static Integer getValueIndex(String key, String value) {
        Integer idx = getKeyIndex(true, key);
        if (idx == null) {
            throw new IllegalArgumentException("Cannot found key in url param:" + key);
        }
        // KEYS和VALUES的索引对称
        ParamValue paramValue = VALUES.get(idx);
        // 注意getIndex其实是getOrCreate
        return paramValue.getIndex(value);
    }

    public static String getKey(int offset) {
        return KEYS.get(offset);
    }

    public static boolean isDefaultValue(String key, String value) {
        return Objects.equals(value, VALUES.get(getKeyIndex(true, key)).defaultVal());
    }

    // 一个key对应一个val，这个val内部有含有多个value
    public static String getValue(int vi, Integer offset) {
        return VALUES.get(vi).getN(offset);
    }

    public static String getDefaultValue(int vi) {
        // the default value stored at index = 0
        return VALUES.get(vi).defaultVal();
    }

    public static void init() {
        List<String> keys = new LinkedList<>();
        List<ParamValue> values = new LinkedList<>();
        Map<String, Integer> key2Index = new HashMap<>(64);
        // 预先填充了一个kv
        keys.add("");
        values.add(new DynamicValues(null));

        ExtensionLoader.getExtensionLoader(DynamicParamSource.class)
                .getSupportedExtensionInstances().forEach(source -> source.init(keys, values));

        for (int i = 0; i < keys.size(); i++) {
            if (!KEYS.contains(keys.get(i))) {
                KEYS.add(keys.get(i));
                VALUES.add(values.get(i));
            }
        }

        for (int i = 0; i < KEYS.size(); i++) {
            if (!KEYS.get(i).isEmpty()) {
                key2Index.put(KEYS.get(i), i);
            }
        }
        // KEYS、VALUES、KEY2INDEX都是先利用一个临时容器(keys、values、key2Index)填充好，然后再写到前三个中，不知道为何？
        KEY2INDEX.putAll(key2Index);
    }
}
