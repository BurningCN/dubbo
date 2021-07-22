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

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.URLStrParser;
import org.apache.dubbo.common.url.component.param.DynamicParamTable;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.StringUtils;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_KEY_PREFIX;
import static org.apache.dubbo.common.constants.CommonConstants.METHODS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMESTAMP_KEY;

/**
 * A class which store parameters for {@link URL}
 * <br/>
 * Using {@link DynamicParamTable} to compress common keys (i.e. side, version)
 * <br/>
 * {@link DynamicParamTable} allow to use only two integer value named `key` and
 * `value-offset` to find a unique string to string key-pair. Also, `value-offset`
 * is not required if the real value is the default value.
 * <br/>
 * URLParam should operate as Copy-On-Write, each modify actions will return a new Object
 * <br/>
 * <p>
 * NOTE: URLParam is not support serialization! {@link DynamicParamTable} is related with
 * current running environment. If you want to make URL as a parameter, please call
 * {@link URL#toSerializableURL()} to create {@link URLPlainParam} instead.
 *
 * @since 3.0
 * <p>
 * 一个存储 {@link URL} 参数的类
 * 使用 {@link DynamicParamTable} 压缩常用键（即 side、version）
 * {@link DynamicParamTable} 只允许使用两个名为 `key` 和 `value-offset` 的整数值来找到唯一的字符串到字符串键对（就是后面的KEY和VALUE属性）。
 * 此外，如果实际值是默认值，则不需要 `value-offset`。
 * URLParam 应该作为 Copy-On-Write 操作，每次修改操作都会返回一个新对象
 * 注意：URLParam 不支持序列化！ {@link DynamicParamTable} 与当前运行环境有关。 如果要将 URL 作为参数，请调用 {@link URL#toSerializableURL()} 来创建 {@link URLPlainParam}。
 */
public class URLParam {

    /**
     * Maximum size of key-pairs requested using array moving to add into URLParam.
     * If user request like addParameter for only one key-pair, adding value into a array
     * on moving is more efficient. However when add more than ADD_PARAMETER_ON_MOVE_THRESHOLD
     * size of key-pairs, recover compressed array back to map can reduce operation count
     * when putting objects.
     * 使用数组移动请求的密钥对的最大大小以添加到 URLParam。如果用户像 addParameter 这样只请求一个密钥对，
     * 则在移动时向数组添加值会更有效。 但是，
     * 当添加超过 ADD_PARAMETER_ON_MOVE_THRESHOLD 大小的密钥对时，将压缩的数组恢复回映射可以减少放置对象时的操作次数。
     */
    private static final int ADD_PARAMETER_ON_MOVE_THRESHOLD = 1;

    /**
     * the original parameters string, empty if parameters have been modified or init by {@link Map}
     * 原始参数字符串，如果参数已被 {@link Map} 修改或初始化，则为空
     */
    private final String rawParam;

    /**
     * using bit to save if index exist even if value is default value
     * 即使值是默认值，也使用位来保存索引是否存在
     */
    private final BitSet KEY;

    /**
     * using bit to save if value is default value (reduce VALUE size)
     * 如果值为默认值，则使用位保存（减少 VALUE 大小）
     */
    private final BitSet DEFAULT_KEY;

    /**
     * a compressed (those key not exist or value is default value are bring removed in this array) array which contains value-offset
     * 包含值偏移量的压缩（那些键不存在或值是默认值在此数组中删除）数组
     */
    private final Integer[] VALUE;

    /**
     * store extra parameters which key not match in {@link DynamicParamTable}
     * 在 {@link DynamicParamTable} 中存储键不匹配的额外参数
     */
    private final Map<String, String> EXTRA_PARAMS;

    /**
     * store method related parameters
     * <p>
     * K - key
     * V -
     * K - method
     * V - value
     * <p>
     * e.g. method1.mock=true => ( mock, (method1, true) )
     */
    private final Map<String, Map<String, String>> METHOD_PARAMETERS;

    private transient long timestamp;

    /**
     * Whether to enable DynamicParamTable compression
     * 是否开启 DynamicParamTable 压缩
     */
    protected boolean enableCompressed;

