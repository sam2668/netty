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
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import io.netty.util.internal.OneTimeTask;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

public final class JdkDnsResolver implements DnsResolver {

    public static final DnsResolver DEFAULT = new JdkDnsResolver(ImmediateEventExecutor.INSTANCE);

    private final EventExecutorGroup group;

    private JdkDnsResolver(EventExecutorGroup group) {
        if (group == null) {
            throw new NullPointerException("group");
        }
        this.group = group;
    }

    @Override
    public Future<InetAddress> lookup(String name) {
        EventExecutor executor = group.next();
        Promise<InetAddress> promise = executor.newPromise();
        lookup0(executor, name, promise);
        return promise;
    }

    @Override
    public Future<InetAddress> lookup(String name, Promise<InetAddress> promise) {
        lookup0(group.next(), name, promise);
        return promise;
    }

    @Override
    public Future<List<InetAddress>> lookupAll(String name) {
        EventExecutor executor = group.next();
        Promise<List<InetAddress>> promise = executor.newPromise();
        lookupAll0(executor, name, promise);
        return promise;
    }

    @Override
    public Future<List<InetAddress>> lookupAll(String name, Promise<List<InetAddress>> promise) {
        lookupAll0(group.next(), name, promise);
        return promise;
    }

    private static void lookup0(final EventExecutor executor, final String name,
                                final Promise<InetAddress> promise) {
        if (executor.inEventLoop()) {
            try {
                promise.setSuccess(InetAddress.getByName(name));
            } catch (Throwable cause) {
                promise.setFailure(new DnsResolverException(cause));
            }
        } else {
            executor.execute(new OneTimeTask() {
                @Override
                public void run() {
                    lookup0(executor, name, promise);
                }
            });
        }
    }


    private static void lookupAll0(final EventExecutor executor, final String name,
                                final Promise<List<InetAddress>> promise) {
        if (executor.inEventLoop()) {
            try {
                promise.setSuccess(Arrays.asList(InetAddress.getAllByName(name)));
            } catch (Throwable cause) {
                promise.setFailure(new DnsResolverException(cause));
            }
        } else {
            executor.execute(new OneTimeTask() {
                @Override
                public void run() {
                    lookupAll0(executor, name, promise);
                }
            });
        }
    }
}
