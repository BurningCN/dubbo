package netty.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author gy821075
 * @date 2021/1/28 18:13
 */
public interface Client {
    void connect() throws RemotingException;

    InnerChannel getChannel();
}
