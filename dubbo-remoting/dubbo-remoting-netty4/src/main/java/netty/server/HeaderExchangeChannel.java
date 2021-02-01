package netty.server;


import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;


/**
 * @author geyu
 * @date 2021/1/31 16:57
 */
public class HeaderExchangeChannel implements ExchangeChannel {

    private volatile boolean closed = false;

    private final Channel channel;

    private static final String CHANNEL_KEY = HeaderExchangeChannel.class.getName() + ".CHANNEL";

    HeaderExchangeChannel(Channel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("channel == null");
        }
        this.channel = channel;
    }

    public static HeaderExchangeChannel getOrAddChannel(Channel channel) {
        if (channel == null) {
            throw new IllegalArgumentException("channel == null");
        }
        HeaderExchangeChannel ret = (HeaderExchangeChannel) channel.getAttribute(CHANNEL_KEY);
        if (ret == null) {
            ret = new HeaderExchangeChannel(channel);
            if (channel.isConnected()) {
                channel.setAttribute(CHANNEL_KEY, ret);
            }
        }
        return ret;
    }

    static void removeChannelIfDisconnected(Channel ch) {
        if (ch != null && !ch.isConnected()) {
            ch.removeAttribute(CHANNEL_KEY);
        }
    }

    static void removeChannel(Channel ch) {
        if (ch != null) {
            ch.removeAttribute(CHANNEL_KEY);
        }
    }

    // ==== ==== ============ send  ====================

    @Override
    public void send(Object message) throws RemotingException {
        send(message, false);
    }

    // eg 服务端接收到客户端请求并reply获取消息之后然后如下send
    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        if (closed) {
            throw new RemotingException(this.getLocalAddress(), null, "Failed to send message " + message + ", cause: The channel " + this + " is closed!");
        }
        if (message instanceof Request
                || message instanceof Response
                || message instanceof String) {
            channel.send(message, sent); // 最终进入 nettyChanel.send
        } else {
            Request request = new Request();
            request.setVersion(Version.DEFAULT_VERSION);
            request.setTwoWay(false);
            request.setData(message);
            channel.send(request, sent);
        }
    }


    // ==== ==== ============ request  ====================
    @Override
    public CompletableFuture<Object> request(Object request) throws RemotingException {
        return request(request, null);
    }

    @Override
    public CompletableFuture<Object> request(Object request, int timeout) throws RemotingException {
        return request(request, timeout, null);
    }

    @Override
    public CompletableFuture<Object> request(Object request, ExecutorService executor) throws RemotingException {
        return request(request, channel.getUrl().getPositiveParameter(Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT), executor);
    }

    @Override
    public CompletableFuture<Object> request(Object data, int timeout, ExecutorService executor) throws RemotingException {

        if (closed) {
            throw new RemotingException(this.getLocalAddress(), null, "Failed to send message " + data + ", cause: The channel " + this + " is closed!");
        }
        Request request = new Request();
        request.setVersion(Version.DEFAULT_VERSION);
        request.setTwoWay(true);
        request.setData(data);
        DefaultFuture future = DefaultFuture.newFuture(channel, request, timeout, executor);
        try {
            channel.send(request);
        } catch (RemotingException e) {
            future.cancel();
            throw e;
        }
        return future;
    }

    // ==== ==== ============ close  ====================

    @Override
    public boolean isClosed() {
        return closed;
    }

    // 被ExchangeHeaderClient调用的
    @Override
    public void close() {
        DefaultFuture.closeChannel(channel);
        channel.close();
    }

    @Override
    public void close(int timeout) {
        if (closed) {
            return;
        }
        closed = true;
        if (timeout > 0) {
            long start = System.currentTimeMillis();
            while (DefaultFuture.hasFuture(channel) && System.currentTimeMillis() - start < timeout) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        close();
    }

    // ==== ==== ============ 直接委托  ====================

    @Override
    public InetSocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    @Override
    public void startClose() {
        channel.startClose();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return channel.getRemoteAddress();
    }

    @Override
    public URL getUrl() {
        return channel.getUrl();
    }

    @Override
    public boolean isConnected() {
        return channel.isConnected();
    }

    @Override
    public ChannelHandler getChannelHandler() {
        return channel.getChannelHandler();
    }

    @Override
    public ExchangeHandler getExchangeHandler() {
        return (ExchangeHandler) channel.getChannelHandler();
    }

    @Override
    public Object getAttribute(String key) {
        return channel.getAttribute(key);
    }

    @Override
    public void setAttribute(String key, Object value) {
        channel.setAttribute(key, value);
    }

    @Override
    public void removeAttribute(String key) {
        channel.removeAttribute(key);
    }

    @Override
    public boolean hasAttribute(String key) {
        return channel.hasAttribute(key);
    }

    // ==== ==== ============ Object  ====================


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (getClass() != o.getClass()) {
            return false;
        }
        HeaderExchangeChannel other = (HeaderExchangeChannel) o;
        if (channel == null) {
            if (other != null) {
                return false;
            }
        } else if (!channel.equals(other.channel)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = result * prime + (channel == null ? 0 : channel.hashCode());
        return result;
    }


}
