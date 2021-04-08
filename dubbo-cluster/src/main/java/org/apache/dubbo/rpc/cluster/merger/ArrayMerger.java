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
package org.apache.dubbo.rpc.cluster.merger;

import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.rpc.cluster.Merger;

import java.lang.reflect.Array;

public class ArrayMerger implements Merger<Object[]> {

    public static final ArrayMerger INSTANCE = new ArrayMerger();

    // 将多个Object[]合并为一个Object[]
    @Override
    public Object[] merge(Object[]... items) {
        if (ArrayUtils.isEmpty(items)) {
            return new Object[0];
        }

        // 找到第一个不为null的下标
        int i = 0;
        while (i < items.length && items[i] == null) {
            i++;
        }

        if (i == items.length) {
            return new Object[0];
        }

        Class<?> type = items[i].getClass().getComponentType();

        int totalLen = 0;

        for (; i < items.length; i++) {
            // i一开始记录的第一个不null的，但是其后面也是可能为null的。不过这里的i不应该从i开始遍历，而是i+1位置
            // 上面后半句是错误的，因为后面需要计算长度
            if (items[i] == null) {
                continue;
            }
            // 确保所有数组的元素类型要一致
            Class<?> itemType = items[i].getClass().getComponentType();
            if (itemType != type) {
                throw new IllegalArgumentException("Arguments' types are different");
            }
            totalLen += items[i].length;
        }

        if (totalLen == 0) {
            return new Object[0];
        }

        Object result = Array.newInstance(type, totalLen);

        int index = 0;
        for (Object[] array : items) {
            if (array != null) {
                for (int j = 0; j < array.length; j++) {
                    Array.set(result, index++, array[j]);
                }
            }
        }
        return (Object[]) result;
    }
}
