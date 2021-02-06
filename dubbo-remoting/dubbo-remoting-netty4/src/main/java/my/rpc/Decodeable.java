package my.rpc;

import java.io.IOException;

/**
 * @author gy821075
 * @date 2021/2/5 20:28
 */
public interface Decodeable {
    void decode() throws IOException;
}
