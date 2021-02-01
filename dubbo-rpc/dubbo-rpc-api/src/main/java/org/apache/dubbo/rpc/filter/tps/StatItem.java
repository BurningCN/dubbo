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
package org.apache.dubbo.rpc.filter.tps;

import java.util.concurrent.atomic.LongAdder;

/**
 * Judge whether a particular invocation of service provider method should be allowed within a configured time interval.
 * As a state it contain name of key ( e.g. method), last invocation time, interval and rate count.
 */
// OK
class StatItem {

    private String name;

    private long lastResetTime;

    private long interval;

    private LongAdder token;

    private int rate;

    StatItem(String name, int rate, long interval) {
        this.name = name;

        // 下两个表示在interval间隔内最多只能调用rate+1次（rate从0计数）（比如1s最多调用2次）
        this.rate = rate;
        this.interval = interval;
        //  记录当前时间作为上次重置时间
        this.lastResetTime = System.currentTimeMillis();
        // LongAdder和AtomicLong功能一样，不过前者效率更高，进去
        this.token = buildLongAdder(rate);
    }

    public boolean isAllowable() {
        long now = System.currentTimeMillis();
        // 调用间隔检查
        if (now > lastResetTime + interval) {
            // 重置
            token = buildLongAdder(rate);
            lastResetTime = now;
        }

        // 再间隔内，但是次数用完了，返回false禁止调用
        if (token.sum() < 0) {
            return false;
        }
        // 在间隔内，次数--
        token.decrement();
        return true;
    }

    public long getInterval() {
        return interval;
    }


    public int getRate() {
        return rate;
    }


    long getLastResetTime() {
        return lastResetTime;
    }

    long getToken() {
        return token.sum();
    }

    @Override
    public String toString() {
        return new StringBuilder(32).append("StatItem ")
                .append("[name=").append(name).append(", ")
                .append("rate = ").append(rate).append(", ")
                .append("interval = ").append(interval).append("]")
                .toString();
    }

    private LongAdder buildLongAdder(int rate) {
        LongAdder adder = new LongAdder();
        adder.add(rate);
        return adder;
    }

}
