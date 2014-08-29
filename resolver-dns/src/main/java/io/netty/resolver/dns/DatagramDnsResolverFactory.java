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
package io.netty.resolver.dns;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.resolver.DnsResolver;
import io.netty.util.resolver.DnsResolverFactory;

import java.util.concurrent.ConcurrentHashMap;

public final class DatagramDnsResolverFactory implements DnsResolverFactory<EventLoop> {

    private final Class<? extends DatagramChannel> clazz;
    private final NameServers servers;
    private final ConcurrentHashMap<EventExecutor, Future<DnsResolver>> cache =
            new ConcurrentHashMap<EventExecutor, Future<DnsResolver>>();

    private final ChannelFutureListener cacheRemover = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            cache.remove(future.channel().eventLoop());
        }
    };

    DatagramDnsResolverFactory(Class<? extends DatagramChannel> clazz, NameServers servers) {
        this.clazz = clazz;
        this.servers = servers;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Future<DnsResolver> resolver(final EventLoop executor) {
        Future<DnsResolver> future = cache.get(executor);
        if (future == null) {
            try {
                final Promise<DnsResolver> promise = executor.newPromise();
                future = promise;
                Future<DnsResolver> old = cache.putIfAbsent(executor, future);
                if (old != null) {
                    return old;
                }

                executor.terminationFuture().addListener(new GenericFutureListener() {
                    @Override
                    public void operationComplete(Future future) throws Exception {
                        // Executure terminted so also remove it from the cache
                        cache.remove(executor);
                    }
                });
                final DatagramChannel channel = newChannel();
                executor.register(channel).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            channel.closeFuture().addListener(cacheRemover);
                            promise.setSuccess(new DatagramDnsResolver(channel, 10000, servers));
                        } else {
                            cache.remove(executor);
                            promise.setFailure(future.cause());
                        }
                    }
                });
            } catch (Throwable cause) {
                return executor.newFailedFuture(cause);
            }
        }
        return future;
    }

    @Override
    public Future<DnsResolver> resolver(EventLoop executor, final Promise<DnsResolver> promise) {
        Future<DnsResolver> future = resolver(executor);
        if (future.isDone()) {
            if (future.isSuccess()) {
                promise.setSuccess(future.getNow());
            } else {
                promise.setFailure(future.cause());
            }
        } else {
            future.addListener(new FutureListener<DnsResolver>() {
                @Override
                public void operationComplete(Future<DnsResolver> future) throws Exception {
                    if (future.isSuccess()) {
                        promise.setSuccess(future.getNow());
                    } else {
                        promise.setFailure(future.cause());
                    }
                }
            });
        }
        return promise;
    }

    private DatagramChannel newChannel() throws Exception {
        return clazz.newInstance();
    }
}
