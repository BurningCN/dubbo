package netty.server.serialization.hessian2;

import netty.server.Constants;
import netty.server.serialization.Serialization;
import netty.server.serialization.ObjectInput;
import netty.server.serialization.ObjectOutput;

import java.io.InputStream;
import java.io.OutputStream;


/**
 * @author geyu
 * @date 2021/2/2 19:03
 */
public class Hessian2Serialization implements Serialization {
    @Override
    public byte getContentTypeId() {
        return Constants.NATIVE_HESSIAN_SERIALIZATION_ID;
    }

    @Override
    public String getContentType() {
        return "x-application/native-hessian";
    }

    @Override
    public ObjectOutput serialize(OutputStream bos) {
        return new Hessian2ObjectOutput(bos);
    }

    @Override
    public ObjectInput deSerialize(InputStream bis) {
        return new Hessian2ObjectInput(bis);

    }
}
