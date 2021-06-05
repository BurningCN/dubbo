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
package org.apache.dubbo.common.utils;

import java.io.OutputStream;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

// OK
public class JVMUtil {
    public static void jstack(OutputStream stream) throws Exception {
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        //threadMxBean.dumpAllThreads(true, true)的结果如下
        //result = {ThreadInfo[8]@2116}
        // 0 = {ThreadInfo@2118} ""pool-2-thread-1" Id=14 RUNNABLE\n\tat sun.management.ThreadImpl.dumpThreads0(Native Method)\n\tat sun.management.ThreadImpl.dumpAllThreads(ThreadImpl.java:454)\n\tat org.apache.dubbo.common.utils.JVMUtil.jstack(JVMUtil.java:29)\n\tat org.apache.dubbo.common.threadpool.support.AbortPolicyWithReport.lambda$dumpJStack$0(AbortPolicyWithReport.java:128)\n\tat org.apache.dubbo.common.threadpool.support.AbortPolicyWithReport$$Lambda$271/1740846921.run(Unknown Source)\n\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)\n\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)\n\tat java.lang.Thread.run(Thread.java:748)\n\n\tNumber of locked synchronizers = 1\n\t- java.util.concurrent.ThreadPoolExecutor$Worker@662b4c69\n\n"
        // 1 = {ThreadInfo@2119} ""JDWP Command Reader" Id=7 RUNNABLE\n\n"
        // 2 = {ThreadInfo@2120} ""JDWP Event Helper Thread" Id=6 RUNNABLE\n\n"
        // 3 = {ThreadInfo@2121} ""JDWP Transport Listener: dt_socket" Id=5 RUNNABLE\n\n"
        // 4 = {ThreadInfo@2122} ""Signal Dispatcher" Id=4 RUNNABLE\n\n"
        // 5 = {ThreadInfo@2123} ""Finalizer" Id=3 WAITING on java.lang.ref.ReferenceQueue$Lock@6d2c1a80\n\tat java.lang.Object.wait(Native Method)\n\t-  waiting on java.lang.ref.ReferenceQueue$Lock@6d2c1a80\n\tat java.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:143)\n\tat java.lang.ref.ReferenceQueue.remove(ReferenceQueue.java:164)\n\tat java.lang.ref.Finalizer$FinalizerThread.run(Finalizer.java:209)\n\n"
        // 6 = {ThreadInfo@2124} ""Reference Handler" Id=2 WAITING on java.lang.ref.Reference$Lock@643fc1a1\n\tat java.lang.Object.wait(Native Method)\n\t-  waiting on java.lang.ref.Reference$Lock@643fc1a1\n\tat java.lang.Object.wait(Object.java:502)\n\tat java.lang.ref.Reference.tryHandlePending(Reference.java:191)\n\tat java.lang.ref.Reference$ReferenceHandler.run(Reference.java:153)\n\n"
        // 7 = {ThreadInfo@2125} ""main" Id=1 RUNNABLE (suspended)\n\tat org.apache.dubbo.common.threadpool.support.AbortPolicyWithReport.dumpJStack(AbortPolicyWithReport.java:137)\n\tat org.apache.dubbo.common.threadpool.support.AbortPolicyWithReport.rejectedExecution(AbortPolicyWithReport.java:84)\n\tat org.apache.dubbo.common.threadpool.support.AbortPolicyWithReportTest.jStackDumpTest_dumpDirectoryNotExists_canBeCreated(AbortPolicyWithReportTest.java:86)\n\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\n\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\n\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\n\tat java.lang.reflect.Method.invoke(Method.java:498)\n\tat org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:688)\n\t...\n\n"
        for (ThreadInfo threadInfo : threadMxBean.dumpAllThreads(true, true)) {
            // getThreadDumpString进去
            stream.write(getThreadDumpString(threadInfo).getBytes());
        }
    }

    private static String getThreadDumpString(ThreadInfo threadInfo) {
        // 1.线程名称、id、状态
        StringBuilder sb = new StringBuilder("\"" + threadInfo.getThreadName() + "\"" +
                " Id=" + threadInfo.getThreadId() + " " +
                threadInfo.getThreadState());
        // 2.线程在什么锁上等待
        if (threadInfo.getLockName() != null) {
            sb.append(" on " + threadInfo.getLockName());
        }
        // 3，线程持有的锁
        if (threadInfo.getLockOwnerName() != null) {
            sb.append(" owned by \"" + threadInfo.getLockOwnerName() +
                    "\" Id=" + threadInfo.getLockOwnerId());
        }
        // 4.是否被挂起
        if (threadInfo.isSuspended()) {
            sb.append(" (suspended)");
        }
        // 5.是否是本地线程
        if (threadInfo.isInNative()) {
            sb.append(" (in native)");
        }
        sb.append('\n');
        int i = 0;

        // 线程的栈跟踪信息
        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
        // 锁定的监视器
        MonitorInfo[] lockedMonitors = threadInfo.getLockedMonitors();
        for (; i < stackTrace.length && i < 32; i++) {
            StackTraceElement ste = stackTrace[i];
            sb.append("\tat " + ste.toString());
            sb.append('\n');
            if (i == 0 && threadInfo.getLockInfo() != null) {
                Thread.State ts = threadInfo.getThreadState();
                switch (ts) {
                    case BLOCKED:
                        sb.append("\t-  blocked on " + threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    case WAITING:
                        sb.append("\t-  waiting on " + threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    case TIMED_WAITING:
                        sb.append("\t-  waiting on " + threadInfo.getLockInfo());
                        sb.append('\n');
                        break;
                    default:
                }
            }

            for (MonitorInfo mi : lockedMonitors) {
                if (mi.getLockedStackDepth() == i) {
                    sb.append("\t-  locked " + mi);
                    sb.append('\n');
                }
            }
        }
        if (i < stackTrace.length) {
            sb.append("\t...");
            sb.append('\n');
        }

        LockInfo[] locks = threadInfo.getLockedSynchronizers();
        if (locks.length > 0) {
            sb.append("\n\tNumber of locked synchronizers = " + locks.length);
            sb.append('\n');
            for (LockInfo li : locks) {
                sb.append("\t- " + li);
                sb.append('\n');
            }
        }
        sb.append('\n');
        return sb.toString();
    }
}
