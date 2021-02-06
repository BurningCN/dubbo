package my.rpc;

import my.common.utils.ArrayUtils;
import my.server.*;
import my.server.serialization.ObjectInput;
import my.server.serialization.ObjectOutput;

import java.io.IOException;

import static my.rpc.defaults.Constants.*;
import static my.common.constants.CommonConstants.*;
import static my.common.constants.CommonConstants.TIMEOUT_KEY;

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
        result.getObjectAttachments().put(VERSION_KEY, Version.DEFAULT_VERSION);
        output.writeAttachments(result.getObjectAttachments());
    }

    @Override
    protected Object decodeRequestData(ObjectInput input, Request request) throws IOException {
        DecodeableRpcInvocation decodeableRpcInvocation = null;
        boolean isDecodeInIOThread = getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD);
        if (isDecodeInIOThread) {
            decodeableRpcInvocation = new DecodeableRpcInvocation(request, input);
            decodeableRpcInvocation.decode();
        } else {
            // todo myRpc 原版用的是 UnsafeByteArrayInputStream
        }
        return decodeableRpcInvocation;
    }

    @Override
    protected Object decodeResponseData(ObjectInput input, Response response) throws IOException {
        DecodeableRpcResult decodeableRpcResult = null;
        boolean isDecodeInIOThread = getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD);
        if (isDecodeInIOThread) {
            decodeableRpcResult = new DecodeableRpcResult(response, input, (Invocation) getRequestData(response.getId()));
            decodeableRpcResult.decode();
        } else {
            // todo myRpc 原版用的是 UnsafeByteArrayInputStream
        }
        return decodeableRpcResult;

    }


    private Object getRequestData(long id) {
        DefaultFuture future = DefaultFuture.getFuture(id);
        return future == null ? null : future.getRequest().getData();
    }
}

//

