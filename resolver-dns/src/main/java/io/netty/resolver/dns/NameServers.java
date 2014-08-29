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

import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class NameServers {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NameServers.class);
    private static final List<InetSocketAddress> SYSTEM_DEFAULTS = useSystemDefault();

    private static List<InetSocketAddress> useSystemDefault() {
        try {
            Class<?> configClass = Class.forName("sun.net.dns.ResolverConfiguration");
            Method open = configClass.getMethod("open");
            Method nameservers = configClass.getMethod("nameservers");
            Object instance = open.invoke(null);
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) nameservers.invoke(instance);
            InetSocketAddress[] addresses = new InetSocketAddress[list.size()];
            for (int i = 0; i < addresses.length; i++) {
                addresses[i] = new InetSocketAddress(InetAddress.getByName(list.get(i)), 53);
            }
            return Arrays.asList(addresses);
        } catch (Exception e) {
            logger.error("Failed to obtain system's DNS server addresses.", e);
            return Collections.emptyList();
        }
    }

    public static List<InetSocketAddress> defaults() {
        return SYSTEM_DEFAULTS;
    }

    /**
     * Return next nameserver to use
     */
    public abstract InetSocketAddress next();

    /**
     * Creates an new {@link NameServers} intstance which will return the provided servers in a
     * round-robin like fashion when call {@link #next()}.
     */
    public static NameServers roundRobin(final InetSocketAddress... servers) {
        return roundRobin(Arrays.asList(servers));
    }

    /**
     * Creates an new {@link NameServers} intstance which will return the provided servers in a
     * round-robin like fashion when call {@link #next()}.
     */
    public static NameServers roundRobin(final List<InetSocketAddress> servers) {
        if (servers == null) {
            throw new NullPointerException("servers");
        }
        final int size = servers.size();
        if (size == 0) {
            throw new IllegalArgumentException();
        }

        return new NameServers() {

            private final AtomicInteger idx = new AtomicInteger(0);

            @Override
            public InetSocketAddress next() {
                return servers.get(Math.abs(idx.incrementAndGet() % size));
            }
        };
    }
    /**
     * Creates an new {@link NameServers} intstance which will return the same server when call {@link #next()}.
     */
    public static NameServers one(final InetSocketAddress server) {
        if (server == null) {
            throw new NullPointerException("server");
        }

        return new NameServers() {

            @Override
            public InetSocketAddress next() {
                return server;
            }
        };
    }
}
