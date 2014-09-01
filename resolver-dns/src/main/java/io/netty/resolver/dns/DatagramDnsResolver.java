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

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsQueryEncoder;
import io.netty.handler.codec.dns.DnsQueryHeader;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsResponseDecoder;
import io.netty.handler.codec.dns.DnsResponseHeader;
import io.netty.handler.codec.dns.DnsType;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.resolver.DnsResolverException;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class DatagramDnsResolver implements AdvancedDnsResolver {
    private static final DnsResponseDecoder RESPONSE_DECODER = new DnsResponseDecoder();
    private static final DnsQueryEncoder QUERY_ENCODER = new DnsQueryEncoder();
    private static final DnsResolverException TIMEOUT = new DnsResolverException("DNS query timeout");

    static {
        TIMEOUT.setStackTrace(EmptyArrays.EMPTY_STACK_TRACE);
    }

    private final NameServers servers;
    private final AtomicInteger id = new AtomicInteger(0);
    private final DatagramChannel channel;
    private final DnsResourceDecoder<?> decoder = DefaultDnsResourceDecoder.INSTANCE;
    private final long timeout;

    DatagramDnsResolver(DatagramChannel channel, long timeout, NameServers servers) {
        if (timeout <= 0) {
            throw new IllegalArgumentException();
        }
        if (!channel.isRegistered()) {
            throw new IllegalStateException("Channel must be registered before");
        }
        if (servers == null) {
            throw new NullPointerException("servers");
        }
        this.servers = servers;
        this.channel = channel;
        channel.pipeline().addLast("decoder", RESPONSE_DECODER).addLast("encoder", QUERY_ENCODER)
                .addLast("handler", new DnsResponseHandler());
        this.timeout = timeout;
    }

    @Override
    public Future<InetAddress> lookup(String name) {
        return lookup(name, channel.eventLoop().<InetAddress>newPromise());
    }

    @Override
    public Future<InetAddress> lookup(String name, Promise<InetAddress> promise) {
        return sendQuery(name, servers.next(), true, promise, DnsType.A, DnsType.AAAA);
    }

    @Override
    public Future<List<InetAddress>> lookupAll(String name) {
        return lookupAll(name, channel.eventLoop().<List<InetAddress>>newPromise());
    }

    @Override
    public Future<List<InetAddress>> lookupAll(String name, Promise<List<InetAddress>> promise) {
        return sendQuery(name, servers.next(), false, promise, DnsType.A, DnsType.AAAA);
    }

    @Override
    public Future<Inet4Address> lookup4(String name) {
        return lookup4(name, channel.eventLoop().<Inet4Address>newPromise());
    }

    @Override
    public Future<Inet4Address> lookup4(String name, Promise<Inet4Address> promise) {
        return sendQuery(name, servers.next(), true, promise, DnsType.A);
    }

    @Override
    public Future<List<Inet4Address>> lookup4All(String name) {
        return lookup4All(name, channel.eventLoop().<List<Inet4Address>>newPromise());
    }

    @Override
    public Future<List<Inet4Address>> lookup4All(String name, Promise<List<Inet4Address>> promise) {
        return sendQuery(name, servers.next(), false, promise, DnsType.A);
    }

    @Override
    public Future<Inet6Address> lookup6(String name) {
        return lookup6(name, channel.eventLoop().<Inet6Address>newPromise());
    }

    @Override
    public Future<Inet6Address> lookup6(String name, Promise<Inet6Address> promise) {
        return sendQuery(name, servers.next(), true, promise, DnsType.AAAA);
    }

    @Override
    public Future<List<Inet6Address>> lookup6All(String name) {
        return lookup6All(name, channel.eventLoop().<List<Inet6Address>>newPromise());
    }

    @Override
    public Future<List<Inet6Address>> lookup6All(String name, Promise<List<Inet6Address>> promise) {
        return sendQuery(name, servers.next(), false, promise, DnsType.AAAA);
    }

    private <T> Future<T> sendQuery(String domain, InetSocketAddress dnsServerAddress, boolean single,
                                    Promise<T> promise, DnsType... types) {
        final ResolverDnsQuery<T> query = new ResolverDnsQuery<T>(nextId(), dnsServerAddress, single, promise);
        for (DnsType type: types) {
            query.addQuestion(new DnsQuestion(domain, type));
        }
        channel.writeAndFlush(query);
        return promise;
    }

    /**
     * Returns an id in the range 0-65536.
     */
    private int nextId() {
        for (;;) {
            int nextId = id.incrementAndGet();
            if (nextId < 0 || nextId > 65536)  {
                id.set(-1);
            } else {
                return nextId;
            }
        }
    }

    @ChannelHandler.Sharable
    private final class DnsResponseHandler extends ChannelDuplexHandler {
        private final Map<Integer, ResolverDnsQuery<?>> queries = PlatformDependent.newConcurrentHashMap();

        /**
         * Called when a new {@link DnsResponse} is received. The callback corresponding to this message is found and
         * finished.
         */
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            try {
                DnsResponse response = (DnsResponse) msg;
                DnsResponseHeader header = response.header();
                ResolverDnsQuery<?> query = queries.remove(header.id());
                if (query != null) {
                    query.timeoutFuture.cancel(false);

                    DnsResponseCode responseCode = header.responseCode();
                    int code = responseCode.code();
                    if (code == DnsResponseCode.NOERROR.code()) {
                        if (processResource(query, response.answers())) {
                            return;
                        }
                        query.promise.tryFailure(new DnsResolverException("Unable to decode resources"));
                    } else if (code == DnsResponseCode.NXDOMAIN.code()) {
                        query.promise.tryFailure(new DnsResolverException(new UnknownHostException()));
                    } else {
                        query.promise.tryFailure(new DnsResolverException(responseCode.toString()));
                    }
                }
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private boolean processResource(ResolverDnsQuery query, List<DnsResource> resources) {
            boolean single = query.single;

            List<Object> decoded = null;
            for (int i = 0; i < resources.size(); i++) {
                DnsResource resource = resources.get(i);
                Object result = decoder.decode(resource);
                if (result != null) {
                    if (single) {
                        query.promise.trySuccess(result);
                        query.timeoutFuture.cancel(false);
                        return true;
                    }
                    if (decoded == null) {
                        decoded = new ArrayList<Object>(resources.size());
                    }
                    decoded.add(result);
                }
            }
            if (!decoded.isEmpty()) {
                query.promise.trySuccess(decoded);
                query.timeoutFuture.cancel(false);
                return true;
            }
            return false;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            final ResolverDnsQuery<?> query = (ResolverDnsQuery<?>) msg;
            DnsQueryHeader header = query.header();
            final int id = header.id();
            queries.put(id, query);
            // Schedule task to detect timeout
            query.timeoutFuture = ctx.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    ResolverDnsQuery<?> query = queries.remove(id);
                    if (query != null) {
                        query.promise.tryFailure(TIMEOUT);
                    }
                }
            }, timeout, TimeUnit.MILLISECONDS);

            promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        queries.remove(id);
                        query.promise.tryFailure(future.cause());
                        query.timeoutFuture.cancel(false);
                    }
                }
            });
            super.write(ctx, msg, promise);
        }
    }

    private static final class ResolverDnsQuery<T> extends DnsQuery {
        private final Promise<T> promise;
        private final boolean single;
        private ScheduledFuture<?> timeoutFuture;

        private ResolverDnsQuery(int id, InetSocketAddress recipient, boolean single, Promise<T> promise) {
            super(id, recipient);
            this.promise = promise;
            this.single = single;
        }
    }
}
