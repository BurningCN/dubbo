package my.server.serialization;

import java.io.IOException;

/**
 * @author gy821075
 * @date 2021/1/29 11:59
 */
public interface DataOutput {
    void writeUTF(String v) throws IOException;
}
