package netty.server;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author geyu
 * @date 2021/1/29 11:29
 */
public class FastJsonSerializable implements Serialization {

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
