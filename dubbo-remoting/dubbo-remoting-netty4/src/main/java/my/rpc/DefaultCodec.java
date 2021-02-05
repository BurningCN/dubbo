package my.rpc;

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

    public DefaultCodec(URL url) {
        super(url);
    }

    @Override
    protected void encodeRequestData(ObjectOutput output, Object data) throws IOException {
        RpcInvocation inv = (RpcInvocation) data;
        output.writeUTF(inv.getAttachment(VERSION_KEY));
        output.writeUTF(inv.getAttachment(PATH_KEY));
        output.writeUTF(inv.getMethodName());
        output.writeUTF(inv.getParameterTypesDesc());
        output.writeObject(inv.getArguments());  // todo myRPC 原版是做了更多的处理
        output.writeObject(inv.getObjectAttachments());
    }

    @Override
    protected void encodeResponseDate(ObjectOutput output, Object data) throws IOException {

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

    // 下面是原版的内容，因为感觉有很多和父类的重复代码，我就直接利用保护方法，让子类重写和父类不同的部分即可。

    //    @Override
//    protected Object decodeBody(ChannelBuffer buffer, byte[] header) throws IOException {
//        ChannelBufferInputStream bis = new ChannelBufferInputStream(buffer);
//        ObjectInput input = serialization.deSerialize(bis);
//        byte flag = header[2], proto = (byte) (flag & SERIALIZATION_MASK); // todo myRPC 支持spi
//        long id = Bytes.bytes2int(header, 4);
//        if ((flag & FLAG_REQUEST) != 0) {
//            Response response = new Response(id);
//            if ((flag & FLAG_EVENT) == 1) {
//                response.setEvent(true);
//            }
//            byte status = header[3];
//            response.setStatus(status);
//            if (status == Response.OK) {
//                Object data = null;
//                if (response.isHeartbeat()) {
//                    data = decodeHeartbeatData(input);
//                } else if (response.isEvent()) {
//                    data = decodeEventData(input);
//                } else {
//                    DecodeableRpcResult decodeableRpcResult = null;
//                    boolean isDecodeInIOThread = getUrl().getParameter(DECODE_IN_IO_THREAD_KEY, DEFAULT_DECODE_IN_IO_THREAD);
//                    if (isDecodeInIOThread) {
//                        decodeableRpcResult = new DecodeableRpcResult(response, input, (Invocation) getRequestData(id));
//                        decodeableRpcResult.decode();
//                    } else {
//                        // todo myRpc 原版用的是 UnsafeByteArrayInputStream
//                        //decodeableRpcResult = new DecodeableRpcResult(response, input, (Invocation) getRequestData(id));
//                    }
//                    data = decodeableRpcResult;
//                }
//                response.setResult(data);
//            } else {
//                response.setErrorMessage(input.readUTF());
//                return response;
//            }
//        } else {
//            Request request = new Request(id);
//            request.setVersion(Version.DEFAULT_VERSION);
//            request.setTwoWay((flag & FLAG_TWOWAY) == 1);
//            if ((flag & FLAG_EVENT) == 1) {
//                request.isEvent(true);
//            }
//        }
//
//        return null;
//    }


//    private byte[] readMessageData(InputStream is) throws IOException {
//        byte[] ret = new byte[is.available()];
//        is.read(ret);
//        return ret;
//    }
}
