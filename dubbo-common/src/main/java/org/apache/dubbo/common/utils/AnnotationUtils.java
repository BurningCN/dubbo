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

import org.apache.dubbo.config.annotation.Service;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static org.apache.dubbo.common.function.Predicates.and;
import static org.apache.dubbo.common.function.Streams.filterAll;
import static org.apache.dubbo.common.function.Streams.filterFirst;
import static org.apache.dubbo.common.utils.ClassUtils.getAllInheritedTypes;
import static org.apache.dubbo.common.utils.ClassUtils.resolveClass;
import static org.apache.dubbo.common.utils.CollectionUtils.first;
import static org.apache.dubbo.common.utils.MethodUtils.findMethod;
import static org.apache.dubbo.common.utils.MethodUtils.invokeMethod;

/**
 * Commons Annotation Utilities class
 *
 * @since 2.7.6
 */
// OK
public interface AnnotationUtils {

    /**
     * Resolve the annotation type by the annotated element and resolved class name
     *
     * @param annotatedElement    the annotated element
     * @param annotationClassName the class name of annotation
     * @param <A>                 the type of annotation
     * @return If resolved, return the type of annotation, or <code>null</code>
     */
    static <A extends Annotation> Class<A> resolveAnnotationType(AnnotatedElement annotatedElement,
                                                                 String annotationClassName) {
        // 获取加载器，如果返回的是null表示根类加载器
        ClassLoader classLoader = annotatedElement.getClass().getClassLoader();
        // 加载类，进去
        Class<?> annotationType = resolveClass(annotationClassName, classLoader);
        // isAssignableFrom判断上面加载返回的Class是不是注解
        if (annotationType == null || !Annotation.class.isAssignableFrom(annotationType)) {
            return null;
        }
        return (Class<A>) annotationType;
    }

    /**
     * Is the specified type a generic {@link Class type}
     *
     * @param annotatedElement the annotated element
     * @return if <code>annotatedElement</code> is the {@link Class}, return <code>true</code>, or <code>false</code>
     * @see ElementType#TYPE
     */
    // AnnotatedElement可以接Class和Method类型（当然本身可以用原类型接，比如Class接A.class），用这个类型接表示类或者方法是带注解
    // 下面方法体的判断表示这个具体类型是不是Class的，因为该工具类其他方法会处理"类"上的注解，所以会调用下面的isType来预先判断下是不是Class
    // 传入的值比如说A.class
    static boolean isType(AnnotatedElement annotatedElement) {
        return annotatedElement instanceof Class;
    }

    /**
     * Is the type of specified annotation same to the expected type?
     *
     * @param annotation     the specified {@link Annotation}
     * @param annotationType the expected annotation type
     * @return if same, return <code>true</code>, or <code>false</code>
     */
    // eg : isSameType(A.class.getAnnotation(Service.class), Service.class)
    static boolean isSameType(Annotation annotation, Class<? extends Annotation> annotationType) {
        if (annotation == null || annotationType == null) {
            return false;
        }
        // annotation.annotationType()获取的就是注解的Class信息，比如@Service注解，那么就是Service.class
        // 即：A.class.getAnnotation(Service.class)返回的是Annotation类型，对其调用annotationType返回的是Class<? extends Annotation>类型
        return Objects.equals(annotation.annotationType(), annotationType);
    }


    /**
     * Build an instance of {@link Predicate} to excluded annotation type
     *
     * @param excludedAnnotationType excluded annotation type
     * @return non-null
     */
    // 作用看上面注释
    static Predicate<Annotation> excludedType(Class<? extends Annotation> excludedAnnotationType) {
        return annotation -> !isSameType(annotation, excludedAnnotationType);
    }

    /**
     * Get the attribute from the specified {@link Annotation annotation}
     *
     * @param annotation    the specified {@link Annotation annotation}
     * @param attributeName the attribute name
     * @param <T>           the type of attribute
     * @return the attribute value
     * @throws IllegalArgumentException If the attribute name can't be found
     */
    // 获取注解的某个属性值
    static <T> T getAttribute(Annotation annotation, String attributeName) throws IllegalArgumentException {
        // 调用的居然是invokeMethod--Method！！其实内部看了下是把Annotation当做了Class，
        // 属性key即这里的attributeName当做了方法，属性val就是attributeName=后面的值当做了方法返回值，详见testGetAttribute测试方法
        // 因为其实在一个类上面加注解比如@Service(interfaceName="xxx")本身就是对@Service注解里面的interfaceName属性（不过方法的样子）赋值为xxx
        // invokeMethod 进去
        return annotation == null ? null : invokeMethod(annotation, attributeName);
    }

