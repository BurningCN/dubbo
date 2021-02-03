package my.server;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author geyu
 * @date 2021/2/3 15:55
 */
public class DefaultThreadFactory implements ThreadFactory {

    private static AtomicLong count = new AtomicLong(0);
    private String prefix;
    private boolean isDaemon;

    public DefaultThreadFactory(String prefix) {
        this(prefix, true);
    }

    public DefaultThreadFactory(String prefix, boolean isDaemon) {
        this.prefix = prefix;
        this.isDaemon = isDaemon;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setName(prefix + "-" + count.getAndIncrement());
        thread.setDaemon(isDaemon);
        return thread;
    }
}
