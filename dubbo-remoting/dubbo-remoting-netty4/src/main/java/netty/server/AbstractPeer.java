package netty.server;


import java.net.InetSocketAddress;

/**
 * @author geyu
 * @date 2021/1/31 14:29
 */
public class AbstractPeer implements Endpoint {

    private volatile URL url;
    private final ChannelHandler handler;
    private volatile boolean closing;
    private volatile boolean closed;

    public AbstractPeer(URL url, ChannelHandler handler) {

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
    public void send(Object message) throws RemotingException {
        send(message, getUrl().getParameter(Constants.SENT_KEY, false));
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        // todo myRPC
        return null;
    }

    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        // todo myRPC
    }

    @Override
    public URL getUrl() {
        return url;
    }

    protected void setUrl(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }
        this.url = url;
    }

    @Override
    public ChannelHandler getChannelHandler() {
        return handler;
    }

    // NettyChannel 的 close会调用这里
    @Override
    public void close() {
        closed = true;
    }

    @Override
    public void close(int timeout) {
        close();
    }

    @Override
    public void startClose() {
        if (isClosed()) {
            return;
        }
        closing = true;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    public boolean isClosing() {
        return closing && !closed;
    }
}
