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

import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static java.util.Arrays.asList;
import static java.util.Collections.sort;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Prioritized} Test
 *
 * @since 2.7.5
 */
// OK
public class PrioritizedTest {

    @Test
    public void testConstants() {
        assertEquals(Integer.MAX_VALUE, Prioritized.MIN_PRIORITY);
        assertEquals(Integer.MIN_VALUE, Prioritized.MAX_PRIORITY);
    }

    @Test
    public void testGetPriority() {
        assertEquals(Prioritized.NORMAL_PRIORITY, new Prioritized() {
        }.getPriority());
    }

    @Test
    public void testComparator() {

        List<Object> list = new LinkedList<>();

        // All Prioritized of进去
        list.add(of(1));
        list.add(of(2));
        list.add(of(3));

        List<Object> copy = new LinkedList<>(list);

        // 排序，按照从小到大，1 2 3
        sort(list, Prioritized.COMPARATOR);

        assertEquals(copy, list);

        // MIX non-Prioritized and Prioritized
        list.clear();

        // 这个元素不是Prioritized的，在排序的时候会排到最后，即最大的位置
        list.add(1);
        list.add(of(2));
        list.add(of(1));

        sort(list, Prioritized.COMPARATOR);

        copy = asList(of(1), of(2), 1);

        // 注意会调用PrioritizedValue的equals方法
        assertEquals(copy, list);

        // All non-Prioritized
        // 如果都不是Prioritized的,按照插入顺序排序
        list.clear();
        list.add(1);
        list.add(2);
        list.add(3);

        sort(list, Prioritized.COMPARATOR);

        copy = asList(1, 2, 3);

        assertEquals(copy, list);

    }

    // of其实就是工厂方法，类似什么Stream.of等
    public static PrioritizedValue of(int value) {
        // 子类
        return new PrioritizedValue(value);
    }

    static class PrioritizedValue implements Prioritized {

        private final int value;

        private PrioritizedValue(int value) {
            this.value = value;
        }

        // 重写了getPriority，优先级用的自己的value属性，没用父接口的那3个
        // 重写的原因是因为Prioritized.COMPARATOR内部会调用Prioritized.compareTo，compareTo内部是根据getPriority的值比较的
        public int getPriority() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PrioritizedValue)) return false;
            PrioritizedValue that = (PrioritizedValue) o;
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
