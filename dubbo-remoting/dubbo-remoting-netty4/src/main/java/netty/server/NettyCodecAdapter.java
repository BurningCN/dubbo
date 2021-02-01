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

    public NettyCodecAdapter(Codec2 codec){
        this.codec = codec;
    }

    private class InternalEncoder extends MessageToByteEncoder{
        @Override
        protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf output) throws Exception {
            ChannelBuffer buffer = new NettyBackedChannelBuffer(output);
//            Channel channel = ctx.channel();
//            InternalChannel internalChannel = InternalChannelImpl.getOrAddChannel(channel);
            codec.encode(buffer,msg);
        }
    }

    private class InternalDecoder extends ByteToMessageDecoder{
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf input, List<Object> out) throws Exception {
            ChannelBuffer buffer = new NettyBackedChannelBuffer(input);
//            InternalChannel internalChannel = InternalChannelImpl.getOrAddChannel(ctx.channel());
            while(buffer.readable()){
                Object msg = codec.decode(buffer);
                out.add(msg);
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
