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
package org.apache.dubbo.config.event.listener;

import org.apache.dubbo.config.event.*;
import org.apache.dubbo.event.EventDispatcher;
import org.junit.jupiter.api.Test;

public class LoggingEventListenerTest {
    @Test
    public void test() {
        EventDispatcher eventDispatcher = EventDispatcher.getDefaultExtension();

        eventDispatcher.dispatch(new DubboServiceInitializedEvent(this));

        eventDispatcher.dispatch(new DubboServiceStartingEvent(this));

        eventDispatcher.dispatch(new DubboServiceReadyEvent(this));

        eventDispatcher.dispatch(new DubboServiceStartedEvent(this));

        eventDispatcher.dispatch(new DubboServiceAwaitingEvent(this));

        eventDispatcher.dispatch(new DubboServiceShutdownEvent(this));

        eventDispatcher.dispatch(new DubboServiceDestroyedEvent(this));
    }
}
