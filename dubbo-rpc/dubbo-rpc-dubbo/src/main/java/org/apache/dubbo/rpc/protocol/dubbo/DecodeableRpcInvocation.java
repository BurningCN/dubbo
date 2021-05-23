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


import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.serialize.Cleanable;
import org.apache.dubbo.common.serialize.ObjectInput;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Codec;
import org.apache.dubbo.remoting.Decodeable;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.transport.CodecSupport;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.MethodDescriptor;
import org.apache.dubbo.rpc.model.ServiceDescriptor;
import org.apache.dubbo.rpc.model.ServiceRepository;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.apache.dubbo.common.URL.buildKey;
import static org.apache.dubbo.common.constants.CommonConstants.DUBBO_VERSION_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.rpc.protocol.dubbo.CallbackServiceCodec.decodeInvocationArgument;

// OK
public class DecodeableRpcInvocation extends RpcInvocation implements Codec, Decodeable {

    private static final Logger log = LoggerFactory.getLogger(DecodeableRpcInvocation.class);

    private Channel channel;

    private byte serializationType;

    private InputStream inputStream;

    private Request request;

    private volatile boolean hasDecoded;

    public DecodeableRpcInvocation(Channel channel, Request request, InputStream is, byte id) {
        Assert.notNull(channel, "channel == null");
        Assert.notNull(request, "request == null");
        Assert.notNull(is, "inputStream == null");
        this.channel = channel;
        this.request = request;
        this.inputStream = is;
        this.serializationType = id;
    }