    /**
     * Get the "value" attribute from the specified {@link Annotation annotation}
     *
     * @param annotation the specified {@link Annotation annotation}
     * @param <T>        the type of attribute
     * @return the value of "value" attribute
     * @throws IllegalArgumentException If the attribute name can't be found
     */
    // 作用看上面注释
    static <T> T getValue(Annotation annotation) throws IllegalArgumentException {
        return getAttribute(annotation, "value");
    }

    /**
     * Get the {@link Annotation} from the specified {@link AnnotatedElement the annotated element} and
     * {@link Annotation annotation} class name
     *
     * @param annotatedElement    {@link AnnotatedElement}
     * @param annotationClassName the class name of annotation
     * @param <A>                 The type of {@link Annotation}
     * @return the {@link Annotation} if found
     * @throws ClassCastException If the {@link Annotation annotation} type that client requires can't match actual type
     */
    // eg : getAnnotation(A.class, "org.apache.dubbo.config.annotation.Service")
    static <A extends Annotation> A getAnnotation(AnnotatedElement annotatedElement, String annotationClassName)
            throws ClassCastException {
        // 将string->Class，进去
        Class<? extends Annotation> annotationType = resolveAnnotationType(annotatedElement, annotationClassName);
        if (annotationType == null) {
            return null;
        }
        // 根据注解的Class 获取类上的注解
        return (A) annotatedElement.getAnnotation(annotationType);
    }

    /**
     * Get annotations that are <em>directly present</em> on this element.
     * This method ignores inherited annotations. --- > 注意这个方法不返回继承的注解
     *
     * @param annotatedElement    the annotated element
     * @param annotationsToFilter the annotations to filter
     * @return non-null read-only {@link List}
     */
    static List<Annotation> getDeclaredAnnotations(AnnotatedElement annotatedElement,
                                                   Predicate<Annotation>... annotationsToFilter) {
        if (annotatedElement == null) {
            return emptyList();
        }
        // 除了annotatedElement有getDeclaredAnnotations方法，直接Class c = A.class;c.getDeclaredAnnotations()也可以
        // 之所以用AnnotatedElement来接受的原因是：有时候我们不仅仅想获取类上的注解（传入A.class），还想获取方法上的注解（传入的是Method m）
        // 虽然Class和Method都有getDeclaredAnnotations，但是为了通用，就都用AnnotatedElement接受，一定程度的抽取公共部分解耦。
        return unmodifiableList(filterAll(asList(annotatedElement.getDeclaredAnnotations()), annotationsToFilter));
    }

    /**
     * Get all directly declared annotations of the the annotated element, not including
     * meta annotations.
     *
     * @param annotatedElement    the annotated element
     * @param annotationsToFilter the annotations to filter
     * @return non-null read-only {@link List}
     */
    // 这个和前面方法的区别就是如果是具体类型Class的AnnotatedElement，还需要返回继承的注解（比如测试程序的B类继承了A类，那么A的注解也能拿到）
    static List<Annotation> getAllDeclaredAnnotations(AnnotatedElement annotatedElement,
                                                      Predicate<Annotation>... annotationsToFilter) {
        // isType判断是否是"注解的类"还是"注解的方法"
        if (isType(annotatedElement)) {
            // 获取类的注解，包括父类上的注解、父类的父类上的注解....
            return getAllDeclaredAnnotations((Class) annotatedElement, annotationsToFilter);
        } else {
            // 获取方法的注解，因为方法没有继承，所以直接获取method上面的
            return getDeclaredAnnotations(annotatedElement, annotationsToFilter);
        }
    }

