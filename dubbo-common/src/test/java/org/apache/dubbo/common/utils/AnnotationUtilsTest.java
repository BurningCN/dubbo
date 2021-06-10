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

import org.apache.dubbo.common.extension.Adaptive;
import org.apache.dubbo.common.function.Streams;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.config.annotation.Service;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.apache.dubbo.common.utils.AnnotationUtils.excludedType;
import static org.apache.dubbo.common.utils.AnnotationUtils.findAnnotation;
import static org.apache.dubbo.common.utils.AnnotationUtils.findMetaAnnotation;
import static org.apache.dubbo.common.utils.AnnotationUtils.findMetaAnnotations;
import static org.apache.dubbo.common.utils.AnnotationUtils.getAllDeclaredAnnotations;
import static org.apache.dubbo.common.utils.AnnotationUtils.getAllMetaAnnotations;
import static org.apache.dubbo.common.utils.AnnotationUtils.getAnnotation;
import static org.apache.dubbo.common.utils.AnnotationUtils.getAttribute;
import static org.apache.dubbo.common.utils.AnnotationUtils.getDeclaredAnnotations;
import static org.apache.dubbo.common.utils.AnnotationUtils.getMetaAnnotations;
import static org.apache.dubbo.common.utils.AnnotationUtils.getValue;
import static org.apache.dubbo.common.utils.AnnotationUtils.isAnnotationPresent;
import static org.apache.dubbo.common.utils.AnnotationUtils.isAnyAnnotationPresent;
import static org.apache.dubbo.common.utils.AnnotationUtils.isSameType;
import static org.apache.dubbo.common.utils.AnnotationUtils.isType;
import static org.apache.dubbo.common.utils.MethodUtils.findMethod;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AnnotationUtils} Test
 *
 * @since 2.7.6
 */
public class AnnotationUtilsTest {

    @Test
    public void testIsType() throws NoSuchMethodException {
        // null checking 进去
        assertFalse(isType(null));
        // Method checking findMethod进去
        assertFalse(isType(findMethod(A.class, "execute")));
        // Class checking 进去
        assertTrue(isType(A.class));
    }

    @Test
    public void testIsSameType() {

        Service annotation = A.class.getAnnotation(Service.class);
        System.out.println(Objects.equals(annotation.getClass(),annotation.annotationType()));
        // isSameType进去
        assertTrue(isSameType(A.class.getAnnotation(Service.class), Service.class));
        // 进去
        assertFalse(isSameType(A.class.getAnnotation(Service.class), Deprecated.class));
        assertFalse(isSameType(A.class.getAnnotation(Service.class), null));
        assertFalse(isSameType(null, Deprecated.class));
        assertFalse(isSameType(null, null));
    }

    @Test
    public void testExcludedType() {
        // excludedType方法返回predicate，进去
        assertFalse(excludedType(Service.class).test(A.class.getAnnotation(Service.class)));
        assertTrue(excludedType(Service.class).test(A.class.getAnnotation(Deprecated.class)));
    }

    @Test
    public void testGetAttribute() {
        Annotation annotation = A.class.getAnnotation(Service.class);
        // @Service(interfaceName = "java.lang.CharSequence", interfaceClass = CharSequence.class)
        // 获取注解中属性key为interfaceName的属性值，即获取上面 java.lang.CharSequence，进去
        assertEquals("java.lang.CharSequence", getAttribute(annotation, "interfaceName"));

        // 属性值是Class
        assertEquals(CharSequence.class, getAttribute(annotation, "interfaceClass"));

        // 下面四个没有值
        assertEquals("", getAttribute(annotation, "version"));
        assertEquals("", getAttribute(annotation, "group"));
        assertEquals("", getAttribute(annotation, "path"));

        // @Service注解里面export的默认值就是true
        assertEquals(true, getAttribute(annotation, "export"));
        assertEquals(false, getAttribute(annotation, "deprecated"));
    }

    @Test
    public void testGetValue() {
        Adaptive adaptive = A.class.getAnnotation(Adaptive.class);
        System.out.println(adaptive.annotationType() == Adaptive.class); // true
        // 获取@Adaptive注解的value属性值，一般注解都会有一个value属性，进去
        String[] value = getValue(adaptive);
        assertEquals(asList("a", "b", "c"), asList(value));
    }

    @Test
    public void testGetDeclaredAnnotations() {
        // 获取A类上的所有注解（三个注解），进去
        List<Annotation> annotations = getDeclaredAnnotations(A.class);
        assertADeclaredAnnotations(annotations, 0);

        // 带了predicate，只筛选出@Service注解
        annotations = getDeclaredAnnotations(A.class, a -> isSameType(a, Service.class));
        assertEquals(1, annotations.size());
        Service service = (Service) annotations.get(0);
        assertEquals("java.lang.CharSequence", service.interfaceName());
        assertEquals(CharSequence.class, service.interfaceClass());
    }

