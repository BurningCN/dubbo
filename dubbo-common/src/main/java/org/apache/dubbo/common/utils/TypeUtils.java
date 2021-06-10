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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.StreamSupport.stream;
import static org.apache.dubbo.common.function.Predicates.and;
import static org.apache.dubbo.common.function.Streams.filterAll;
import static org.apache.dubbo.common.function.Streams.filterList;
import static org.apache.dubbo.common.utils.ClassUtils.getAllInterfaces;
import static org.apache.dubbo.common.utils.ClassUtils.getAllSuperClasses;
import static org.apache.dubbo.common.utils.ClassUtils.isAssignableFrom;

/**
 * The utilities class for {@link Type}
 *
 * @since 2.7.6
 */
public interface TypeUtils {

    Predicate<Class<?>> NON_OBJECT_TYPE_FILTER = t -> !Objects.equals(Object.class, t);

    static boolean isParameterizedType(Type type) {
        return type instanceof ParameterizedType;
    }

    static Type getRawType(Type type) {
        if (isParameterizedType(type)) {
            return ((ParameterizedType) type).getRawType();
        } else {
            return type;
        }
    }

    static Class<?> getRawClass(Type type) {
        Type rawType = getRawType(type);
        if (isClass(rawType)) {
            return (Class) rawType;
        }
        return null;
    }

    static boolean isClass(Type type) {
        return type instanceof Class;
    }

    // index目前调用处只传了0和1，以type为String2IntegerConverter为例
    // getSourceType方法传0，返回 class java.lang.String
    // getTargetType方法传1，返回 class java.lang.Integer
    static <T> Class<T> findActualTypeArgument(Type type, Class<?> interfaceClass, int index) {
        // 进去
        return (Class<T>) findActualTypeArguments(type, interfaceClass).get(index);
    }

    // 这个方法的作用就是:看下面☆
    static List<Class<?>> findActualTypeArguments(Type type, Class<?> interfaceClass) {
        // eg: type = String2IntegerConverter、interfaceClass = Converter
        List<Class<?>> actualTypeArguments = new LinkedList<>();


        // ☆getAllGenericTypes获取所有父类、父接口的泛型参数！第二个参数/lambda是对前一个结果做过滤的，比如父接口最多顶到interfaceClass(比如Converter)
        // Generic:通用、泛化的、泛型 。getAllGenericTypes进去
        getAllGenericTypes(type, t -> isAssignableFrom(interfaceClass, getRawClass(t)))
                .forEach(parameterizedType -> {
                    // 下面这段代码的逻辑其实就是为了获取泛型的具体类型的。比如StringConverter<Integer>那么为了获取Integer的

                    // 比如type为String2IntegerConverter，那么上面forEach遍历的list就会有两个元素：
                    // 0 = {ParameterizedTypeImpl@2023} "org.apache.dubbo.common.convert.StringConverter<java.lang.Integer>"
                    // 1 = {ParameterizedTypeImpl@2024} "org.apache.dubbo.common.convert.Converter<java.lang.String, T>"

                    // 将ParameterizedTypeImpl转化为Class类型，目的是为了后面获取其父类，进去
                    Class<?> rawClass = getRawClass(parameterizedType);

                    // 获取具体的类型，即<>里的Integer。注意返回的是数组，比如Converter<String,T>那么typeArguments = {String,T}
                    Type[] typeArguments = parameterizedType.getActualTypeArguments();

                    for (int i = 0; i < typeArguments.length; i++) {
                        Type typeArgument = typeArguments[i];
                        if (typeArgument instanceof Class) {
                            // 如果是Class的类型，填充到容器，这是因为比如在处理Converter<String,T>，T不是Class子类型，所以会被过滤掉
                            // 且注意这里的add(i)带了下标，表示放到最开头
                            // 比如前面的两个元素，最后actualTypeArguments = {String,Integer} --StringToIntegerConverter
                            // 所以Converter#getSourceType -> get(0)取到的就是String，Converter#getTargetType....也就满足Converter#accept了
                            actualTypeArguments.add(i, (Class) typeArgument);
                        }
                    }

                    // 递归处理父类(注意是类，不是父接口)
                    Class<?> superClass = rawClass.getSuperclass();
                    if (superClass != null) {
                        actualTypeArguments.addAll(findActualTypeArguments(superClass, interfaceClass));
                    }
                });

        // 0 = {Class@324} "class java.lang.String"
        // 1 = {Class@255} "class java.lang.Integer"
        return unmodifiableList(actualTypeArguments);
    }

