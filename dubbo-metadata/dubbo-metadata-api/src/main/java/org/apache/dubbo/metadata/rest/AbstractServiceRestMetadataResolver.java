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
package org.apache.dubbo.metadata.rest;

import org.apache.dubbo.common.utils.MethodComparator;
import org.apache.dubbo.common.utils.ServiceAnnotationResolver;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.config.annotation.Service;
import org.apache.dubbo.metadata.definition.MethodDefinitionBuilder;
import org.apache.dubbo.metadata.definition.model.MethodDefinition;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static java.util.Collections.sort;
import static java.util.Collections.unmodifiableMap;
import static org.apache.dubbo.common.extension.ExtensionLoader.getExtensionLoader;
import static org.apache.dubbo.common.function.ThrowableFunction.execute;
import static org.apache.dubbo.common.utils.AnnotationUtils.isAnyAnnotationPresent;
import static org.apache.dubbo.common.utils.ClassUtils.forName;
import static org.apache.dubbo.common.utils.ClassUtils.getAllInterfaces;
import static org.apache.dubbo.common.utils.MethodUtils.excludedDeclaredClass;
import static org.apache.dubbo.common.utils.MethodUtils.getAllMethods;
import static org.apache.dubbo.common.utils.MethodUtils.overrides;

/**
 * The abstract {@link ServiceRestMetadataResolver} class to provider some template methods assemble the instance of
 * {@link ServiceRestMetadata} will extended by the sub-classes.
 *
 * @since 2.7.6
 */
public abstract class AbstractServiceRestMetadataResolver implements ServiceRestMetadataResolver {

    private final Map<String, List<AnnotatedMethodParameterProcessor>> parameterProcessorsMap;

    public AbstractServiceRestMetadataResolver() {
        this.parameterProcessorsMap = loadAnnotatedMethodParameterProcessors();
    }

    @Override
    public final boolean supports(Class<?> serviceType) {
        // 1.必须得实现接口
        // 2.类的头上必须有@DubboService/@Service注解
        // 3.满足子类实现的supports0方法，比如jaxrs，serviceType的类头上面必须有@Path("/xx")的注解，而spring-mvc则必须有@Controller
        return isImplementedInterface(serviceType) && isServiceAnnotationPresent(serviceType) && supports0(serviceType);
    }

    protected final boolean isImplementedInterface(Class<?> serviceType) {
        return !getAllInterfaces(serviceType).isEmpty();
    }

    protected final boolean isServiceAnnotationPresent(Class<?> serviceType) {
        return isAnyAnnotationPresent(serviceType, DubboService.class, Service.class,
                com.alibaba.dubbo.config.annotation.Service.class);
    }

    /**
     * internal support method
     *
     * @param serviceType Dubbo Service interface or type
     * @return If supports, return <code>true</code>, or <code>false</code>
     */
    protected abstract boolean supports0(Class<?> serviceType);

    @Override
    public final ServiceRestMetadata resolve(Class<?> serviceType) {

        ServiceRestMetadata serviceRestMetadata = new ServiceRestMetadata();

        // Process ServiceRestMetadata（将后者进行解析填充到前者）
        processServiceRestMetadata(serviceRestMetadata, serviceType);

        // Process RestMethodMetadata
        processAllRestMethodMetadata(serviceRestMetadata, serviceType);

        return serviceRestMetadata;
    }

    /**
     * Process the service type including the sub-routines:
     * <ul>
     *     <li>{@link ServiceRestMetadata#setServiceInterface(String)}</li>
     *     <li>{@link ServiceRestMetadata#setVersion(String)}</li>
     *     <li>{@link ServiceRestMetadata#setGroup(String)}</li>
     * </ul>
     *
     * @param serviceRestMetadata {@link ServiceRestMetadata}
     * @param serviceType         Dubbo Service interface or type
     */
    protected void processServiceRestMetadata(ServiceRestMetadata serviceRestMetadata, Class<?> serviceType) {
        ServiceAnnotationResolver resolver = new ServiceAnnotationResolver(serviceType);
        // 填充ServiceRestMetadata的三个普通属性
        serviceRestMetadata.setServiceInterface(resolver.resolveInterfaceClassName());
        serviceRestMetadata.setVersion(resolver.resolveVersion());
        serviceRestMetadata.setGroup(resolver.resolveGroup());
    }

