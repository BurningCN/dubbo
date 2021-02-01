package netty.server;

import java.util.Collection;

/**
 * @author geyu
 * @date 2021/2/1 17:31
 */
public abstract class AbstractTimerTask implements Runnable {
    private final ChannelProvider channelProvider;
    private final long interval;
    private volatile boolean stopped = false;
    private Thread thread;

    AbstractTimerTask(ChannelProvider channelProvider, long interval) {
        this.channelProvider = channelProvider;
        this.interval = interval;
        thread = new Thread(this, "TimerTask-Thread");
        thread.start();
    }

    @Override
    public void run() {
        try {
            while (!isStopped()) {
                Collection<InnerChannel> channels = channelProvider.getChannels();
                for (InnerChannel channel : channels) {
                    doTask(channel);
                }
                Thread.sleep(interval);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (RemotingException e) {
            // ignore
        }
    }

    protected void stop() {
        this.stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }

    protected abstract void doTask(InnerChannel channel) throws RemotingException;

    @FunctionalInterface
    interface ChannelProvider {
        Collection<InnerChannel> getChannels();
    }
}

