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
package org.apache.dubbo.common.infra;

import org.apache.dubbo.common.extension.SPI;

import java.util.Map;

/**
 * Used to interact with other systems. Typical use cases are:
 * 1. get extra attributes from underlying infrastructures related to the instance on which Dubbo is currently deploying.
 * 2. get configurations from third-party systems which maybe useful for a specific component.
 * / **
 *   *用于与其他系统进行交互。 典型的用例是：
 *   * 1.从与Dubbo当前正在部署的实例相关的基础基础结构中获取额外的属性。
 *   * 2.从第三方系统获取可能对特定组件有用的配置。
 *   * /
 */

@SPI
public interface InfraAdapter {

    /**
     * get extra attributes
     *
     * @param params application name or hostname are most likely to be used as input params.
     *               应用程序名称或主机名最有可能用作输入参数。
     * @return
     */
    Map<String, String> getExtraAttributes(Map<String, String> params);

    /**
     * @param key
     * @return
     */
    String getAttribute(String key);

}