    /**
     * Get the specified types' generic types(including super classes and interfaces) that are assignable from {@link ParameterizedType} interface
     *
     * @param type        the specified type
     * @param typeFilters one or more {@link Predicate}s to filter the {@link ParameterizedType} instance
     * @return non-null read-only {@link List}
     */
    static List<ParameterizedType> getGenericTypes(Type type, Predicate<ParameterizedType>... typeFilters) {

        Class<?> rawClass = getRawClass(type);

        if (rawClass == null) {
            return emptyList();
        }

        List<Type> genericTypes = new LinkedList<>();

        genericTypes.add(rawClass.getGenericSuperclass());
        genericTypes.addAll(asList(rawClass.getGenericInterfaces()));

        return unmodifiableList(
                filterList(genericTypes, TypeUtils::isParameterizedType)
                        .stream()
                        .map(ParameterizedType.class::cast)
                        .filter(and(typeFilters))
                        .collect(toList())
        );
    }

    /**
     * Get all generic types(including super classes and interfaces) that are assignable from {@link ParameterizedType} interface
     *
     * @param type        the specified type
     * @param typeFilters one or more {@link Predicate}s to filter the {@link ParameterizedType} instance
     * @return non-null read-only {@link List}
     */
    static List<ParameterizedType> getAllGenericTypes(Type type, Predicate<ParameterizedType>... typeFilters) {
        List<ParameterizedType> allGenericTypes = new LinkedList<>();
        // Add generic super classes 进去
        allGenericTypes.addAll(getAllGenericSuperClasses(type, typeFilters));
        // Add generic super interfaces 进去
        allGenericTypes.addAll(getAllGenericInterfaces(type, typeFilters));

        // wrap unmodifiable object

        // 比如type为String2IntegerConverter，那么就会有两个结果：
        // 0 = {ParameterizedTypeImpl@2023} "org.apache.dubbo.common.convert.StringConverter<java.lang.Integer>"
        // 1 = {ParameterizedTypeImpl@2024} "org.apache.dubbo.common.convert.Converter<java.lang.String, T>"
        return unmodifiableList(allGenericTypes);
    }

    /**
     * Get all generic super classes that are assignable from {@link ParameterizedType} interface
     *
     * @param type        the specified type
     * @param typeFilters one or more {@link Predicate}s to filter the {@link ParameterizedType} instance
     * @return non-null read-only {@link List}
     */
    static List<ParameterizedType> getAllGenericSuperClasses(Type type, Predicate<ParameterizedType>... typeFilters) {

        Class<?> rawClass = getRawClass(type);

        if (rawClass == null || rawClass.isInterface()) {
            return emptyList();
        }

        List<Class<?>> allTypes = new LinkedList<>();
        // Add current class
        allTypes.add(rawClass);
        // Add all super classes
        allTypes.addAll(getAllSuperClasses(rawClass, NON_OBJECT_TYPE_FILTER));

        List<ParameterizedType> allGenericSuperClasses = allTypes
                .stream()
                .map(Class::getGenericSuperclass)
                .filter(TypeUtils::isParameterizedType)
                .map(ParameterizedType.class::cast)
                .collect(Collectors.toList());

        return unmodifiableList(filterAll(allGenericSuperClasses, typeFilters));
    }

