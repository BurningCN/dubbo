package my.rpc.api.filter.tps;

import my.server.URL;

/**
 * @author gy821075
 * @date 2021/2/19 11:45
 */
public interface TpsLimiter {
    boolean isAllowable(URL url);
}