    private final static URLParam EMPTY_PARAM = new URLParam(new BitSet(0), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), "");

    protected URLParam() {
        this.rawParam = null;
        this.KEY = null;
        this.DEFAULT_KEY = null;
        this.VALUE = null;
        this.EXTRA_PARAMS = null;
        this.METHOD_PARAMETERS = null;
        this.enableCompressed = true;
    }

    protected URLParam(BitSet key, Map<Integer, Integer> value, Map<String, String> extraParams, Map<String, Map<String, String>> methodParameters, String rawParam) {
        this.KEY = key;
        this.DEFAULT_KEY = new BitSet(KEY.size());
        this.VALUE = new Integer[value.size()];

        // compress VALUE
        // nextSetBit(0)表示从0开始，找第一个bit位被设置为1的索引
        // 参数key包含了在DynamicTable中存在的k的散列/映射，但参数value这个map并不是有所有的kv（他只存储那些key的值不是默认值的key），默认值的那些k就存在DEFAULT_KEY
        // （1）假设有一个version:1参数，那么此时参数key = {1},value = {1,1}，此时下面一段操作后VALUE[0] = 1;
        // （2）再比如假设有两个参数 {side:consumer,version:1}，此时参数key={1,2},value={1,1}，如下操作之后，VALUE[0]=1,DEFAULT_KEY = {2}
        for (int i = key.nextSetBit(0), offset = 0; i >= 0; i = key.nextSetBit(i + 1)) {
            if (value.containsKey(i)) {
                // 注意这里的值是 paramValue里具体值的下标（即ParamValue#getIndex的值）。
                VALUE[offset++] = value.get(i);
            } else {
                DEFAULT_KEY.set(i);
            }
        }

        this.EXTRA_PARAMS = Collections.unmodifiableMap((extraParams == null ? new HashMap<>() : new HashMap<>(extraParams)));
        this.METHOD_PARAMETERS = Collections.unmodifiableMap((methodParameters == null) ? Collections.emptyMap() : new LinkedHashMap<>(methodParameters));
        this.rawParam = rawParam;

        this.timestamp = System.currentTimeMillis();
        this.enableCompressed = true;
    }

    protected URLParam(BitSet key, BitSet defaultKey, Integer[] value, Map<String, String> extraParams, Map<String, Map<String, String>> methodParameters, String rawParam) {
        this.KEY = key;
        this.DEFAULT_KEY = defaultKey;

        this.VALUE = value;

        this.EXTRA_PARAMS = Collections.unmodifiableMap((extraParams == null ? new HashMap<>() : new HashMap<>(extraParams)));
        this.METHOD_PARAMETERS = Collections.unmodifiableMap((methodParameters == null) ? Collections.emptyMap() : new LinkedHashMap<>(methodParameters));
        this.rawParam = rawParam;

        this.timestamp = System.currentTimeMillis();
        this.enableCompressed = true;
    }

    /**
     * Weather there contains some parameter match method
     *
     * @param method method name
     * @return contains or not
     */
    public boolean hasMethodParameter(String method) {
        if (method == null) {
            return false;
        }

        String methodsString = getParameter(METHODS_KEY);
        if (StringUtils.isNotEmpty(methodsString)) {
            if (!methodsString.contains(method)) {
                return false;
            }
        }

        for (Map.Entry<String, Map<String, String>> methods : METHOD_PARAMETERS.entrySet()) {
            if (methods.getValue().containsKey(method)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get method related parameter. If not contains, use getParameter(key) instead.
     * Specially, in some situation like `method1.1.callback=true`, key is `1.callback`.
     *
     * @param method method name
     * @param key    key
     * @return value
     */
    public String getMethodParameter(String method, String key) {
        String strictResult = getMethodParameterStrict(method, key);
        return StringUtils.isNotEmpty(strictResult) ? strictResult : getParameter(key);
    }

    /**
     * Get method related parameter. If not contains, return null.
     * Specially, in some situation like `method1.1.callback=true`, key is `1.callback`.
     *
     * @param method method name
     * @param key    key
     * @return value
     */
    public String getMethodParameterStrict(String method, String key) {
        String methodsString = getParameter(METHODS_KEY);
        if (StringUtils.isNotEmpty(methodsString)) {
            if (!methodsString.contains(method)) {
                return null;
            }
        }

        Map<String, String> methodMap = METHOD_PARAMETERS.get(key);
        if (CollectionUtils.isNotEmptyMap(methodMap)) {
            return methodMap.get(method);
        } else {
            return null;
        }
    }

    public static Map<String, Map<String, String>> initMethodParameters(Map<String, String> parameters) {
        Map<String, Map<String, String>> methodParameters = new HashMap<>();
        if (parameters == null) {
            return methodParameters;
        }

        String methodsString = parameters.get(METHODS_KEY);
        if (StringUtils.isNotEmpty(methodsString)) {
            String[] methods = methodsString.split(",");
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String key = entry.getKey();
                for (String method : methods) {
                    String methodPrefix = method + '.';
                    if (key.startsWith(methodPrefix)) {
                        String realKey = key.substring(methodPrefix.length());
                        URL.putMethodParameter(method, realKey, entry.getValue(), methodParameters);
                    }
                }
            }
        } else {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String key = entry.getKey();
                int methodSeparator = key.indexOf('.');
                if (methodSeparator > 0) {
                    String method = key.substring(0, methodSeparator);
                    String realKey = key.substring(methodSeparator + 1);
                    URL.putMethodParameter(method, realKey, entry.getValue(), methodParameters);
                }
            }
        }
        return methodParameters;
    }

    /**
     * An embedded Map adapt to URLParam
     * <br/>
     * copy-on-write mode, urlParam reference will be changed after modify actions.
     * If wishes to get the result after modify, please use {@link URLParamMap#getUrlParam()}
     *
     * copy-on-write 模式，urlParam 引用将在修改操作后更改。
     * 如果希望得到修改后的结果，请使用{@link URLParamMap#getUrlParam()}
     */
    public static class URLParamMap implements Map<String, String> {
        private URLParam urlParam;

        public URLParamMap(URLParam urlParam) {
            this.urlParam = urlParam;
        }

        public static class Node implements Map.Entry<String, String> {
            private final String key;
            private String value;

            public Node(String key, String value) {
                this.key = key;
                this.value = value;
            }

            @Override
            public String getKey() {
                return key;
            }

            @Override
            public String getValue() {
                return value;
            }

            @Override
            public String setValue(String value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                Node node = (Node) o;
                return Objects.equals(key, node.key) && Objects.equals(value, node.value);
            }

            @Override
            public int hashCode() {
                return Objects.hash(key, value);
            }
        }

        @Override
        public int size() {
            // 注意这里反应的URLParam实际参数的组成/总和（KEY+EXTRA_PARAMS）
            return urlParam.KEY.cardinality() + urlParam.EXTRA_PARAMS.size();
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public boolean containsKey(Object key) {
            if (key instanceof String) {
                return urlParam.hasParameter((String) key);
            } else {
                return false;
            }
        }

        @Override
        public boolean containsValue(Object value) {
            return values().contains(value);
        }

        @Override
        public String get(Object key) {
            if (key instanceof String) {
                return urlParam.getParameter((String) key);
            } else {
                return null;
            }
        }

        @Override
        public String put(String key, String value) {
            String previous = urlParam.getParameter(key);
            urlParam = urlParam.addParameter(key, value);
            return previous;
        }

        @Override
        public String remove(Object key) {
            if (key instanceof String) {
                String previous = urlParam.getParameter((String) key);
                urlParam = urlParam.removeParameters((String) key);
                return previous;
            } else {
                return null;
            }
        }

        @Override
        public void putAll(Map<? extends String, ? extends String> m) {
            urlParam = urlParam.addParameters((Map<String, String>) m);
        }

        @Override
        public void clear() {
            urlParam = urlParam.clearParameters();
        }

        // 下面三个方法，keySet、values、entrySet的逻辑结构基本是一致的，都是操作 urlParam.EXTRA_PARAMS 和  urlParam.KEY/VALUE/DEFAULT_KEY
        @Override
        public Set<String> keySet() {
            Set<String> set = new LinkedHashSet<>((int) ((urlParam.VALUE.length + urlParam.EXTRA_PARAMS.size()) / 0.75) + 1);
            for (int i = urlParam.KEY.nextSetBit(0); i >= 0; i = urlParam.KEY.nextSetBit(i + 1)) {
                set.add(DynamicParamTable.getKey(i));
            }
            for (Entry<String, String> entry : urlParam.EXTRA_PARAMS.entrySet()) {
                set.add(entry.getKey());
            }
            return Collections.unmodifiableSet(set);
        }

        @Override
        public Collection<String> values() {
            Set<String> set = new LinkedHashSet<>((int) ((urlParam.VALUE.length + urlParam.EXTRA_PARAMS.size()) / 0.75) + 1);
            for (int i = urlParam.KEY.nextSetBit(0); i >= 0; i = urlParam.KEY.nextSetBit(i + 1)) {
                String value;
                if (urlParam.DEFAULT_KEY.get(i)) {
                    value = DynamicParamTable.getDefaultValue(i);
                } else {
                    Integer offset = urlParam.keyIndexToOffset(i);
                    value = DynamicParamTable.getValue(i, offset);
                }
                set.add(value);
            }

            for (Entry<String, String> entry : urlParam.EXTRA_PARAMS.entrySet()) {
                set.add(entry.getValue());
            }
            return Collections.unmodifiableSet(set);
        }

        @Override
        public Set<Entry<String, String>> entrySet() {
            int capacity = (int) ((urlParam.KEY.cardinality() + urlParam.EXTRA_PARAMS.size()) / 0.75) + 1;
            // 记录元素的插入顺序
            Set<Entry<String, String>> set = new LinkedHashSet<>(capacity);
            for (int i = urlParam.KEY.nextSetBit(0); i >= 0; i = urlParam.KEY.nextSetBit(i + 1)) {
                String value;
                if (urlParam.DEFAULT_KEY.get(i)) {
                    value = DynamicParamTable.getDefaultValue(i);
                } else {
                    Integer offset = urlParam.keyIndexToOffset(i);
                    value = DynamicParamTable.getValue(i, offset);
                }
                set.add(new Node(DynamicParamTable.getKey(i), value));
            }

            for (Entry<String, String> entry : urlParam.EXTRA_PARAMS.entrySet()) {
                set.add(new Node(entry.getKey(), entry.getValue()));
            }
            return Collections.unmodifiableSet(set);
        }

        public URLParam getUrlParam() {
            return urlParam;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            URLParamMap that = (URLParamMap) o;
            return Objects.equals(urlParam, that.urlParam);
        }

        @Override
        public int hashCode() {
            return Objects.hash(urlParam);
        }
    }

    /**
     * Get a Map like URLParam
     *
     * @return a {@link URLParamMap} adapt to URLParam
     */
    public Map<String, String> getParameters() {
        return new URLParamMap(this);
    }

    /**
     * Get any method related parameter which match key
     * 获取与key匹配的任何方法相关参数
     *
     * @param key key
     * @return result ( if any, random choose one )
     */
    public String getAnyMethodParameter(String key) {
        // key就是方法名称
        Map<String, String> methodMap = METHOD_PARAMETERS.get(key);
        if (CollectionUtils.isNotEmptyMap(methodMap)) {
            // 获取methods参数值
            String methods = getParameter(METHODS_KEY);
            if (StringUtils.isNotEmpty(methods)) {
                for (String method : methods.split(",")) {
                    String value = methodMap.get(method);
                    if (StringUtils.isNotEmpty(value)) {
                        return value;
                    }
                }
            } else {
                return methodMap.values().iterator().next();
            }
        }
        return null;
    }

    /**
     * Add parameters to a new URLParam.
     *
     * @param key   key
     * @param value value
     * @return A new URLParam
     */
    public URLParam addParameter(String key, String value) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
            return this;
        }
        // Collections.singletonMap(key, value) 这种用法注意
        return addParameters(Collections.singletonMap(key, value));
    }

    /**
     * Add absent parameters to a new URLParam.
     *
     * @param key   key
     * @param value value
     * @return A new URLParam
     */
    public URLParam addParameterIfAbsent(String key, String value) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(value)) {
            return this;
        }
        if (hasParameter(key)) {
            return this;
        }
        return addParametersIfAbsent(Collections.singletonMap(key, value));
    }

    /**
     * Add parameters to a new URLParam.
     * If key-pair is present, this will cover it.
     *
     * @param parameters parameters in key-value pairs
     * @return A new URLParam
     */
    public URLParam addParameters(Map<String, String> parameters) {
        if (CollectionUtils.isEmptyMap(parameters)) {
            return this;
        }

        // 技巧点hasAndEqual
        boolean hasAndEqual = true;
        Map<String, String> urlParamMap = getParameters();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String value = urlParamMap.get(entry.getKey());
            if (value == null) {
                if (entry.getValue() != null) {
                    hasAndEqual = false;
                    break;
                }
            } else {
                if (!value.equals(entry.getValue())) {
                    hasAndEqual = false;
                    break;
                }
            }
        }
        // return immediately if there's no change
        if (hasAndEqual) {
            return this;
        }

        // false 参数表示如果存在则覆盖
        return doAddParameters(parameters, false);
    }

    /**
     * Add absent parameters to a new URLParam.
     *
     * @param parameters parameters in key-value pairs
     * @return A new URL
     */
    public URLParam addParametersIfAbsent(Map<String, String> parameters) {
        if (CollectionUtils.isEmptyMap(parameters)) {
            return this;
        }

        return doAddParameters(parameters, true);
    }

    private URLParam doAddParameters(Map<String, String> parameters, boolean skipIfPresent) {
        // lazy init, null if no modify
        BitSet newKey = null;
        BitSet defaultKey = null;
        Integer[] newValueArray = null;
        Map<Integer, Integer> newValueMap = null;
        Map<String, String> newExtraParams = null;
        Map<String, Map<String, String>> newMethodParams = null;
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (skipIfPresent && hasParameter(entry.getKey())) {
                continue;
            }
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            Integer keyIndex = DynamicParamTable.getKeyIndex(enableCompressed, entry.getKey());
            if (keyIndex == null) {
                // entry key is not present in DynamicParamTable, add it to EXTRA_PARAMS
                if (newExtraParams == null) {
                    newExtraParams = new HashMap<>(EXTRA_PARAMS);
                }
                newExtraParams.put(entry.getKey(), entry.getValue());
                String[] methodSplit = entry.getKey().split("\\.");
                if (methodSplit.length == 2) {
                    if (newMethodParams == null) {
                        newMethodParams = new HashMap<>(METHOD_PARAMETERS);
                    }
                    Map<String, String> methodMap = newMethodParams.computeIfAbsent(methodSplit[1], (k) -> new HashMap<>());
                    methodMap.put(methodSplit[0], entry.getValue());
                }
            } else {
                // todo 下面这段逻辑待理解
                if (KEY.get(keyIndex)) {
                    // contains key, replace value
                    if (parameters.size() > ADD_PARAMETER_ON_MOVE_THRESHOLD) {
                        // recover VALUE back to Map, use map to replace key pair
                        if (newValueMap == null) {
                            newValueMap = recoverCompressedValue();
                        }
                        newValueMap.put(keyIndex, DynamicParamTable.getValueIndex(entry.getKey(), entry.getValue()));
                    } else if (!DynamicParamTable.isDefaultValue(entry.getKey(), entry.getValue())) {
                        // new value is not the default key
                        if (DEFAULT_KEY.get(keyIndex)) {
                            // old value is the default value
                            // value is default value, add to defaultKey directly
                            if (defaultKey == null) {
                                defaultKey = (BitSet) DEFAULT_KEY.clone();
                            }
                            defaultKey.set(keyIndex, false);
                            newValueArray = addByMove(VALUE, keyIndexToCompressIndex(KEY, DEFAULT_KEY, keyIndex), DynamicParamTable.getValueIndex(entry.getKey(), entry.getValue()));
                        } else {
                            // old value is not the default key, replace offset in VALUE array
                            newValueArray = replaceOffset(VALUE, keyIndexToCompressIndex(KEY, DEFAULT_KEY, keyIndex), DynamicParamTable.getValueIndex(entry.getKey(), entry.getValue()));
                        }
                    } else {
                        // value is default value, add to defaultKey directly
                        if (defaultKey == null) {
                            defaultKey = (BitSet) DEFAULT_KEY.clone();
                        }
                        defaultKey.set(keyIndex);
                    }
                } else {
                    // key is absent, add it
                    if (newKey == null) {
                        newKey = (BitSet) KEY.clone();
                    }
                    newKey.set(keyIndex);

                    if (parameters.size() > ADD_PARAMETER_ON_MOVE_THRESHOLD) {
                        // recover VALUE back to Map
                        if (newValueMap == null) {
                            newValueMap = recoverCompressedValue();
                        }
                        newValueMap.put(keyIndex, DynamicParamTable.getValueIndex(entry.getKey(), entry.getValue()));
                    } else if (!DynamicParamTable.isDefaultValue(entry.getKey(), entry.getValue())) {
                        // add parameter by moving array, only support for adding once
                        // 通过移动数组添加参数，仅支持添加一次
                        newValueArray = addByMove(VALUE, keyIndexToCompressIndex(newKey, DEFAULT_KEY, keyIndex), DynamicParamTable.getValueIndex(entry.getKey(), entry.getValue()));
                    } else {
                        // value is default value, add to defaultKey directly
                        if (defaultKey == null) {
                            defaultKey = (BitSet) DEFAULT_KEY.clone();
                        }
                        defaultKey.set(keyIndex);
                    }
                }
            }
        }
        if (newKey == null) {
            newKey = KEY;
        }
        if (defaultKey == null) {
            defaultKey = DEFAULT_KEY;
        }
        if (newValueArray == null && newValueMap == null) {
            newValueArray = VALUE;
        }
        if (newExtraParams == null) {
            newExtraParams = EXTRA_PARAMS;
        }
        if (newMethodParams == null) {
            newMethodParams = METHOD_PARAMETERS;
        }
        if (newValueMap == null) {
            return new URLParam(newKey, defaultKey, newValueArray, newExtraParams, newMethodParams, null);
        } else {
            return new URLParam(newKey, newValueMap, newExtraParams, newMethodParams, null);
        }
    }

    private Map<Integer, Integer> recoverCompressedValue() {
        Map<Integer, Integer> map = new HashMap<>((int) (KEY.size() / 0.75) + 1);
        for (int i = KEY.nextSetBit(0), offset = 0; i >= 0; i = KEY.nextSetBit(i + 1)) {
            if (!DEFAULT_KEY.get(i)) {
                map.put(i, VALUE[offset++]);
            }
        }
        return map;
    }

    private Integer[] addByMove(Integer[] array, int index, Integer value) {
        if (index < 0 || index > array.length) {
            throw new IllegalArgumentException();
        }
        // copy-on-write
        Integer[] result = new Integer[array.length + 1];

        System.arraycopy(array, 0, result, 0, index);
        result[index] = value;
        System.arraycopy(array, index, result, index + 1, array.length - index);

        return result;
    }

    private Integer[] replaceOffset(Integer[] array, int index, Integer value) {
        if (index < 0 || index > array.length) {
            throw new IllegalArgumentException();
        }
        // copy-on-write
        Integer[] result = new Integer[array.length];

        System.arraycopy(array, 0, result, 0, array.length);
        result[index] = value;

        return result;
    }

    /**
     * remove specified parameters in URLParam
     *
     * @param keys keys to being removed
     * @return A new URLParam
     */
    public URLParam removeParameters(String... keys) {
        if (keys == null || keys.length == 0) {
            return this;
        }
        // lazy init, null if no modify 延迟加载（就和很多在属性中光声明，在构造函数里面才进行初始化赋值的逻辑）
        BitSet newKey = null;
        BitSet defaultKey = null;
        Integer[] newValueArray = null;
        Map<String, String> newExtraParams = null;
        Map<String, Map<String, String>> newMethodParams = null;
        for (String key : keys) {
            Integer keyIndex = DynamicParamTable.getKeyIndex(enableCompressed, key);
            if (keyIndex != null && KEY.get(keyIndex)) {
                if (newKey == null) {
                    newKey = (BitSet) KEY.clone();
                }
                // 将对应的位置为0，因为删除参数，同时对应的bitSet的bit位也要去除映射
                newKey.clear(keyIndex);
                // 下面if分支逻辑同上面
                if (DEFAULT_KEY.get(keyIndex)) {
                    // is default value, remove in DEFAULT_KEY
                    if (defaultKey == null) {
                        defaultKey = (BitSet) DEFAULT_KEY.clone();
                    }
                    defaultKey.clear(keyIndex);
                } else {
                    // which offset is in VALUE array, set value as -1, compress in the end
                    // VALUE数组中的偏移量，设置值为-1，最后压缩（所谓的压缩就是在后面逻辑会把-1的位置给删掉）
                    if (newValueArray == null) {
                        newValueArray = new Integer[VALUE.length];
                        System.arraycopy(VALUE, 0, newValueArray, 0, VALUE.length);
                    }
                    // KEY is immutable
                    newValueArray[keyIndexToCompressIndex(KEY, DEFAULT_KEY, keyIndex)] = -1;
                }
            }
            if (EXTRA_PARAMS.containsKey(key)) {
                if (newExtraParams == null) {
                    newExtraParams = new HashMap<>(EXTRA_PARAMS);
                }
                newExtraParams.remove(key);

                String[] methodSplit = key.split("\\.");
                if (methodSplit.length == 2) {
                    if (newMethodParams == null) {
                        newMethodParams = new HashMap<>(METHOD_PARAMETERS);
                    }
                    Map<String, String> methodMap = newMethodParams.get(methodSplit[1]);
                    if (CollectionUtils.isNotEmptyMap(methodMap)) {
                        methodMap.remove(methodSplit[0]);
                    }
                }
            }
            // ignore if key is absent
        }
        if (newKey == null) {
            newKey = KEY;
        }
        if (defaultKey == null) {
            defaultKey = DEFAULT_KEY;
        }
        if (newValueArray == null) {
            newValueArray = VALUE;
        } else {
            // remove -1 value
            newValueArray = compressArray(newValueArray);
        }
        if (newExtraParams == null) {
            newExtraParams = EXTRA_PARAMS;
        }
        if (newMethodParams == null) {
            newMethodParams = METHOD_PARAMETERS;
        }
        // cardinality 就是 bit位是1 的个数
        if (newKey.cardinality() + newExtraParams.size() == 0) {
            // empty, directly return cache
            return EMPTY_PARAM;
        } else {
            return new URLParam(newKey, defaultKey, newValueArray, newExtraParams, newMethodParams, null);
        }
    }

    private Integer[] compressArray(Integer[] array) {
        int total = 0;
        for (int i : array) {
            if (i > -1) {
                total++;
            }
        }
        if (total == 0) {
            return new Integer[0];
        }

        Integer[] result = new Integer[total];
        for (int i = 0, offset = 0; i < array.length; i++) {
            // skip if value if less than 0
            if (array[i] > -1) {
                result[offset++] = array[i];
            }
        }
        return result;
    }

    /**
     * remove all of the parameters in URLParam
     *
     * @return An empty URLParam
     */
    public URLParam clearParameters() {
        return EMPTY_PARAM;
    }

    /**
     * check if specified key is present in URLParam
     *
     * @param key specified key
     * @return present or not
     */
    public boolean hasParameter(String key) {
        Integer keyIndex = DynamicParamTable.getKeyIndex(enableCompressed, key);
        if (keyIndex == null) {
            return EXTRA_PARAMS.containsKey(key);
        }
        return KEY.get(keyIndex);
    }

    /**
     * get value of specified key in URLParam
     *
     * @param key specified key
     * @return value, null if key is absent
     */
    public String getParameter(String key) {
        Integer keyIndex = DynamicParamTable.getKeyIndex(enableCompressed, key);
        if (keyIndex == null) {
            if (EXTRA_PARAMS.containsKey(key)) {
                return EXTRA_PARAMS.get(key);
            }
            return null;
        }
        if (KEY.get(keyIndex)) {
            String value;
            if (DEFAULT_KEY.get(keyIndex)) {
                value = DynamicParamTable.getDefaultValue(keyIndex);
            } else {
                Integer offset = keyIndexToOffset(keyIndex);
                // 如下方法调用的参数正好和 valueMap.put(keyIndex, DynamicParamTable.getValueIndex(key, value)); 对应！！
                // 也就再次说明下面的offset就是paramValue的值下标，比如DynamicValues的indexSeq
                value = DynamicParamTable.getValue(keyIndex, offset);
            }
            if (StringUtils.isEmpty(value)) {
                // Forward compatible, make sure key dynamic increment can work.
                // In that case, some values which are proceed before increment will set in EXTRA_PARAMS.
                // 向前兼容，确保键动态增量可以工作。
                // 在这种情况下，一些在增量之前进行的值将设置在 EXTRA_PARAMS 中。
                return EXTRA_PARAMS.get(key);
            } else {
                return value;
            }
        }
        return null;
    }


    private int keyIndexToCompressIndex(BitSet key, BitSet defaultKey, int keyIndex) {
        int index = 0;
        // 在构造函数介绍过KEY、DEFAULT_KEY、VALUE的关系，比如KEY有 0 1 2 ，0索引位的key的值是默认值，那么DEFAULT_KEY就有 0 （bit位为1），
        // 而VALUE为 0 1（对应KEY的 1 2）
        for (int i = 0; i < keyIndex; i++) {
            if (key.get(i)) {
                if (!defaultKey.get(i)) {
                    index++;
                }
            }
        }
        return index;
    }

    private Integer keyIndexToOffset(int keyIndex) {
        int arrayOffset = keyIndexToCompressIndex(KEY, DEFAULT_KEY, keyIndex);
        return VALUE[arrayOffset];
    }

    /**
     * get raw string like parameters
     *
     * @return raw string like parameters
     */
    public String getRawParam() {
        if (StringUtils.isNotEmpty(rawParam)) {
            return rawParam;
        } else {
            // empty if parameters have been modified or init by Map
            return toString();
        }
    }

    protected Map<String, Map<String, String>> getMethodParameters() {
        return METHOD_PARAMETERS;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        URLParam urlParam = (URLParam) o;

        if (Objects.equals(KEY, urlParam.KEY)
            && Objects.equals(DEFAULT_KEY, urlParam.DEFAULT_KEY)
            && Arrays.equals(VALUE, urlParam.VALUE)) {
            if (CollectionUtils.isNotEmptyMap(EXTRA_PARAMS)) {
                if (CollectionUtils.isEmptyMap(urlParam.EXTRA_PARAMS) || EXTRA_PARAMS.size() != urlParam.EXTRA_PARAMS.size()) {
                    return false;
                }
                for (Map.Entry<String, String> entry : EXTRA_PARAMS.entrySet()) {
                    if (TIMESTAMP_KEY.equals(entry.getKey())) {
                        continue;
                    }
                    if (!entry.getValue().equals(urlParam.EXTRA_PARAMS.get(entry.getKey()))) {
                        return false;
                    }
                }
                return true;
            }
            return CollectionUtils.isEmptyMap(urlParam.EXTRA_PARAMS);
        }
        return false;
    }

    private int hashCodeCache = -1;

    @Override
    public int hashCode() {
        if (hashCodeCache == -1) {
            for (Map.Entry<String, String> entry : EXTRA_PARAMS.entrySet()) {
                if (!TIMESTAMP_KEY.equals(entry.getKey())) {
                    hashCodeCache = hashCodeCache * 31 + Objects.hashCode(entry);
                }
            }
            for (Integer value : VALUE) {
                hashCodeCache = hashCodeCache * 31 + value;
            }
            hashCodeCache = hashCodeCache * 31 + ((KEY == null) ? 0 : KEY.hashCode());
            hashCodeCache = hashCodeCache * 31 + ((DEFAULT_KEY == null) ? 0 : DEFAULT_KEY.hashCode());
        }
        return hashCodeCache;
    }

    @Override
    public String toString() {
        if (StringUtils.isNotEmpty(rawParam)) {
            return rawParam;
        }
        if ((KEY.cardinality() + EXTRA_PARAMS.size()) == 0) {
            return "";
        }

        // StringJoiner的使用注意下
        StringJoiner stringJoiner = new StringJoiner("&");
        for (int i = KEY.nextSetBit(0); i >= 0; i = KEY.nextSetBit(i + 1)) {
            String key = DynamicParamTable.getKey(i);
            String value = DEFAULT_KEY.get(i) ?
                DynamicParamTable.getDefaultValue(i) : DynamicParamTable.getValue(i, keyIndexToOffset(i));
            value = value == null ? "" : value.trim();

            stringJoiner.add(String.format("%s=%s", key, value));
        }
        for (Map.Entry<String, String> entry : EXTRA_PARAMS.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            value = value == null ? "" : value.trim();
            stringJoiner.add(String.format("%s=%s", key, value));
        }

        return stringJoiner.toString();
    }

    /**
     * Parse URLParam
     * Init URLParam by constructor is not allowed
     * rawParam field in result will be null while {@link URLParam#getRawParam()} will automatically create it
     *
     * @param params params map added into URLParam
     * @return a new URLParam
     */
    public static URLParam parse(Map<String, String> params) {
        return parse(params, null);
    }

    /**
     * Parse URLParam
     * Init URLParam by constructor is not allowed
     *
     * @param rawParam        original rawParam string
     * @param encoded         if parameters are URL encoded
     * @param extraParameters extra parameters to add into URLParam
     * @return a new URLParam
     */
    public static URLParam parse(String rawParam, boolean encoded, Map<String, String> extraParameters) {
        Map<String, String> parameters = URLStrParser.parseParams(rawParam, encoded);
        if (CollectionUtils.isNotEmptyMap(extraParameters)) {
            // 后者的优先级更高
            parameters.putAll(extraParameters);
        }
        return parse(parameters, rawParam);
    }

    /**
     * Parse URLParam
     * Init URLParam by constructor is not allowed
     *
     * @param rawParam original rawParam string
     * @return a new URLParam
     */
    public static URLParam parse(String rawParam) {
        String[] parts = rawParam.split("&");

        int capcity = (int) (parts.length / .75f) + 1;
        BitSet keyBit = new BitSet(capcity);
        Map<Integer, Integer> valueMap = new HashMap<>(capcity);
        Map<String, String> extraParam = new HashMap<>(capcity);
        Map<String, Map<String, String>> methodParameters = new HashMap<>(capcity);

        for (String part : parts) {
            part = part.trim();
            if (part.length() > 0) {
                // 其实这里也可以用split切割
                int j = part.indexOf('=');
                if (j >= 0) {
                    String key = part.substring(0, j);
                    String value = part.substring(j + 1);
                    // 最后的false表示如果存在则跳过，类似于putIfAbsent
                    addParameter(keyBit, valueMap, extraParam, methodParameters, key, value, false);
                    // compatible with lower versions registering "default." keys
                    if (key.startsWith(DEFAULT_KEY_PREFIX)) {
                        addParameter(keyBit, valueMap, extraParam, methodParameters, key.substring(DEFAULT_KEY_PREFIX.length()), value, true);
                    }
                } else {
                    // val = key
                    addParameter(keyBit, valueMap, extraParam, methodParameters, part, part, false);
                }
            }
        }
        return new URLParam(keyBit, valueMap, extraParam, methodParameters, rawParam);
    }

    /**
     * Parse URLParam
     * Init URLParam by constructor is not allowed
     *
     * @param params   params map added into URLParam
     * @param rawParam original rawParam string, directly add to rawParam field,
     *                 will not effect real key-pairs store in URLParam.
     *                 Please make sure it can correspond with params or will
     *                 cause unexpected result when calling {@link URLParam#getRawParam()}
     *                 and {@link URLParam#toString()} ()}. If you not sure, you can call
     *                 {@link URLParam#parse(String)} to init.
     * @return a new URLParam
     */
    public static URLParam parse(Map<String, String> params, String rawParam) {
        if (CollectionUtils.isNotEmptyMap(params)) {
            BitSet keyBit = new BitSet((int) (params.size() / .75f) + 1);
            Map<Integer, Integer> valueMap = new HashMap<>((int) (params.size() / .75f) + 1);
            Map<String, String> extraParam = new HashMap<>((int) (params.size() / .75f) + 1);
            Map<String, Map<String, String>> methodParameters = new HashMap<>((int) (params.size() / .75f) + 1);

            for (Map.Entry<String, String> entry : params.entrySet()) {
                // 最终调用的还是这个。原有的parse是query str的方式，这里是map的entry方式
                addParameter(keyBit, valueMap, extraParam, methodParameters, entry.getKey(), entry.getValue(), false);
            }
            return new URLParam(keyBit, valueMap, extraParam, methodParameters, rawParam);
        } else {
            return EMPTY_PARAM;
        }
    }

    private static void addParameter(BitSet keyBit, Map<Integer, Integer> valueMap, Map<String, String> extraParam,
                                     Map<String, Map<String, String>> methodParameters, String key, String value, boolean skipIfPresent) {
        Integer keyIndex = DynamicParamTable.getKeyIndex(true, key);
        // skipIfPresent 若存在则跳过
        if (skipIfPresent) {
            // keyIndex存在则操作keyBit，否则extraParam
            if (keyIndex == null) {
                // 若存在则跳过
                if (extraParam.containsKey(key)) {
                    return;
                }
            } else {
                // 若存在则跳过
                if (keyBit.get(keyIndex)) {
                    return;
                }
            }
        }

        // keyIndex存在则操作keyBit，否则extraParam
        if (keyIndex == null) {
            extraParam.put(key, value);
            // limit=2限定最后的返回结果长度为2
            String[] methodSplit = key.split("\\.", 2);
            if (methodSplit.length == 2) {
                // sync.method1=false --> {method1:{sync:false}} 感觉和以前的有点不同，以前的是[0]表示方法
                Map<String, String> methodMap = methodParameters.computeIfAbsent(methodSplit[1], (k) -> new HashMap<>());
                methodMap.put(methodSplit[0], value);
            }
        } else {
            // 如果 side:consumer，那么就是默认值
            if (!DynamicParamTable.isDefaultValue(key, value)) {
                // getValueIndex 其实是 getOrCreate...
                // valueMap实际是keyIndex->"ParamValue里的值所在index的映射"，注意value部分不是 ParamValue 本身 index（也没必要存储，因为keyIndex和valIndex在DynamicParamTable是成对对称出现的）
                // valueMap的value部分是 ParamValue "里面"！！ 具体值的下标，因为ParamValue内部可以存储多个value。实际DynamicParamTable可以看成一级和二级缓存
                // 一级缓存就是{keyIndex,valueIndex(ParamValue本身)}，二级缓存就是{keyIndex:{paramValue-Key,ParamValue-Val}}

                // 比如 version:1.0，针对下面的kv，k就是1(version在KEYS下标)，v就是1（就是在DynamicValues的indexSeq的值）
                valueMap.put(keyIndex, DynamicParamTable.getValueIndex(key, value));
            }
            // 指定索引的bit位设置为1
            keyBit.set(keyIndex);
        }
    }
}
