package netty.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.apache.dubbo.remoting.transport.netty4.NettyClient;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static netty.server.CommonConstants.*;

/**
 * @author geyu
 * @date 2021/1/31 14:25
 */
public class NettyChannel extends AbstractChannel {

    private final Channel channel;
    private static final Map<Channel, NettyChannel> CHANNEL_MAP = new ConcurrentHashMap<>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private AtomicBoolean active = new AtomicBoolean(false);

    private NettyChannel(Channel channel, URL url, ChannelHandler handler) {
        super(url, handler);// 进去
        if (channel == null) {
            throw new IllegalArgumentException("netty channel == null;");
        }
        this.channel = channel;
    }

    // ===== ===== ===== CHANNEL_MAP ===== ===== ===== ===== =====

    public static NettyChannel getOrAddChannel(Channel channel, URL url, ChannelHandler handler) {
        if (channel == null) {
            return null;
        }
        NettyChannel ret = CHANNEL_MAP.get(channel);
        if (ret == null) {
            NettyChannel nettyChannel = new NettyChannel(channel, url, handler);
            ret = nettyChannel;
            if (channel.isActive()) {
                nettyChannel.markActive(true);
                ret = CHANNEL_MAP.putIfAbsent(channel, nettyChannel);
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
    public void send(Object message, boolean sent) throws RemotingException {
        super.send(message, sent);
        try {
            boolean success = false;
            int timeout = 0;
            ChannelFuture future = channel.writeAndFlush(message);
            if (sent) {
                timeout = getUrl().getPositiveParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT);
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
    public void close() {
        super.close();
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
        return active.get() && !isClosed();
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
//        if(o instanceof NettyClient){
//            NettyClient channel = (NettyClient) o;
//            return this.channel.equals(channel.);
//        }
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
        return "NettyChannel:[" + channel + "]";
    }
}
