/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.resolver;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;

import java.util.concurrent.ConcurrentHashMap;

public final class JdkDnsResolverFactory implements DnsResolverFactory<EventExecutor> {

    private final ConcurrentHashMap<EventExecutor, Future<DnsResolver>> cache =
            new ConcurrentHashMap<EventExecutor, Future<DnsResolver>>();

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Future<DnsResolver> resolver(final EventExecutor executor) {
        Future<DnsResolver> future = cache.get(executor);
        if (future == null) {
             future = executor.<DnsResolver>newSucceededFuture(new JdkDnsResolver(executor));
             Future<DnsResolver> old = cache.putIfAbsent(executor, future);
             if (old != null) {
                 future = old;
             } else {
                 executor.terminationFuture().addListener(new GenericFutureListener() {
                     @Override
                     public void operationComplete(Future future) throws Exception {
                        // Executure terminted so also remove it from the cache
                        cache.remove(executor);
                     }
                 });
             }
        }
        return future;
    }

    @Override
    public Future<DnsResolver> resolver(EventExecutor executor, Promise<DnsResolver> promise) {
        return promise.setSuccess(resolver(executor).getNow());
    }
}
