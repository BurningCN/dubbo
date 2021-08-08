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

package org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice;

import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.match.DoubleMatch;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.match.DubboAttachmentMatch;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.match.DubboMethodMatch;
import org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.match.StringMatch;

import java.util.Map;


public class DubboMatchRequest {
    private String name;
    private DubboMethodMatch method;
    // 调用端打的相关 lables, 包含应用名、机器分组、机器环境变量信息等; 对于 HSF-JAVA 来说，可以从上报的 URL 拿到对应的 key/value
    private Map<String, String> sourceLabels;
    // 请求附带的其他信息，比如 HSF 请求上下文、Eagleeye 上下文等
    private DubboAttachmentMatch attachments;
    // 通用的请求协议字段等，如接口名、方法名、超时等
    private Map<String, StringMatch> headers;
    // 调用的 subset 列表的机器，占整个 host 的阀值
    private DoubleMatch threshold;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DubboMethodMatch getMethod() {
        return method;
    }

    public void setMethod(DubboMethodMatch method) {
        this.method = method;
    }

    public Map<String, String> getSourceLabels() {
        return sourceLabels;
    }

    public void setSourceLabels(Map<String, String> sourceLabels) {
        this.sourceLabels = sourceLabels;
    }

    public DubboAttachmentMatch getAttachments() {
        return attachments;
    }

    public void setAttachments(DubboAttachmentMatch attachments) {
        this.attachments = attachments;
    }

    public Map<String, StringMatch> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, StringMatch> headers) {
        this.headers = headers;
    }

    public DoubleMatch getThreshold() {
        return threshold;
    }

    public void setThreshold(DoubleMatch threshold) {
        this.threshold = threshold;
    }


    public static boolean isMatch(DubboMatchRequest dubboMatchRequest,
                                  String methodName, String[] parameterTypeList, Object[] parameters,
                                  // 这个是url.getParameters
                                  Map<String, String> sourceLabels,
                                  // dubboContext 是 invocation.getAttachments()
                                  Map<String, String> eagleeyeContext, Map<String, String> dubboContext,
                                  Map<String, String> headers
    ) {
        if (dubboMatchRequest.getMethod() != null) {
            if (!DubboMethodMatch.isMatch(dubboMatchRequest.getMethod(), methodName, parameterTypeList, parameters)) {
                return false;
            }
        }

        if (dubboMatchRequest.getSourceLabels() != null) {
            for (Map.Entry<String, String> entry : dubboMatchRequest.getSourceLabels().entrySet()) {
                String value = sourceLabels.get(entry.getKey());
                if (value == null || !entry.getValue().equals(value)) {
                    return false;
                }
            }
        }

        if (dubboMatchRequest.getAttachments() != null) {
            if (!DubboAttachmentMatch.isMatch(dubboMatchRequest.getAttachments(), eagleeyeContext, dubboContext)) {
                return false;
            }
        }

        //TODO headers


        return true;

    }

    @Override
    public String toString() {
        return "DubboMatchRequest{" +
            "name='" + name + '\'' +
            ", method=" + method +
            ", sourceLabels=" + sourceLabels +
            ", attachments=" + attachments +
            ", headers=" + headers +
            ", threshold=" + threshold +
            '}';
    }
}
