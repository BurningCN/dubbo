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
package org.apache.dubbo.container;

import org.apache.dubbo.common.extension.SPI;

/**
 * Container. (SPI, Singleton, ThreadSafe)
 */
@SPI("spring")
public interface Container {

    /**
     * start method to load the container.
     */
    void start();

    /**
     * stop method to unload the container.
     */
    void stop();

}
/*
https://zhuanlan.zhihu.com/p/120925888
1：dubbo几大角色
Provider: 暴露服务的服务提供方。

Consumer: 调用远程服务的服务消费方。

Registry: 服务注册与发现的注册中心。

Monitor: 统计服务的调用次调和调用时间的监控中心。

Container: 服务运行容器。

2：Container详解
Dubbo的Container详解模块，是一个独立的容器，因为服务通常不需要Tomcat/JBoss等Web容器的特性，没必要用Web容器去加载服务。

服务容器只是一个简单的Main方法，并加载一个简单的Spring容器，用于暴露服务。

com.alibaba.dubbo.container.Main 是服务启动的主类

了解Container接口有只有 start() stop()他的实现类

他的实现类有SpringContainer、Log4jContainer、JettyContainer、JavaConfigContainer、LogbackContainer。

当然你也可以自定义容器

官网：http://dubbo.io/Developer+Guide.htm#DeveloperGuide-ContainerSPI

Spring Container

自动加载META-INF/spring目录下的所有Spring配置。 （这个在源码里面已经写死了DEFAULT_SPRING_CONFIG = "classpath*:META-INF/spring/*.xml" 所以服务端的主配置放到META-INF/spring/这个目录下）

配置：(配在java命令-D参数或者dubbo.properties中)

dubbo.spring.config=classpath*:META-INF/spring/*.xml ----配置spring配置加载位置Container



Jetty Container

启动一个内嵌Jetty，用于汇报状态。

配置：(配在java命令-D参数或者dubbo.properties中)

dubbo.jetty.port=8080 ----配置jetty启动端口

dubbo.jetty.directory=/foo/bar ----配置可通过jetty直接访问的目录，用于存放静态文件

dubbo.jetty.page=log,status,system ----配置显示的页面，缺省加载所有页面



Log4j Container

自动配置log4j的配置，在多进程启动时，自动给日志文件按进程分目录。

配置：(配在java命令-D参数或者dubbo.properties中)

dubbo.log4j.file=/foo/bar.log ----配置日志文件路径

dubbo.log4j.level=WARN ----配置日志级别

dubbo.log4j.subdirectory=20880 ----配置日志子目录，用于多进程启动，避免冲突

3：容器启动
从上面的我们知道只需执行main方法就能启动服务，那么默认到底是调用那个容器呢？

查看com.alibaba.dubbo.container.Container接口我们发现他上面有一个注解@SPI("spring")

知道默认调用的是com.alibaba.dubbo.container.spring.SpringContainer

这里main方法里面dubbo他们自定义了一个loader，叫ExtensionLoader

所以目前启动容器的时候我们可以选：spring、javaconfig、jetty、log4j、logback等参数

4：容器停止
Dubbo是通过JDK的ShutdownHook来完成优雅停机的，所以如果用户使用"kill -9 PID"等强制关闭指令，是不会执行优雅停机的，只有通过"kill PID"时，才会执行。

停止源码（在Main方法里面）


*/