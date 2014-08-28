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

import io.netty.handler.codec.dns.DnsResource;
import io.netty.handler.codec.dns.DnsType;

import java.net.InetAddress;

public class DefaultDnsResourceDecoder implements DnsResourceDecoder<Object> {
    private static final DnsResourceDecoder<InetAddress> A_RESOURCE_DECODER = new AddressDecoder(4);
    private static final DnsResourceDecoder<InetAddress> AAAA_RESOURCE_DECODER = new AddressDecoder(16);

    public static final DnsResourceDecoder<Object> INSTANCE = new DefaultDnsResourceDecoder();

    private DefaultDnsResourceDecoder() {}

    @Override
    public Object decode(DnsResource resource) {
        // Only supports A and AAAA records for now
        DnsType type = resource.type();
        if (type == DnsType.A) {
            return A_RESOURCE_DECODER.decode(resource);
        }
        if (type == DnsType.AAAA) {
            return AAAA_RESOURCE_DECODER.decode(resource);
        }
        throw new IllegalStateException("Unsupported resource record type [type: " + type + "].");
    }
}
