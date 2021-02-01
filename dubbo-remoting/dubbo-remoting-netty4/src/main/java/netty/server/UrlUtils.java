package netty.server;

/**
 * @author geyu
 * @date 2021/2/1 17:22
 */
public class UrlUtils {
    public static int getIdleTimeout(URL url) {
        int heartbeat = getHeartbeat(url);
        int idleTimeout = url.getPositiveParameter(Constants.HEARTBEAT_TIMEOUT_KEY, heartbeat * 3);
        if (idleTimeout < heartbeat * 2) {
            throw new IllegalArgumentException("idleTimeout< heartbeat*2");
        }
        return idleTimeout;
    }

    public static int getHeartbeat(URL url) {
        return url.getPositiveParameter(Constants.HEARTBEAT_KEY, Constants.DEFAULT_HEARTBEAT);
    }

}
