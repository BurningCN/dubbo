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
package org.apache.dubbo.metadata.definition.builder;

import org.apache.dubbo.metadata.definition.TypeDefinitionBuilder;
import org.apache.dubbo.metadata.definition.model.TypeDefinition;
import org.apache.dubbo.metadata.definition.util.ClassUtils;
import org.apache.dubbo.metadata.definition.util.JaketConfigurationUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * 2015/1/27.
 */
public final class DefaultTypeBuilder {

    public static TypeDefinition build(Class<?> clazz, Map<Class<?>, TypeDefinition> typeCache) {
//        final String canonicalName = clazz.getCanonicalName();
        final String name = clazz.getName();

        TypeDefinition td = new TypeDefinition(name);
        // Try to get a cached definition  这里做缓存，防止重复构建，比如都是同一类型，直接返回一个TypeDefinition即可
        // todo need pr 下面应该放在开头
        if (typeCache.containsKey(clazz)) {
            return typeCache.get(clazz);
        }

        // Primitive type
        if (!JaketConfigurationUtils.needAnalyzing(clazz)) {
            return td;
        }

        // Custom type，和前面的一样，todo need pr 这里没有任何卵用，到最后return的上一句，会覆盖掉下面put的
        TypeDefinition ref = new TypeDefinition(name);
        ref.set$ref(name);
        typeCache.put(clazz, ref);

        // todo need pr-pr !isSimpleType(clazz) ，这里如果是原生类型，不需要考虑其字段，因为在TypeDefinitionBuilder有一个步骤发现if (isSimpleType(clazz))则会置属性为null，下面又白计算了
        //  所以我们可以在下面计算前加上if (!isSimpleType(clazz)) { ，并把TypeDefinitionBuilder#if (isSimpleType(clazz)) {的步骤也一并去掉，详见myMQ
        List<Field> fields = ClassUtils.getNonStaticFields(clazz);
        for (Field field : fields) {
            String fieldName = field.getName();
            Class<?> fieldClass = field.getType();//注意这两个api方法 --- > interface java.util.Map
            Type fieldType = field.getGenericType();//  --- > java.util.Map<java.lang.String, java.lang.String>

            // 对属性也进行构建对应的TypeDefinition
            TypeDefinition fieldTd = TypeDefinitionBuilder.build(fieldType, fieldClass, typeCache);
            // 在存到td的属性集合中
            td.getProperties().put(fieldName, fieldTd);
        }

        typeCache.put(clazz, td);
        return td;
    }

    private DefaultTypeBuilder() {
    }
}
