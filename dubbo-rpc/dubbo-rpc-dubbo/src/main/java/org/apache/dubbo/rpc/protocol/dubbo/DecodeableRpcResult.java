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
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.Assert;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.Codec;
import org.apache.dubbo.remoting.Decodeable;
import org.apache.dubbo.remoting.exchange.Response;
import org.apache.dubbo.remoting.transport.CodecSupport;
import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.support.RpcUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;

public class DecodeableRpcResult extends AppResponse implements Codec, Decodeable {

    private static final Logger log = LoggerFactory.getLogger(DecodeableRpcResult.class);

    private Channel channel;

    private byte serializationType;

    private InputStream inputStream;

    private Response response;

    private Invocation invocation;

    private volatile boolean hasDecoded;

    public DecodeableRpcResult(Channel channel, Response response, InputStream is, Invocation invocation, byte id) {
        Assert.notNull(channel, "channel == null");
        Assert.notNull(response, "response == null");
        Assert.notNull(is, "inputStream == null");
        this.channel = channel;
        this.response = response;
        this.inputStream = is;
        // 注意inv的填充来源，以及这个在该类的作用
        this.invocation = invocation;
        this.serializationType = id;
    }

    @Override
    public void encode(Channel channel, OutputStream output, Object message) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object decode(Channel channel, InputStream input) throws IOException {
        if (log.isDebugEnabled()) {
            Thread thread = Thread.currentThread();
            log.debug("Decoding in thread -- [" + thread.getName() + "#" + thread.getId() + "]");
        }

        ObjectInput in = CodecSupport.getSerialization(channel.getUrl(), serializationType)
                .deserialize(channel.getUrl(), input);

        // 反序列化响应类型
        byte flag = in.readByte();
        switch (flag) {
            case DubboCodec.RESPONSE_NULL_VALUE:
                break;
            case DubboCodec.RESPONSE_VALUE:
                handleValue(in);
                break;
            case DubboCodec.RESPONSE_WITH_EXCEPTION:
                handleException(in);
                break;
            // 返回值为空，且携带了 attachments 集合
            case DubboCodec.RESPONSE_NULL_VALUE_WITH_ATTACHMENTS:
                handleAttachment(in); // 进去
                break;
            // 返回值不为空，且携带了 attachments 集合
            case DubboCodec.RESPONSE_VALUE_WITH_ATTACHMENTS:
                handleValue(in);// 进去
                handleAttachment(in);// 进去
                break;
            // 异常对象不为空，且携带了 attachments 集合
            case DubboCodec.RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS:
                handleException(in);// 进去
                handleAttachment(in);// 进去
                break;
            default:
                throw new IOException("Unknown result flag, expect '0' '1' '2' '3' '4' '5', but received: " + flag);
        }
        if (in instanceof Cleanable) {
            ((Cleanable) in).cleanup();
        }
        return this;
        // 本篇文章所分析的源码版本为 2.6.4，该版本下的 Response 支持 attachments 集合，所以上面仅对部分 case 分支进行了注释。其他 case
        // 分支的逻辑比被注释分支的逻辑更为简单，这里就忽略了。我们所使用的测试服务接口 DemoService 包含了一个具有返回值的方法，正常调用下，线程
        // 会进入 RESPONSE_VALUE_WITH_ATTACHMENTS 分支中。然后线程会从 invocation 变量（大家探索一下 invocation 变量的由来）中获取返回
        // 值类型，接着对调用结果进行反序列化，并将序列化后的结果存储起来。最后对 attachments 集合进行反序列化，并存到指定字段中。到此，关于响应
        // 数据的解码过程就分析完了。接下来，我们再来探索一下响应对象 Response 的去向。
        // #### 2.5.2 向用户线程传递调用结果
        //
        // 响应数据解码完成后，Dubbo 会将响应对象派发到线程池上。要注意的是，线程池中的线程并非用户的调用线程，所以要想办法将响应对象从线程池线程传
        // 递到用户线程上。我们在 2.1 节分析过用户线程在发送完请求后的动作，即调用 DefaultFuture 的 get 方法等待响应对象的到来。当响应对象到来
        // 后，用户线程会被唤醒，并通过**调用编号**获取属于自己的响应对象。下面我们来看一下整个过程对应的代码。

        // .... 代码不粘贴过来了，代码较老，旧版本DefaultFuture是自己搞的，现版本是直接利用CompletableFuture....


        // 以上逻辑是将响应对象保存到相应的 DefaultFuture 实例中，然后再唤醒用户线程，随后用户线程即可从 DefaultFuture 实例中获取到相应结果。
        // 本篇文章在多个地方都强调过调用编号很重要，但一直没有解释原因，这里简单说明一下。一般情况下，服务消费方会并发调用多个服务，每个用户线程发
        // 送请求后，会调用不同 DefaultFuture 对象的 get 方法进行等待。 一段时间后，服务消费方的线程池会收到多个响应对象。这个时候要考虑一个问
        // 题，如何将每个响应对象传递给相应的 DefaultFuture 对象，且不出错。答案是通过调用编号。DefaultFuture 被创建时，会要求传入一个
        // Request 对象。此时 DefaultFuture 可从 Request 对象中获取调用编号，并将 <调用编号, DefaultFuture 对象> 映射关系存入到静态
        // Map 中，即 FUTURES。线程池中的线程在收到 Response 对象后，会根据 Response 对象中的调用编号到 FUTURES 集合中取出相应的
        // DefaultFuture 对象，然后再将 Response 对象设置到 DefaultFuture 对象中。最后再唤醒用户线程，这样用户线程即可从 DefaultFuture
        // 对象中获取调用结果了。整个过程大致如下图：
    }

    @Override
    public void decode() throws Exception {
        if (!hasDecoded && channel != null && inputStream != null) {
            try {// 进去
                decode(channel, inputStream);
            } catch (Throwable e) {
                if (log.isWarnEnabled()) {
                    log.warn("Decode rpc result failed: " + e.getMessage(), e);
                }
                response.setStatus(Response.CLIENT_ERROR);
                response.setErrorMessage(StringUtils.toString(e));
            } finally {
                hasDecoded = true;
            }
        }
    }

    private void handleValue(ObjectInput in) throws IOException {
        try {
            // 获取返回值类型
            Type[] returnTypes;
            if (invocation instanceof RpcInvocation) {
                returnTypes = ((RpcInvocation) invocation).getReturnTypes();
            } else {
                returnTypes = RpcUtils.getReturnTypes(invocation);
            }
            // 反序列化调用结果，并保存起来
            Object value = null;
            if (ArrayUtils.isEmpty(returnTypes)) {
                // This almost never happens?
                value = in.readObject();
            } else if (returnTypes.length == 1) {
                value = in.readObject((Class<?>) returnTypes[0]);
            } else {
                value = in.readObject((Class<?>) returnTypes[0], returnTypes[1]);
            }
            setValue(value);
        } catch (ClassNotFoundException e) {
            rethrow(e);
        }
    }

    private void handleException(ObjectInput in) throws IOException {
        try {
            // 反序列化异常对象并 设置异常对象
            setException(in.readThrowable());
        } catch (ClassNotFoundException e) {
            rethrow(e);
        }
    }

    private void handleAttachment(ObjectInput in) throws IOException {
        try {
            // 反序列化 attachments 集合，并存储起来
            setObjectAttachments(in.readAttachments());
        } catch (ClassNotFoundException e) {
            rethrow(e);
        }
    }

    private void rethrow(Exception e) throws IOException {
        throw new IOException(StringUtils.toString("Read response data failed.", e));
    }
}
