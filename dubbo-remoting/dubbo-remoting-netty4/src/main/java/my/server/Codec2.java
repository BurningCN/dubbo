package my.server;

import java.io.IOException;

/**
 * @author gy821075
 * @date 2021/1/28 14:47
 */
public interface Codec2 {
    void encode(ChannelBuffer buffer, Object msg) throws IOException;

    Object decode(ChannelBuffer buffer) throws Exception;

    enum DecodeResult {
        NEED_MORE_INPUT
    }
}
