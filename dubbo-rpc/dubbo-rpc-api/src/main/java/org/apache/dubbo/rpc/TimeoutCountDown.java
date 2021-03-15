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
package org.apache.dubbo.rpc;

import java.util.concurrent.TimeUnit;

// OK
public final class TimeoutCountDown implements Comparable<TimeoutCountDown> {

    public static TimeoutCountDown newCountDown(long timeout, TimeUnit unit) {
        // 进去
        return new TimeoutCountDown(timeout, unit);
    }

    private final long timeoutInMillis;
    private final long deadlineInNanos;
    private volatile boolean expired;

    // sao 死亡点+convert api+volatile expired
    // 其实也不是什么sao，只是当前直接计算了死亡点，判定过期的时候直接 和 新的当前时间比较，
    // 也可以判断是否过期的时候临时比较，即下面的构造方法记录当前时间和timeout即可，过期的时候新的当前时间 - 旧的 是否 > timeout
    // 所以说过期的判定方式有很多
    private TimeoutCountDown(long timeout, TimeUnit unit) {
        timeoutInMillis = TimeUnit.MILLISECONDS.convert(timeout, unit);// eg 3000 ms
        deadlineInNanos = System.nanoTime() + TimeUnit.NANOSECONDS.convert(timeout, unit);// 死亡点
    }

    public long getTimeoutInMilli() {
        return timeoutInMillis;
    }

    // gx
    public boolean isExpired() {
        // 用expired减少重复计算判断
        if (!expired) {
            if (deadlineInNanos - System.nanoTime() <= 0) {
                expired = true;
            } else {
                return false;
            }
        }
        return true;
    }

    // gx
    public long elapsedMillis() {
        if (isExpired()) {
            return timeoutInMillis + TimeUnit.MILLISECONDS.convert(System.nanoTime() - deadlineInNanos, TimeUnit.NANOSECONDS);
        } else {
            // 这个计算的不对！！todo need pr
            return TimeUnit.MILLISECONDS.convert(deadlineInNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }
    }

    // gx
    public long timeRemaining(TimeUnit unit) {
        final long currentNanos = System.nanoTime();
        if (!expired && deadlineInNanos - currentNanos <= 0) {
            expired = true;
        }
        return unit.convert(deadlineInNanos - currentNanos, TimeUnit.NANOSECONDS);
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

    @Override
    public int compareTo(TimeoutCountDown another) {
        long delta = this.deadlineInNanos - another.deadlineInNanos;
        // 正常可以直接return this.deadlineInNanos - another.deadlineInNanos;，但是返回的数很大，所以再次判断返回-1 1 0 "小"数
        if (delta < 0) {
            return -1;
        } else if (delta > 0) {
            return 1;
        }
        return 0;
    }
}
