/**
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
 * SPDX-License-Identifier: Apache-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mbed.coap.server;

import static java.util.concurrent.CompletableFuture.*;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Method;
import com.mbed.coap.utils.Service;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RouterService implements Service<CoapRequest, CoapResponse> {

    private final Map<RequestMatcher, Service<CoapRequest, CoapResponse>> handlers;
    private final List<Entry<RequestMatcher, Service<CoapRequest, CoapResponse>>> prefixedHandlers;

    public static RouteBuilder builder() {
        return new RouteBuilder();
    }

    public final static Service<CoapRequest, CoapResponse> NOT_FOUND_SERVICE = request -> completedFuture(CoapResponse.notFound());

    RouterService(Map<RequestMatcher, Service<CoapRequest, CoapResponse>> handlers) {

        this.handlers = Collections.unmodifiableMap(handlers.entrySet().stream()
                .filter(entry -> !entry.getKey().isPrefixed())
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue))
        );

        this.prefixedHandlers = Collections.unmodifiableList(handlers.entrySet().stream()
                .filter(entry -> entry.getKey().isPrefixed())
                .collect(Collectors.toList())
        );
    }

    @Override
    public CompletableFuture<CoapResponse> apply(CoapRequest request) {
        RequestMatcher requestMatcher = new RequestMatcher(request.getMethod(), request.options().getUriPath());

        return handlers
                .getOrDefault(requestMatcher, findHandler(requestMatcher))
                .apply(request);
    }

    private Service<CoapRequest, CoapResponse> findHandler(RequestMatcher requestMatcher) {
        return prefixedHandlers.stream()
                .filter(e -> e.getKey().matches(requestMatcher))
                .findFirst()
                .map(Entry::getValue)
                .orElse(NOT_FOUND_SERVICE);

    }

    public static class RouteBuilder {
        private final Map<RequestMatcher, Service<CoapRequest, CoapResponse>> handlers = new HashMap<>();

        public RouteBuilder get(String uriPath, Service<CoapRequest, CoapResponse> service) {
            return add(Method.GET, uriPath, service);
        }

        public RouteBuilder post(String uriPath, Service<CoapRequest, CoapResponse> service) {
            return add(Method.POST, uriPath, service);
        }

        public RouteBuilder put(String uriPath, Service<CoapRequest, CoapResponse> service) {
            return add(Method.PUT, uriPath, service);
        }

        public RouteBuilder delete(String uriPath, Service<CoapRequest, CoapResponse> service) {
            return add(Method.DELETE, uriPath, service);
        }

        private RouteBuilder add(Method method, String uriPath, Service<CoapRequest, CoapResponse> service) {
            handlers.put(new RequestMatcher(method, uriPath), service);
            return this;
        }


        public Service<CoapRequest, CoapResponse> build() {
            return new RouterService(handlers);
        }
    }

    static final class RequestMatcher {
        private final Method method;
        private final String uriPath;
        private transient final boolean isPrefixed;

        RequestMatcher(Method method, String uriPath) {
            if (uriPath == null) {
                uriPath = "/";
            }
            this.method = method;
            this.isPrefixed = uriPath.endsWith("*");
            if (isPrefixed) {
                this.uriPath = uriPath.substring(0, uriPath.length() - 1);
            } else {
                this.uriPath = uriPath;
            }
        }

        public boolean isPrefixed() {
            return isPrefixed;
        }

        public boolean matches(RequestMatcher other) {
            return (other.method == method && other.uriPath.startsWith(uriPath));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RequestMatcher that = (RequestMatcher) o;
            return method == that.method && Objects.equals(uriPath, that.uriPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, uriPath);
        }
    }
}
