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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.resolver.DnsResolver;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.List;

public interface AdvancedDnsResolver extends DnsResolver {
    Future<Inet4Address> lookup4(String name);
    Future<Inet4Address> lookup4(String name, Promise<Inet4Address> promise);

    Future<List<Inet4Address>> lookup4All(String name);
    Future<List<Inet4Address>> lookup4All(String name, Promise<List<Inet4Address>> promise);

    Future<Inet6Address> lookup6(String name);
    Future<Inet6Address> lookup6(String name, Promise<Inet6Address> promise);

    Future<List<Inet6Address>> lookup6All(String name);
    Future<List<Inet6Address>> lookup6All(String name, Promise<List<Inet6Address>> promise);

    Future<List<MailExchangerRecord>> lookupMx(String name);
    Future<List<MailExchangerRecord>> lookupMx(String name, Promise<List<MailExchangerRecord>> promise);

    Future<List<ServiceRecord>> lookupSrv(String name);
    Future<List<ServiceRecord>> lookupSrv(String name, Promise<List<ServiceRecord>> promise);

    Future<List<String>> lookupCname(String name);
    Future<List<String>> lookupCname(String name, Promise<List<String>> promise);

    Future<List<String>> lookupNs(String name);
    Future<List<String>> lookupNs(String name, Promise<List<String>> promise);

    Future<String> lookupPtr(String name);
    Future<String> lookupPtr(String name, Promise<String> promise);

    // TODO: Add stuff for TXT
}
