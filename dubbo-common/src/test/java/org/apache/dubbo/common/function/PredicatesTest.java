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
package org.apache.dubbo.common.function;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.apache.dubbo.common.function.Predicates.alwaysFalse;
import static org.apache.dubbo.common.function.Predicates.alwaysTrue;
import static org.apache.dubbo.common.function.Predicates.and;
import static org.apache.dubbo.common.function.Predicates.or;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Predicates} Test
 *
 * @since 2.7.5
 */

// OK
// 小技巧：import了静态类Predicates的方法，所以直接用方法，不带类前缀
public class PredicatesTest {

    @Test
    public void testAlwaysTrue() {
        assertTrue(alwaysTrue().test(null));
    }

    @Test
    public void testAlwaysFalse() {
        assertFalse(alwaysFalse().test(null));
    }

    @Test
    public void testAnd() {
        assertTrue(and(alwaysTrue(), alwaysTrue(), alwaysTrue()).test(null));
        assertFalse(and(alwaysFalse(), alwaysFalse(), alwaysFalse()).test(null));
        assertFalse(and(alwaysTrue(), alwaysFalse(), alwaysFalse()).test(null));
        assertFalse(and(alwaysTrue(), alwaysTrue(), alwaysFalse()).test(null));
    }

    @Test
    public void testOr() {
        assertTrue(or(alwaysTrue(), alwaysTrue(), alwaysTrue()).test(null));
        assertTrue(or(alwaysTrue(), alwaysTrue(), alwaysFalse()).test(null));
        assertTrue(or(alwaysTrue(), alwaysFalse(), alwaysFalse()).test(null));
        assertFalse(or(alwaysFalse(), alwaysFalse(), alwaysFalse()).test(null));
    }

    @Test
    public void testReduce() {
        int[] ints = {1, 2, 3, 4, 5};
        int sum = Arrays.stream(ints).reduce(0, (a, b) -> a + b);
        int sum1 = Arrays.stream(ints).reduce(0, Integer::sum);
        assertEquals(sum,sum1);

        OptionalInt optionalInt = Arrays.stream(ints).reduce((a, b) -> a>b?a:b);
        OptionalInt optionalInt1 = Arrays.stream(ints).reduce(Integer::max);
        OptionalInt optionalInt2 = Arrays.stream(ints).reduce(Integer::min);
        assertEquals(optionalInt.orElse(-1),optionalInt1.orElse(-1));

        int count = Arrays.stream(ints).map(i -> 1).reduce(0, (a, b) -> a + b);
        long count1 = Arrays.stream(ints).count();
        assertTrue(count == count1);

    }
}