    /**
     * Get all generic interfaces that are assignable from {@link ParameterizedType} interface
     *
     * @param type        the specified type
     * @param typeFilters one or more {@link Predicate}s to filter the {@link ParameterizedType} instance
     * @return non-null read-only {@link List}
     */
    static List<ParameterizedType> getAllGenericInterfaces(Type type, Predicate<ParameterizedType>... typeFilters) {

        Class<?> rawClass = getRawClass(type);

        if (rawClass == null) {
            return emptyList();
        }

        List<Class<?>> allTypes = new LinkedList<>();
        // Add current class(下面结果的0)
        allTypes.add(rawClass);
        // Add all super classes(下面结果的1)
        allTypes.addAll(getAllSuperClasses(rawClass, NON_OBJECT_TYPE_FILTER));
        // Add all super interfaces(下面结果的2~5部分)
        allTypes.addAll(getAllInterfaces(rawClass));

        // eg:rawClass为String2IntegerConverter最终得到6个元素(其实就是把String2IntegerConverter的所有父类(去除Object)、父接口获取了)
        // 0 = {Class@1984} "class org.apache.dubbo.common.extension.convert.String2IntegerConverter"
        // 1 = {Class@1977} "class org.apache.dubbo.common.convert.StringToIntegerConverter"
        // 2 = {Class@1971} "interface org.apache.dubbo.common.convert.StringConverter"
        // 3 = {Class@1554} "interface org.apache.dubbo.common.convert.Converter"
        // 4 = {Class@1553} "interface org.apache.dubbo.common.lang.Prioritized"
        // 5 = {Class@326} "interface java.lang.Comparable"

        // 下面代码片段1是我自己根据片段2"翻译"的

        // 代码片段1-第一次操作，获取带泛型的父接口
        List<Type> typeList = new ArrayList<>();
        for(Class clz : allTypes ){
            Type[] genericInterfaces = clz.getGenericInterfaces();
            List<Type> types = asList(genericInterfaces);
            typeList.addAll(types);
        }
        // 此时typeList 为
        // 0 = {ParameterizedTypeImpl@2025} "org.apache.dubbo.common.convert.StringConverter<java.lang.Integer>"
        // 1 = {ParameterizedTypeImpl@2026} "org.apache.dubbo.common.convert.Converter<java.lang.String, T>"
        // 2 = {Class@1553} "interface org.apache.dubbo.common.lang.Prioritized"
        // 3 = {ParameterizedTypeImpl@2027} "java.lang.Comparable<org.apache.dubbo.common.lang.Prioritized>"

        // 代码片段1-第二次操作，获取泛型参数化/具体化的type
        List<ParameterizedType> parameterizedTypeList = new ArrayList<>();
        for(Type ty : typeList){
            if(ty instanceof ParameterizedType){
                parameterizedTypeList.add((ParameterizedType)ty);
            }
        }
        // 此时parameterizedTypeList为
        // 0 = {ParameterizedTypeImpl@2026} "org.apache.dubbo.common.convert.StringConverter<java.lang.Integer>"
        // 1 = {ParameterizedTypeImpl@2027} "org.apache.dubbo.common.convert.Converter<java.lang.String, T>"
        // 2 = {ParameterizedTypeImpl@2028} "java.lang.Comparable<org.apache.dubbo.common.lang.Prioritized>"

        // 代码片段2（源代码）
        List<ParameterizedType> allGenericInterfaces = allTypes
                .stream()
                .map(Class::getGenericInterfaces)
                .map(Arrays::asList)
                .flatMap(Collection::stream)
                .filter(TypeUtils::isParameterizedType)
                .map(ParameterizedType.class::cast)
                .collect(toList());

        // eg:typeFilters = t->Converter.class.isAssignableFrom(t); 获取所有Converter以及其sub的
        // filterAll(allGenericInterfaces, typeFilters)过滤的结果为
        // 0 = {ParameterizedTypeImpl@2222} "org.apache.dubbo.common.convert.StringConverter<java.lang.Integer>"
        // 1 = {ParameterizedTypeImpl@2223} "org.apache.dubbo.common.convert.Converter<java.lang.String, T>"
        return unmodifiableList(filterAll(allGenericInterfaces, typeFilters));
    }

    static String getClassName(Type type) {
        return getRawType(type).getTypeName();
    }

    static Set<String> getClassNames(Iterable<? extends Type> types) {
        return stream(types.spliterator(), false)
                .map(TypeUtils::getClassName)
                .collect(toSet());
    }
}
