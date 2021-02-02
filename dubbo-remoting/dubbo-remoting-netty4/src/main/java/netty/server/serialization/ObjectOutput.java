package netty.server.serialization;

import java.io.IOException;

/**
 * @author gy821075
 * @date 2021/1/29 11:15
 */
public interface ObjectOutput extends DataOutput {
    void flushBuffer() throws IOException;

    void writeObject(Object data) throws IOException;

}
