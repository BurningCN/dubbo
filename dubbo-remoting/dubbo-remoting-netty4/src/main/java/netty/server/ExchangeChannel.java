package netty.server;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author gy821075
 * @date 2021/1/31 16:57
 */
public interface ExchangeChannel extends Channel {

    @Deprecated
    CompletableFuture<Object> request(Object request) throws RemotingException;

    @Deprecated
    CompletableFuture<Object> request(Object request, int timeout) throws RemotingException;

    CompletableFuture<Object> request(Object request, ExecutorService executor) throws RemotingException;

    CompletableFuture<Object> request(Object request, int timeout, ExecutorService executor) throws RemotingException;

    ExchangeHandler getExchangeHandler();
}
