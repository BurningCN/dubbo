package my.server;


import java.util.concurrent.CompletableFuture;

/**
 * @author gy821075
 * @date 2021/1/30 16:37
 */
public interface ExchangeHandler extends ChannelHandler {
    CompletableFuture<Object> reply(InnerChannel channel, Object request);
}