    @Test
    public void testGetAllDeclaredAnnotations() {
        // 这里的All就是获取A类和A的父类、父类的父类的注解
        List<Annotation> annotations = getAllDeclaredAnnotations(A.class);
        assertADeclaredAnnotations(annotations, 0);

        // B类是继承A类的，同时B类上面有一个自己的注解，那么最后会返回4个注解（B的1个+A的3个）
        annotations = getAllDeclaredAnnotations(B.class);
        // B上的注解
        assertTrue(isSameType(annotations.get(0), Service5.class));
        assertADeclaredAnnotations(annotations, 1);

        // C继承自B.... 最后返回5个注解
        annotations = new LinkedList<>(getAllDeclaredAnnotations(C.class));
        // C上的注解
        assertTrue(isSameType(annotations.get(0), MyAdaptive.class));
        // B上的注解
        assertTrue(isSameType(annotations.get(1), Service5.class));
        // A上注解，进去
        assertADeclaredAnnotations(annotations, 2);

        // 获取A类的execute方法，这个方法上面也有注解，传进去的Method也是被AnnotatedElement接受的（之前A.class也是）
        // 注意这个方法 和 前面的是重载的两个方法，第一个参数类型不同
        annotations = getAllDeclaredAnnotations(findMethod(A.class, "execute"));
        // execute方法上的注解就是MyAdaptive，强转下
        MyAdaptive myAdaptive = (MyAdaptive) annotations.get(0);
        // 获取注解的值（这里是直接拿到注解获取值，工具类内部是利用反射获取的，详见getAttribute）
        assertArrayEquals(new String[]{"e"}, myAdaptive.value());

        annotations = getAllDeclaredAnnotations(findMethod(B.class, "execute"));
        Adaptive adaptive = (Adaptive) annotations.get(0);
        assertArrayEquals(new String[]{"f"}, adaptive.value());
    }

    @Test
    public void testGetMetaAnnotations() {
        // 获取元注解（就是注解上的注解），第二个参数限定只要Inherited注解，进去
        List<Annotation> metaAnnotations = getMetaAnnotations(Service.class, a -> isSameType(a, Inherited.class));
        assertEquals(1, metaAnnotations.size());
        // metaAnnotations.get(0).annotationType() ---> 将Annotation转化为Class
        assertEquals(Inherited.class, metaAnnotations.get(0).annotationType());

        // 没带谓词参数，所以拿到了两个元注解
        metaAnnotations = getMetaAnnotations(Service.class);
        assertEquals(2, metaAnnotations.size());
        assertEquals(Inherited.class, metaAnnotations.get(0).annotationType());
        assertEquals(Deprecated.class, metaAnnotations.get(1).annotationType());
    }

    @Test
    public void testGetAllMetaAnnotations() {
        // 和前面相比多了一个All，进去
        List<Annotation> metaAnnotations = getAllMetaAnnotations(Service5.class);
        int offset = 0;
        assertEquals(9, metaAnnotations.size());
        assertEquals(Inherited.class, metaAnnotations.get(offset++).annotationType());
        assertEquals(Service4.class, metaAnnotations.get(offset++).annotationType());
        assertEquals(Inherited.class, metaAnnotations.get(offset++).annotationType());
        assertEquals(Service3.class, metaAnnotations.get(offset++).annotationType());
        assertEquals(Inherited.class, metaAnnotations.get(offset++).annotationType());
        assertEquals(Service2.class, metaAnnotations.get(offset++).annotationType());
        assertEquals(Inherited.class, metaAnnotations.get(offset++).annotationType());
        assertEquals(DubboService.class, metaAnnotations.get(offset++).annotationType());
        assertEquals(Inherited.class, metaAnnotations.get(offset++).annotationType());

        metaAnnotations = getAllMetaAnnotations(MyAdaptive.class);
        offset = 0;
        assertEquals(2, metaAnnotations.size());
        assertEquals(Inherited.class, metaAnnotations.get(offset++).annotationType());
        assertEquals(Adaptive.class, metaAnnotations.get(offset++).annotationType());
    }


    @Test
    public void testIsAnnotationPresent() {
        assertTrue(isAnnotationPresent(A.class, true, Service.class));
        // 进去
        assertTrue(isAnnotationPresent(A.class, true, Service.class, com.alibaba.dubbo.config.annotation.Service.class));
        assertTrue(isAnnotationPresent(A.class, Service.class));
        assertTrue(isAnnotationPresent(A.class, "org.apache.dubbo.config.annotation.Service"));
        // 进去
        assertTrue(AnnotationUtils.isAllAnnotationPresent(A.class, Service.class, Service.class, com.alibaba.dubbo.config.annotation.Service.class));
        assertTrue(isAnnotationPresent(A.class, Deprecated.class));
    }