    /**
     * Process all {@link RestMethodMetadata}
     *
     * @param serviceRestMetadata {@link ServiceRestMetadata}
     * @param serviceType         Dubbo Service interface or type
     */
    protected void processAllRestMethodMetadata(ServiceRestMetadata serviceRestMetadata, Class<?> serviceType) {
        // 加载服务接口类并返回
        Class<?> serviceInterfaceClass = resolveServiceInterfaceClass(serviceRestMetadata, serviceType);
        // resolveServiceMethodsMap的两个参数一般是实现类和实现类的接口
        // 返回的结果 映射关系为，实现类方法A : 接口方法A
        Map<Method, Method> serviceMethodsMap = resolveServiceMethodsMap(serviceType, serviceInterfaceClass);
        for (Map.Entry<Method, Method> entry : serviceMethodsMap.entrySet()) {
            // try the overrider method first
            Method serviceMethod = entry.getKey();
            // If failed, it indicates the overrider method does not contain metadata , then try the declared method
            // 如果失败，则表明覆盖方法不包含元数据，然后尝试声明的方法
            // 下面的方法一般会返回true，即成功，如果失败则用entry的value部分进行，此时value是接口的方法。注意最后一个是Consumer。
            if (!processRestMethodMetadata(serviceMethod, serviceType, serviceInterfaceClass, serviceRestMetadata.getMeta()::add)) {
                Method declaredServiceMethod = entry.getValue();
                processRestMethodMetadata(declaredServiceMethod, serviceType, serviceInterfaceClass,
                        serviceRestMetadata.getMeta()::add);
            }
        }
    }

    /**
     * Resolve a map of all public services methods from the specified service type and its interface class, whose key is the
     * declared method, and the value is the overrider method
     *
     * @param serviceType           the service interface implementation class
     * @param serviceInterfaceClass the service interface class
     * @return non-null read-only {@link Map}
     */
    protected Map<Method, Method> resolveServiceMethodsMap(Class<?> serviceType, Class<?> serviceInterfaceClass) {
        Map<Method, Method> serviceMethodsMap = new LinkedHashMap<>();
        // exclude the public methods declared in java.lang.Object.class
        List<Method> declaredServiceMethods = new ArrayList<>(getAllMethods(serviceInterfaceClass, excludedDeclaredClass(Object.class)));
        List<Method> serviceMethods = new ArrayList<>(getAllMethods(serviceType, excludedDeclaredClass(Object.class)));

        // sort methods
        sort(declaredServiceMethods, MethodComparator.INSTANCE);
        sort(serviceMethods, MethodComparator.INSTANCE);

        for (Method declaredServiceMethod : declaredServiceMethods) {
            for (Method serviceMethod : serviceMethods) {
                // 找到后者实现了前者的方法填充到集合
                if (overrides(serviceMethod, declaredServiceMethod)) {
                    // 映射关系为，实现类方法A : 接口方法A
                    serviceMethodsMap.put(serviceMethod, declaredServiceMethod);
                    continue;
                }
            }
        }
        // make them to be read-only
        return unmodifiableMap(serviceMethodsMap);
    }

    /**
     * Resolve the class of Dubbo Service interface
     *
     * @param serviceRestMetadata {@link ServiceRestMetadata}
     * @param serviceType         Dubbo Service interface or type
     * @return non-null
     * @throws RuntimeException If the class is not found, the {@link RuntimeException} wraps the cause will be thrown
     */
    protected Class<?> resolveServiceInterfaceClass(ServiceRestMetadata serviceRestMetadata, Class<?> serviceType) {
        return execute(serviceType.getClassLoader(), classLoader -> {
            String serviceInterface = serviceRestMetadata.getServiceInterface();
            // 加载接口
            return forName(serviceInterface, classLoader);
        });
    }

