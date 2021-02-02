package netty.server;

import static netty.server.Constants.*;

/**
 * @author geyu
 * @date 2021/2/1 17:22
 */
public class UrlUtils {
    public static int getIdleTimeout(URL url) {
        int heartbeat = getHeartbeat(url);
        int idleTimeout = url.getPositiveParameter(HEARTBEAT_TIMEOUT_KEY, heartbeat * 3);
        if (idleTimeout < heartbeat * 2) {
            throw new IllegalArgumentException("idleTimeout< heartbeat*2");
        }
        return idleTimeout;
    }

    public static int getHeartbeat(URL url) {
        return url.getPositiveParameter(HEARTBEAT_KEY,DEFAULT_HEARTBEAT);
    }

    public static long calculateLeastDuration(int time) {
        if (time / HEARTBEAT_CHECK_TICK <= 0) {
            return LEAST_HEARTBEAT_DURATION;
        } else {
            return time / HEARTBEAT_CHECK_TICK;
        }
    }

}
