package my.server;


import io.netty.buffer.ByteBuf;
import org.apache.dubbo.common.utils.Assert;

/**
 * @author geyu
 * @date 2021/1/28 19:31
 */
public class NettyBackedChannelBuffer implements ChannelBuffer {
    private ByteBuf byteBuf;

    public NettyBackedChannelBuffer(ByteBuf byteBuf) {
        Assert.notNull(byteBuf, "buffer == null");
        this.byteBuf = byteBuf;
    }

    @Override
    public int writerIndex() {
        return byteBuf.writerIndex();
    }

    @Override
    public void writerIndex(int writerIndex) {
        byteBuf.writerIndex(writerIndex);
    }

    @Override
    public void writerBytes(byte[] header) {
        byteBuf.writeBytes(header);
    }

    @Override
    public void writerBytes(byte[] b, int off, int len) {
        byteBuf.writeBytes(b, off, len);
    }

    @Override
    public int readableBytes() {
        return byteBuf.readableBytes();
    }

    @Override
    public void readBytes(byte[] header) {
        byteBuf.readBytes(header);
    }

    @Override
    public int readerIndex() {
        return byteBuf.readerIndex();
    }

    @Override
    public void readerIndex(int readerIndex) {
        byteBuf.readerIndex(readerIndex);
    }

    @Override
    public boolean readable() {
        return byteBuf.isReadable();
    }

    @Override
    public int readByte() {
        return byteBuf.readByte();
    }
}

