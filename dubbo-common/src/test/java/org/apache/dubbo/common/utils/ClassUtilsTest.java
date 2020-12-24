/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dubbo.common.utils;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.verify;

public class ClassUtilsTest {
    // test ForName With  "ThreadContextClassLoader"
    @Test
    public void testForNameWithThreadContextClassLoader() throws Exception {
        // 获取当前线程上下文加载器，一会我们修改，最后恢复
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader classLoader = Mockito.mock(ClassLoader.class);
            // 设置线程上下文加载器
            Thread.currentThread().setContextClassLoader(classLoader);
            // 加载a.b.c.D类，不过最后loadClass的时候返回的是null，正常是返回Class<?>
            ClassUtils.forNameWithThreadContextClassLoader("a.b.c.D");
            verify(classLoader).loadClass("a.b.c.D");
        } finally {
            // 恢复
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

    // testForNameWith  "CallerClassLoader"
    @Test
    public void tetForNameWithCallerClassLoader() throws Exception {
        // ClassUtilsTest本测试类就是调用类，使用调用类的类加载器加载目标类（ClassUtils.class.getName()）
        Class c = ClassUtils.forNameWithCallerClassLoader(ClassUtils.class.getName(), ClassUtilsTest.class);
        assertThat(c == ClassUtils.class, is(true));
    }

    // easy
    @Test
    public void testGetCallerClassLoader() throws Exception {
        assertThat(ClassUtils.getCallerClassLoader(ClassUtilsTest.class), sameInstance(ClassUtilsTest.class.getClassLoader()));
    }

    @Test
    public void testGetClassLoader1() throws Exception {
        ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // 肯定和上下文加载器是等的，因为ClassUtilsTest的加载器肯定是app应用程序加载器或者说系统类加载器，而上下文加载器默认也是系统类加载器
            assertThat(ClassUtils.getClassLoader(ClassUtilsTest.class), sameInstance(oldClassLoader));
            Thread.currentThread().setContextClassLoader(null);
            // 上面设置了null，此时ClassUtilsTest的加载器肯定还是app，所以和后面的相等，都是app
            assertThat(ClassUtils.getClassLoader(ClassUtilsTest.class), sameInstance(ClassUtilsTest.class.getClassLoader()));
        } finally {
            Thread.currentThread().setContextClassLoader(oldClassLoader);
        }
    }

    @Test
    public void testGetClassLoader2() throws Exception {
        // getClassLoader内部就是获取上下文加载器，为null就获取ClassUtils类的加载器，进去
        assertThat(ClassUtils.getClassLoader(), sameInstance(ClassUtils.class.getClassLoader()));
    }

    @Test
    public void testForName1() throws Exception {
        // 加载类，内部loadClass，进去
        assertThat(ClassUtils.forName(ClassUtilsTest.class.getName()) == ClassUtilsTest.class, is(true));
    }

    @Test
    public void testForName2() throws Exception {
        // 加载原生类型，内部会进resolvePrimitiveClassName方法，从类初始化就填充好的map容器取出byte.class，进去
        assertThat(ClassUtils.forName("byte") == byte.class, is(true));
        // 内部涉及到递归，java.lang.String[]会截取出java.lang.String，然后递归调用forName，走到最后一步loadClass，加载String，
        // 但是已经被加载完了（rt包的核心类都是null根类加载器加载的），不会重复加载（双亲委派）
        assertThat(ClassUtils.forName("java.lang.String[]") == String[].class, is(true));
        assertThat(ClassUtils.forName("[Ljava.lang.String;") == String[].class, is(true));
    }

    @Test
    public void testForName3() throws Exception {
        ClassLoader classLoader = Mockito.mock(ClassLoader.class);
        ClassUtils.forName("a.b.c.D", classLoader);
        verify(classLoader).loadClass("a.b.c.D");
    }

    @Test
    public void testResolvePrimitiveClassName() throws Exception {
        assertThat(ClassUtils.resolvePrimitiveClassName("boolean") == boolean.class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("byte") == byte.class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("char") == char.class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("double") == double.class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("float") == float.class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("int") == int.class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("long") == long.class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("short") == short.class, is(true));

        // boolean[].class的输出就是Class [Z
        assertThat(ClassUtils.resolvePrimitiveClassName("[Z") == boolean[].class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("[B") == byte[].class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("[C") == char[].class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("[D") == double[].class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("[F") == float[].class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("[I") == int[].class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("[J") == long[].class, is(true));
        assertThat(ClassUtils.resolvePrimitiveClassName("[S") == short[].class, is(true));
    }

    @Test
    public void testToShortString() throws Exception {
        assertThat(ClassUtils.toShortString(null), equalTo("null"));
        // 进去
        assertThat(ClassUtils.toShortString(new ClassUtilsTest()), startsWith("ClassUtilsTest@"));
    }

    // easy
    @Test
    public void testConvertPrimitive() throws Exception {

        assertThat(ClassUtils.convertPrimitive(char.class, ""), equalTo('\0'));
        assertThat(ClassUtils.convertPrimitive(char.class, null), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(char.class, "6"), equalTo('6'));

        assertThat(ClassUtils.convertPrimitive(boolean.class, ""), equalTo(Boolean.FALSE));
        assertThat(ClassUtils.convertPrimitive(boolean.class, null), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(boolean.class, "true"), equalTo(Boolean.TRUE));


        assertThat(ClassUtils.convertPrimitive(byte.class, ""), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(byte.class, null), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(byte.class, "127"), equalTo(Byte.MAX_VALUE));


        assertThat(ClassUtils.convertPrimitive(short.class, ""), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(short.class, null), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(short.class, "32767"), equalTo(Short.MAX_VALUE));

        assertThat(ClassUtils.convertPrimitive(int.class, ""), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(int.class, null), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(int.class, "6"), equalTo(6));

        assertThat(ClassUtils.convertPrimitive(long.class, ""), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(long.class, null), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(long.class, "6"), equalTo(new Long(6)));

        assertThat(ClassUtils.convertPrimitive(float.class, ""), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(float.class, null), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(float.class, "1.1"), equalTo(new Float(1.1)));

        assertThat(ClassUtils.convertPrimitive(double.class, ""), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(double.class, null), equalTo(null));
        assertThat(ClassUtils.convertPrimitive(double.class, "10.1"), equalTo(new Double(10.1)));
    }

}
