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

import org.apache.dubbo.common.utils.CollectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ServiceModel and ServiceMetadata are to some extend duplicated with each other.
 * We should merge them in the future.
 *
 * ServiceModel和ServiceMetadata在某种程度上是相互复制的。
 * 我们将来应该合并它们。
 */
// OK
public class ServiceDescriptor {
    private final String serviceName;
    private final Class<?> serviceInterfaceClass;
    // to accelerate search
    private final Map<String, List<MethodDescriptor>> methods = new HashMap<>();
    private final Map<String, Map<String, MethodDescriptor>> descToMethods = new HashMap<>();

    // gx
    public ServiceDescriptor(Class<?> interfaceClass) {
        this.serviceInterfaceClass = interfaceClass;
        this.serviceName = interfaceClass.getName();
        // 进去
        initMethods();
    }

    private void initMethods() {
        Method[] methodsToExport = this.serviceInterfaceClass.getMethods();
        for (Method method : methodsToExport) {
            method.setAccessible(true); // todo need pr  // 其实不需要，因为前面getMethods就是true的
            // 注意methods的结构，可能有多个同名的方法。取出或创建list
            List<MethodDescriptor> methodModels = methods.computeIfAbsent(method.getName(), (k) -> new ArrayList<>(1));
            // 填充到list，MethodDescriptor构造器里面就获取了方法的一些关键信息，进去
            methodModels.add(new MethodDescriptor(method));
        }

        // map的forEach
        methods.forEach((methodName, methodList) -> {
            Map<String, MethodDescriptor> descMap = descToMethods.computeIfAbsent(methodName, k -> new HashMap<>());
            methodList.forEach(methodDescriptor -> descMap.put(methodDescriptor.getParamDesc(), methodDescriptor));
        });
        // descToMethods eg 如下：{ "$echo" : {"Ljava/lang/Object;" : MethodDescriptor}}

        //"$echo" -> {HashMap@938}  size = 1
        // key = "$echo"
        // value = {HashMap@938}  size = 1
        //  "Ljava/lang/Object;" -> {MethodDescriptor@943}
        //   key = "Ljava/lang/Object;"
        //   value = {MethodDescriptor@943}
        //    method = {Method@944} "public abstract java.lang.Object org.apache.dubbo.rpc.service.EchoService.$echo(java.lang.Object)"
        //    paramDesc = "Ljava/lang/Object;"
        //    compatibleParamSignatures = {String[1]@945}
        //    parameterClasses = {Class[1]@946}
        //    returnClass = {Class@328} "class java.lang.Object"
        //    returnTypes = {Type[2]@947}
        //    methodName = "$echo"
        //    generic = false
    }

    public String getServiceName() {
        return serviceName;
    }

    public Class<?> getServiceInterfaceClass() {
        return serviceInterfaceClass;
    }

    // gx
    public Set<MethodDescriptor> getAllMethods() {
        Set<MethodDescriptor> methodModels = new HashSet<>();

        methods.forEach((k, v) -> methodModels.addAll(v));
        return methodModels;
    }

    /**
     * Does not use Optional as return type to avoid potential performance decrease.
     *
     * @param methodName
     * @param params
     * @return
     */
    // 这个肯定就是服务调用者触发的了，需要调用哪个服务的哪个方法
    public MethodDescriptor getMethod(String methodName, String params) {
        Map<String, MethodDescriptor> methods = descToMethods.get(methodName);
        if (CollectionUtils.isNotEmptyMap(methods)) {
            return methods.get(params);
        }
        return null;
    }

    /**
     * Does not use Optional as return type to avoid potential performance decrease.
     *
     * @param methodName
     * @param paramTypes
     * @return
     */
    public MethodDescriptor getMethod(String methodName, Class<?>[] paramTypes) {
        List<MethodDescriptor> methodModels = methods.get(methodName);
        if (CollectionUtils.isNotEmpty(methodModels)) {
            // 遍历MethodDescriptor列表
            for (int i = 0; i < methodModels.size(); i++) {
                MethodDescriptor descriptor = methodModels.get(i);
                // 判断参数列表类型和方法庙湖存储的是否一致
                if (Arrays.equals(paramTypes, descriptor.getParameterClasses())) {
                    return descriptor;
                }
            }
        }
        return null;
    }

    public List<MethodDescriptor> getMethods(String methodName) {
        return methods.get(methodName);
    }

}
