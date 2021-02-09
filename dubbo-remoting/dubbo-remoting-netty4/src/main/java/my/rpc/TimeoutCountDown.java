package my.rpc;

import java.sql.Time;
import java.util.concurrent.TimeUnit;

/**
 * @author geyu
 * @date 2021/2/5 13:23
 */
public class TimeoutCountDown implements Comparable<TimeoutCountDown> {

    private long timeoutInMillis;
    private long deadlineInNanos;
    private volatile boolean expired;

    private TimeoutCountDown(long timeout, TimeUnit timeUnit) {
        timeoutInMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
        deadlineInNanos = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeout, timeUnit);
    }

    public static TimeoutCountDown newCountDown(long timeout, TimeUnit unit) {
        return new TimeoutCountDown(timeout, unit);
    }


    public boolean isExpired() {
        if (expired)
            return true;
        if (System.nanoTime() > deadlineInNanos) {
            expired = true;
            return true;
        } else {
            return false;
        }
    }

    public long elapsedMillis() {
        if (isExpired()) {
            return timeoutInMillis + TimeUnit.MILLISECONDS.convert(System.nanoTime() - deadlineInNanos, TimeUnit.NANOSECONDS);
        } else {
            return TimeUnit.MILLISECONDS.convert(System.nanoTime() - (deadlineInNanos - TimeUnit.NANOSECONDS.convert(timeoutInMillis, TimeUnit.MILLISECONDS)), TimeUnit.NANOSECONDS);
        }
    }



    public long timeRemaining(TimeUnit unit) {
        long currentNanos = System.nanoTime();
        if (!expired && deadlineInNanos <= currentNanos) {
            expired = true;
        }
        return unit.convert(deadlineInNanos - currentNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(TimeoutCountDown o) {
        long delta = this.deadlineInNanos - o.deadlineInNanos;
        if (delta < 0) {
            return -1;
        } else if (delta > 0) {
            return 1;
        } else {
            return 0;
        }

    }
    @Override
    public String toString() {
        long timeoutMillis = TimeUnit.MILLISECONDS.convert(deadlineInNanos, TimeUnit.NANOSECONDS);
        long remainingMillis = timeRemaining(TimeUnit.MILLISECONDS);

        StringBuilder buf = new StringBuilder();
        buf.append("Total timeout value - ");
        buf.append(timeoutMillis);
        buf.append(", times remaining - ");
        buf.append(remainingMillis);
        return buf.toString();
    }

}

