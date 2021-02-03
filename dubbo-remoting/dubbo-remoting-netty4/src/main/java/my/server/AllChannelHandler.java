package my.server;

import java.util.concurrent.RejectedExecutionException;

/**
 * @author geyu
 * @date 2021/1/30 12:16
 */
public class AllChannelHandler extends AbstractChannelHandlerDelegate {

    public AllChannelHandler(ChannelHandler handler) {
        super(handler);
    }

    @Override
    public void connected(InnerChannel channel) throws RemotingException {
        try {
            getSharedExecutorServer().execute(new ChannelEventTask(handler, channel, ChannelEventTask.ChannelState.CONNECTED));
        } catch (Throwable t) {
            throw new ExecutionException(); // todo myRPC
        }

    }

    @Override
    public void disconnected(InnerChannel channel) throws RemotingException {
        try {
            getSharedExecutorServer().execute(new ChannelEventTask(handler, channel, ChannelEventTask.ChannelState.DISCONNECTED));
        } catch (Throwable t) {
            throw new ExecutionException(); // todo myRPC
        }
    }

    @Override
    public void received(InnerChannel channel, Object message) throws RemotingException {
        try {
            getSharedExecutorServer().execute(new ChannelEventTask(handler, channel, ChannelEventTask.ChannelState.RECEIVED, message));
        } catch (Throwable t) {
            if (message instanceof Request && t instanceof RejectedExecutionException) {
                sendFeedBack(channel, (Request) message, t);
                return;
            }
            throw new ExecutionException(); // todo myRPC
        }
    }

    @Override
    public void caught(InnerChannel channel, Throwable exception) throws RemotingException {
        try {
            getSharedExecutorServer().execute(new ChannelEventTask(handler, channel, ChannelEventTask.ChannelState.CAUGHT,exception));
        } catch (Throwable t) {
            throw new ExecutionException(); // todo myRPC
        }
    }
}