    /**
     * Get all directly declared annotations of the specified type and its' all hierarchical types, not including
     * meta annotations.
     *
     * @param type                the specified type
     * @param annotationsToFilter the annotations to filter
     * @return non-null read-only {@link List}
     */
    // 代码很清晰易懂，不写注释了
    static List<Annotation> getAllDeclaredAnnotations(Class<?> type, Predicate<Annotation>... annotationsToFilter) {

        if (type == null) {
            return emptyList();
        }

        List<Annotation> allAnnotations = new LinkedList<>();

        // All types
        Set<Class<?>> allTypes = new LinkedHashSet<>();
        // Add current type
        allTypes.add(type);
        // Add all inherited types （内部是获取所有父类、父接口）进去
        allTypes.addAll(getAllInheritedTypes(type, t -> !Object.class.equals(t)));

        for (Class<?> t : allTypes) {
            allAnnotations.addAll(getDeclaredAnnotations(t, annotationsToFilter));
        }

        return unmodifiableList(allAnnotations);
    }


    /**
     * Get the meta-annotated {@link Annotation annotations} directly, excluding {@link Target}, {@link Retention}
     * and {@link Documented}
     *
     * @param annotationType          the {@link Annotation annotation} type
     * @param metaAnnotationsToFilter the meta annotations to filter
     * @return non-null read-only {@link List}
     */
    // 获取注解的元注解，代码很清晰易懂，不写注释了
    static List<Annotation> getMetaAnnotations(Class<? extends Annotation> annotationType,
                                               Predicate<Annotation>... metaAnnotationsToFilter) {
        return getDeclaredAnnotations(annotationType,
                // Excludes the Java native annotation types or it causes the stack overflow, e.g,
                // @Target annotates itself --->排除Java本地注释类型，否则会导致堆栈溢出
                excludedType(Target.class),
                excludedType(Retention.class),
                excludedType(Documented.class),
                // Add other predicates
                and(metaAnnotationsToFilter)
        );
    }

    /**
     * Get all meta annotations from the specified {@link Annotation annotation} type
     *
     * @param annotationType      the {@link Annotation annotation} type
     * @param annotationsToFilter the annotations to filter
     * @return non-null read-only {@link List}
     */
    // 获取注解的元注解，包括元注解的元注解...... eg : getAllMetaAnnotations(Service5.class);
    static List<Annotation> getAllMetaAnnotations(Class<? extends Annotation> annotationType,
                                                  Predicate<Annotation>... annotationsToFilter) {

        List<Annotation> allMetaAnnotations = new LinkedList<>();

        List<Annotation> metaAnnotations = getMetaAnnotations(annotationType);

        allMetaAnnotations.addAll(metaAnnotations);

        // 对获取到的元注解递归!!
        for (Annotation metaAnnotation : metaAnnotations) {
            // Get the nested meta annotations recursively
            allMetaAnnotations.addAll(getAllMetaAnnotations(metaAnnotation.annotationType()));
        }

        return unmodifiableList(filterAll(allMetaAnnotations, annotationsToFilter));
    }

