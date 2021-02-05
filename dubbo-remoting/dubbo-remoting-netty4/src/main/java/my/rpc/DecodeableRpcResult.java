package my.rpc;

import my.server.Response;
import my.server.serialization.ObjectInput;

/**
 * @author geyu
 * @date 2021/2/5 18:48
 */
public class DecodeableRpcResult implements Decodeable {
    public DecodeableRpcResult(ObjectInput input, Response response) {
    }

    public DecodeableRpcResult(Response response, ObjectInput objectInput, Invocation requestData) {
    }

    public void decode() {
    }
}
