package netty.server;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.ThreadFactory;

/**
 * @author geyu
 * @date 2021/1/28 13:59
 */
public class NettyEventLoopFactory {

    public static EventLoopGroup eventLoopGroup(int threads, String threadName) {
        DefaultThreadFactory threadFactory = new DefaultThreadFactory(threadName, true);
        return shouldEpoll() ? new EpollEventLoopGroup(threads, threadFactory) : new NioEventLoopGroup(threads, threadFactory);
    }

    public static Class<? extends ServerSocketChannel> serverSocketChannelClass() {
        return shouldEpoll() ? EpollServerSocketChannel.class : NioServerSocketChannel.class;
    }

    public static Class<? extends SocketChannel> socketChannelClass() {
        return shouldEpoll() ? EpollSocketChannel.class : NioSocketChannel.class;
    }

    private static boolean shouldEpoll() {
        // 待优化
        String property = System.getProperty("os.name");
        return property.toLowerCase().contains("linux") && Epoll.isAvailable();
    }
}
