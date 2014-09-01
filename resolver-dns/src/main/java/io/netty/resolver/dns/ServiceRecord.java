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


/**
 * Represents an SRV (service) record, which contains the location, or hostname
 * and port, of servers for specified services. For example, a service "http"
 * may be running on the server "example.com" on port 80.
 */
public final class ServiceRecord {

    private final int priority;
    private final int weight;
    private final int port;
    private final String name;
    private final String protocol;
    private final String service;
    private final String target;

    /**
     * Constructs an SRV (service) record.
     *
     * @param priority
     *            relative priority of this service, range 0-65535 (lower is
     *            higher priority)
     * @param weight
     *            determines how often multiple services will be used in the
     *            event they have the same priority (greater weight means
     *            service is used more often)
     * @param port
     *            the port for the service
     * @param target
     *            the name of the host for the service
     */
    ServiceRecord(String service, String protocol, String name, int priority, int weight, int port, String target) {
        this.service = service;
        this.protocol = protocol;
        this.name = name;
        this.priority = priority;
        this.weight = weight;
        this.port = port;
        this.target = target;
    }

    /**
     * Returns the priority for this service record.
     */
    public int priority() {
        return priority;
    }

    /**
     * Returns the weight of this service record.
     */
    public int weight() {
        return weight;
    }

    /**
     * Returns the port the service is running on.
     */
    public int port() {
        return port;
    }

    /**
     * Returns the name for the server being queried.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the protocol for the service being queried (i.e. "_tcp").
     */
    public String protocol() {
        return protocol;
    }

    /**
     * Returns the service's name (i.e. "_http").
     */
    public String service() {
        return service;
    }

    /**
     * Returns the name of the host for the service.
     */
    public String target() {
        return target;
    }
}
