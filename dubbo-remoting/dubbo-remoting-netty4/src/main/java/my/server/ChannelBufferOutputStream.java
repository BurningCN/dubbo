package my.server;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author geyu
 * @date 2021/1/29 11:04
 */
public class ChannelBufferOutputStream extends OutputStream {

    ChannelBuffer buffer;
    int startIndex;

    // ByteBuf->ChannelBuffer->ChannelBufferOutputStream
    public ChannelBufferOutputStream(ChannelBuffer buffer) {
        this.buffer = buffer;
        this.startIndex = buffer.writerIndex();
    }

    @Override
    public void write(byte[] b) throws IOException {
        buffer.writerBytes(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        buffer.writerBytes(b, off, len);
    }

    @Override
    public void write(int b) throws IOException {
        // buffer.writeByte((byte(b)));
    }

    public int writtenBytes() {
        return buffer.writerIndex() - startIndex;
    }
}
