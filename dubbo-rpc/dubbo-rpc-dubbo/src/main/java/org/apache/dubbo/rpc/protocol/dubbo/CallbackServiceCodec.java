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
package org.apache.dubbo.rpc.protocol.dubbo;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.bytecode.Wrapper;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.protocol.AsyncToSyncInvoker;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.apache.dubbo.common.constants.CommonConstants.CALLBACK_INSTANCES_LIMIT_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_CALLBACK_INSTANCES;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.METHODS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.rpc.Constants.IS_SERVER_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.CALLBACK_SERVICE_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.CALLBACK_SERVICE_PROXY_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.CHANNEL_CALLBACK_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.Constants.IS_CALLBACK_SERVICE;

/**
 * callback service helper
 */
class CallbackServiceCodec {
    private static final Logger logger = LoggerFactory.getLogger(CallbackServiceCodec.class);

    private static final ProxyFactory PROXY_FACTORY = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();
    private static final DubboProtocol PROTOCOL = DubboProtocol.getDubboProtocol();
    private static final byte CALLBACK_NONE = 0x0;
    private static final byte CALLBACK_CREATE = 0x1;
    private static final byte CALLBACK_DESTROY = 0x2;
    private static final String INV_ATT_CALLBACK_KEY = "sys_callback_arg-";

    private static byte isCallBack(URL url, String protocolServiceKey, String methodName, int argIndex) {
        // parameter callback rule: method-name.parameter-index(starting from 0).callback
        byte isCallback = CALLBACK_NONE;
        if (url != null && url.hasServiceMethodParameter(protocolServiceKey, methodName)) {
            // addListener.0.callback
            String callback = url.getServiceParameter(protocolServiceKey, methodName + "." + argIndex + ".callback");
            if (callback != null) {
                if ("true".equalsIgnoreCase(callback)) {
                    isCallback = CALLBACK_CREATE;
                } else if ("false".equalsIgnoreCase(callback)) {
                    isCallback = CALLBACK_DESTROY;
                }
            }
        }
        return isCallback;
    }

    /**
     * export or unexport callback service on client side
     *
     * @param channel
     * @param url
     * @param clazz
     * @param inst
     * @param export
     * @throws IOException
     */
    // 给客户端使用的方法
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String exportOrUnexportCallbackService(Channel channel, URL url, Class clazz, Object inst, Boolean export) throws IOException {
        // clazz和inst分别是对应index参数的类型和参数值
        // inst就是index对应的值 比如 dubbo-samples # CallbackConsumer  的 callbackService.addListener("foo.bar", msg -> System.out.println("callback:" + msg)); 的第1个参数（index从0开始）
        int instid = System.identityHashCode(inst);

        Map<String, String> params = new HashMap<>(3);
        // no need to new client again  影响 openServer方法
        params.put(IS_SERVER_KEY, Boolean.FALSE.toString());
        // mark it's a callback, for troubleshooting
        params.put(IS_CALLBACK_SERVICE, Boolean.TRUE.toString());
        String group = (url == null ? null : url.getParameter(GROUP_KEY));
        if (group != null && group.length() > 0) {
            params.put(GROUP_KEY, group);
        }
        // add method, for verifying against method, automatic fallback (see dubbo protocol)
        // 注意clazz不是常规service的接口，而是service的方法的index对应参数的class类型，比如dubbo-samples的 CallbackService void addListener(String key, CallbackListener listener); 的第二个参数
        params.put(METHODS_KEY, StringUtils.join(Wrapper.getWrapper(clazz).getDeclaredMethodNames(), ","));
        //params = {HashMap@6399}  size = 3
        // "is_callback_service" -> "true"
        // "methods" -> "changed"
        // "isserver" -> "false"

        // 将url和params的填充到tmpMap
        Map<String, String> tmpMap = new HashMap<>();
        if (url != null) {
            Map<String, String> parameters = url.getParameters();
            if (parameters != null && !parameters.isEmpty()) {
                tmpMap.putAll(parameters);
            }
        }
        tmpMap.putAll(params);
        
        tmpMap.remove(VERSION_KEY);// doesn't need to distinguish version for callback
        tmpMap.remove(Constants.BIND_PORT_KEY); //callback doesn't needs bind.port
        tmpMap.put(INTERFACE_KEY, clazz.getName());
        // clazz.getName() + "." + instid  eg samples.callback.CallbackListener.483505398
        URL exportUrl = new URL(DubboProtocol.NAME, channel.getLocalAddress().getAddress().getHostAddress(), channel.getLocalAddress().getPort(), clazz.getName() + "." + instid, tmpMap);

        // no need to generate multiple exporters for different channel in the same JVM, cache key cannot collide.
        // 无需为同一JVM中的不同通道生成多个导出器，高速缓存键不会发生冲突。
        //cacheKey = "callback.service.instid.483505398"
        //countKey = "callback.service.instid.samples.callback.CallbackListener.COUNT"
        String cacheKey = getClientSideCallbackServiceCacheKey(instid);
        String countKey = getClientSideCountKey(clazz.getName());
        if (export) {
            // one channel can have multiple callback instances, no need to re-export for different instance.
            // 一个通道可以有多个回调实例，而无需为其他实例重新导出。
            if (!channel.hasAttribute(cacheKey)) {
                // 进去
                if (!isInstancesOverLimit(channel, url, clazz.getName(), instid, false)) {
                    ApplicationModel.getServiceRepository().registerService(clazz);
                    Invoker<?> invoker = PROXY_FACTORY.getInvoker(inst, clazz, exportUrl);
                    // should destroy resource?
                    // 进去 因为side不是sever，所以内部并不会openServer
                    Exporter<?> exporter = PROTOCOL.export(invoker);
                    // this is used for tracing if instid has published service or not.
                    // 将exporter填充到channel的属性
                    channel.setAttribute(cacheKey, exporter);
                    logger.info("Export a callback service :" + exportUrl + ", on " + channel + ", url is: " + url);
                    // 进去
                    increaseInstanceCount(channel, countKey);
                }
            }
            // unexport 和前面相反
        } else {
            if (channel.hasAttribute(cacheKey)) {
                Exporter<?> exporter = (Exporter<?>) channel.getAttribute(cacheKey);
                exporter.unexport();
                channel.removeAttribute(cacheKey);
                decreaseInstanceCount(channel, countKey);
            }
        }
        return String.valueOf(instid);
    }

