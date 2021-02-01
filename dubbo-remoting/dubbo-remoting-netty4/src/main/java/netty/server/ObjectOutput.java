package netty.server;

import java.io.IOException;

/**
 * @author gy821075
 * @date 2021/1/29 11:15
 */
public interface ObjectOutput extends DataOutput {
    void flushBuffer();

    void writeObject(Object data) throws IOException;

}
