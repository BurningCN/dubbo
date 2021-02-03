package my.server;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author geyu
 * @date 2021/2/2 13:24
 */
public class ChannelHandlerDispatcher implements ChannelHandler {

    private final Collection<ChannelHandler> handlers = new CopyOnWriteArrayList<>();

    public ChannelHandlerDispatcher() {
        
    }

    public ChannelHandlerDispatcher(ChannelHandler[] handlers) {
        if (handlers != null && handlers.length > 0) {
            this.handlers.addAll(Arrays.asList(handlers));
        }
    }


    public ChannelHandlerDispatcher addHandler(ChannelHandler handler) {
        handlers.add(handler);
        return this;
    }

    public ChannelHandlerDispatcher removeHandler(ChannelHandler handler) {
        handlers.remove(handler);
        return this;
    }

    @Override
    public void connected(InnerChannel channel) throws RemotingException {
        for (ChannelHandler listener : handlers) {
            listener.connected(channel);
        }
    }

    @Override
    public void disconnected(InnerChannel channel) throws RemotingException {
        for (ChannelHandler listener : handlers) {
            listener.disconnected(channel);
        }
    }

    @Override
    public void sent(InnerChannel channel, Object message) throws RemotingException {
        for (ChannelHandler listener : handlers) {
            listener.sent(channel, message);
        }
    }

    @Override
    public void received(InnerChannel channel, Object message) throws RemotingException {
        for (ChannelHandler listener : handlers) {
            listener.received(channel, message);
        }
    }

    @Override
    public void caught(InnerChannel channel, Throwable exception) throws RemotingException {
        for (ChannelHandler listener : handlers) {
            listener.caught(channel, exception);
        }
    }
}
