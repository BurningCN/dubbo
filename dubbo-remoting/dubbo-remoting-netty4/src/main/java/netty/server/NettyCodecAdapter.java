package netty.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.List;

/**
 * @author geyu
 * @date 2021/1/28 14:40
 */
public class NettyCodecAdapter {

    private Codec2 codec;

    private InternalEncoder internalEncoder = new InternalEncoder();

    private InternalDecoder internalDecoder = new InternalDecoder();

    public NettyCodecAdapter(Codec2 codec) {
        this.codec = codec;
    }

    private class InternalEncoder extends MessageToByteEncoder {
        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf output) throws Exception {
            ChannelBuffer buffer = new NettyBackedChannelBuffer(output);
//            Channel channel = ctx.channel();
//            InternalChannel internalChannel = InternalChannelImpl.getOrAddChannel(channel);
            // NettyChannel.getOrAddChannel(ctx.channel())
            codec.encode(buffer, msg);
        }
    }

    private class InternalDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf input, List<Object> out) throws Exception {
            ChannelBuffer buffer = new NettyBackedChannelBuffer(input);
//            InternalChannel internalChannel = InternalChannelImpl.getOrAddChannel(ctx.channel());
            while (buffer.readable()) {
                int readerIndex = buffer.readerIndex();
                // （拷贝原）注意这里的判断非常重要！！！因为如果数据量大的话，tcp会拆包，比如发送端发送10726字节的数据，那么接受的时候此时Message，
                // 第一个过来的分节一般是1024，其并不是一个完整的数据包（还差很多），而codec.decode内部就会有判断逻辑（），如果不够的话，
                // 返回NEED_MORE_INPUT，进行如下处理，恢复读指针，然后等待tcp传入更多的数据，此时第二次（或者第三次...）进来decode方法的时候
                // 此时的input参数很可能就是10726了！！！详见测试程序testDataPackage（大数据量的）
                Object msg = codec.decode(buffer);
                if (msg == Codec2.DecodeResult.NEED_MORE_INPUT) {
                    buffer.readerIndex(readerIndex);
                    break;
                } else {
                    out.add(msg);
                }
            }
        }
    }

    public InternalDecoder getInternalDecoder() {
        return internalDecoder;
    }

    public InternalEncoder getInternalEncoder() {
        return internalEncoder;
    }
}
