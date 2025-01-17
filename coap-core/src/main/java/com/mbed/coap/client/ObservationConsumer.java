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
package com.mbed.coap.client;

import com.mbed.coap.packet.CoapResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ObservationConsumer {

    public static void consumeFrom(Supplier<CompletableFuture<CoapResponse>> supplier, Function<CoapResponse, Boolean> consumer) {
        if (supplier == null) {
            return;
        }

        final Consumer<CoapResponse> obsConsumer = new Consumer<CoapResponse>() {
            @Override
            public void accept(CoapResponse obs) {
                boolean more = consumer.apply(obs);
                if (more && obs.getCode().getHttpCode() < 299) {
                    supplier.get().thenAccept(this);
                }
            }
        };

        supplier.get().thenAccept(obsConsumer);
    }

}
