package my.common.utils;

import java.util.concurrent.*;

/**
 * @author geyu
 * @date 2021/2/9 10:39
 */
public class ExecutorUtil {
    private static final ThreadPoolExecutor SHUTDOWN_EXECUTOR = new ThreadPoolExecutor(0, 1,
            0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(100), new NamedThreadFactory("Close-ExecutorService-Timer", true));

    public static boolean isTerminated(Executor executor) {
        if (executor instanceof ExecutorService) {
            return ((ExecutorService) executor).isTerminated();
        }
        return false;
    }

    public static void gracefulShutdown(Executor executor, int timeout) {
        if (!(executor instanceof ExecutorService) || isTerminated(executor)) {
            return;
        }
        ExecutorService executorService = (ExecutorService) executor;
        ((ExecutorService) executor).shutdown();

        try {
            if (!executorService.awaitTermination(timeout, TimeUnit.MILLISECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (!isTerminated(executor)) {
            newThreadToCloseExecutor(executorService);
        }

    }

    private static void newThreadToCloseExecutor(ExecutorService executorService) {
        if (executorService.isTerminated()) {
            return;
        }
        SHUTDOWN_EXECUTOR.execute(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    executorService.shutdown();
                    if (executorService.awaitTermination(10, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (Throwable e) {

            }
        });
    }
}