    @Test
    public void testIsAnyAnnotationPresent() {
        // 进去
        assertTrue(isAnyAnnotationPresent(A.class, Service.class, com.alibaba.dubbo.config.annotation.Service.class, Deprecated.class));
        assertTrue(isAnyAnnotationPresent(A.class, Service.class, com.alibaba.dubbo.config.annotation.Service.class));
        assertTrue(isAnyAnnotationPresent(A.class, Service.class, Deprecated.class));
        assertTrue(isAnyAnnotationPresent(A.class, com.alibaba.dubbo.config.annotation.Service.class, Deprecated.class));
        assertTrue(isAnyAnnotationPresent(A.class, Service.class));
        assertTrue(isAnyAnnotationPresent(A.class, com.alibaba.dubbo.config.annotation.Service.class));
        assertTrue(isAnyAnnotationPresent(A.class, Deprecated.class));
    }

    @Test
    public void testGetAnnotation() {
        // 第二个传入的是注解的全限定名，进去
        assertNotNull(getAnnotation(A.class, "org.apache.dubbo.config.annotation.Service"));
        assertNotNull(getAnnotation(A.class, "com.alibaba.dubbo.config.annotation.Service"));
        assertNotNull(getAnnotation(A.class, "org.apache.dubbo.common.extension.Adaptive"));

        // 内部是在最后一步getAnnotation(x)的时候返回null，进去
        assertNull(getAnnotation(A.class, "java.lang.Deprecated"));

        // 内部是在Annotation.class.isAssignableFrom(annotationType)不通过导致返回null，因为String.Class肯定不是注解类型，进去
        assertNull(getAnnotation(A.class, "java.lang.String"));

        // 内部是在加载类Class.forName的时候返回null，进去
        assertNull(getAnnotation(A.class, "NotExistedClass"));
    }

    @Test
    public void testFindAnnotation() throws NoSuchMethodException {
        // 进去
        Service service = findAnnotation(A.class, Service.class);
        assertEquals("java.lang.CharSequence", service.interfaceName());
        assertEquals(CharSequence.class, service.interfaceClass());

        // B类上的直接注解是@Service5，但是B继承了A，A是有Service注解的，进去
        service = findAnnotation(B.class, Service.class);
        assertEquals(CharSequence.class, service.interfaceClass());
    }

    @Test
    public void testFindMetaAnnotations() {
        List<DubboService> services = findMetaAnnotations(B.class, DubboService.class);
        assertEquals(1, services.size());

        DubboService service = services.get(0);
        assertEquals("", service.interfaceName());
        assertEquals(Cloneable.class, service.interfaceClass());

        services = findMetaAnnotations(Service5.class, DubboService.class);
        assertEquals(1, services.size());

        service = services.get(0);
        assertEquals("", service.interfaceName());
        assertEquals(Cloneable.class, service.interfaceClass());
    }

    @Test
    public void testFindMetaAnnotation() {
        DubboService service = findMetaAnnotation(B.class, DubboService.class);
        assertEquals(Cloneable.class, service.interfaceClass());

        service = findMetaAnnotation(B.class, "org.apache.dubbo.config.annotation.DubboService");
        assertEquals(Cloneable.class, service.interfaceClass());

        service = findMetaAnnotation(Service5.class, DubboService.class);
        assertEquals(Cloneable.class, service.interfaceClass());
    }

    @Service(interfaceName = "java.lang.CharSequence", interfaceClass = CharSequence.class)
    @com.alibaba.dubbo.config.annotation.Service(interfaceName = "java.lang.CharSequence", interfaceClass = CharSequence.class)
    @Adaptive(value = {"a", "b", "c"})
    static class A {

        @MyAdaptive("e")
        public void execute() {

        }


    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    @Inherited
    @DubboService(interfaceClass = Cloneable.class)
    @interface Service2 {


    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    @Inherited
    @Service2
    @interface Service3 {


    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    @Inherited
    @Service3
    @interface Service4 {


    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    @Inherited
    @Service4
    @interface Service5 {


    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Inherited
    @Adaptive
    @interface MyAdaptive {

        String[] value() default {};

    }

    @Service5
    static class B extends A {

        @Adaptive("f")
        @Override
        public void execute() {

        }


    }

    @MyAdaptive
    static class C extends B {

    }

    private void assertADeclaredAnnotations(List<Annotation> annotations, int offset) {
        int size = 3 + offset;
        assertEquals(size, annotations.size());
        Service service = (Service) annotations.get(offset++);
        assertEquals("java.lang.CharSequence", service.interfaceName());
        assertEquals(CharSequence.class, service.interfaceClass());

        com.alibaba.dubbo.config.annotation.Service s = (com.alibaba.dubbo.config.annotation.Service) annotations.get(offset++);
        assertEquals("java.lang.CharSequence", service.interfaceName());
        assertEquals(CharSequence.class, service.interfaceClass());

        Adaptive adaptive = (Adaptive) annotations.get(offset++);
        assertArrayEquals(new String[]{"a", "b", "c"}, adaptive.value());
    }
}