    /**
     * Process the single {@link RestMethodMetadata} by the specified {@link Consumer} if present
     *
     * @param serviceMethod         Dubbo Service method
     * @param serviceType           Dubbo Service interface or type
     * @param serviceInterfaceClass The type of Dubbo Service interface
     * @param metadataToProcess     {@link RestMethodMetadata} to process if present
     * @return if processed successfully, return <code>true</code>, or <code>false</code>
     */
    protected boolean processRestMethodMetadata(Method serviceMethod, Class<?> serviceType,
                                                Class<?> serviceInterfaceClass,
                                                Consumer<RestMethodMetadata> metadataToProcess) {

        // 验证方法上面是否含有指定注解，看实现类
        if (!isRestCapableMethod(serviceMethod, serviceType, serviceInterfaceClass)) {
            return false;
        }

        // 获取请求路由值，比如StandardRestService#form就是 "/form"，看实现类
        String requestPath = resolveRequestPath(serviceMethod, serviceType, serviceInterfaceClass); // requestPath is required

        if (requestPath == null) {
            return false;
        }

        // 获取请求方法，比如jaxrs的@HttpMethod("GET")的"GET"（赋值requestMethod），@GET的元注解注意下
        String requestMethod = resolveRequestMethod(serviceMethod, serviceType, serviceInterfaceClass); // requestMethod is required

        if (requestMethod == null) {
            return false;
        }

        RestMethodMetadata metadata = new RestMethodMetadata();

        MethodDefinition methodDefinition = resolveMethodDefinition(serviceMethod, serviceType, serviceInterfaceClass);
        // Set MethodDefinition
        metadata.setMethod(methodDefinition);

        // 处理方法的参数，以及上面的注解
        // process the annotated method parameters
        processAnnotatedMethodParameters(serviceMethod, serviceType, serviceInterfaceClass, metadata);

        // process produces
        Set<String> produces = new LinkedHashSet<>();
        // 处理@Producers注解,将注解里面的值填充到produces集合中
        processProduces(serviceMethod, serviceType, serviceInterfaceClass, produces);

        // process consumes
        Set<String> consumes = new LinkedHashSet<>();
        // 处理@Consumes注解,将注解里面的值填充到集合中
        processConsumes(serviceMethod, serviceType, serviceInterfaceClass, consumes);

        // Initialize RequestMetadata
        RequestMetadata request = metadata.getRequest();
        request.setPath(requestPath);
        request.setMethod(requestMethod);
        request.setProduces(produces);
        request.setConsumes(consumes);

        // Post-Process 空实现
        postResolveRestMethodMetadata(serviceMethod, serviceType, serviceInterfaceClass, metadata);

        // Accept RestMethodMetadata consumer的逻辑，一般是add到集合，比如 serviceRestMetadata.getMeta()::add
        metadataToProcess.accept(metadata);

        return true;
    }

    /**
     * Test the service method is capable of REST or not?
     *
     * @param serviceMethod         Dubbo Service method
     * @param serviceType           Dubbo Service interface or type
     * @param serviceInterfaceClass The type of Dubbo Service interface
     * @return If capable, return <code>true</code>
     */
    protected abstract boolean isRestCapableMethod(Method serviceMethod, Class<?> serviceType, Class<?>
            serviceInterfaceClass);

    /**
     * Resolve the request method
     *
     * @param serviceMethod         Dubbo Service method
     * @param serviceType           Dubbo Service interface or type
     * @param serviceInterfaceClass The type of Dubbo Service interface
     * @return if can't be resolve, return <code>null</code>
     */
    protected abstract String resolveRequestMethod(Method serviceMethod, Class<?> serviceType, Class<?>
            serviceInterfaceClass);

    /**
     * Resolve the request path
     *
     * @param serviceMethod         Dubbo Service method
     * @param serviceType           Dubbo Service interface or type
     * @param serviceInterfaceClass The type of Dubbo Service interface
     * @return if can't be resolve, return <code>null</code>
     */
    protected abstract String resolveRequestPath(Method serviceMethod, Class<?> serviceType, Class<?>
            serviceInterfaceClass);

