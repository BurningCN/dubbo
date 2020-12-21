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
package org.apache.dubbo.common.convert;

import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.extension.SPI;
import org.apache.dubbo.common.lang.Prioritized;

import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.apache.dubbo.common.utils.ClassUtils.isAssignableFrom;
import static org.apache.dubbo.common.utils.TypeUtils.findActualTypeArgument;

/**
 * A class to convert the source-typed value to the target-typed value
 *
 * @param <S> The source type
 * @param <T> The target type
 * @since 2.7.6
 */
// OK
@SPI
@FunctionalInterface
public interface Converter<S, T> extends Prioritized {

    /**
     * Accept the source type and target type or not
     *
     * @param sourceType the source type
     * @param targetType the target type
     * @return if accepted, return <code>true</code>, or <code>false</code>
     */
    // 作用看上面，因为不是随便什么类型都能互转的，比如Double -> Long 就不行，因为没有DoubleToLongConverter，具体怎么识别能否转化，就是利用下面两个判断(isAssignableFrom)。
    // getSourceType()、getTargetType()分别取的StringConverter<XX> 、Converter<String, T>的XX和String(详细过程getSourceType进去看下)
    // XX的值有好几种，比如Boolean，大概支持的转化对这里简单列出几种:{Boolean,String}、{Character,String}、{Double,String}、{Long,String}....
    // 所以就是判断参数的两个类型是否属于现有支持的转化对的其中之一
    default boolean accept(Class<?> sourceType, Class<?> targetType) {
        // isAssignableFrom、getSourceType 进去
        return isAssignableFrom(sourceType, getSourceType()) && isAssignableFrom(targetType, getTargetType());
    }

    /**
     * Convert the source-typed value to the target-typed value
     *
     * @param source the source-typed value
     * @return the target-typed value
     */
    // 函数式接口的唯一待实现的方法。Converter既是SPI接口也是函数式接口
    T convert(S source);

    /**
     * Get the source type
     *
     * @return non-null
     */
    default Class<S> getSourceType() {
        // getClass()就是this.getClass()，this就是当前遍历到的扩赞类对象，比如StringToBooleanConverter.
        // 最后一个参数传0，进去
        return findActualTypeArgument(getClass(), Converter.class, 0);
    }

    /**
     * Get the target type
     *
     * @return non-null
     */
    default Class<T> getTargetType() {
        // 和前面一样，最后一个参数传1
        return findActualTypeArgument(getClass(), Converter.class, 1);
    }

    /**
     * Get the Converter instance from {@link ExtensionLoader} with the specified source and target type
     *
     * @param sourceType the source type
     * @param targetType the target type
     * @return
     * @see ExtensionLoader#getSupportedExtensionInstances()
     */
    static Converter<?, ?> getConverter(Class<?> sourceType, Class<?> targetType) {
        // 遍历Converter的支持的扩展类，寻找第一个能支持sourceType->targetType的转化器
        // Converter既是SPI接口也是函数式接口
        return getExtensionLoader(Converter.class)
                .getSupportedExtensionInstances()
                .stream()
                // accept 进去
                .filter(converter -> converter.accept(sourceType, targetType))
                .findFirst()
                .orElse(null);
    }

    /**
     * Convert the value of source to target-type value if possible
     *
     * @param source     the value of source
     * @param targetType the target type
     * @param <T>        the target type
     * @return <code>null</code> if can't be converted
     * @since 2.7.8
     */
    static <T> T convertIfPossible(Object source, Class<T> targetType) {
        // 获取支持sourceType->targetType的转化器(扩展类实例)（注意：目前支持的都是源类型String的转化），进去
        Converter converter = getConverter(source.getClass(), targetType);
        if (converter != null) {
            return (T) converter.convert(source);
        }
        return null;
    }
}
