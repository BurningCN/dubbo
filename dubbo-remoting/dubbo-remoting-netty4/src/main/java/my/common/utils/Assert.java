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

// OK
public abstract class Assert {

    // 抽象类可以有构造方法，如果内部有一个抽象方法的话，外界不能实例化，如果没有抽象方法的，是可以实例化的
    // 可以把下面的protected改成public试验下，并提供抽象方法实验
    protected Assert() {
    }

    // gx
    public static void notNull(Object obj, String message) {
        if (obj == null) {
            // 不合法参数异常
            throw new IllegalArgumentException(message);
        }
    }

    public static void notEmptyString(String str,String message) {
        if(StringUtils.isEmpty(str)) {
            throw new IllegalArgumentException(message);
        }
    }
    public static void notNull(Object obj, RuntimeException exception) {
        if (obj == null) {
            throw exception;
        }
    }


}
