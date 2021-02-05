package my.server;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author gy821075
 * @date 2021/1/29 13:05
 */
public class ChannelBufferInputStream extends InputStream {

    ChannelBuffer buffer;
    int startIndex;

    public ChannelBufferInputStream(ChannelBuffer buffer) {
        this.buffer = buffer;
        this.startIndex = buffer.readerIndex();
    }

    @Override
    public int read() throws IOException {
        if (!buffer.readable()) {
            return -1;
        }
        return buffer.readByte() & 0xff;
    }


}
