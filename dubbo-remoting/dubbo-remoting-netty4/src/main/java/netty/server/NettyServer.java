package netty.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * @author geyu
 * @date 2021/1/28 13:52
 */
public class NettyServer extends AbstractServer {

    private io.netty.channel.Channel serverNettyChannel;
    private ServerBootstrap serverBootstrap;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private int workerSize = Runtime.getRuntime().availableProcessors();
    private int bossSize = 1;

    public NettyServer(URL url, ChannelHandler handler) throws RemotingException{
        super(url, ChannelHandlers.wrap(handler));
    }

    protected void doOpen() {
        serverBootstrap = new ServerBootstrap();
        bossGroup = NettyEventLoopFactory.eventLoopGroup(bossSize, "NettyServerBoss");
        workerGroup = NettyEventLoopFactory.eventLoopGroup(workerSize, "NettyServerWorker");
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NettyEventLoopFactory.serverSocketChannelClass())
                .option(ChannelOption.SO_REUSEADDR, Boolean.TRUE)
                .childOption(ChannelOption.TCP_NODELAY, Boolean.TRUE)
                .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        NettyCodecAdapter nettyCodecAdapter = new NettyCodecAdapter(getCodec());
                        NettyServerHandler serverHandler = new NettyServerHandler(getUrl(), getChannelHandler());

                        ChannelPipeline pipeline = socketChannel.pipeline();
                        pipeline.addLast("encoder", nettyCodecAdapter.getInternalEncoder());
                        pipeline.addLast("decoder", nettyCodecAdapter.getInternalDecoder());
                        pipeline.addLast("server-idle-handler", new IdleStateHandler(0,0, getIdleTimeout(), TimeUnit.MILLISECONDS));
                        pipeline.addLast("handler", serverHandler);
                    }
                });

        ChannelFuture future = serverBootstrap.bind(getBindAddress());
        future.syncUninterruptibly();
        serverNettyChannel = future.channel();
    }
}
