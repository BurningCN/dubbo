package netty.server;

import java.util.concurrent.CompletableFuture;

import static netty.server.Constants.CHANNEL_ATTRIBUTE_READONLY_KEY;

/**
 * @author geyu
 * @date 2021/1/30 16:43
 */
// 注意 有个 AbstractChannelHandlerDelegate 抽象类，该类没有直接继承，而是 注意直接实现 ChannelHandlerDelegate 接口了，因为全部方法都重
// 写了，没必要extends了。不过我感觉最好继承好点，如下，不过缺点就是因为用的是ExchangeHandler，所以必须要先保存到本地属性
public class HeaderExchangeHandler extends AbstractChannelHandlerDelegate {

    private final ExchangeHandler handler;

    public HeaderExchangeHandler(ExchangeHandler handler) {
        super(handler);
        this.handler = handler;
    }

    @Override
    public void connected(Channel channel) throws RemotingException {
        ExchangeChannel exChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        handler.connected(exChannel);
    }

    @Override
    public void disconnected(Channel channel) throws RemotingException {
        ExchangeChannel exChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        try {
            handler.disconnected(exChannel);
        } finally {
            DefaultFuture.closeChannel(channel);
            HeaderExchangeChannel.removeChannel(channel);
        }


    }

    @Override
    public void caught(Channel channel, Throwable exception) throws RemotingException {
        // todo myRPC
    }

    // channelWrite 比如客户端发数据
    @Override
    public void sent(Channel channel, Object message) throws RemotingException {
        ExchangeChannel exChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        handler.sent(exChannel, message);
        if (message instanceof Request) {
            Request req = (Request) message;
            DefaultFuture.sent(req);
        }
        // todo myRPC exception的处理
    }

    @Override
    public void received(Channel channel, Object msg) throws RemotingException {

        if (msg instanceof Request) {
            handleRequest(channel, (Request) msg);
        } else if (msg instanceof Response) {
            handleResponse((Response) msg);
        } else if (msg instanceof String) {
            // todo myRPC
        } else {
            handler.received(channel, msg);
        }
    }


    private void handleResponse(Response msg) {
        Response response = msg;
        DefaultFuture.handlerResponse(response);
    }

    private void handleRequest(Channel channel, Request msg) throws RemotingException {
        ExchangeChannel exChannel = HeaderExchangeChannel.getOrAddChannel(channel);
        Request request = msg;
        if (request.isEvent() && request.getData() != null && request.getData().equals(Constants.READONLY_EVENT)) {
            channel.setAttribute(CHANNEL_ATTRIBUTE_READONLY_KEY, Boolean.TRUE);
        } else if (request.isTwoWay()) {
            Response response = new Response(request.getId(), request.getVersion());
//            if (request.isBroken()) {// todo myRPC
//            }
            try {
                Object data = request.getData();
                CompletableFuture<Object> reply = handler.reply(exChannel, data);
                reply.whenComplete((result, t) -> {
                    try {
                        if (t == null) {
                            response.setStatus(Response.OK);
                            response.setResult(result);
                        } else {
                            response.setStatus(Response.SERVICE_ERROR);
                            response.setErrorMessage("exception:" + t.getMessage()); // todo myRPC 需要提供更完整的异常消息
                        }
                        exChannel.send(response);
                    } catch (RemotingException e) {
                        e.printStackTrace();
                    }
                });
            } catch (Throwable e) {
                response.setStatus(Response.SERVICE_ERROR);
                response.setErrorMessage(e.getMessage());
                exChannel.send(response);
            }
        } else {
            handler.received(channel, msg.getData());
        }
    }


    @Override
    public ChannelHandler getHandler() {
        if (handler instanceof ChannelHandlerDelegate) {
            return ((ChannelHandlerDelegate) handler).getHandler();// 链式继续向下找
        }
        return handler;
    }
}
