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

import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
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
import io.netty.util.internal.EmptyArrays;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.resolver.DnsResolver;
import io.netty.util.resolver.DnsResolverException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DatagramDnsResolver implements DnsResolver {
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
        if (timeout < 0) {
            throw new IllegalArgumentException();
        }
        if (!channel.isRegistered()) {
            throw new IllegalStateException("Channel must be registered before");
        }
        this.servers = servers;
        this.channel = channel;
        ChannelConfig config = channel.config();
        config.setOption(ChannelOption.DATAGRAM_CHANNEL_ACTIVE_ON_REGISTRATION, true);
        config.setOption(ChannelOption.SO_BROADCAST, true);
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
                    DnsResponseCode code = header.responseCode();
                    if (code == DnsResponseCode.NOERROR) {
                        if (processResource(query, response.answers())) {
                            return;
                        }
                        query.setFailure(new DnsResolverException("Unable to decode resources"));
                    } else {
                        query.setFailure(new DnsResolverException(code.toString()));
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
                        query.setSuccess(result);
                        return true;
                    }
                    if (decoded == null) {
                        decoded = new ArrayList<Object>(resources.size());
                    }
                    decoded.add(result);
                }
            }
            if (!decoded.isEmpty()) {
                query.setSuccess(decoded);
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
            promise.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        queries.remove(id);
                        query.setFailure(future.cause());
                    }
                }
            });
            // Schedule task to detect timeout
            ctx.executor().schedule(new Runnable() {
                @Override
                public void run() {
                    ResolverDnsQuery<?> query = queries.remove(id);
                    if (query != null) {
                        query.setFailure(TIMEOUT);
                    }
                }
            }, timeout, TimeUnit.MILLISECONDS);
            super.write(ctx, msg, promise);
        }
    }

    private static final class ResolverDnsQuery<T> extends DnsQuery {
        private final Promise<T> promise;
        private final boolean single;

        private ResolverDnsQuery(int id, InetSocketAddress recipient, boolean single, Promise<T> promise) {
            super(id, recipient);
            this.promise = promise;
            this.single = single;
        }

        void setSuccess(T result) {
            promise.trySuccess(result);
        }

        void setFailure(Throwable cause) {
            promise.tryFailure(cause);
        }
    }
}
