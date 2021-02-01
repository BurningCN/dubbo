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
package org.apache.dubbo.rpc.protocol.dubbo;

import org.apache.dubbo.rpc.AppResponse;
import org.apache.dubbo.rpc.RpcException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This is the type of the Future instance users get in an async call:
 * 1. unwrap AppResponse in appResponseFuture and convert to plain biz result represented by FutureAdapter.
 * 2. customized behaviors meaningful for RPC, for example, {@link #cancel(boolean)}
 * 这是用户在异步调用中获得的未来实例的类型:
 * 1. 在“appsponsefuture”中展开“appsponse”，并将其转换为“FutureAdapter”表示的简单商务结果。
 * 2. 对RPC有意义的定制行为，例如，{@link #cancel(boolean)}
 */
// OK
public class FutureAdapter<V> extends CompletableFuture<V> {

    private CompletableFuture<AppResponse> appResponseFuture;

    // gx
    public FutureAdapter(CompletableFuture<AppResponse> future) {
        this.appResponseFuture = future;
        future.whenComplete((appResponse, t) -> {
            if (t != null) {
                if (t instanceof CompletionException) {
                    t = t.getCause();
                }
                this.completeExceptionally(t);
            } else {
                if (appResponse.hasException()) {
                    this.completeExceptionally(appResponse.getException());
                } else {
                    this.complete((V) appResponse.getValue());
                }
            }
        });
    }

    public static void main(String[] args) {
        CompletableFuture<Long> completableFuture = new CompletableFuture<>();
        completableFuture.thenApply(a->{
            System.out.println(a);
            return a;});
        new Thread(()->{
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            completableFuture.complete(10L);
        }).start();
        System.out.println("over");
    }

    // TODO figure out the meaning of cancel in DefaultFuture.
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
//        Invocation invocation = invocationSoftReference.get();
//        if (invocation != null) {
//            invocation.getInvoker().invoke(cancel);
//        }
        return appResponseFuture.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return appResponseFuture.isCancelled();
    }

    @Override
    public boolean isDone() {
        return super.isDone();
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get() throws InterruptedException, ExecutionException {
        try {
            return super.get();
        } catch (ExecutionException | InterruptedException e) {
            throw e;
        } catch (Throwable e) {
            throw new RpcException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return super.get(timeout, unit);
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            throw e;
        } catch (Throwable e) {
            throw new RpcException(e);
        }
    }

}