    /**
     * Resolve the {@link MethodDefinition}
     *
     * @param serviceMethod         Dubbo Service method
     * @param serviceType           Dubbo Service interface or type
     * @param serviceInterfaceClass The type of Dubbo Service interface
     * @return if can't be resolve, return <code>null</code>
     * @see MethodDefinitionBuilder
     */
    protected MethodDefinition resolveMethodDefinition(Method serviceMethod, Class<?> serviceType,
                                                       Class<?> serviceInterfaceClass) {
        MethodDefinitionBuilder builder = new MethodDefinitionBuilder();
        return builder.build(serviceMethod);
    }

    private void processAnnotatedMethodParameters(Method serviceMethod, Class<?> serviceType,
                                                  Class<?> serviceInterfaceClass, RestMethodMetadata metadata) {
        int paramCount = serviceMethod.getParameterCount();
        // 获取所有参数
        Parameter[] parameters = serviceMethod.getParameters();
        for (int i = 0; i < paramCount; i++) {
            Parameter parameter = parameters[i];
            // Add indexed parameter name 下标和参数的映射
            metadata.addIndexToName(i, parameter.getName());
            // 处理参数的注解信息
            processAnnotatedMethodParameter(parameter, i, serviceMethod, serviceType, serviceInterfaceClass, metadata);
        }
    }

    private void processAnnotatedMethodParameter(Parameter parameter, int parameterIndex, Method serviceMethod,
                                                 Class<?> serviceType, Class<?> serviceInterfaceClass,
                                                 RestMethodMetadata metadata) {
        // 获取参数上的注解，比如StandardRestService#form方法的参数签名为@FormParam("f") String form
        // 就是获取这里的@FormParam("f")注解
        Annotation[] annotations = parameter.getAnnotations();
        for (Annotation annotation : annotations) {
            // eg 上面的@FormParam("f")注解的名称就是"javax.ws.rs.FormParam"，也就是注解的全限定名称
            String annotationType = annotation.annotationType().getName();
            // parameterProcessorsMap就是开始加载的spi扩展实例，专门处理参数的，这里根据annotationType获取对应的spi实例
            // 比如上面的"javax.ws.rs.FormParam"对应的spi实例就是 FormParamParameterProcessor/ParamAnnotationParameterProcessor(子父类关系)
            // StandardRestService#pathVariables，里面的参数注解@PathParam("p1")，对应的 javax.ws.rs.PathParam 是没有对应的spi实例的，下面的会取getOrDefault的default，即emptyList()
            parameterProcessorsMap.getOrDefault(annotationType, emptyList())
                    .forEach(processor -> {
                        processor.process(annotation, parameter, parameterIndex, serviceMethod, serviceType,
                                serviceInterfaceClass, metadata);
                    });
        }
    }

    protected abstract void processProduces(Method serviceMethod, Class<?> serviceType, Class<?>
            serviceInterfaceClass,
                                            Set<String> produces);

    protected abstract void processConsumes(Method serviceMethod, Class<?> serviceType, Class<?>
            serviceInterfaceClass,
                                            Set<String> consumes);

    protected void postResolveRestMethodMetadata(Method serviceMethod, Class<?> serviceType,
                                                 Class<?> serviceInterfaceClass, RestMethodMetadata metadata) {
    }

    private static Map<String, List<AnnotatedMethodParameterProcessor>> loadAnnotatedMethodParameterProcessors() {
        Map<String, List<AnnotatedMethodParameterProcessor>> parameterProcessorsMap = new LinkedHashMap<>();
        getExtensionLoader(AnnotatedMethodParameterProcessor.class)
                .getSupportedExtensionInstances()
                .forEach(processor -> {
                    List<AnnotatedMethodParameterProcessor> processors =
                            // 注意填充的key不是spi文件的扩展名，而是调用了getAnnotationType，7个有各自的值（在RestMetadataConstants），自己注意下
                            parameterProcessorsMap.computeIfAbsent(processor.getAnnotationType(), k -> new LinkedList<>());
                    processors.add(processor);
                });
        return parameterProcessorsMap;
    }
}
