/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.common.lang;

import java.util.Comparator;

import static java.lang.Integer.compare;

/**
 * {@code Prioritized} interface can be implemented by objects that
 * should be sorted, for example the tasks in executable queue.
 *
 * @since 2.7.5
 */

// OK
public interface Prioritized extends Comparable<Prioritized> {

    /**
     * The {@link Comparator} of {@link Prioritized}
     */
    // gx,外界这样用Collections.sort(list, Prioritized.COMPARATOR);
    // 以前看的都是Comparable的CompareTo里面使用Comparator的compare，现在还可以反过来
    Comparator<Object> COMPARATOR = (one, two) -> {
        boolean b1 = one instanceof Prioritized;
        boolean b2 = two instanceof Prioritized;
        if (b1 && !b2) {        // one is Prioritized, two is not
            return -1; // a和b比较，如果返回负数负数a小
        } else if (b2 && !b1) { // two is Prioritized, one is not
            return 1;
        } else if (b1 && b2) {  //  one and two both are Prioritized
            // 比较优先级，去看compareTo
            return ((Prioritized) one).compareTo((Prioritized) two);
        } else {                // no different
            return 0;
        }
    };

    /**
     * The maximum priority
     */
    int MAX_PRIORITY = Integer.MIN_VALUE;

    /**
     * The minimum priority
     */
    int MIN_PRIORITY = Integer.MAX_VALUE;

    /**
     * Normal Priority
     */
    int NORMAL_PRIORITY = 0;

    /**
     * Get the priority
     *
     * @return the default is {@link #MIN_PRIORITY minimum one}
     */
    default int getPriority() {
        return NORMAL_PRIORITY;
    }

    // 接口可以含有默认方法（属于对象的，实例方法，不是类方法，注意下）
    // 这里重写了父接口的compareTo方法，给出了默认实现
    @Override
    default int compareTo(Prioritized that) {
        // 因为优先级是int类型，直接调用Integer的compare。String类型也有对应的compareTo方法,str1.compareTo(str2)
        // 按照数字从小到大排序
        return compare(this.getPriority(), that.getPriority());
    }
}
