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
package org.apache.dubbo.common.convert.multiple;

import org.apache.dubbo.common.convert.Converter;

import java.lang.reflect.Array;

import static java.lang.reflect.Array.newInstance;

/**
 * The class to convert {@link String} to array-type object
 *
 * @since 2.7.6
 */
// OK
public class StringToArrayConverter implements StringToMultiValueConverter {

    public boolean accept(Class<String> type, Class<?> multiValueType) {
        // 没用到type参数

        // eg:multiValueType 为 char[].class
        if (multiValueType != null && multiValueType.isArray()) {
            return true;
        }
        return false;
    }

    @Override
    public Object convert(String[] segments, int size, Class<?> targetType, Class<?> elementType) {
        // 没用到elementType参数

        // 获取Component类型(Component:组成部分；成分；组件，元件)，就是数组里面的元素类型，比如targetType = Integer[].class(输出为class [Ljava.lang.Integer)，
        // 这里就是获取Integer.class(输出为class java.lang.Integer)
        Class<?> componentType = targetType.getComponentType();

        // 获取支持Converter，比如StringToIntegerConverter
        Converter converter = Converter.getConverter(String.class, componentType);

        // Array.newInstance
        Object array = newInstance(componentType, size);

        for (int i = 0; i < size; i++) {
            // 转化后填充
            Array.set(array, i, converter.convert(segments[i]));
        }

        return array;
    }


    @Override
    public int getPriority() {
        // 最小优先级，实际是Integer.MAX_VALUE，所以值越小优先级越高
        return MIN_PRIORITY;
    }
}
