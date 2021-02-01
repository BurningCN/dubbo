package netty.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static netty.server.DataTimeUtil.now;

/**
 * @author geyu
 * @date 2021/1/28 14:51
 */
@io.netty.channel.ChannelHandler.Sharable
public class NettyServerHandler extends ChannelDuplexHandler {

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final URL url;
    private final ChannelHandler handler;

    public NettyServerHandler(URL url, ChannelHandler handler) {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler == null");
        }
        this.url = url;
        this.handler = handler;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        try {
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(),url);
            channels.putIfAbsent(NetUtils.toAddressString((InetSocketAddress) ctx.channel().remoteAddress()), ctx.channel());
            handler.connected(channel);
            System.out.println(now() + "The connection of " + channel.getRemoteAddress() + " -> " + channel.getLocalAddress() + " is established.");
        } catch (RemotingException e) {
            e.printStackTrace();
        }


    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(),url);
            channels.remove(NetUtils.toAddressString((InetSocketAddress) ctx.channel().remoteAddress()));
            NettyChannel.removeChannel(ctx.channel());
            handler.disconnected(channel);
            System.out.println(now() + "The connection of " + channel.getRemoteAddress() + " -> " + channel.getLocalAddress() + " is disconnected.");
        } catch (RemotingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        super.userEventTriggered(ctx, evt);
        if (evt instanceof IdleStateEvent) {
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(),url);
            try {
                System.out.println(now() + "IdleStateEvent triggered, close channel " + channel);
                channel.close();
            } finally {
                NettyChannel.removeChannelIfDisconnected(ctx.channel());
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        try {
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(),url);
            handler.caught(channel, cause);
            NettyChannel.removeChannelIfDisconnected(ctx.channel());
        } catch (RemotingException e) {

        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(),url);
            handler.received(channel, msg);
        } catch (RemotingException e) {

        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            // 其他的都可以不需要super，这里必须要调用，把数据发给对端
            super.write(ctx, msg, promise);
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(),url);
            handler.sent(channel, msg);
        } catch (RemotingException e) {

        }
    }
}
