package netty.server;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author gy821075
 * @date 2021/1/31 14:50
 */
public interface InnerChannel {

    URL getUrl();

    InetSocketAddress getLocalAddress();

    InetSocketAddress getRemoteAddress();

    void send(Object message) throws RemotingException;

    void send(Object message, boolean sent) throws RemotingException;

    CompletableFuture<Object> request(Object request) throws RemotingException;

    CompletableFuture<Object> request(Object request, int timeout) throws RemotingException;

    CompletableFuture<Object> request(Object request, ExecutorService executor) throws RemotingException;

    CompletableFuture<Object> request(Object request, int timeout, ExecutorService executor) throws RemotingException;

    void close();

    void close(int timeout);

    boolean isConnected();

    boolean hasAttribute(String key);

    Object getAttribute(String key);

    void setAttribute(String key, Object value);

    void removeAttribute(String key);
}

