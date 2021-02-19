package my.rpc.api.filter.tps;

import java.util.concurrent.atomic.LongAdder;

/**
 * @author geyu
 * @date 2021/2/19 11:46
 */
public class StatItem {
    private String name;

    private long lastResetTime;

    private long interval;

    private LongAdder token;

    private int rate;

    public StatItem(String name, int rate, long interval) {
        this.name = name;
        this.rate = rate;
        this.interval = interval;
        this.lastResetTime = System.currentTimeMillis();
        this.token = buildLongAdder(rate);
    }

    private LongAdder buildLongAdder(int rate) {
        LongAdder adder = new LongAdder();
        adder.add(rate);
        return adder;
    }

    public int getRate() {
        return rate;
    }

    public long getInterval() {
        return interval;
    }

    public boolean isAllowable() {
        long now = System.currentTimeMillis();
        if (now - lastResetTime > interval) {
            token = buildLongAdder(rate);
            lastResetTime = now;
        }
        if (token.sum() < 0) {
            return false;
        }
        token.decrement();
        return true;
    }
}
