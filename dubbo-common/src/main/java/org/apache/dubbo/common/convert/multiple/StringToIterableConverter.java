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

import org.apache.dubbo.common.convert.StringConverter;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import static org.apache.dubbo.common.convert.Converter.getConverter;
import static org.apache.dubbo.common.utils.ClassUtils.getAllInterfaces;
import static org.apache.dubbo.common.utils.ClassUtils.isAssignableFrom;
import static org.apache.dubbo.common.utils.TypeUtils.findActualTypeArgument;

/**
 * The class to convert {@link String} to {@link Iterable}-based value
 *
 * @since 2.7.6
 */
// OK
// 泛型T由子类给出
public abstract class StringToIterableConverter<T extends Iterable> implements StringToMultiValueConverter {

    public boolean accept(Class<String> type, Class<?> multiValueType) {
        // 第一个参数没用到

        // getSupportedType进去
        return isAssignableFrom(getSupportedType(), multiValueType);
    }

    @Override
    public final Object convert(String[] segments, int size, Class<?> multiValueType, Class<?> elementType) {

        // 进去
        Optional<StringConverter> stringConverter = getStringConverter(elementType);

        return stringConverter.map(converter -> {

            // createMultiValue抽象方法，子类给出实现，创建对应size大小的容器
            T convertedObject = createMultiValue(size, multiValueType);

            if (convertedObject instanceof Collection) {
                Collection collection = (Collection) convertedObject;
                for (int i = 0; i < size; i++) {
                    String segment = segments[i];
                    Object element = converter.convert(segment);
                    collection.add(element);
                }
                return collection;
            }

            return convertedObject;
        }).orElse(null);
    }

    protected abstract T createMultiValue(int size, Class<?> multiValueType);

    protected Optional<StringConverter> getStringConverter(Class<?> elementType) {
        StringConverter converter = (StringConverter) getConverter(String.class, elementType);
        return Optional.ofNullable(converter);
    }

    protected final Class<T> getSupportedType() {
        // 下面这个调用讲过了，比如this为StringToBlockingDequeueConverter实例，那么返回的结果就是BlockingDeque.class
        // 注意传入的是StringToIterableConverter ，影响到这行，做一些过滤  -> isAssignableFrom(interfaceClass, getRawClass(t))
        return findActualTypeArgument(getClass(), StringToIterableConverter.class, 0);
    }

    // todo
    @Override
    public final int getPriority() {
        Set<Class<?>> allInterfaces = getAllInterfaces(getSupportedType(), type ->
                isAssignableFrom(Iterable.class, type));
        // 案例1
        //this = {StringToBlockingDequeConverter@2059}
        //allInterfaces = {LinkedHashSet@2058}  size = 5
        // 0 = {Class@2061} "interface java.util.concurrent.BlockingQueue"
        // 1 = {Class@365} "interface java.util.Deque"
        // 2 = {Class@364} "interface java.util.Queue"
        // 3 = {Class@223} "interface java.util.Collection"
        // 4 = {Class@224} "interface java.lang.Iterable"

        // 案例2
        /*
        this = {StringToBlockingQueueConverter@2068}
        allInterfaces = {LinkedHashSet@2067}  size = 3
        0 = {Class@364} "interface java.util.Queue"
        1 = {Class@223} "interface java.util.Collection"
        2 = {Class@224} "interface java.lang.Iterable"

        // 案例3
        this = {StringToCollectionConverter@2098}
        allInterfaces = {LinkedHashSet@2097}  size = 1
        0 = {Class@224} "interface java.lang.Iterable"
        */
        int level = allInterfaces.size();
        return MIN_PRIORITY - level;
    }
}
