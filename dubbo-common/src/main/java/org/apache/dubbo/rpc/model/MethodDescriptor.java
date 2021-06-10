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
package org.apache.dubbo.rpc.model;

import org.apache.dubbo.common.utils.ReflectUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.stream.Stream;

import static org.apache.dubbo.common.constants.CommonConstants.$INVOKE;
import static org.apache.dubbo.common.constants.CommonConstants.$INVOKE_ASYNC;


// OK
public class MethodDescriptor {
    private final Method method;
    private final String paramDesc;
    private final String[] compatibleParamSignatures;
    private final Class<?>[] parameterClasses;
    private final Class<?> returnClass;
    private final Type[] returnTypes;
    private final String methodName;
    private final boolean generic;

    public MethodDescriptor(Method method) {
        this.method = method;
        this.parameterClasses = method.getParameterTypes();
        this.returnClass = method.getReturnType();
        // 注意，进去
        this.returnTypes = ReflectUtils.getReturnTypes(method);
        // 比如EchoService#$echo 则 Ljava/lang/Object;
        this.paramDesc = ReflectUtils.getDesc(parameterClasses);
        // java.lang.Object
        this.compatibleParamSignatures = Stream.of(parameterClasses)
                .map(Class::getName)
                .toArray(String[]::new);
        this.methodName = method.getName();
        this.generic = (methodName.equals($INVOKE) || methodName.equals($INVOKE_ASYNC)) && parameterClasses.length == 3;
        //interface Demo {
        //        CompletableFuture<String> hello(List<String> list);
        //    }
        //this = {MethodDescriptor@1993}
        // method = {Method@1970} "public abstract java.util.concurrent.CompletableFuture org.apache.dubbo.rpc.model.MethodDescriptorTest$Demo.hello(java.util.List)"
        // paramDesc = "Ljava/util/List;"
        // compatibleParamSignatures = {String[1]@1998}
        //  0 = "java.util.List"
        // parameterClasses = {Class[1]@1994}
        //  0 = {Class@222} "interface java.util.List"
        // returnClass = {Class@1995} "class java.util.concurrent.CompletableFuture"
        // returnTypes = {Type[2]@1996}
        //  0 = {Class@324} "class java.lang.String"
        //  1 = {Class@324} "class java.lang.String"
        // methodName = "hello"
        // generic = false
    }

    public boolean matchParams (String params) {
        return paramDesc.equalsIgnoreCase(params);
    }

    public Method getMethod() {
        return method;
    }

    public String getParamDesc() {
        return paramDesc;
    }

    public String[] getCompatibleParamSignatures() {
        return compatibleParamSignatures;
    }

    public Class<?>[] getParameterClasses() {
        return parameterClasses;
    }

    public Class<?> getReturnClass() {
        return returnClass;
    }

    public Type[] getReturnTypes() {
        return returnTypes;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean isGeneric() {
        return generic;
    }

}
