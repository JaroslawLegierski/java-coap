/*
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

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.packet.CoapResponse.of;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.Opaque;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CriticalOptionVerifierTest {

    private final CriticalOptionVerifier filter = new CriticalOptionVerifier();

    @Test
    void shouldReturnBadOptionWhenUnrecognizedCriticalOption() {
        CoapRequest req = get("/test").options(o -> o.put(1001, Opaque.of("foo")));

        CompletableFuture<CoapResponse> resp = filter.apply(req, null);

        assertEquals(of(Code.C402_BAD_OPTION), resp.join());
    }
}