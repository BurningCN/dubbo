package my.rpc;

import my.server.Request;
import my.server.serialization.ObjectInput;
import org.apache.dubbo.common.utils.Assert;

import java.util.concurrent.atomic.AtomicBoolean;
import static my.rpc.defaults.Constants.*;
import static my.common.constants.CommonConstants.*;
import static my.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * @author geyu
 * @date 2021/2/5 19:36
 */
public class DecodeableRpcInvocation extends RpcInvocation implements Decodeable {
    private final Request request;
    private final ObjectInput input;
    private AtomicBoolean decoded = new AtomicBoolean(false);

    public DecodeableRpcInvocation(Request request, ObjectInput input) {
        Assert.notNull(input, "input == null");
        Assert.notNull(request, "request == null");
        this.request = request;
        this.input = input;
    }

//    output.writeUTF(inv.getAttachment(VERSION_KEY));
//        output.writeUTF(inv.getAttachment(PATH_KEY));
//        output.writeUTF(inv.getMethodName());
//        output.writeUTF(inv.getParameterTypesDesc());
//        output.writeObject(inv.getArguments());  // todo myRPC 原版是做了更多的处理
//        output.writeObject(inv.getObjectAttachments());

    public void decode() {
        if (decoded.compareAndSet(false, true) && input != null)
            try {
                String version = input.readUTF();
                String path = input.readUTF();
                String methodName = input.readUTF();
                String desc = input.readUTF();

                setAttachment(VERSION_KEY, version);
                setAttachment(PATH_KEY, path);
                setMethodName(methodName);
                setParameterTypesDesc(desc);

                Object[] args = new Object[0];
                Class<?>[] pts = new Class[0];


            } catch (Throwable e) {
                request.setBroken(true);
                request.setData(e);
            }
    }

}
