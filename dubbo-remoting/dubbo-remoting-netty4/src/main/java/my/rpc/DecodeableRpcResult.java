package my.rpc;

import my.common.utils.ArrayUtils;
import my.common.utils.Assert;
import my.common.utils.StringUtils;
import my.server.CodecSupport;
import my.server.Response;
import my.server.serialization.ObjectInput;
import my.server.serialization.Serialization;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author geyu
 * @date 2021/2/5 18:48
 */
public class DecodeableRpcResult extends AppResponse implements Decodeable {
    private InputStream is;
    private byte proto;
    private Response response;
    private ObjectInput input;
    private Invocation invocation;
    private AtomicBoolean decoded = new AtomicBoolean(false);


    public DecodeableRpcResult(Response response, InputStream is, Invocation invocation, byte proto) {
        Assert.notNull(response, "response == null");
        Assert.notNull(is, "is == null");
        Assert.notNull(invocation, "requestData == null");
        this.response = response;
        this.is = is;
        this.invocation = invocation;
        this.proto = proto;
    }

    public void decode() throws IOException {
        try {
            if (decoded.compareAndSet(false, true) && is != null) {
                this.doDecode();
            }
        } catch (Throwable e) {
            System.out.printf("Decode rpc result failed: " + e.getMessage());
            response.setResult(Response.CLIENT_ERROR);
            response.setErrorMessage(StringUtils.toString(e));
        }

    }

    public void doDecode() throws IOException {
        Serialization serialization = CodecSupport.getSerializationById(proto);
        input = serialization.deSerialize(is);
        // 反序列化响应类型
        byte flag = input.readByte();
        switch (flag) {
            case DefaultCodec.RESPONSE_NULL_VALUE:
                break;
            case DefaultCodec.RESPONSE_VALUE:
                handleValue();
                break;
            case DefaultCodec.RESPONSE_WITH_EXCEPTION:
                handleException();
                break;
            case DefaultCodec.RESPONSE_NULL_VALUE_WITH_ATTACHMENTS:
                handleAttachment();
                break;
            case DefaultCodec.RESPONSE_VALUE_WITH_ATTACHMENTS:
                handleValue();
                handleAttachment();
                break;
            case DefaultCodec.RESPONSE_WITH_EXCEPTION_WITH_ATTACHMENTS:
                handleException();
                handleAttachment();
                break;
            default:
                throw new IOException("Unknown result flag, expect '0' '1' '2' '3' '4' '5', but received: " + flag);
        }
    }

    private void handleException() throws IOException {
        try {
            setException(input.readThrowable());
        } catch (IOException e) {
            rethrow(e);
        }
    }

    private void rethrow(Exception e) throws IOException {
        throw new IOException(StringUtils.toString("Read response data failed.", e));
    }

    private void handleAttachment() throws IOException {
        setObjectAttachments(input.readAttachments());
    }

    private void handleValue() throws IOException {
        Type[] returnTypes;
        if (invocation instanceof RpcInvocation) {
            RpcInvocation inv = (RpcInvocation) invocation;
            returnTypes = inv.getReturnTypes();
        } else {
            returnTypes = RpcUtils.getReturnTypes(invocation);
        }
        Object data = null;
        if (ArrayUtils.isEmpty(returnTypes)) {
            data = input.readObject();
        } else if (returnTypes.length == 1) {
            data = input.readObject((Class<?>) returnTypes[0]);
        } else if (returnTypes.length == 2) {
            data = input.readObject((Class<?>) returnTypes[0], returnTypes[1]);
        }
        setValue(data);
    }
}
