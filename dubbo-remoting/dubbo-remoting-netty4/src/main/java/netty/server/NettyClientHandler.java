package netty.server;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * @author geyu
 * @date 2021/1/29 19:50
 */
public class NettyClientHandler extends ChannelDuplexHandler {

    private ChannelHandler handler;

    private URL url;

    public NettyClientHandler(ChannelHandler handler, URL url) {
        this.handler = handler;
        this.url = url;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        try {
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url);
            System.out.println(DataTimeUtil.now() + "The connection of " + channel.getLocalAddress() + " -> " + channel.getRemoteAddress() + " is established.");
            handler.connected(channel);
        } catch (RemotingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url);
            System.out.println(DataTimeUtil.now() + "The connection of " + channel.getLocalAddress() + " -> " + channel.getRemoteAddress() + " is disconnected.");
            handler.disconnected(channel);
        } catch (RemotingException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            handler.received(NettyChannel.getOrAddChannel(ctx.channel(), url), msg);
        } catch (RemotingException e) {
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        super.write(ctx, msg, promise);
        NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url);
        promise.addListener(future -> {
            try {
                if (future.isSuccess()) {
                    handler.sent(channel, msg);
                } else if (msg instanceof Request) {
                    Request request = (Request) msg;
                    Throwable cause = future.cause();
                    Response response = new Response(request.getId(), request.getVersion());
                    response.setErrorMessage(cause.toString());
                    response.setStatus(Response.BAD_REQUEST);
                    handler.received(channel, response);
                }
            } catch (RemotingException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        try {
            if (evt instanceof IdleStateEvent) {
                System.out.println(DataTimeUtil.now() + "IdleStateEvent triggered, send heartbeat to channel:" + ctx.channel());
                NettyChannel channel = NettyChannel.getOrAddChannel(ctx.channel(), url);
                if (url.getParameter("dont.send", false)) return; // only for test
                channel.send(Request.makeHeartbeat());
            } else {
                super.userEventTriggered(ctx, evt);
            }
        } catch (RemotingException e) {
            e.printStackTrace();
        } finally {
            NettyChannel.removeChannelIfDisconnected(ctx.channel());
        }
    }

}
