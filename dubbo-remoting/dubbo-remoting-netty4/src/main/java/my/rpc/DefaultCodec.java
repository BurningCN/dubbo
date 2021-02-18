package my.rpc;

import my.common.io.UnsafeByteArrayInputStream;
import my.common.utils.ArrayUtils;
import my.server.*;
import my.server.serialization.ObjectInput;
import my.server.serialization.ObjectOutput;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static my.rpc.defaults.Constants.*;
import static my.common.constants.CommonConstants.*;

/**
 * @author geyu
 * @date 2021/2/4 20:37
 */
public class DefaultCodec extends ExchangeCodec {
    public static final String NAME = "default";
    public static final byte RESPONSE_WITH_EXCEPTION = 0;
    public static final byte RESPONSE_VALUE = 1;
    public static final byte RESPONSE_NULL_VALUE = 2;
    public static final byte RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS = 3;
    public static final byte RESPONSE_VALUE_WITH_ATTACHMENTS = 4;
    public static final byte RESPONSE_NULL_VALUE_WITH_ATTACHMENTS = 5;


    public DefaultCodec(URL url) {
        super(url);
    }


    // 原版的内容，因为感觉有很多和父类的重复代码，我就直接利用保护方法，让子类重写和父类不同的部分即可。

    private static AtomicInteger atomicInteger = new AtomicInteger(0);

    @Override
    protected void encodeRequestData(ObjectOutput output, Object data) throws IOException {
        RpcInvocation inv = (RpcInvocation) data;
        output.writeUTF(inv.getAttachment(VERSION_KEY));
        output.writeUTF(inv.getAttachment(PATH_KEY));
        output.writeUTF(inv.getMethodName());
        output.writeUTF(inv.getParameterTypesDesc());
        Object[] arguments = inv.getArguments();
        if (ArrayUtils.isNotEmpty(arguments)) {
            for (int i = 0; i < arguments.length; i++) {
                output.writeObject(arguments[i]); // todo myRPC 原版是做了更多的处理,主要是回调的特殊处理
            }
        }
        output.writeAttachments(inv.getObjectAttachments());
        output.writeUTF(inv.getTargetServiceUniqueName());
    }

    @Override
    protected void encodeResponseDate(ObjectOutput output, Object data) throws IOException {
        Result result = (Result) data;
        Throwable throwable = result.getException();
        boolean attach = Version.isSupportResponseAttachment();
        try {
            if (throwable == null) {
                Object value = result.getValue();
                if (value == null) {
                    output.writeByte(attach ? RESPONSE_NULL_VALUE_WITH_ATTACHMENTS : RESPONSE_NULL_VALUE);
                } else {
                    output.writeByte(attach ? RESPONSE_VALUE_WITH_ATTACHMENTS : RESPONSE_VALUE);
                    output.writeObject(value);
                }
            } else {
                output.writeByte(attach ? RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS : RESPONSE_WITH_EXCEPTION);
                output.writeThrowable(throwable);
            }
        } catch (Throwable e) {
            throw e; // 服务端对没有实现序列化接口的对象进行encode的时候回抛异常
        }

        result.getObjectAttachments().put(VERSION_KEY, Version.DEFAULT_VERSION);
        output.writeAttachments(result.getObjectAttachments());
    }

    @Override
    protected Object decodeRequestData(ObjectInput input, Request request, InputStream is, byte proto) throws IOException {
        DecodeableRpcInvocation decodeableRpcInvocation;
        boolean isDecodeInIOThread = getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD);
        if (isDecodeInIOThread) {
            decodeableRpcInvocation = new DecodeableRpcInvocation(request, is, proto);
            decodeableRpcInvocation.decode();
        } else {
            decodeableRpcInvocation = new DecodeableRpcInvocation(request, readRemainingBytes(is), proto);
        }
        return decodeableRpcInvocation;
    }

    @Override
    protected Object decodeResponseData(ObjectInput input, Response response, InputStream is, byte proto) throws IOException {
        DecodeableRpcResult decodeableRpcResult;
        boolean isDecodeInIOThread = getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD);
        if (isDecodeInIOThread) {
            decodeableRpcResult = new DecodeableRpcResult(response, is, (Invocation) getRequestData(response.getId()), proto);
            decodeableRpcResult.decode();
        } else {
            decodeableRpcResult = new DecodeableRpcResult(response, readRemainingBytes(is), (Invocation) getRequestData(response.getId()), proto);
        }
        return decodeableRpcResult;

    }

    //  注意该方法很重要!在ExchangeCodec进子类的逻辑，即DefaultCodec的时候，如果判定不在当前线程解码的话，需要重新构建一个【读指针移动到末尾操作】的新的ObjectInput，最根本的原因是需要把is里面包装的byteBuf的读指针读完，使得r=w，不然的话回到NettyCodecAdapter的时候会判定依然满足buffer.readableBytes>0，导致继续解码，其实是不必要的。
    private InputStream readRemainingBytes(InputStream is) throws IOException {
        byte[] result = new byte[is.available()];
        if (is.available() > 0) {
            is.read(result);
        }
        return new UnsafeByteArrayInputStream(result);
    }

    private Object getRequestData(long id) {
        DefaultFuture future = DefaultFuture.getFuture(id);
        return future == null ? null : future.getRequest().getData();
    }
}
