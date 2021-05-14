/*
 *
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package samples.annotation.action;

import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.Method;
import org.springframework.stereotype.Component;
import samples.annotation.api.GreetingService;
import samples.annotation.api.HelloService;

@Component("annotationAction")
public class AnnotationAction {

    @DubboReference(interfaceClass = HelloService.class, version = samples.annotation.AnnotationConstants.VERSION)
    private HelloService helloService;

    @DubboReference(interfaceClass = GreetingService.class,
            version = samples.annotation.AnnotationConstants.VERSION,
            timeout = 1000,
            methods = {@Method(name = "greeting", timeout = 3000, retries = 1)})
    private GreetingService greetingService;

    public String doSayHello(String name) {
        try {
            return helloService.sayHello(name);
        } catch (Exception e) {
            e.printStackTrace();
            return "Throw Exception";
        }
    }

    public String doSayGoodbye(String name) {
        try {
            return helloService.sayGoodbye(name);
        } catch (Exception e) {
            e.printStackTrace();
            return "Throw Exception";
        }

    }

    public String doGreeting(String name) {
        try {
            return greetingService.greeting(name);
        } catch (Exception e) {
            e.printStackTrace();
            return "Throw Exception";
        }

    }

    public String replyGreeting(String name) {
        try {
            return greetingService.replyGreeting(name);
        } catch (Exception e) {
            e.printStackTrace();
            return "Throw Exception";
        }
    }
}