    // decode的逻辑可以在netty的io线程中进行(DubbCodec)，也可以在派发线程池（服务端线程池，在NettyServer初始化的）中执行(DecodeHandler <- 该handler是是AllChannelHandler里
    // 面使用线程提交ChannelEventRunnable)
    @Override
    public void decode() throws Exception {
        // todo need pr 这里应该 使用AtomicBoolean 类型 且结合cas，否则依然会导致重复decode
        if (!hasDecoded && channel != null && inputStream != null) {
            try {
                decode(channel, inputStream);// 进去
            } catch (Throwable e) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode rpc invocation failed: " + e.getMessage(), e);
                }
                request.setBroken(true);
                request.setData(e);
            } finally {
                hasDecoded = true;
            }
        }
    }

    @Override
    public void encode(Channel channel, OutputStream output, Object message) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object decode(Channel channel, InputStream input) throws IOException {
        ObjectInput in = CodecSupport.getSerialization(channel.getUrl(), serializationType)
                .deserialize(channel.getUrl(), input);

        // 通过反序列化得到 dubbo version，并保存到 attachments 变量中
        String dubboVersion = in.readUTF();
        request.setVersion(dubboVersion);
        setAttachment(DUBBO_VERSION_KEY, dubboVersion);

        // 通过反序列化得到 path，version，并保存到 attachments 变量中
        String path = in.readUTF();
        setAttachment(PATH_KEY, path);
        setAttachment(VERSION_KEY, in.readUTF());

        // 通过反序列化得到调用方法名
        setMethodName(in.readUTF());

        // 通过反序列化得到参数类型字符串，比如 Ljava/lang/String;
        String desc = in.readUTF();
        setParameterTypesDesc(desc);

        try {
            Object[] args = DubboCodec.EMPTY_OBJECT_ARRAY;
            Class<?>[] pts = DubboCodec.EMPTY_CLASS_ARRAY;
            if (desc.length() > 0) {
//                if (RpcUtils.isGenericCall(path, getMethodName()) || RpcUtils.isEcho(path, getMethodName())) {
//                    pts = ReflectUtils.desc2classArray(desc);
//                } else {
                ServiceRepository repository = ApplicationModel.getServiceRepository();
                ServiceDescriptor serviceDescriptor = repository.lookupService(path);
                if (serviceDescriptor != null) {
                    MethodDescriptor methodDescriptor = serviceDescriptor.getMethod(getMethodName(), desc);
                    if (methodDescriptor != null) {
                        pts = methodDescriptor.getParameterClasses();
                        this.setReturnTypes(methodDescriptor.getReturnTypes());
                    }
                }
                if (pts == DubboCodec.EMPTY_CLASS_ARRAY) {
                    if (!RpcUtils.isGenericCall(desc, getMethodName()) && !RpcUtils.isEcho(desc, getMethodName())) {
                        throw new IllegalArgumentException("Service not found:" + path + ", " + getMethodName());
                    }
                    // 将 desc 解析为参数类型数组
                    pts = ReflectUtils.desc2classArray(desc);
                }
//                }

                args = new Object[pts.length];
                for (int i = 0; i < args.length; i++) {
                    try {
                        // 解析运行时参数
                        args[i] = in.readObject(pts[i]);
                    } catch (Exception e) {
                        if (log.isWarnEnabled()) {
                            log.warn("Decode argument failed: " + e.getMessage(), e);
                        }
                    }
                }
            }
            // 设置参数类型数组
            setParameterTypes(pts);

            // 通过反序列化得到原 attachment 的内容
            Map<String, Object> map = in.readAttachments();
            if (map != null && map.size() > 0) {
                Map<String, Object> attachment = getObjectAttachments();
                if (attachment == null) {
                    attachment = new HashMap<>();
                }
                // 将 map 与当前对象中的 attachment 集合进行融合
                attachment.putAll(map);
                setObjectAttachments(attachment);
            }

            //decode argument ,may be callback // 对 callback 类型的参数进行处理
            for (int i = 0; i < args.length; i++) {
                args[i] = decodeInvocationArgument(channel, this, pts, i, args[i]);
            }

            // 设置参数列表
            setArguments(args);
            String targetServiceName = buildKey((String) getAttachment(PATH_KEY),
                    getAttachment(GROUP_KEY),
                    getAttachment(VERSION_KEY));
            setTargetServiceUniqueName(targetServiceName);

            // 上面的方法通过反序列化将诸如 path、version、调用方法名、参数列表等信息依次解析出来，并设置到相应的字段中，最终得到一个具有完整调
            // 用信息的 DecodeableRpcInvocation 对象。
            // 到这里，请求数据解码的过程就分析完了。此时我们得到了一个 Request 对象，这个对象会被传送到下一个入站处理器中，我们继续往下看。

            // #### 2.3.2 调用服务
            //
            // 解码器将数据包解析成 Request 对象后，NettyHandler 的 messageReceived 方法紧接着会收到这个对象，并将这个对象继续向下传递。
            // 这期间该对象会被依次传递给 NettyServer、MultiMessageHandler、HeartbeatHandler 以及 AllChannelHandler。
            // 最后由 AllChannelHandler 将该对象封装到 Runnable 实现类对象中，并将 Runnable 放入线程池中执行后续的调用逻辑。整个调用栈如下：
            //
            //NettyHandler#messageReceived(ChannelHandlerContext, MessageEvent)   ----> 进 NettyServerHandler 的 channelRead
            //  —> AbstractPeer#received(Channel, Object)
            //    —> MultiMessageHandler#received(Channel, Object)
            //      —> HeartbeatHandler#received(Channel, Object)
            //        —> AllChannelHandler#received(Channel, Object)
            //          —> ExecutorService#execute(Runnable)    // 由线程池执行后续的调用逻辑

            //考虑到篇幅，以及很多中间调用的逻辑并非十分重要，所以这里就不对调用栈中的每个方法都进行分析了。这里我们直接分析调用栈中的分析第一个和最后一个调用方法逻辑。如下：
        } catch (ClassNotFoundException e) {
            throw new IOException(StringUtils.toString("Read invocation data failed.", e));
        } finally {
            if (in instanceof Cleanable) {
                ((Cleanable) in).cleanup();
            }
        }
        return this;
    }

}
