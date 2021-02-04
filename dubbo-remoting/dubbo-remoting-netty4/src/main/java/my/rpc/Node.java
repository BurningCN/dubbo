package my.rpc;

import my.server.URL;

/**
 * @author gy821075
 * @date 2021/2/4 11:31
 */
public interface Node {
    URL getURL();

    boolean isAvailable();

    void destroy();
}
