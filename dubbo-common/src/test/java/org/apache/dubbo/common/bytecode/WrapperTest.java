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
package org.apache.dubbo.common.bytecode;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class WrapperTest {
    @Test
    public void testMain() throws Exception {
        Wrapper w = Wrapper.getWrapper(I1.class);
        // 反编译的w如下
        /**

         package org.apache.dubbo.common.bytecode;

         import java.lang.reflect.InvocationTargetException;
         import java.util.Map;
         import org.apache.dubbo.common.bytecode.ClassGenerator.DC;
         import org.apache.dubbo.common.bytecode.WrapperTest.I1;

         public class Wrapper0 extends Wrapper implements DC {
         public static String[] pns;
         public static Map pts;
         public static String[] mns;
         public static String[] dmns;
         public static Class[] mts0;
         public static Class[] mts1;
         public static Class[] mts2;
         public static Class[] mts3;
         public static Class[] mts4;
         public static Class[] mts5;

         public String[] getPropertyNames() {
            return pns;
         }

         public boolean hasProperty(String var1) {
            return pts.containsKey(var1);
         }

         public Class getPropertyType(String var1) {
            return (Class)pts.get(var1);
         }

         public String[] getMethodNames() {
            return mns;
         }

         public String[] getDeclaredMethodNames() {
            return dmns;
         }

         public void setPropertyValue(Object var1, String var2, Object var3) {
             I1 var4;
             try {
                var4 = (I1)var1;
             } catch (Throwable var6) {
                throw new IllegalArgumentException(var6);
             }

             if (var2.equals("name")) {
                var4.setName((String)var3);
             } else if (var2.equals("float")) {
                var4.setFloat(((Number)var3).floatValue());
             } else {
                throw new NoSuchPropertyException("Not found property \"" + var2 + "\" field or setter method in class org.apache.dubbo.common.bytecode.WrapperTest$I1.");
             }
         }

         public Object getPropertyValue(Object var1, String var2) {
             I1 var3;
             try {
                var3 = (I1)var1;
             } catch (Throwable var5) {
                throw new IllegalArgumentException(var5);
             }

             if (var2.equals("float")) {
                return new Float(var3.getFloat());
             } else if (var2.equals("name")) {
                return var3.getName();
             } else {
                throw new NoSuchPropertyException("Not found property \"" + var2 + "\" field or getter method in class org.apache.dubbo.common.bytecode.WrapperTest$I1.");
             }
         }

         public Object invokeMethod(Object var1, String var2, Class[] var3, Object[] var4) throws InvocationTargetException {
             I1 var5;
             try {
                var5 = (I1)var1;
             } catch (Throwable var8) {
                throw new IllegalArgumentException(var8);
             }

             try {
                if ("hello".equals(var2) && var3.length == 1) {
                     var5.hello((String)var4[0]);
                     return null;
                }

                if ("showInt".equals(var2) && var3.length == 1) {
                    return new Integer(var5.showInt(((Number)var4[0]).intValue()));
                }

                 if ("getFloat".equals(var2) && var3.length == 0) {
                 return new Float(var5.getFloat());
                 }

                 if ("setName".equals(var2) && var3.length == 1) {
                 var5.setName((String)var4[0]);
                 return null;
                 }

                 if ("setFloat".equals(var2) && var3.length == 1) {
                     var5.setFloat(((Number)var4[0]).floatValue());
                     return null;
                 }

                 if ("getName".equals(var2) && var3.length == 0) {
                    return var5.getName();
                 }
             } catch (Throwable var9) {
                throw new InvocationTargetException(var9);
             }
             throw new NoSuchMethodException("Not found method \"" + var2 + "\" in class org.apache.dubbo.common.bytecode.WrapperTest$I1.");
         }

             public Wrapper0() {
             }
         }
         */

        /*
          getDeclaredMethods()
          返回 Method 对象的一个数组，这些对象反映此 Class 对象表示的类或接口声明的所有方法，包括公共、保护、默认（包）访问和私有方法，但不包括继承的方法。

          getMethods()
          返回一个包含某些 Method 对象的数组，这些对象反映此 Class 对象所表示的类或接口（包括那些由该类或接口声明的以及从超类和超接口继承的那些的类或接口）的公共 member 方法。
        */
        String[] ns = w.getDeclaredMethodNames();
        assertEquals(ns.length, 5);
        ns = w.getMethodNames();
        assertEquals(ns.length, 6);

        Object obj = new Impl1();
        assertEquals(w.getPropertyValue(obj, "name"), "you name");

        w.setPropertyValue(obj, "name", "changed");
        assertEquals(w.getPropertyValue(obj, "name"), "changed");

        w.invokeMethod(obj, "hello", new Class<?>[]{String.class}, new Object[]{"qianlei"});
    }

    // bug: DUBBO-132
    @Test
    public void test_unwantedArgument() throws Exception {
        Wrapper w = Wrapper.getWrapper(I1.class);
        Object obj = new Impl1();
        try {
            w.invokeMethod(obj, "hello", new Class<?>[]{String.class, String.class},
                    new Object[]{"qianlei", "badboy"});
            fail();
        } catch (NoSuchMethodException expected) {
        }
    }

    //bug: DUBBO-425
    @Test
    public void test_makeEmptyClass() throws Exception {
        Wrapper.getWrapper(EmptyServiceImpl.class);
    }

    @Test
    public void testHasMethod() throws Exception {
        Wrapper w = Wrapper.getWrapper(I1.class);
        Assertions.assertTrue(w.hasMethod("setName"));
        Assertions.assertTrue(w.hasMethod("hello"));
        Assertions.assertTrue(w.hasMethod("showInt"));
        Assertions.assertTrue(w.hasMethod("getFloat"));
        Assertions.assertTrue(w.hasMethod("setFloat"));
        Assertions.assertFalse(w.hasMethod("setFloatXXX"));
    }

    @Test
    public void testWrapperObject() throws Exception {
        Wrapper w = Wrapper.getWrapper(Object.class);
        Assertions.assertEquals(4, w.getMethodNames().length);
        Assertions.assertEquals(0, w.getPropertyNames().length);
        Assertions.assertNull(w.getPropertyType(null));
    }

    @Test
    public void testGetPropertyValue() throws Exception {
        Assertions.assertThrows(NoSuchPropertyException.class, () -> {
            Wrapper w = Wrapper.getWrapper(Object.class);
            w.getPropertyValue(null, null);
        });
    }

    @Test
    public void testSetPropertyValue() throws Exception {
        Assertions.assertThrows(NoSuchPropertyException.class, () -> {
            Wrapper w = Wrapper.getWrapper(Object.class);
            w.setPropertyValue(null, null, null);
        });
    }

    @Test
    public void testInvokeWrapperObject() throws Exception {
        Wrapper w = Wrapper.getWrapper(Object.class);
        Object instance = new Object();
        Assertions.assertEquals(instance.getClass(), (Class<?>) w.invokeMethod(instance, "getClass", null, null));
        Assertions.assertEquals(instance.hashCode(), (int) w.invokeMethod(instance, "hashCode", null, null));
        Assertions.assertEquals(instance.toString(), (String) w.invokeMethod(instance, "toString", null, null));
        Assertions.assertTrue((boolean)w.invokeMethod(instance, "equals", null, new Object[] {instance}));
    }

    @Test
    public void testNoSuchMethod() throws Exception {
        Assertions.assertThrows(NoSuchMethodException.class, () -> {
            Wrapper w = Wrapper.getWrapper(Object.class);
            w.invokeMethod(new Object(), "__XX__", null, null);
        });
    }

    @Test
    public void test_getDeclaredMethodNames_ContainExtendsParentMethods() throws Exception {
        assertArrayEquals(new String[]{"hello",}, Wrapper.getWrapper(Parent1.class).getMethodNames());

        assertArrayEquals(new String[]{}, Wrapper.getWrapper(Son.class).getDeclaredMethodNames());
    }

    @Test
    public void test_getMethodNames_ContainExtendsParentMethods() throws Exception {
        assertArrayEquals(new String[]{"hello", "world"}, Wrapper.getWrapper(Son.class).getMethodNames());
    }

    public interface I0 {
        String getName();
    }

    public interface I1 extends I0 {
        void setName(String name);

        void hello(String name);

        int showInt(int v);

        float getFloat();

        void setFloat(float f);
    }

    public interface EmptyService {
    }

    public interface Parent1 {
        void hello();
    }


    public interface Parent2 {
        void world();
    }

    public interface Son extends Parent1, Parent2 {

    }

    public static class Impl0 {
        public float a, b, c;
    }

    public static class Impl1 implements I1 {
        private String name = "you name";

        private float fv = 0;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public void hello(String name) {
            System.out.println("hello " + name);
        }

        public int showInt(int v) {
            return v;
        }

        public float getFloat() {
            return fv;
        }

        public void setFloat(float f) {
            fv = f;
        }
    }

    public static class EmptyServiceImpl implements EmptyService {
    }
}