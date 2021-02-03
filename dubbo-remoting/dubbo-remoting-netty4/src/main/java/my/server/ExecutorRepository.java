package my.server;

import java.util.concurrent.ExecutorService;

/**
 * @author geyu
 * @date 2021/2/1 10:53
 */
public interface ExecutorRepository {
    ExecutorService getExecutor(URL url);

    ExecutorService createExecutor(URL url);
}
