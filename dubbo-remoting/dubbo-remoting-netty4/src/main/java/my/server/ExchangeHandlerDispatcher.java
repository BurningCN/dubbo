package my.server;
import java.util.concurrent.CompletableFuture;

/**
 * @author geyu
 * @date 2021/2/2 13:18
 */
public class ExchangeHandlerDispatcher implements ExchangeHandler {

    private final ChannelHandlerDispatcher channelHandlerDispatcher;
    private final ReplierDispatcher replierDispatcher;

    public ExchangeHandlerDispatcher() {
        this(null,null);
    }

    public ExchangeHandlerDispatcher(Replier<?> replier) {
        this(replier,null);
    }

    public ExchangeHandlerDispatcher(ChannelHandler... handlers) {
        this(null,handlers);
    }
    
    public ExchangeHandlerDispatcher(Replier<?> replier, ChannelHandler... handlers) {
        this.replierDispatcher = new ReplierDispatcher(replier);
        this.channelHandlerDispatcher = new ChannelHandlerDispatcher(handlers);
    }

    @Override
    public CompletableFuture<Object> reply(InnerChannel channel, Object request) {
        return CompletableFuture.completedFuture(replierDispatcher.reply(channel, request));
    }

    @Override
    public void connected(InnerChannel channel) throws RemotingException {
        channelHandlerDispatcher.connected(channel);
    }

    @Override
    public void disconnected(InnerChannel channel) throws RemotingException {
        channelHandlerDispatcher.disconnected(channel);
    }

    @Override
    public void sent(InnerChannel channel, Object message) throws RemotingException {
        channelHandlerDispatcher.sent(channel, message);
    }

    @Override
    public void received(InnerChannel channel, Object message) throws RemotingException {
        channelHandlerDispatcher.received(channel, message);
    }

    @Override
    public void caught(InnerChannel channel, Throwable exception) throws RemotingException {
        channelHandlerDispatcher.caught(channel, exception);
    }
}
