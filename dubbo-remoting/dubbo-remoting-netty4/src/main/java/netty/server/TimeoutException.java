package netty.server;


/**
 * @author geyu
 * @date 2021/1/30 19:18
 */
public class TimeoutException extends RemotingException {
    public static final int CLIENT_SIDE = 0;
    public static final int SERVER_SIDE = 1;
    private static final long serialVersionUID = 3122966731958222692L;
    private final int phase;

    public TimeoutException(boolean serverSide, InnerChannel channel, String msg) {
        super(channel, msg);
        phase = serverSide ? SERVER_SIDE : CLIENT_SIDE;
    }
}
