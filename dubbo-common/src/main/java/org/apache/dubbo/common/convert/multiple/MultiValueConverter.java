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

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.lang.Prioritized;

import java.util.Collection;

import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.apache.dubbo.common.utils.TypeUtils.findActualTypeArgument;

/**
 * An interface to convert the source-typed value to multiple value, e.g , Java array, {@link Collection} or
 * sub-interfaces
 *
 * @param <S> The source type
 * @since 2.7.6
 */
// OK
// 直接子接口只有一个StringToMultiValueConverter，传给S的值String
// 和Converter<S,T>不同，其作用是S->(转化为)T，当然了S目前只有String,这里MultiValueConverter<S>的作用是把String的值拆分放到集合容器中,
// 比如"1,2,3"放到ArrayList<>(){1,2,3}。和Converter一样都继承了Prioritized
@SPI
public interface MultiValueConverter<S> extends Prioritized {

    /**
     * Accept the source type and target type or not
     *
     * @param sourceType     the source type
     * @param multiValueType the multi-value type
     * @return if accepted, return <code>true</code>, or <code>false</code>
     */
    // StringToMultiValueConverter的两个直接子类给出了实现
    boolean accept(Class<S> sourceType, Class<?> multiValueType);

    /**
     * Convert the source to be the multiple value
     *
     * @param source         the source-typed value
     * @param multiValueType the multi-value type
     * @param elementType    the element type
     * @return
     */
    Object convert(S source, Class<?> multiValueType, Class<?> elementType);

    /**
     * Get the source type
     *
     * @return non-null
     */
    default Class<S> getSourceType() {
        return findActualTypeArgument(getClass(), MultiValueConverter.class, 0);
    }

    /**
     * Find the {@link MultiValueConverter} instance from {@link ExtensionLoader} with the specified source and target type
     *
     * @param sourceType the source type
     * @param targetType the target type
     * @return <code>null</code> if not found
     * @see ExtensionLoader#getSupportedExtensionInstances()
     * @since 2.7.8
     */
    // sourceType目前只能是String.class，targetType是各种奇葩类型，比如String[].class、BlockingQueue.class、List.class等
    static MultiValueConverter<?> find(Class<?> sourceType, Class<?> targetType) {
        return getExtensionLoader(MultiValueConverter.class)
                // 有11个实例，详见MultiValueConverter的SPI文件，且注意内部会对其排序，根据各个子类的getPriority方法
                .getSupportedExtensionInstances()
                // accept进去
                .stream()
                .filter(converter -> converter.accept(sourceType, targetType))
                .findFirst()
                .orElse(null);
    }

    static <T> T convertIfPossible(Object source, Class<?> multiValueType, Class<?> elementType) {
        Class<?> sourceType = source.getClass();
        MultiValueConverter converter = find(sourceType, multiValueType);
        if (converter != null) {
            return (T) converter.convert(source, multiValueType, elementType);
        }
        return null;
    }
}
