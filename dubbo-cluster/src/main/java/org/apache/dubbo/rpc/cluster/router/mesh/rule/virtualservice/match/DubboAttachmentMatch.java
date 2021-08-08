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

package org.apache.dubbo.rpc.cluster.router.mesh.rule.virtualservice.match;

import java.util.Map;


// gx 属于DubboMatchRequest
public class DubboAttachmentMatch {
    private Map<String, StringMatch> eagleEyeContext;
    private Map<String, StringMatch> dubboContext;

    public Map<String, StringMatch> getEagleEyeContext() {
        return eagleEyeContext;
    }

    public void setEagleEyeContext(Map<String, StringMatch> eagleEyeContext) {
        this.eagleEyeContext = eagleEyeContext;
    }

    public Map<String, StringMatch> getDubboContext() {
        return dubboContext;
    }

    public void setDubboContext(Map<String, StringMatch> dubboContext) {
        this.dubboContext = dubboContext;
    }

    // 匹配两个map，要求在该类对象属性中存在的，要在参数的map也存在
    public static boolean isMatch(DubboAttachmentMatch dubboAttachmentMatch, Map<String, String> eagleeyeContext, Map<String, String> dubboContext) {
        if (dubboAttachmentMatch.getDubboContext() != null) {
            for (Map.Entry<String, StringMatch> stringStringMatchEntry : dubboAttachmentMatch.getDubboContext().entrySet()) {
                String key = stringStringMatchEntry.getKey();
                StringMatch stringMatch = stringStringMatchEntry.getValue();

                String dubboContextValue = dubboContext.get(key);
                if (dubboContextValue == null) {
                    return false;
                }
                if (!StringMatch.isMatch(stringMatch, dubboContextValue)) {
                    return false;
                }
            }
        }

        if (dubboAttachmentMatch.getEagleEyeContext() != null) {
            for (Map.Entry<String, StringMatch> stringStringMatchEntry : dubboAttachmentMatch.getEagleEyeContext().entrySet()) {
                String key = stringStringMatchEntry.getKey();
                StringMatch stringMatch = stringStringMatchEntry.getValue();

                String eagleeyeContextValue = eagleeyeContext.get(key);
                if (eagleeyeContextValue == null) {
                    return false;
                }
                if (!StringMatch.isMatch(stringMatch, eagleeyeContextValue)) {
                    return false;
                }
            }
        }

        return true;
    }
}
