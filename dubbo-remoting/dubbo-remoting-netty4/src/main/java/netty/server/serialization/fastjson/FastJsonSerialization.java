package netty.server.serialization.fastjson;

import netty.server.Constants;
import netty.server.serialization.ObjectInput;
import netty.server.serialization.Serialization;
import netty.server.serialization.ObjectOutput;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author geyu
 * @date 2021/1/29 11:29
 */
public class FastJsonSerialization implements Serialization {

    @Override
    public String getContentType() {
        return "text/json";
    }

    @Override
    public byte getContentTypeId() {
        return Constants.FAST_JSON_SERIALIZATION_ID;
    }

    @Override
    public ObjectOutput serialize(OutputStream os) {
        return new FastJsonObjectOutput(os);
    }

    @Override
    public ObjectInput deSerialize(InputStream bis) {
        return new FastJsonObjectInput(bis);
    }
}
