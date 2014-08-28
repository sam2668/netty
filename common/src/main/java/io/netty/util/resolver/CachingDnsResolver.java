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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CachingDnsResolver implements DnsResolver {

    private final DnsResolver resolver;
    // TODO: Implement caching
    private final long cachingTimeMillis;

    public CachingDnsResolver(DnsResolver resolver, long cachingTime, TimeUnit unit) {
        this(resolver, unit.toMillis(cachingTime));
    }

    public CachingDnsResolver(DnsResolver resolver, long cachingTimeMillis) {
        if (resolver == null) {
            throw new NullPointerException("resolver");
        }
        this.resolver = resolver;
        this.cachingTimeMillis = cachingTimeMillis;
    }

    @Override
    public Future<InetAddress> lookup(String name) {
        return resolver.lookup(name);
    }

    @Override
    public Future<InetAddress> lookup(String name, Promise<InetAddress> promise) {
        return resolver.lookup(name, promise);
    }

    @Override
    public Future<List<InetAddress>> lookupAll(String name) {
        return resolver.lookupAll(name);
    }

    @Override
    public Future<List<InetAddress>> lookupAll(String name, Promise<List<InetAddress>> promise) {
        return resolver.lookupAll(name, promise);
    }
}
