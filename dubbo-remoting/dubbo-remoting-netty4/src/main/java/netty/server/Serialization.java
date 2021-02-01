package netty.server;


import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author gy821075
 * @date 2021/1/28 20:22
 */
public interface Serialization {
    byte getContentTypeId();

    ObjectOutput serialize(OutputStream bos);

    ObjectInput deSerialize(InputStream bis);
}
