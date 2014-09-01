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

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.dns.DnsClass;
import io.netty.handler.codec.dns.DnsResource;
import io.netty.handler.codec.dns.DnsType;
import org.junit.Assert;
import org.junit.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

public class AddressDecoderTest {
    private static final byte[] IP6_BYTES = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 };
    private static final byte[] IP4_BYTES = { 127, 0, 0, 1 };

    @Test
    public void testDecodeARecord() {
        AddressDecoder decoder = AddressDecoder.A_RECORD_DECODER;
        InetAddress address = decoder.decode(
                new DnsResource("netty.io", DnsType.A, DnsClass.IN, 1024, Unpooled.wrappedBuffer(IP4_BYTES)));
        Assert.assertTrue(address instanceof Inet4Address);
        Assert.assertArrayEquals(IP4_BYTES, address.getAddress());
    }

    @Test(expected = DecoderException.class)
    public void testDecodeARecordOctetsMissmatch() {
        AddressDecoder decoder = AddressDecoder.A_RECORD_DECODER;
        decoder.decode(
                new DnsResource("netty.io", DnsType.A, DnsClass.IN, 1024, Unpooled.wrappedBuffer(IP6_BYTES)));
    }

    @Test
    public void testDecodeAAAARecord() {
        AddressDecoder decoder = AddressDecoder.AAAA_RECORD_DECODER;
        InetAddress address = decoder.decode(
                new DnsResource("netty.io", DnsType.A, DnsClass.IN, 1024, Unpooled.wrappedBuffer(IP6_BYTES)));
        Assert.assertTrue(address instanceof Inet6Address);
        Assert.assertArrayEquals(IP6_BYTES, address.getAddress());
    }

    @Test(expected = DecoderException.class)
    public void testDecodeAAAARecordOctetsMissmatch() {
        AddressDecoder decoder = AddressDecoder.AAAA_RECORD_DECODER;
        decoder.decode(
                new DnsResource("netty.io", DnsType.A, DnsClass.IN, 1024, Unpooled.wrappedBuffer(IP4_BYTES)));
    }
}
