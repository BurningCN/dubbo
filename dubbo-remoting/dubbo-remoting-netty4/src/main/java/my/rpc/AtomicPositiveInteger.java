package my.rpc;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author geyu
 * @date 2021/2/5 12:12
 */
public class AtomicPositiveInteger extends Number {

    private volatile int index = 0;

    AtomicIntegerFieldUpdater INDEX_UPDATER = AtomicIntegerFieldUpdater.newUpdater(AtomicPositiveInteger.class, "index");


    public final int getAndIncrement() {
        return INDEX_UPDATER.getAndIncrement(this) & Integer.MAX_VALUE;
    }

    public final int get() {
        return INDEX_UPDATER.get(this) & Integer.MAX_VALUE;
    }

    @Override
    public int intValue() {
        return get();
    }

    @Override
    public long longValue() {
        return get();
    }

    @Override
    public float floatValue() {
        return get();
    }

    @Override
    public double doubleValue() {
        return get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null) {
            return false;
        }
        if (!(o instanceof AtomicPositiveInteger)) {
            return false;
        }
        AtomicPositiveInteger other = (AtomicPositiveInteger) o;
        return this.get() == other.get();
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = result * prime + get();
        return result;
    }
}
