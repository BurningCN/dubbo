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

package org.apache.dubbo.common.threadpool.factory;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.threadpool.manager.ExecutorRepository;
import org.apache.dubbo.common.threadpool.manager.IsolationExecutorRepository;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * isolation executor repository factory
 */
public class IsolationExecutorRepositoryFactory implements ExecutorRepositoryFactory {

    private final ConcurrentMap<String, IsolationExecutorRepository> isolationRepositoryMap = new ConcurrentHashMap<>();

    @Override
    public ExecutorRepository getExecutorRepository(URL url) {
        String port = String.valueOf(url.getPort());
        return isolationRepositoryMap.computeIfAbsent(port, key -> new IsolationExecutorRepository(url));
    }
}
