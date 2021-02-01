package netty.server;

/**
 * @author gy821075
 * @date 2021/1/31 16:49
 */
public interface ChannelHandlerDelegate extends ChannelHandler {
    ChannelHandler getHandler();
}
