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
package org.apache.dubbo.common.utils;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;

/**
 * Miscellaneous collection utility methods.
 * Mainly for internal use within the framework.
 *
 * @author william.liangf
 * @since 2.0.7
 */
// OK
public class CollectionUtils {

    //和Prioritized接口一样，里面也有一个如下方式构造的Comparator ，用的时候都是Collections.sort(list,SIMPLE_NAME_COMPARATOR);
    // gx
    private static final Comparator<String> SIMPLE_NAME_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String s1, String s2) {
            if (s1 == null && s2 == null) {
                return 0;
            }
            if (s1 == null) {
                return -1;
            }
            if (s2 == null) {
                return 1;
            }
            // 比较的string一般是class.getSimpleName，当然也可以对普通字符串按照字典序排序
            int i1 = s1.lastIndexOf('.');
            if (i1 >= 0) {
                // 获取.之后的字符串
                s1 = s1.substring(i1 + 1);
            }
            int i2 = s2.lastIndexOf('.');
            if (i2 >= 0) {
                s2 = s2.substring(i2 + 1);
            }
            // compareTo
            return s1.compareToIgnoreCase(s2);
        }
    };

    private CollectionUtils() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> List<T> sort(List<T> list) {
        if (isNotEmpty(list)) {
            // 这里没有传入Comparator的话，需要T自己实现了Comparable接口  ，gx
            Collections.sort((List) list);
        }
        return list;
    }

    // gx
    public static List<String> sortSimpleName(List<String> list) {
        if (list != null && list.size() > 0) {
            Collections.sort(list, SIMPLE_NAME_COMPARATOR);
        }
        return list;
    }

    // gx 把String拆分变成Map<String, Map<String, String>>结构
    public static Map<String, Map<String, String>> splitAll(Map<String, List<String>> list, String separator) {
        if (list == null) {
            return null;
        }
        // 双map结构
        Map<String, Map<String, String>> result = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : list.entrySet()) {
            // eg:某个entry为 input.put("key1", Arrays.asList("1:a", "2:b", "3:c"));传进来的分隔符肯定是:了，split进去
            result.put(entry.getKey(), split(entry.getValue(), separator));
        }
        return result;
    }

    // gx 和前面的反操作，把Map<String, Map<String, String>>结构变成string
    public static Map<String, List<String>> joinAll(Map<String, Map<String, String>> map, String separator) {
        if (map == null) {
            return null;
        }
        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : map.entrySet()) {
            result.put(entry.getKey(), join(entry.getValue(), separator));
        }
        return result;
    }

    //
    public static Map<String, String> split(List<String> list, String separator) {
        if (list == null) {
            return null;
        }
        Map<String, String> map = new HashMap<>();
        if (list.isEmpty()) {
            return map;
        }
        for (String item : list) {
            // 常见法，截取:两边的字符串
            int index = item.indexOf(separator);
            if (index == -1) {
                map.put(item, "");
            } else {
                map.put(item.substring(0, index), item.substring(index + 1));
            }
        }
        return map;
    }

    public static List<String> join(Map<String, String> map, String separator) {
        if (map == null) {
            return null;
        }
        List<String> list = new ArrayList<>();
        if (map.size() == 0) {
            return list;
        }
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (StringUtils.isEmpty(value)) {
                list.add(key);
            } else {
                // 拼接
                list.add(key + separator + value);
            }
        }
        return list;
    }

    public static String join(List<String> list, String separator) {
        StringBuilder sb = new StringBuilder();
        for (String ele : list) {
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(ele);
        }
        return sb.toString();
    }

    // 两个map是否相等
    public static boolean mapEquals(Map<?, ?> map1, Map<?, ?> map2) {
        if (map1 == null && map2 == null) {
            return true;
        }
        if (map1 == null || map2 == null) {
            return false;
        }
        if (map1.size() != map2.size()) {
            return false;
        }
        for (Map.Entry<?, ?> entry : map1.entrySet()) {
            Object key = entry.getKey();
            Object value1 = entry.getValue();
            Object value2 = map2.get(key);
            if (!objectEquals(value1, value2)) {
                return false;
            }
        }
        return true;
    }

    private static boolean objectEquals(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) {
            return true;
        }
        if (obj1 == null || obj2 == null) {
            return false;
        }
        return obj1.equals(obj2);
    }

    public static Map<String, String> toStringMap(String... pairs) {
        Map<String, String> parameters = new HashMap<>();
        if (ArrayUtils.isEmpty(pairs)) {
            return parameters;
        }

        if (pairs.length > 0) {
            if (pairs.length % 2 != 0) {
                throw new IllegalArgumentException("pairs must be even.");
            }
            for (int i = 0; i < pairs.length; i = i + 2) {
                parameters.put(pairs[i], pairs[i + 1]);
            }
        }
        return parameters;
    }

    // easy
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> toMap(Object... pairs) {
        Map<K, V> ret = new HashMap<>();
        if (pairs == null || pairs.length == 0) {
            return ret;
        }

        if (pairs.length % 2 != 0) {
            // 信息
            throw new IllegalArgumentException("Map pairs can not be odd number.");
        }
        int len = pairs.length / 2;
        for (int i = 0; i < len; i++) {
            ret.put((K) pairs[2 * i], (V) pairs[2 * i + 1]);
        }
        return ret;
    }

    /**
     * Return {@code true} if the supplied Collection is {@code null} or empty.
     * Otherwise, return {@code false}.
     *
     * @param collection the Collection to check
     * @return whether the given Collection is empty
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Return {@code true} if the supplied Collection is {@code not null} or not empty.
     * Otherwise, return {@code false}.
     *
     * @param collection the Collection to check
     * @return whether the given Collection is not empty
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * Return {@code true} if the supplied Map is {@code null} or empty.
     * Otherwise, return {@code false}.
     *
     * @param map the Map to check
     * @return whether the given Map is empty
     */
    public static boolean isEmptyMap(Map map) {
        return map == null || map.size() == 0;
    }

    /**
     * Return {@code true} if the supplied Map is {@code not null} or not empty.
     * Otherwise, return {@code false}.
     *
     * @param map the Map to check
     * @return whether the given Map is not empty
     */
    public static boolean isNotEmptyMap(Map map) {
        return !isEmptyMap(map);
    }

    /**
     * Convert to multiple values to be {@link LinkedHashSet}
     *
     * @param values one or more values
     * @param <T>    the type of <code>values</code>
     * @return read-only {@link Set}
     */
    public static <T> Set<T> ofSet(T... values) {
        int size = values == null ? 0 : values.length;
        if (size < 1) {
            return emptySet();
        }

        // 搞了个负载因子
        float loadFactor = 1f / ((size + 1) * 1.0f);

        if (loadFactor > 0.75f) {
            loadFactor = 0.75f;
        }

        // LinkedHashSet记录插入顺序的set
        Set<T> elements = new LinkedHashSet<>(size, loadFactor);
        for (int i = 0; i < size; i++) {
            elements.add(values[i]);
        }
        return unmodifiableSet(elements);
    }



    /**
     * Get the size of the specified {@link Collection}
     *
     * @param collection the specified {@link Collection}
     * @return must be positive number
     * @since 2.7.6
     */
    public static int size(Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }

    /**
     * Compares the specified collection with another, the main implementation references
     * {@link AbstractSet}
     *
     * @param one     {@link Collection}
     * @param another {@link Collection}
     * @return if equals, return <code>true</code>, or <code>false</code>
     * @since 2.7.6
     */
    public static boolean equals(Collection<?> one, Collection<?> another) {

        // 先比较地址
        if (one == another) {
            return true;
        }

        if (isEmpty(one) && isEmpty(another)) {
            return true;
        }

        // 比较大小
        if (size(one) != size(another)) {
            return false;
        }

        try {
            // containsAll
            return one.containsAll(another);
        } catch (ClassCastException unused) {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }
    }

    /**
     * Add the multiple values into {@link Collection the specified collection}
     *
     * @param collection {@link Collection the specified collection}
     * @param values     the multiple values
     * @param <T>        the type of values
     * @return the effected count after added
     * @since 2.7.6
     */
    // 没调用点
    public static <T> int addAll(Collection<T> collection, T... values) {

        int size = values == null ? 0 : values.length;

        if (collection == null || size < 1) {
            return 0;
        }

        int effectedCount = 0;
        for (int i = 0; i < size; i++) {
            if (collection.add(values[i])) {
                effectedCount++;
            }
        }

        return effectedCount;
    }

    /**
     * Take the first element from the specified collection
     *
     * @param values the collection object
     * @param <T>    the type of element of collection
     * @return if found, return the first one, or <code>null</code>
     * @since 2.7.6
     */
    public static <T> T first(Collection<T> values) {
        if (isEmpty(values)) {
            return null;
        }
        if (values instanceof List) {
            List<T> list = (List<T>) values;
            return list.get(0);
        } else {
            // 迭代器模式，不考虑values的具体类型
            return values.iterator().next();
        }
    }

}
