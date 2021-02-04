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
package my.common.bytecode;

import org.apache.dubbo.common.utils.ClassUtils;
import org.apache.dubbo.common.utils.ReflectUtils;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.dubbo.common.constants.CommonConstants.MAX_PROXY_COUNT;

/**
 * Proxy.
 */

public abstract class Proxy {
    public static final InvocationHandler RETURN_NULL_INVOKER = (proxy, method, args) -> null;
    public static final InvocationHandler THROW_UNSUPPORTED_INVOKER = new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            throw new UnsupportedOperationException("Method [" + ReflectUtils.getName(method) + "] unimplemented.");
        }
    };
    private static final AtomicLong PROXY_CLASS_COUNTER = new AtomicLong(0);
    private static final String PACKAGE_NAME = Proxy.class.getPackage().getName();
    private static final Map<ClassLoader, Map<String, Object>> PROXY_CACHE_MAP = new WeakHashMap<ClassLoader, Map<String, Object>>();

    private static final Object PENDING_GENERATION_MARKER = new Object();

    protected Proxy() {
    }

    /**
     * Get proxy.
     *
     * @param ics interface class array.
     * @return Proxy instance.
     */
    public static Proxy getProxy(Class<?>... ics) {
        return getProxy(ClassUtils.getClassLoader(Proxy.class), ics);
    }

    /**
     * Get proxy.
     *
     * @param cl  class loader.
     * @param ics interface class array.
     * @return Proxy instance.
     */
    public static Proxy getProxy(ClassLoader cl, Class<?>... ics) {
        if (ics.length > MAX_PROXY_COUNT) {
            throw new IllegalArgumentException("interface limit exceeded");
        }

        // 遍历接口列表
        // 这里debug的时候接口列表有：HelloService和EchoService
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ics.length; i++) {
            String itf = ics[i].getName();
            // 检测类型是否为接口
            if (!ics[i].isInterface()) {
                throw new RuntimeException(itf + " is not a interface.");
            }

            Class<?> tmp = null;
            try {
                // 重新加载接口类
                tmp = Class.forName(itf, false, cl);
            } catch (ClassNotFoundException e) {
            }

            // 检测接口是否相同，这里 tmp 有可能为空
            if (tmp != ics[i]) {
                throw new IllegalArgumentException(ics[i] + " is not visible from class loader");
            }

            // 拼接接口全限定名，分隔符为 ;
            sb.append(itf).append(';');
        }

        // 使用拼接后的接口名作为 key
        // debug的时候key=org.apache.dubbo.samples.api.client.HelloService;com.alibaba.dubbo.rpc.service.EchoService;
        // use interface class name list as key.
        String key = sb.toString();

        // get cache by class loader. key就是上面的key，value为proxy
        final Map<String, Object> cache;
        // PROXY_CACHE_MAP是WeakHashMap，用sync锁保证安全
        synchronized (PROXY_CACHE_MAP) {
            cache = PROXY_CACHE_MAP.computeIfAbsent(cl, k -> new HashMap<>());
        }

        Proxy proxy = null;
        synchronized (cache) { // cache本身是HashMap，用sync锁保证安全
            do {
                // 从缓存中获取 Reference<Proxy> 实例
                Object value = cache.get(key);
                if (value instanceof Reference<?>) {
                    // 强引用的获取对象方式
                    proxy = (Proxy) ((Reference<?>) value).get();
                    if (proxy != null) {
                        return proxy;
                    }
                }

                // 并发控制，保证只有一个线程可以进行后续操作
                if (value == PENDING_GENERATION_MARKER) {
                    try {
                        // 其他线程在此处进行等待
                        cache.wait();
                    } catch (InterruptedException e) {
                    }
                } else {
                    // 放置标志位（正在构建标记，本身是一个Object）到缓存中，并跳出 while 循环进行后续操作
                    cache.put(key, PENDING_GENERATION_MARKER);
                    break;
                }
            }
            while (true);
        }

        long id = PROXY_CLASS_COUNTER.getAndIncrement();
        String pkg = null;
        ClassGenerator ccp = null, ccm = null;
        try {
            // 创建 ClassGenerator 对象
            ccp = ClassGenerator.newInstance(cl);

            Set<String> worked = new HashSet<>(); // 存放所有处理过的接口的所有方法描述
            List<Method> methods = new ArrayList<>();// 存放所有处理过的接口的所有方法引用

            for (int i = 0; i < ics.length; i++) {
                // 检测接口访问级别是否为 protected 或 privete
                // if这一段里面代码的作用就是做校验的，校验每个接口的包名必须都一样，否则抛异常，当然一般不会走这个分支，因为大部分接口都是public的
                if (!Modifier.isPublic(ics[i].getModifiers())) {  // --->
                    // 获取接口包名
                    String npkg = ics[i].getPackage().getName();
                    if (pkg == null) {
                        pkg = npkg;
                    } else {
                        if (!pkg.equals(npkg)) {
                            // 非 public 级别的接口必须在同一个包下，否者抛出异常
                            throw new IllegalArgumentException("non-public interfaces from different packages");
                        }
                    }
                }
                // 添加接口到 ClassGenerator 中，进去
                ccp.addInterface(ics[i]);

                // 遍历接口方法
                for (Method method : ics[i].getMethods()) {
                    // 获取方法描述，可理解为方法签名，比如org.apache.dubbo.rpc.support.DemoService接口的sayHello、$invoke方法如下
                    // DemoService.sayHello 返回的desc = sayHello(Ljava/lang/String;)V
                    // DemoService.$invoke  返回的desc = $invoke(Ljava/lang/String;Ljava/lang/String;)V
                    String desc = ReflectUtils.getDesc(method);
                    // 如果方法描述字符串已在 worked 中，则忽略。考虑这种情况，
                    // A 接口和 B 接口中包含一个完全相同的方法，但是在代理类里面只会生成一个方法
                    if (worked.contains(desc) || Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    if (ics[i].isInterface() && Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }

                    // 表示这个方法已处理过
                    worked.add(desc);

                    int ix = methods.size();

                    // 获取方法返回值类型
                    Class<?> rt = method.getReturnType();
                    // 获取参数列表
                    Class<?>[] pts = method.getParameterTypes();

                    // 生成 Object[] args = new Object[1...N]
                    StringBuilder code = new StringBuilder("Object[] args = new Object[").append(pts.length).append("];");
                    for (int j = 0; j < pts.length; j++) {
                        // 生成 args[1...N] = ($w)$1...N;
                        code.append(" args[").append(j).append("] = ($w)$").append(j + 1).append(";");
                    }
                    // 生成 InvokerHandler 接口的 invoker 方法调用语句 eg:Object ret = handler.invoke(this, methods[0], args);
                    code.append(" Object ret = handler.invoke(this, methods[").append(ix).append("], args);");
                    if (!Void.TYPE.equals(rt)) {
                        // 生成返回语句，形如 return (java.lang.String) ret;
                        code.append(" return ").append(asArgument(rt, "ret")).append(";");
                    }
                    methods.add(method);
                    // org.apache.dubbo.rpc.support.DemoService的$invoke方法此时对应的code如下:
                    // Object[] args = new Object[2]; args[0] = ($w)$1; args[1] = ($w)$2; Object ret = handler.invoke(this, methods[1], args);
                    // 格式化一下如下({}里面的是code,{}外层的还没生成-->这部分在下面的addMethod内部会构建)
                    // void $invoke(String s1, String s2){
                    //  Object[] args = new Object[2];
                    //  args[0] = ($w)$1;
                    //  args[1] = ($w)$2;
                    //  Object ret = handler.invoke(this, methods[1], args);
                    // }

                    // 添加方法名、访问控制符、参数列表、方法代码等信息到 ClassGenerator 中，进去
                    ccp.addMethod(method.getName(), method.getModifiers(), rt, pts, method.getExceptionTypes(), code.toString());
                }
            }

            if (pkg == null) {
                // package com.alibaba.dubbo.common.bytecode; 一般都满足这个分支，这个pgk也是代理类的第一行数据
                pkg = PACKAGE_NAME;
            }

            // 构建接口代理类名称：pkg + ".proxy" + id，比如 org.apache.dubbo.common.bytecode.proxy0
            String pcn = pkg + ".proxy" + id;
            // 设置代理类名称
            ccp.setClassName(pcn);
            // 为了给代理类生成 public static java.lang.reflect.Method[] methods;
            ccp.addField("public static java.lang.reflect.Method[] methods;");
            // 为了给代理类生成 private java.lang.reflect.InvocationHandler handler;
            ccp.addField("private " + InvocationHandler.class.getName() + " handler;");
            // 为接口代理类添加带有 InvocationHandler 参数的构造方法，比如：
            // proxy0(java.lang.reflect.InvocationHandler arg0) {
            //     handler=$1;
            // }
            ccp.addConstructor(Modifier.PUBLIC, new Class<?>[]{InvocationHandler.class}, new Class<?>[0], "handler=$1;");
            // 为接口代理类添加默认构造方法
            ccp.addDefaultConstructor();

            // 直到ccp.toClass()才真正生成代理类，前面是生成一个代理类必要的数据都存到的ccp中了，这些信息包括addMethod、setClassName、
            // addField、addConstructor、addDefaultConstructor等方法的调用，内部会存到相应的属性中，下面toClass内部肯定会利用这些存好
            // 了值的属性进行接口代理类的生成。进去
            Class<?> clazz = ccp.toClass();
            // 内部有把代理类proxy0写出到文件，自己可以反编译看下，或者看稍后面的大块注释

            // 给代理类的method属性赋值，第一个参数为null说明这个属性肯定是public static的
            clazz.getField("methods").set(null, methods.toArray(new Method[0]));

            //---------------------☆分割线☆---------------------
            // 下面是另一个动态代理类的生成

            // 构建 Proxy 子类名称，比如 Proxy1，Proxy2 等
            String fcn = Proxy.class.getName() + id;
            ccm = ClassGenerator.newInstance(cl);
            ccm.setClassName(fcn);
            ccm.addDefaultConstructor();
            ccm.setSuperClass(Proxy.class);
            // 下面一句是关键，将ccp和ccm分别生成的两个代理类关联起来了，为 Proxy 的抽象方法 newInstance 生成实现代码，形如：
            // public Object newInstance(java.lang.reflect.InvocationHandler h) {
            //     return new org.apache.dubbo.proxy0($1); ---> 这里的proxy0就是前面ccp创建的，这一行就是调用其(proxy0)带有InvocationHandler参数的构造方法
            // }
            ccm.addMethod("public Object newInstance(" + InvocationHandler.class.getName() + " h){ return new " + pcn + "($1); }");
            // 生成 Proxy 实现类
            Class<?> pc = ccm.toClass();

            // ccp和ccm生成从两个接口代理类，不太一样，前者最后的代理类名称是proxy0/1..开头小写，后者是Proxy0/1..开头大写，且后者是以Proxy为父类的，实现了DC接口
            // 而前者一般没有父类，实现了用户提供的接口列表，详见doc模块/其他/ccm和ccp文件夹下的两个class结尾文件

            // 通过反射创建 Proxy 实例
            proxy = (Proxy) pc.newInstance();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            // release ClassGenerator
            if (ccp != null) {
                // 释放资源 进去
                ccp.release();
            }
            if (ccm != null) {
                // 释放资源
                ccm.release();
            }
            synchronized (cache) {
                if (proxy == null) {
                    cache.remove(key);
                } else {
                    // 写缓存 eg key为com.alibaba.dubbo.rpc.service.EchoService;org.apache.dubbo.rpc.service.Destroyable;org.apache.dubbo.rpc.support.DemoService;
                    cache.put(key, new WeakReference<Proxy>(proxy));
                }
                // 唤醒其他等待线程
                cache.notifyAll();
            }
        }
        return proxy;
        //1.遍历接口列表，重加载接口类这里debug的时候接口列表有：HelloService和EchoService
        //2.ClassGenerator ccp 用于为服务接口生成代理类，比如测试代码中的 HelloService接口，这个接口代理类就是由 ccp 生成的。
        //3.ClassGenerator ccm 则是用于为 org.apache.dubbo.common.bytecode.Proxy 抽象类生成子类，主要是实现 Proxy 类的抽象方法。
        // 找到HelloService接口生成的代理类的.class文件如下
        /*
        package com.alibaba.dubbo.common.bytecode;
        import com.alibaba.dubbo.common.bytecode.ClassGenerator.DC;
        import com.alibaba.dubbo.rpc.service.EchoService;
        import java.lang.reflect.InvocationHandler;
        import java.lang.reflect.Method;
        import org.apache.dubbo.samples.api.client.HelloService;

        public class proxy0 implements DC, HelloService, EchoService {
            public static Method[] methods;
            private InvocationHandler handler;

            public proxy0(InvocationHandler var1) {
                this.handler = var1;
            }

            public proxy0() {
            }

            public String sayHello(String var1) {
                Object[] var2 = new Object[]{var1};
                Object var3 = this.handler.invoke(this, methods[0], var2);
                return (String)var3;
            }

            public Object $echo(Object var1) {
                Object[] var2 = new Object[]{var1};
                Object var3 = this.handler.invoke(this, methods[1], var2);
                return (Object)var3;
            }
        }*/
    }

    private static String asArgument(Class<?> cl, String name) {
        if (cl.isPrimitive()) {
            if (Boolean.TYPE == cl) {
                return name + "==null?false:((Boolean)" + name + ").booleanValue()";
            }
            if (Byte.TYPE == cl) {
                return name + "==null?(byte)0:((Byte)" + name + ").byteValue()";
            }
            if (Character.TYPE == cl) {
                return name + "==null?(char)0:((Character)" + name + ").charValue()";
            }
            if (Double.TYPE == cl) {
                return name + "==null?(double)0:((Double)" + name + ").doubleValue()";
            }
            if (Float.TYPE == cl) {
                return name + "==null?(float)0:((Float)" + name + ").floatValue()";
            }
            if (Integer.TYPE == cl) {
                return name + "==null?(int)0:((Integer)" + name + ").intValue()";
            }
            if (Long.TYPE == cl) {
                return name + "==null?(long)0:((Long)" + name + ").longValue()";
            }
            if (Short.TYPE == cl) {
                return name + "==null?(short)0:((Short)" + name + ").shortValue()";
            }
            throw new RuntimeException(name + " is unknown primitive type.");
        }
        return "(" + ReflectUtils.getName(cl) + ")" + name;
    }

    /**
     * get instance with default handler.
     *
     * @return instance.
     */
    public Object newInstance() {
        return newInstance(THROW_UNSUPPORTED_INVOKER);
    }

    /**
     * get instance with special handler.
     *
     * @return instance.
     */
    abstract public Object newInstance(InvocationHandler handler);
}
