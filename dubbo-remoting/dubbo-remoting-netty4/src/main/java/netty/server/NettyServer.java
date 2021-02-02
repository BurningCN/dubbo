package netty.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * @author geyu
 * @date 2021/1/28 13:52
 */
public class NettyServer extends AbstractServer {

    private InnerChannel channel;
    private ServerBootstrap serverBootstrap;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private NettyServerHandler serverHandler;
    private CloseTimerTask closeTimerTask;

    private int workerSize = Runtime.getRuntime().availableProcessors();
    private int bossSize = 1;

    public NettyServer(URL url, ChannelHandler handler) throws RemotingException {
        super(url, ChannelHandlers.wrap(handler));
    }

    protected void doOpen() {
        serverHandler = new NettyServerHandler(getUrl(), getChannelHandler());

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


                        ChannelPipeline pipeline = socketChannel.pipeline();
                        pipeline.addLast("encoder", nettyCodecAdapter.getInternalEncoder());
                        pipeline.addLast("decoder", nettyCodecAdapter.getInternalDecoder());
                        pipeline.addLast("server-idle-handler", new IdleStateHandler(0, 0, getIdleTimeout(), TimeUnit.MILLISECONDS));
                        pipeline.addLast("handler", serverHandler);
                    }
                });

        ChannelFuture future = serverBootstrap.bind(getBindAddress());
        future.syncUninterruptibly();
        System.out.println("sever bind successfully");
        channel = NettyChannel.getOrAddChannel(future.channel(), getUrl());
    }

    @Override
    protected void doStartTimer() {
        startCloseTimeTask();
    }

    private void startCloseTimeTask() {
        if (!canHandleIdle()) {
            AbstractTimerTask.ChannelProvider channelProvider = () -> serverHandler.getChannels().values();
            int idleTimeout = UrlUtils.getIdleTimeout(getUrl());
            long idleTimeoutTick = UrlUtils.calculateLeastDuration(idleTimeout);
            closeTimerTask = new CloseTimerTask(channelProvider, idleTimeoutTick, idleTimeout);
        }
    }

    @Override
    public boolean canHandleIdle() {
        if (getUrl().getParameter("closeTimeoutTask", false)) { // only for test
            return false;
        }
        return true;
    }


    public void doClose() { // 这里最好是doClose 和client对应
        if (closeTimerTask != null) {
            closeTimerTask.stop();
        }
        if (channel != null) {
            channel.close();
        }
        Collection<InnerChannel> channels = serverHandler.getChannels().values();
        if (channels != null && channels.size() > 0) {
            for (InnerChannel channel : channels) {
                channel.close();
            }
            channels.clear();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

}
