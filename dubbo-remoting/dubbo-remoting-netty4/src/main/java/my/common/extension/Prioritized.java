package my.common.extension;

import java.util.Comparator;

/**
 * @author gy821075
 * @date 2021/2/3 17:33
 */
public interface Prioritized extends Comparable<Prioritized> {


    int MAX_PRIORITY = Integer.MAX_VALUE;
    int MIN_PRIORITY = Integer.MIN_VALUE;
    int NORMAL_PRIORITY = 0;

    default int getPriority() {
        return NORMAL_PRIORITY;
    }

    default int compareTo(Prioritized other) {
        return Integer.compare(this.getPriority(), other.getPriority());
    }
}
