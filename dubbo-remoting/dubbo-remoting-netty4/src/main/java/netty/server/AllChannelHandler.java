package netty.server;

import java.util.concurrent.RejectedExecutionException;

import static netty.server.ChannelEventTask.ChannelState.*;

/**
 * @author geyu
 * @date 2021/1/30 12:16
 */
public class AllChannelHandler extends AbstractChannelHandlerDelegate {

    public AllChannelHandler(ChannelHandler handler) {
        super(handler);
    }

    @Override
    public void connected(Channel channel) throws RemotingException {
        try {
            getSharedExecutorServer().execute(new ChannelEventTask(handler, channel, CONNECTED));
        } catch (Throwable t) {
            throw new ExecutionException(); // todo myRPC
        }

    }

    @Override
    public void disconnected(Channel channel) throws RemotingException {
        try {
            getSharedExecutorServer().execute(new ChannelEventTask(handler, channel, DISCONNECTED));
        } catch (Throwable t) {
            throw new ExecutionException(); // todo myRPC
        }
    }

    @Override
    public void received(Channel channel, Object message) throws RemotingException {
        try {
            getSharedExecutorServer().execute(new ChannelEventTask(handler, channel, RECEIVED, message));
        } catch (Throwable t) {
            if (message instanceof Request && t instanceof RejectedExecutionException) {
                sendFeedBack(channel, (Request) message, t);
                return;
            }
            throw new ExecutionException(); // todo myRPC
        }
    }

    @Override
    public void caught(Channel channel, Throwable exception) throws RemotingException {
        try {
            getSharedExecutorServer().execute(new ChannelEventTask(handler, channel, CAUGHT,exception));
        } catch (Throwable t) {
            throw new ExecutionException(); // todo myRPC
        }
    }
}