    /**
     * Find the annotation that is annotated on the specified element may be a meta-annotation
     *
     * @param annotatedElement    the annotated element
     * @param annotationClassName the class name of annotation
     * @param <A>                 the required type of annotation
     * @return If found, return first matched-type {@link Annotation annotation}, or <code>null</code>
     */
    static <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, String annotationClassName) {
        // 第二个参数是把注解的全限定名称字符串加载到jvm了生成了Class，然后调用了下面的findAnnotation重载方法
        return findAnnotation(annotatedElement, resolveAnnotationType(annotatedElement, annotationClassName));
    }

    /**
     * Find the annotation that is annotated on the specified element may be a meta-annotation
     *
     * @param annotatedElement the annotated element
     * @param annotationType   the type of annotation
     * @param <A>              the required type of annotation
     * @return If found, return first matched-type {@link Annotation annotation}, or <code>null</code>
     */
    static <A extends Annotation> A findAnnotation(AnnotatedElement annotatedElement, Class<A> annotationType) {
        // 虽然是有annotatedElement.getDeclaredAnnotation(annotationType)方法的，但是这个是直接获取，有可能annotationType在父类里
        // 比如Test程序findAnnotation(B.class, Service.class)，B类上面只有@Service5注解，但是继承了A，而A是有@Service 的，
        // 所以使用的getAllDeclaredAnnotations（获取当前类注解以及所有父类注解）  +  predicate
        return (A) filterFirst(getAllDeclaredAnnotations(annotatedElement), a -> isSameType(a, annotationType));
    }

    /**
     * Find the meta annotations from the the {@link Annotation annotation} type by meta annotation type
     *
     * @param annotationType     the {@link Annotation annotation} type
     * @param metaAnnotationType the meta annotation type
     * @param <A>                the type of required annotation
     * @return if found, return all matched results, or get an {@link Collections#emptyList() empty list}
     */
    // 获取某个注解的元注解
    static <A extends Annotation> List<A> findMetaAnnotations(Class<? extends Annotation> annotationType,
                                                              Class<A> metaAnnotationType) {
        // 和前面思想一样 getAllMetaAnnotations + predicate
        // eg findMetaAnnotations(Service5.class, DubboService.class) @Service5注解本身没有@DubboService注解，但是他有@Service4注解
        // 而@Service4注解有@Service3....而getAllMetaAnnotations就能获取所有元注解（内部使用递归）
        return (List<A>) getAllMetaAnnotations(annotationType, a -> isSameType(a, metaAnnotationType));
    }

    /**
     * Find the meta annotations from the the the annotated element by meta annotation type
     *
     * @param annotatedElement   the annotated element
     * @param metaAnnotationType the meta annotation type
     * @param <A>                the type of required annotation
     * @return if found, return all matched results, or get an {@link Collections#emptyList() empty list}
     */
    // 和上面重载方法，第一个参数传递的不是 注解.class，而是A.class、method.class
    static <A extends Annotation> List<A> findMetaAnnotations(AnnotatedElement annotatedElement,
                                                              Class<A> metaAnnotationType) {
        List<A> metaAnnotations = new LinkedList<>();

        for (Annotation annotation : getAllDeclaredAnnotations(annotatedElement)) {
            // annotationType获取到 注解.class
            metaAnnotations.addAll(findMetaAnnotations(annotation.annotationType(), metaAnnotationType));
        }

        return unmodifiableList(metaAnnotations);
    }

    /**
     * Find the meta annotation from the annotated element by meta annotation type
     *
     * @param annotatedElement        the annotated element
     * @param metaAnnotationClassName the class name of meta annotation
     * @param <A>                     the type of required annotation
     * @return {@link #findMetaAnnotation(Class, Class)}
     */
    static <A extends Annotation> A findMetaAnnotation(AnnotatedElement annotatedElement,
                                                       String metaAnnotationClassName) {
        return findMetaAnnotation(annotatedElement, resolveAnnotationType(annotatedElement, metaAnnotationClassName));
    }

    /**
     * Find the meta annotation from the annotation type by meta annotation type
     *
     * @param annotationType     the {@link Annotation annotation} type
     * @param metaAnnotationType the meta annotation type
     * @param <A>                the type of required annotation
     * @return If found, return the {@link CollectionUtils#first(Collection)} matched result, return <code>null</code>.
     * If it requires more result, please consider to use {@link #findMetaAnnotations(Class, Class)}
     * @see #findMetaAnnotations(Class, Class)
     */
    static <A extends Annotation> A findMetaAnnotation(Class<? extends Annotation> annotationType,
                                                       Class<A> metaAnnotationType) {
        return first(findMetaAnnotations(annotationType, metaAnnotationType));
    }

    /**
     * Find the meta annotation from the annotated element by meta annotation type
     *
     * @param annotatedElement   the annotated element
     * @param metaAnnotationType the meta annotation type
     * @param <A>                the type of required annotation
     * @return If found, return the {@link CollectionUtils#first(Collection)} matched result, return <code>null</code>.
     * If it requires more result, please consider to use {@link #findMetaAnnotations(AnnotatedElement, Class)}
     * @see #findMetaAnnotations(AnnotatedElement, Class)
     */
    static <A extends Annotation> A findMetaAnnotation(AnnotatedElement annotatedElement, Class<A> metaAnnotationType) {
        return first(findMetaAnnotations(annotatedElement, metaAnnotationType));
    }

    /**
     * Tests the annotated element is annotated the specified annotations or not
     *
     * @param type            the annotated type
     * @param matchAll        If <code>true</code>, checking all annotation types are present or not, or match any
     * @param annotationTypes the specified annotation types
     * @return If the specified annotation types are present, return <code>true</code>, or <code>false</code>
     */
    static boolean isAnnotationPresent(Class<?> type,
                                       boolean matchAll,
                                       Class<? extends Annotation>... annotationTypes) {

        int size = annotationTypes == null ? 0 : annotationTypes.length;

        if (size < 1) {
            return false;
        }

        int presentCount = 0;

        for (int i = 0; i < size; i++) {
            Class<? extends Annotation> annotationType = annotationTypes[i];
            // 第二个判断的原因是考虑到type不一定是类，有可能是注解本身，所以第二个判断表示判断type注解是否含有元注解annotationType
            if (findAnnotation(type, annotationType) != null || findMetaAnnotation(type, annotationType) != null) {
                presentCount++;
            }
        }

        return matchAll ? presentCount == size : presentCount > 0;
    }

    /**
     * Tests the annotated element is annotated the specified annotation or not
     *
     * @param type           the annotated type
     * @param annotationType the class of annotation
     * @return If the specified annotation type is present, return <code>true</code>, or <code>false</code>
     */
    static boolean isAnnotationPresent(Class<?> type, Class<? extends Annotation> annotationType) {
        return isAnnotationPresent(type, true, annotationType);
    }

    /**
     * Tests the annotated element is present any specified annotation types
     *
     * @param annotatedElement    the annotated element
     * @param annotationClassName the class name of annotation
     * @return If any specified annotation types are present, return <code>true</code>
     */
    static boolean isAnnotationPresent(AnnotatedElement annotatedElement, String annotationClassName) {
        ClassLoader classLoader = annotatedElement.getClass().getClassLoader();
        Class<?> resolvedType = resolveClass(annotationClassName, classLoader);
        if (!Annotation.class.isAssignableFrom(resolvedType)) {
            return false;
        }
        return isAnnotationPresent(annotatedElement, (Class<? extends Annotation>) resolvedType);
    }

    /**
     * Tests the annotated element is present any specified annotation types
     *
     * @param annotatedElement the annotated element
     * @param annotationType   the class of annotation
     * @return If any specified annotation types are present, return <code>true</code>
     */
    static boolean isAnnotationPresent(AnnotatedElement annotatedElement, Class<? extends Annotation> annotationType) {
        if (isType(annotatedElement)) {
            return isAnnotationPresent((Class) annotatedElement, annotationType);
        } else {
            return annotatedElement.isAnnotationPresent(annotationType) ||
                    findMetaAnnotation(annotatedElement, annotationType) != null; // to find meta-annotation
        }
    }

    /**
     * Tests the annotated element is annotated all specified annotations or not
     *
     * @param type            the annotated type
     * @param annotationTypes the specified annotation types
     * @return If the specified annotation types are present, return <code>true</code>, or <code>false</code>
     */
    static boolean isAllAnnotationPresent(Class<?> type, Class<? extends Annotation>... annotationTypes) {
        return isAnnotationPresent(type, true, annotationTypes);
    }

    /**
     * Tests the annotated element is present any specified annotation types
     *
     * @param type            the annotated type
     * @param annotationTypes the specified annotation types
     * @return If any specified annotation types are present, return <code>true</code>
     */
    static boolean isAnyAnnotationPresent(Class<?> type,
                                          Class<? extends Annotation>... annotationTypes) {
        return isAnnotationPresent(type, false, annotationTypes);
    }


    /**
     * Get the default value of attribute on the specified annotation
     *
     * @param annotation    {@link Annotation} object
     * @param attributeName the name of attribute
     * @param <T>           the type of value
     * @return <code>null</code> if not found
     * @since 2.7.9
     */
    static <T> T getDefaultValue(Annotation annotation, String attributeName) {
        return getDefaultValue(annotation.annotationType(), attributeName);
    }

    /**
     * Get the default value of attribute on the specified annotation
     *
     * @param annotationType the type of {@link Annotation}
     * @param attributeName  the name of attribute
     * @param <T>            the type of value
     * @return <code>null</code> if not found
     * @since 2.7.9
     */
    static <T> T getDefaultValue(Class<? extends Annotation> annotationType, String attributeName) {
        Method method = findMethod(annotationType, attributeName);
        return (T) (method == null ? null : method.getDefaultValue());
    }
}
