package my.server;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.*;

import static org.apache.dubbo.common.constants.CommonConstants.DEFAULT_TIMEOUT;
import static org.apache.dubbo.common.constants.CommonConstants.TIMEOUT_KEY;

/**
 * @author geyu
 * @date 2021/1/30 17:10
 */
public class DefaultFuture extends CompletableFuture<Object> {
    private final Request request;
    private final long id;
    private final int timeout;
    private final long start = System.currentTimeMillis();
    private final InnerChannel channel;
    private volatile long sent;
    private static final Map<Long, DefaultFuture> futureTable = new ConcurrentHashMap<>();
    private static final Map<Long, InnerChannel> channelTable = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService schedule = Executors.newSingleThreadScheduledExecutor();// todo myRPC 线程名称
    private final ExecutorService executor;

    public static DefaultFuture newFuture(InnerChannel channel, Request req, int timeout, ExecutorService executor) {
        return new DefaultFuture(channel, req, timeout, executor);
    }

    private DefaultFuture(InnerChannel channel, Request req, int timeout, ExecutorService executor) {
        this.channel = channel;
        this.request = req;
        this.id = req.getId();
        this.timeout = timeout > 0 ? timeout : channel.getUrl().getPositiveParameter(TIMEOUT_KEY, DEFAULT_TIMEOUT);
        this.executor = executor;
        this.futureTable.put(id, this);
        this.channelTable.put(id, channel);
        schedule.schedule(() -> new TimeoutCheck(id), timeout, TimeUnit.MILLISECONDS);
    }

    public static void sent(Request request) {
        futureTable.get(request.getId()).doSent();
    }

    private void doSent() {
        this.sent = System.currentTimeMillis();
    }

    public static boolean hasFuture(InnerChannel channel) {
        return channelTable.containsValue(channel);
    }

    public static DefaultFuture getFuture(long id) {
        return futureTable.get(id);
    }
    public static void closeChannel(InnerChannel channel) {
        for (Map.Entry<Long, InnerChannel> entry : channelTable.entrySet()) {
            if (entry.getValue() == channel) {
                DefaultFuture future = futureTable.get(entry.getKey());
                if (future != null && !future.isDone()) {
                    // todo myRPC 不明白这里为啥关闭线程池
                    ExecutorService executor = future.getExecutor();
                    if (executor != null && !executor.isTerminated()) {
                        executor.shutdownNow();
                    }

                    Response disconnectResponse = new Response(future.getId());
                    disconnectResponse.setStatus(Response.CHANNEL_INACTIVE);
                    disconnectResponse.setErrorMessage("InnerChannel " +
                            channel +
                            " is inactive. Directly return the unFinished request : " +
                            future.getRequest());

                    future.received(disconnectResponse);
                }
            }
        }

    }


    public void received(Response response) {
        if (response == null) {
            throw new IllegalStateException("response == null");
        }
        futureTable.remove(id);
        channelTable.remove(id);
        if (response.getStatus() == Response.OK) {
            this.complete(response.getResult());
        } else if (response.getStatus() == Response.CLIENT_TIMEOUT || response.getStatus() == Response.SERVER_TIMEOUT) {
            this.completeExceptionally(new TimeoutException(response.getStatus() == Response.SERVER_TIMEOUT,channel,response.getErrorMessage()));
        } else {
            this.completeExceptionally(new RemotingException(channel, response.getErrorMessage()));
        }
    }

    private static class TimeoutCheck implements Runnable {
        private Long requestId;

        public TimeoutCheck(Long requestId) {
            this.requestId = requestId;
        }

        @Override
        public void run() {
            DefaultFuture future = futureTable.get(requestId);
            if (future == null && future.isDone()) {
                return;
            }
            if (future.getExecutor() != null) {
                future.getExecutor().execute(() -> notifyTimeout(future));
            } else {
                notifyTimeout(future);
            }
        }

        private void notifyTimeout(DefaultFuture future) {
            Response response = new Response(future.getId());
            response.setStatus(future.getSent() > 0 ? Response.SERVER_TIMEOUT : Response.CLIENT_TIMEOUT);
            response.setErrorMessage(future.getTimeoutMessage(true));
            future.received(response);
        }
    }

    private String getTimeoutMessage(boolean scan) {
        long nowTimestamp = System.currentTimeMillis();
        return (sent > 0 ? "Waiting server-side response timeout" : "Sending request timeout in client-side")
                + (scan ? " by scan timer" : "") + ". start time: "
                + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(start))) + ", end time: "
                + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(nowTimestamp))) + ","
                + (sent > 0 ? " client elapsed: " + (sent - start)
                + " ms, server elapsed: " + (nowTimestamp - sent)
                : " elapsed: " + (nowTimestamp - start)) + " ms, timeout: "
                + timeout + " ms";
    }

    public static void handlerResponse(Response response) {
        DefaultFuture future = futureTable.get(response.getId());
        if (future != null) {
            future.received(response);
        }
    }


    public void cancel() {
        Response response = new Response(id);
        response.setStatus(Response.CLIENT_ERROR);
        response.setErrorMessage("request future has been canceled.");
        received(response);
    }

    public long getSent() {
        return sent;
    }

    public Request getRequest() {
        return request;
    }

    public long getId() {
        return id;
    }

    public int getTimeout() {
        return timeout;
    }

    public ExecutorService getExecutor() {
        return executor;
    }
}
