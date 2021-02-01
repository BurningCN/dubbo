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
package org.apache.dubbo.rpc.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.InvokeMode;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.service.GenericService;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.dubbo.common.constants.CommonConstants.$INVOKE;
import static org.apache.dubbo.common.constants.CommonConstants.$INVOKE_ASYNC;
import static org.apache.dubbo.common.constants.CommonConstants.GENERIC_PARAMETER_DESC;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_ATTACHMENT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;
import static org.apache.dubbo.rpc.Constants.$ECHO;
import static org.apache.dubbo.rpc.Constants.$ECHO_PARAMETER_DESC;
import static org.apache.dubbo.rpc.Constants.ASYNC_KEY;
import static org.apache.dubbo.rpc.Constants.AUTO_ATTACH_INVOCATIONID_KEY;
import static org.apache.dubbo.rpc.Constants.ID_KEY;
import static org.apache.dubbo.rpc.Constants.RETURN_KEY;

/**
 * RpcUtils
 */
// OK
public class RpcUtils {

    private static final Logger logger = LoggerFactory.getLogger(RpcUtils.class);
    private static final AtomicLong INVOKE_ID = new AtomicLong(0);

    public static Class<?> getReturnType(Invocation invocation) {
        try {
            if (invocation != null && invocation.getInvoker() != null
                    && invocation.getInvoker().getUrl() != null
                    && invocation.getInvoker().getInterface() != GenericService.class
                    && !invocation.getMethodName().startsWith("$")) {
                // getServiceInterface进去
                String service = invocation.getInvoker().getUrl().getServiceInterface();
                if (StringUtils.isNotEmpty(service)) {
                    // 进去
                    Method method = getMethodByService(invocation, service);
                    return method.getReturnType();
                }
            }
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
        return null;
    }

    // 这个方法主要是这样的，获取方法返回类型以及带泛型的返回类型。以及如果返回类型是CompletableFuture<x>修饰的，去掉外层的CompletableFuture，
    // 返回x以及具体带泛型的返回类型
    // 比如方法的返回类型是CompletableFuture<List<String>> 返回[List ,List<String>]
    // 比如方法的返回类型是List<String> ，返回[List,List<String>]
    // 比如方法的返回类型是String，返回[String,String]
    public static Type[] getReturnTypes(Invocation invocation) {
        try {
            if (invocation != null && invocation.getInvoker() != null
                    && invocation.getInvoker().getUrl() != null
                    && invocation.getInvoker().getInterface() != GenericService.class
                    && !invocation.getMethodName().startsWith("$")) {
                String service = invocation.getInvoker().getUrl().getServiceInterface();
                if (StringUtils.isNotEmpty(service)) {
                    Method method = getMethodByService(invocation, service);
                    // 上面逻辑和前面的getReturnType一致

                    // 进去
                    return ReflectUtils.getReturnTypes(method);
                }
            }
        } catch (Throwable t) {
            logger.warn(t.getMessage(), t);
        }
        return null;
    }

    public static Long getInvocationId(Invocation inv) {
        String id = inv.getAttachment(ID_KEY);
        return id == null ? null : new Long(id);
    }

    /**
     * Idempotent operation: invocation id will be added in async operation by default
     * 幂等操作:默认情况下，将在异步操作中添加调用id
     *
     * @param url
     * @param inv
     */
    public static void attachInvocationIdIfAsync(URL url, Invocation inv) {
        // 两个都进去
        if (isAttachInvocationId(url, inv) && getInvocationId(inv) == null && inv instanceof RpcInvocation) {
            // 添加到inv，id是自增的
            inv.setAttachment(ID_KEY, String.valueOf(INVOKE_ID.getAndIncrement()));
        }
    }

    private static boolean isAttachInvocationId(URL url, Invocation invocation) {
        // 进去
        String value = url.getMethodParameter(invocation.getMethodName(), AUTO_ATTACH_INVOCATIONID_KEY);
        if (value == null) {
            // add invocationid in async operation by default
            // 进去
            return isAsync(url, invocation);
        } else if (Boolean.TRUE.toString().equalsIgnoreCase(value)) {
            return true;
        } else {
            return false;
        }
    }

    // 下三个方法是一起的，围绕$invoke

    public static String getMethodName(Invocation invocation) {
        if ($INVOKE.equals(invocation.getMethodName())
                && invocation.getArguments() != null
                && invocation.getArguments().length > 0
                // getArguments()[0]是方法名称:String
                // getArguments()[1]是传给方法的参数类型数组:String[]
                // getArguments()[2]是传给方法的参数值数组:Object[]
                // 这里取方法名，肯定是String类型
                && invocation.getArguments()[0] instanceof String) {
            return (String) invocation.getArguments()[0];
        }
        return invocation.getMethodName();
    }

    public static Object[] getArguments(Invocation invocation) {
        if ($INVOKE.equals(invocation.getMethodName())
                && invocation.getArguments() != null
                && invocation.getArguments().length > 2
                // getArguments()[0]是方法名称:String
                // getArguments()[1]是传给方法的参数类型数组:String[]
                // getArguments()[2]是传给方法的参数值数组:Object[]
                // 这里取传给方法的参数值
                && invocation.getArguments()[2] instanceof Object[]) {
            return (Object[]) invocation.getArguments()[2];
        }
        return invocation.getArguments();
    }

    public static Class<?>[] getParameterTypes(Invocation invocation) {
        if ($INVOKE.equals(invocation.getMethodName())
                && invocation.getArguments() != null
                && invocation.getArguments().length > 1
                // getArguments()[0]是方法名称:String
                // getArguments()[1]是传给方法的参数类型数组:String[]
                // getArguments()[2]是传给方法的参数值数组:Object[]
                // 这里取方法的参数类型
                && invocation.getArguments()[1] instanceof String[]) {
            String[] types = (String[]) invocation.getArguments()[1];
            if (types == null) {
                return new Class<?>[0];
            }
            Class<?>[] parameterTypes = new Class<?>[types.length];
            for (int i = 0; i < types.length; i++) {
                // 获取参数类型
                parameterTypes[i] = ReflectUtils.forName(types[0]);
            }
            return parameterTypes;
        }
        return invocation.getParameterTypes();
    }

    public static boolean isAsync(URL url, Invocation inv) {
        boolean isAsync;

        if (inv instanceof RpcInvocation) {
            RpcInvocation rpcInvocation = (RpcInvocation) inv;
            if (rpcInvocation.getInvokeMode() != null) {
                // 1
                return rpcInvocation.getInvokeMode() == InvokeMode.ASYNC;
            }
        }

        // 2
        if (Boolean.TRUE.toString().equals(inv.getAttachment(ASYNC_KEY))) {
            isAsync = true;
        } else {
            // 3 进去
            isAsync = url.getMethodParameter(getMethodName(inv), ASYNC_KEY, false);
        }
        return isAsync;
    }

    public static boolean isReturnTypeFuture(Invocation inv) {
        Class<?> clazz;
        if (inv instanceof RpcInvocation) {
            clazz = ((RpcInvocation) inv).getReturnType();
        } else {
            clazz = getReturnType(inv);
        }
        return (clazz != null && CompletableFuture.class.isAssignableFrom(clazz)) || isGenericAsync(inv);
    }

    public static boolean isGenericAsync(Invocation inv) {
        return $INVOKE_ASYNC.equals(inv.getMethodName());
    }

    // check parameterTypesDesc to fix CVE-2020-1948
    public static boolean isGenericCall(String parameterTypesDesc, String method) {
        return ($INVOKE.equals(method) || $INVOKE_ASYNC.equals(method)) && GENERIC_PARAMETER_DESC.equals(parameterTypesDesc);
    }

    // check parameterTypesDesc to fix CVE-2020-1948
    public static boolean isEcho(String parameterTypesDesc, String method) {
        return $ECHO.equals(method) && $ECHO_PARAMETER_DESC.equals(parameterTypesDesc);
    }

    public static InvokeMode getInvokeMode(URL url, Invocation inv) {
        if (inv instanceof RpcInvocation) {
            RpcInvocation rpcInvocation = (RpcInvocation) inv;
            if (rpcInvocation.getInvokeMode() != null) {
                return rpcInvocation.getInvokeMode();
            }
        }

        // 三种调用模式
        // 进去
        if (isReturnTypeFuture(inv)) {
            return InvokeMode.FUTURE;
            // 进去
        } else if (isAsync(url, inv)) {
            return InvokeMode.ASYNC;
        } else {
            return InvokeMode.SYNC;
        }
    }

    public static boolean isOneway(URL url, Invocation inv) {
        // 方法的返回值为void那么就是oneWay
        boolean isOneway;
        if (Boolean.FALSE.toString().equals(inv.getAttachment(RETURN_KEY))) {
            isOneway = true;
        } else {
            // eg ?test.return=false
            isOneway = !url.getMethodParameter(getMethodName(inv), RETURN_KEY, true);
        }
        return isOneway;
    }

    // 很easy不说了
    private static Method getMethodByService(Invocation invocation, String service) throws NoSuchMethodException {
        Class<?> invokerInterface = invocation.getInvoker().getInterface();
        Class<?> cls = invokerInterface != null ? ReflectUtils.forName(invokerInterface.getClassLoader(), service)
                : ReflectUtils.forName(service);
        Method method = cls.getMethod(invocation.getMethodName(), invocation.getParameterTypes());
        if (method.getReturnType() == void.class) {
            return null;
        }
        return method;
    }

    public static long getTimeout(Invocation invocation, long defaultTimeout) {
        long timeout = defaultTimeout;
        // 进去，看RpcInvocation的实现方法
        Object genericTimeout = invocation.getObjectAttachment(TIMEOUT_ATTACHMENT_KEY);
        if (genericTimeout != null) {
            // 进去
            timeout = convertToNumber(genericTimeout, defaultTimeout);
        }
        return timeout;
    }

    public static long getTimeout(URL url, String methodName, RpcContext context, long defaultTimeout) {
        long timeout = defaultTimeout;
        // 从attachments容器获取，进去
        Object genericTimeout = context.getObjectAttachment(TIMEOUT_KEY);
        if (genericTimeout != null) {
            timeout = convertToNumber(genericTimeout, defaultTimeout);
        } else if (url != null) {
            // 从url获取方法的超时时间，进去
            timeout = url.getMethodPositiveParameter(methodName, TIMEOUT_KEY, defaultTimeout);
        }
        return timeout;
    }

    private static long convertToNumber(Object obj, long defaultTimeout) {
        long timeout = 0;
        try {
            if (obj instanceof String) {
                timeout = Long.parseLong((String) obj);
            } else if (obj instanceof Number) {
                timeout = ((Number) obj).longValue();
            } else {
                timeout = Long.parseLong(obj.toString());
            }
        } catch (Exception e) {
            // ignore
        }
        return timeout;
    }
}
