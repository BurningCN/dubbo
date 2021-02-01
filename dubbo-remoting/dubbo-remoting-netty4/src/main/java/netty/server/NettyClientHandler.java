//package netty.server;
//
//import io.netty.channel.ChannelDuplexHandler;
//import io.netty.channel.ChannelFuture;
//import io.netty.channel.ChannelHandlerContext;
//import io.netty.handler.timeout.IdleStateEvent;
//
///**
// * @author geyu
// * @date 2021/1/29 19:50
// */
//public class NettyClientHandler extends ChannelDuplexHandler {
//
//
//    ChannelHandler handler;
//
//    AbstractClient client; //todo myRPC 使用Client接口最好
//
//    public NettyClientHandler(ChannelHandler handler, NettyClient client) {
//        this.handler = handler;
//        this.client = client;
//    }
//
//
//    @Override
//    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        super.channelRead(ctx, msg);
//        handler.received(ctx.channel(), msg);
//
//    }
//
//    @Override
//    public void channelActive(ChannelHandlerContext ctx) throws Exception {
//        super.channelActive(ctx);
//        // todo myRPC 输出调整
//        System.out.println(DataTimeUtil.now() + "client channelActive：" + ctx.channel().localAddress() + "->" + ctx.channel().remoteAddress());
//        handler.connected(ctx.channel());
//    }
//
//    @Override
//    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
//        super.userEventTriggered(ctx, evt);
//        if (evt instanceof IdleStateEvent) {
//            System.out.println(DataTimeUtil.now() + "IdleStateEvent triggered, send heartbeat to channel:" + ctx.channel());
//
//            boolean success = true;
//            try {
//                ChannelFuture future = ctx.writeAndFlush(Request.makeHeartbeat());
//                if (client.getSent()) {
//                    int sendTimeout = client.getSendTimeout();
//                    success = future.await(sendTimeout);
//                }
//                Throwable cause = future.cause();
//                if (cause != null) {
//                    throw cause;
//                }
//            } catch (Throwable e) {
//                // throw new RemotingException(); // todo myRPC 这里抛异常会有问题
//            }
//            if (!success) {
//                // throw new RemotingException();
//            }
//
//        }
//    }
//}
