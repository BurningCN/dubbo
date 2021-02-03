package my.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static my.server.CommonConstants.DEFAULT_TIMEOUT;
import static my.server.CommonConstants.TIMEOUT_KEY;

/**
 * @author geyu
 * @date 2021/1/31 14:25
 */
public class NettyChannel implements InnerChannel {

    private final Channel channel;
    private static final Map<Channel, NettyChannel> CHANNEL_MAP = new ConcurrentHashMap<>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private AtomicBoolean active = new AtomicBoolean(false);
    private URL url;

    private NettyChannel(Channel channel, URL url) {
        if (channel == null) {
            throw new IllegalArgumentException("netty channel == null;");
        }
        this.channel = channel;
        this.url = url;
    }

    public URL getUrl() {
        return url;
    }

    public Channel getChannel() {
        return channel;
    }

    // ===== ===== ===== CHANNEL_MAP ===== ===== ===== ===== =====

    public static NettyChannel getOrAddChannel(Channel channel, URL url) {
        if (channel == null) {
            return null;
        }
        NettyChannel ret = CHANNEL_MAP.get(channel);
        if (ret == null) {
            NettyChannel nettyChannel = new NettyChannel(channel, url);
            if (channel.isActive()) {
                nettyChannel.markActive(true);
                ret = CHANNEL_MAP.putIfAbsent(channel, nettyChannel);
            }
            if (ret == null) {
                ret = nettyChannel;
            }
        }
        return ret;
    }


    static void removeChannel(Channel ch) {
        if (ch != null) {
            NettyChannel nettyChannel = CHANNEL_MAP.remove(ch);
            if (nettyChannel != null) {
                nettyChannel.markActive(false);
            }
        }
    }

    // ===== ===== ===== send、close ===== ===== ===== ===== =====
    @Override
    public void send(Object message) throws RemotingException {
        send(message, false);
    }

    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        if (!isConnected()) {
            throw new RemotingException(this, "Failed to send message "
                    + (message == null ? "" : message.getClass().getName()) + ":" + org.apache.dubbo.remoting.utils.PayloadDropper.getRequestWithoutData(message)
                    + ", cause: Channel closed. channel: " + getLocalAddress() + " -> " + getRemoteAddress());
        }
        try {
            boolean success = true;
            int timeout = 0;
            ChannelFuture future = channel.writeAndFlush(message);
            if (sent) {
                timeout = url.getPositiveParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT);
                success = future.await(timeout);
                if (future.cause() != null) {
                    throw future.cause();
                }
            }
            if (!success) {
                throw new RemotingException(this, "Failed to send message " +
                        PayloadDropper.getRequestWithoutData(message) + " to " + getRemoteAddress()
                        + "in timeout(" + timeout + "ms) limit");
            }
        } catch (Throwable e) {
            removeChannelIfDisconnected(channel);
            throw new RemotingException(this, "Failed to send message " +
                    PayloadDropper.getRequestWithoutData(message) + " to " + getRemoteAddress() + ", " +
                    "cause: " + e.getMessage(), e);
        }

    }

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
        return request(request, -1, null);
    }

    @Override
    public CompletableFuture<Object> request(Object data, int timeout, ExecutorService executor) throws RemotingException {
        if (!isConnected()) {
            throw new RemotingException(this.getLocalAddress(), null, "Failed to send message " + data + ", cause: The channel " + this + " is closed!");
        }
        Request request = new Request();
        request.setVersion(Version.DEFAULT_VERSION);
        request.setTwoWay(true);
        request.setData(data);
        DefaultFuture future = DefaultFuture.newFuture(this, request, timeout, executor);
        try {
            send(request);
        } catch (RemotingException e) {
            future.cancel();
            throw e;
        }
        return future;
    }

    @Override
    public void close(int timeout) {

    }

    @Override
    public void close() {
        removeChannelIfDisconnected(channel);
        attributes.clear();
        channel.close(); // 关键
    }

    // ===== ===== ===== Disconnected、isActive、isConnected ===== ===== ===== ===== =====


    public void markActive(boolean isActive) {
        active.set(isActive);
    }

    @Override
    public boolean isConnected() {
        return active.get();
    }

    static void removeChannelIfDisconnected(Channel channel) {
        if (channel != null && !channel.isActive()) {
            NettyChannel remove = CHANNEL_MAP.remove(channel);
            if (remove != null) {
                remove.markActive(false);
            }
        }
    }


    // ===== ===== ===== Address ===== ===== ===== ===== =====
    @Override
    public InetSocketAddress getRemoteAddress() {
        return (InetSocketAddress) channel.remoteAddress();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return (InetSocketAddress) channel.localAddress();
    }


    // ===== ===== ===== Attribute ===== ===== ===== ===== =====
    @Override
    public void setAttribute(String key, Object value) {
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.putIfAbsent(key, value);
        }
    }

    @Override
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    @Override
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    @Override
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    // ===== ===== ===== Object ===== ===== ===== ===== =====

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;
        if (this.getClass() != o.getClass())
            return false;
        NettyChannel other = (NettyChannel) o;
        if (channel == null) {
            if (other.channel != null) {
                return false;
            }
        } else if (!channel.equals(other.channel))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = result * prime + (channel == null ? 0 : channel.hashCode());
        result = result * prime + (active == null ? 0 : active.hashCode());
        return result;
    }

    @Override
    public String toString() {
        return "NettyChannel:[" + channel + "]," + getLocalAddress() + " -> " + getRemoteAddress();
    }

}
