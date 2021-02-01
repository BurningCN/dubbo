package netty.server;

/**
 * @author geyu
 * @date 2021/1/30 21:21
 */
public class ChannelEventTask implements Runnable {

    private final ChannelHandler handler;
    private final Channel channel;
    private final ChannelState state;
    private final Object msg;
    private final Throwable exception;

    public ChannelEventTask(ChannelHandler handler, Channel channel, ChannelState state) {
        this(handler, channel, state, null, null);
    }

    public ChannelEventTask(ChannelHandler handler, Channel channel, ChannelState state, Object msg) {
        this(handler, channel, state, msg, null);
    }

    public ChannelEventTask(ChannelHandler handler, Channel channel, ChannelState state, Throwable exception) {
        this(handler, channel, state, null, exception);
    }

    public ChannelEventTask(ChannelHandler handler, Channel channel, ChannelState state, Object msg, Throwable exception) {
        this.handler = handler;
        this.channel = channel;
        this.state = state;
        this.msg = msg;
        this.exception = exception;
    }

    @Override
    public void run() {
        try {
            switch (state) {
                case RECEIVED:
                    handler.received(channel, msg);
                    break;
                case CONNECTED:
                    handler.connected(channel);
                    break;
                case DISCONNECTED:
                    handler.disconnected(channel);
                    break;
                case SENT:
                    handler.sent(channel, msg);
                    break;
                case CAUGHT:
                    handler.caught(channel, exception);
                    break;
                default:
                    System.out.println("unknown state: " + state + ", message is " + msg);
            }
        } catch (RemotingException e) {
            // ignore
        }
    }

    public enum ChannelState {
        CONNECTED,
        DISCONNECTED,
        SENT,
        RECEIVED,
        CAUGHT
    }
}
