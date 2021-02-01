package netty.server;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * @author gy821075
 * @date 2021/1/28 18:13
 */
public interface Client extends IdleSensible {
    void connect() throws RemotingException;

    void disconnect() throws RemotingException;

    void reconnect() throws RemotingException;

    void close() throws RemotingException; // 区别于Channel的close，这里是关闭客户端（当然内部会调用channel.close）

    InnerChannel getChannel();


}