    /**
     * refer or destroy callback service on server side
     *
     * @param url
     */
    @SuppressWarnings("unchecked")
    private static Object referOrDestroyCallbackService(Channel channel, URL url, Class<?> clazz, Invocation inv, int instid, boolean isRefer) {
        Object proxy;
        //invokerCacheKey = "callback.service.proxy.2065514340.samples.callback.CallbackListener.483505398.invoker"
        //proxyCacheKey = "callback.service.proxy.2065514340.samples.callback.CallbackListener.483505398"
        String invokerCacheKey = getServerSideCallbackInvokerCacheKey(channel, clazz.getName(), instid);
        String proxyCacheKey = getServerSideCallbackServiceCacheKey(channel, clazz.getName(), instid);
        proxy = channel.getAttribute(proxyCacheKey);
        // callback.service.proxy.2065514340.samples.callback.CallbackListener.COUNT
        String countkey = getServerSideCountKey(channel, clazz.getName());
        if (isRefer) {
            if (proxy == null) {
                // callback://30.25.58.200:20880/samples.callback.CallbackListener?interface=samples.callback.CallbackListener
                URL referurl = URL.valueOf("callback://" + url.getAddress() + "/" + clazz.getName() + "?" + INTERFACE_KEY + "=" + clazz.getName());
                referurl = referurl.addParametersIfAbsent(url.getParameters()).removeParameter(METHODS_KEY);
                if (!isInstancesOverLimit(channel, referurl, clazz.getName(), instid, true)) {
                    ApplicationModel.getServiceRepository().registerService(clazz);
                    // 这是关键
                    Invoker<?> invoker = new ChannelWrappedInvoker(clazz, channel, referurl, String.valueOf(instid));
                    proxy = PROXY_FACTORY.getProxy(new AsyncToSyncInvoker<>(invoker));
                    channel.setAttribute(proxyCacheKey, proxy);
                    channel.setAttribute(invokerCacheKey, invoker);
                    // callback.service.proxy.1250055066.samples.callback.CallbackListener.COUNT ++
                    increaseInstanceCount(channel, countkey);

                    //convert error fail fast .
                    //ignore concurrent problem.
                    // channel.callback.invokers.key
                    Set<Invoker<?>> callbackInvokers = (Set<Invoker<?>>) channel.getAttribute(CHANNEL_CALLBACK_KEY);
                    if (callbackInvokers == null) {
                        callbackInvokers = new ConcurrentHashSet<>(1);
                        channel.setAttribute(CHANNEL_CALLBACK_KEY, callbackInvokers);
                    }
                    callbackInvokers.add(invoker);
                    logger.info("method " + inv.getMethodName() + " include a callback service :" + invoker.getUrl() + ", a proxy :" + invoker + " has been created.");
                }
            }
        } else {
            if (proxy != null) {
                Invoker<?> invoker = (Invoker<?>) channel.getAttribute(invokerCacheKey);
                try {
                    Set<Invoker<?>> callbackInvokers = (Set<Invoker<?>>) channel.getAttribute(CHANNEL_CALLBACK_KEY);
                    if (callbackInvokers != null) {
                        callbackInvokers.remove(invoker);
                    }
                    invoker.destroy();
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
                // cancel refer, directly remove from the map
                channel.removeAttribute(proxyCacheKey);
                channel.removeAttribute(invokerCacheKey);
                decreaseInstanceCount(channel, countkey);
            }
        }
        return proxy;
    }

    private static String getClientSideCallbackServiceCacheKey(int instid) {
        return CALLBACK_SERVICE_KEY + "." + instid;
    }

    private static String getServerSideCallbackServiceCacheKey(Channel channel, String interfaceClass, int instid) {
        return CALLBACK_SERVICE_PROXY_KEY + "." + System.identityHashCode(channel) + "." + interfaceClass + "." + instid;
    }

    private static String getServerSideCallbackInvokerCacheKey(Channel channel, String interfaceClass, int instid) {
        return getServerSideCallbackServiceCacheKey(channel, interfaceClass, instid) + "." + "invoker";
    }

    private static String getClientSideCountKey(String interfaceClass) {
        return CALLBACK_SERVICE_KEY + "." + interfaceClass + ".COUNT";
    }

    private static String getServerSideCountKey(Channel channel, String interfaceClass) {
        return CALLBACK_SERVICE_PROXY_KEY + "." + System.identityHashCode(channel) + "." + interfaceClass + ".COUNT";
    }

    private static boolean isInstancesOverLimit(Channel channel, URL url, String interfaceClass, int instid, boolean isServer) {
        // 第一次client 进来的时候 count为null
        Integer count = (Integer) channel.getAttribute(isServer ? getServerSideCountKey(channel, interfaceClass) : getClientSideCountKey(interfaceClass));
        // 比如值为1000 ，看dubbo-samples callback demo 的 provider.xml里面就给<dubbo:service callbacks=1000>配置了值
        int limit = url.getParameter(CALLBACK_INSTANCES_LIMIT_KEY, DEFAULT_CALLBACK_INSTANCES);
        if (count != null && count >= limit) {
            //client side error
            throw new IllegalStateException("interface " + interfaceClass + " `s callback instances num exceed providers limit :" + limit
                    + " ,current num: " + (count + 1) + ". The new callback service will not work !!! you can cancle the callback service which exported before. channel :" + channel);
        } else {
            return false;
        }
    }

    private static void increaseInstanceCount(Channel channel, String countkey) {
        try {
            //ignore concurrent problem?
            Integer count = (Integer) channel.getAttribute(countkey);
            if (count == null) {
                count = 1;
            } else {
                count++;
            }
            channel.setAttribute(countkey, count);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static void decreaseInstanceCount(Channel channel, String countkey) {
        try {
            Integer count = (Integer) channel.getAttribute(countkey);
            if (count == null || count <= 0) {
                return;
            } else {
                count--;
            }
            channel.setAttribute(countkey, count);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public static Object encodeInvocationArgument(Channel channel, RpcInvocation inv, int paraIndex) throws IOException {
        // get URL directly
        URL url = inv.getInvoker() == null ? null : inv.getInvoker().getUrl();
        byte callbackStatus = isCallBack(url, inv.getProtocolServiceKey(), inv.getMethodName(), paraIndex);
        Object[] args = inv.getArguments();
        Class<?>[] pts = inv.getParameterTypes();
        switch (callbackStatus) {
            case CallbackServiceCodec.CALLBACK_CREATE:
                // sys_callback_arg-1 -> 483505398
                inv.setAttachment(INV_ATT_CALLBACK_KEY + paraIndex, exportOrUnexportCallbackService(channel, url, pts[paraIndex], args[paraIndex], true));
                return null;
            case CallbackServiceCodec.CALLBACK_DESTROY:
                inv.setAttachment(INV_ATT_CALLBACK_KEY + paraIndex, exportOrUnexportCallbackService(channel, url, pts[paraIndex], args[paraIndex], false));
                return null;
            default:
                return args[paraIndex];
        }
    }

    // 给服务端使用的方法
    public static Object decodeInvocationArgument(Channel channel, RpcInvocation inv, Class<?>[] pts, int paraIndex, Object inObject) throws IOException {
        // if it's a callback, create proxy on client side, callback interface on client side can be invoked through channel
        // need get URL from channel and env when decode
        // 如果是回调，请在客户端上创建代理，可以通过通道调用客户端上的回调接口，需要在解码时从通道获取URL和env
        URL url;
        try {
            url = DubboProtocol.getDubboProtocol().getInvoker(channel, inv).getUrl();
        } catch (RemotingException e) {
            if (logger.isInfoEnabled()) {
                logger.info(e.getMessage(), e);
            }
            return inObject;
        }
        byte callBackStatus = isCallBack(url, inv.getProtocolServiceKey(), inv.getMethodName(), paraIndex);
        switch (callBackStatus) {
            case CallbackServiceCodec.CALLBACK_CREATE:
                try {
                    // Integer.parseInt(inv.getAttachment(INV_ATT_CALLBACK_KEY + paraIndex) 这个值是client传过来的，得到的值为 instId
                    // 进去
                    return referOrDestroyCallbackService(channel, url, pts[paraIndex], inv, Integer.parseInt(inv.getAttachment(INV_ATT_CALLBACK_KEY + paraIndex)), true);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    throw new IOException(StringUtils.toString(e));
                }
            case CallbackServiceCodec.CALLBACK_DESTROY:
                try {
                    return referOrDestroyCallbackService(channel, url, pts[paraIndex], inv, Integer.parseInt(inv.getAttachment(INV_ATT_CALLBACK_KEY + paraIndex)), false);
                } catch (Exception e) {
                    throw new IOException(StringUtils.toString(e));
                }
            default:
                return inObject;
        }
    }
}
