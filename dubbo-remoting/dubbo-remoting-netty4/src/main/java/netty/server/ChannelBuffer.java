package netty.server;

/**
 * @author gy821075
 * @date 2021/1/28 19:33
 */
public interface ChannelBuffer {
    int writerIndex();

    void writerIndex(int i);

    void writerBytes(byte[] header);

    void writerBytes(byte[] b, int off, int len);

    int readableBytes();

    void readBytes(byte[] header);

    int readerIndex();

    void readerIndex(int readerIndex);

    boolean readable();

    int readByte();
}
