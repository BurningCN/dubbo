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
package my.common.utils;

import java.util.Comparator;

/**
 * The {@link Comparator} for {@link CharSequence}
 *
 * @since 2.7.6
 */
// OK
// 注意这个 CharSequence 类型。使用的Comparator而非Comparable<CharSequence>
public class CharSequenceComparator implements Comparator<CharSequence> {

    // 单例模式 gx 传入的class.getSimpleName()（Name接口是继承CharSequence接口）
    public final static CharSequenceComparator INSTANCE = new CharSequenceComparator();

    // 构造方法私有化
    private CharSequenceComparator() {
    }

    @Override
    public int compare(CharSequence c1, CharSequence c2) {
        // 使用的是String的compareTo方法
        return c1.toString().compareTo(c2.toString());
    }
}
