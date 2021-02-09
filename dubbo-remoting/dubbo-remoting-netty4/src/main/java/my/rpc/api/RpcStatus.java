package my.rpc.api;


import my.server.URL;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author geyu
 * @date 2021/2/9 20:49
 */
public class RpcStatus {

    private static Map<String, RpcStatus> SERVICE_STATISTICS = new ConcurrentHashMap<>();

    private static Map<String, Map<String, RpcStatus>> METHOD_STATISTICS = new ConcurrentHashMap<>();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicLong total = new AtomicLong();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicLong totalElapsed = new AtomicLong();
    private final AtomicLong failedElapsed = new AtomicLong();
    private final AtomicLong maxElapsed = new AtomicLong();
    private final AtomicLong failedMaxElapsed = new AtomicLong();
    private final AtomicLong succeededMaxElapsed = new AtomicLong();


    public static boolean beginCount(URL url, String methodName, int max) {
        max = (max <= 0) ? Integer.MAX_VALUE : max;
        RpcStatus appStatus = getStatus(url);
        RpcStatus methodStatus = getStatus(url, methodName);
        if (methodStatus.active.get() == max) {
            return false;
        }
        while (true) {// cas+轮询，每次进来必须查询，获取内存最新值 sao
            int count = methodStatus.active.get();
            if (count == max) {
                return false;
            }
            if (methodStatus.active.compareAndSet(count, count + 1)) {
                break;
            }
        }
        appStatus.active.incrementAndGet();
        return true;
    }

    public static void endCount(URL url, String methodName, long elapsed, boolean succeeded) {
        endCount(getStatus(url), elapsed, succeeded);
        endCount(getStatus(url, methodName), elapsed, succeeded);
    }

    public static void endCount(RpcStatus rpcStatus, long elapsed, boolean succeeded) {
        rpcStatus.active.decrementAndGet();
        rpcStatus.total.incrementAndGet();
        rpcStatus.totalElapsed.addAndGet(elapsed);
        if (rpcStatus.maxElapsed.get() < elapsed) {
            rpcStatus.maxElapsed.set(elapsed);
        }
        if (succeeded) {
            rpcStatus.failed.incrementAndGet();
            rpcStatus.failedElapsed.addAndGet(elapsed);
            if (rpcStatus.failedMaxElapsed.get() < elapsed) {
                rpcStatus.failedMaxElapsed.set(elapsed);
            }
        } else {
            if (rpcStatus.succeededMaxElapsed.get() < elapsed) {
                rpcStatus.succeededMaxElapsed.set(elapsed);
            }
        }

    }

    public static RpcStatus getStatus(URL url, String methodName) {
        String uri = url.toIdentityString();
        Map<String, RpcStatus> method2RpcStatus = METHOD_STATISTICS.computeIfAbsent(uri, k -> new ConcurrentHashMap<>());
        return method2RpcStatus.computeIfAbsent(methodName, k -> new RpcStatus());
    }

    public static RpcStatus getStatus(URL url) {
        String uri = url.toIdentityString();
        return SERVICE_STATISTICS.computeIfAbsent(uri, k -> new RpcStatus());
    }

    public AtomicInteger getActive() {
        return active;
    }

    public AtomicLong getTotal() {
        return total;
    }

    public AtomicInteger getFailed() {
        return failed;
    }

    public AtomicLong getTotalElapsed() {
        return totalElapsed;
    }

    public AtomicLong getFailedElapsed() {
        return failedElapsed;
    }

    public AtomicLong getMaxElapsed() {
        return maxElapsed;
    }

    public AtomicLong getFailedMaxElapsed() {
        return failedMaxElapsed;
    }

    public AtomicLong getSucceededMaxElapsed() {
        return succeededMaxElapsed;
    }
}