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
package org.apache.dubbo.metadata.rest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;

import static java.util.Collections.unmodifiableList;
import static org.apache.dubbo.common.function.ThrowableAction.execute;
import static org.apache.dubbo.metadata.rest.RestMetadataConstants.METADATA_ENCODING;
import static org.apache.dubbo.metadata.rest.RestMetadataConstants.SERVICE_REST_METADATA_RESOURCE_PATH;

/**
 * Class-Path based {@link ServiceRestMetadataReader} implementation
 *
 * @see ServiceRestMetadataReader
 * @since 2.7.6
 */
public class ClassPathServiceRestMetadataReader implements ServiceRestMetadataReader {

    // todo need pr 拼写错误
    private final String serviceRestMetadataJsonResoucePath;

    public ClassPathServiceRestMetadataReader() {
        this(SERVICE_REST_METADATA_RESOURCE_PATH);
    }

    public ClassPathServiceRestMetadataReader(String serviceRestMetadataJsonResoucePath) {
        this.serviceRestMetadataJsonResoucePath = serviceRestMetadataJsonResoucePath;
    }

    @Override
    public List<ServiceRestMetadata> read() {

        List<ServiceRestMetadata> serviceRestMetadataList = new LinkedList<>();

        ClassLoader classLoader = getClass().getClassLoader();

        execute(() -> {
            // 注意getResources和getResourcesAsStream的区别，后者是返回一个流，而前者可以先拿到资源列表，然后每个列表可以建一个流
            // 可以看下 jax-rs-service-rest-metadata.json 文件
            Enumeration<URL> resources = classLoader.getResources(serviceRestMetadataJsonResoucePath);
            Gson gson = new Gson();
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                InputStream inputStream = resource.openStream();
                // gson 的 api
                JsonParser parser = new JsonParser();
                // 字节流转化为字符流，建立流的时候可以指定编解码
                JsonElement jsonElement = parser.parse(new InputStreamReader(inputStream, METADATA_ENCODING));
                if (jsonElement.isJsonArray()) {
                    JsonArray jsonArray = jsonElement.getAsJsonArray();
                    for (int i = 0; i < jsonArray.size(); i++) {
                        JsonElement childJsonElement = jsonArray.get(i);
                        // 每一项转化为ServiceRestMetadata
                        ServiceRestMetadata serviceRestMetadata = gson.fromJson(childJsonElement, ServiceRestMetadata.class);
                        serviceRestMetadataList.add(serviceRestMetadata);
                    }
                }
            }
        });

        return unmodifiableList(serviceRestMetadataList);
    }
}
