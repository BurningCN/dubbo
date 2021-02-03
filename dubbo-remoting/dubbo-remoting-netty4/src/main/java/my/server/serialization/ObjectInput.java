package my.server.serialization;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * @author gy821075
 * @date 2021/1/29 13:17
 */
public interface ObjectInput {
    Object readObject() throws IOException;

    <T> T readObject(Class<T> clz) throws IOException;

    <T> T readObject(Class<T> clz, Type type) throws IOException;

    String readUTF() throws IOException;
}
