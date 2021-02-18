package my.server;

import org.apache.dubbo.remoting.Channel;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author gy821075
 * @date 2021/1/29 13:05
 */
public class ChannelBufferInputStream extends InputStream {


    private ChannelBuffer buffer;
    private int startIndex;
    private int endIndex;

    public ChannelBufferInputStream(ChannelBuffer buffer) {
        this(buffer, buffer.readableBytes());
    }

    public ChannelBufferInputStream(ChannelBuffer buffer, int length) {
        this.buffer = buffer;
        this.startIndex = buffer.readerIndex();
        this.endIndex = startIndex + length;
    }

    @Override
    public int available() throws IOException {
        return endIndex - buffer.readerIndex();
    }

    @Override
    public int read() throws IOException {
        if (!buffer.readable()) {
            return -1;
        }
        return buffer.readByte() & 0xff;
    }


}
