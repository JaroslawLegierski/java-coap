/*
 * Copyright (C) 2022 java-coap contributors (https://github.com/open-coap/java-coap)
 * Copyright (C) 2011-2021 ARM Limited. All rights reserved.
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
package com.mbed.coap.transport.udp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static protocolTests.utils.CoapPacketBuilder.*;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.CoapReceiver;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import protocolTests.utils.CoapPacketBuilder;


public class DatagramSocketTransportTest {

    public static final CoapPacket COAP_PACKET = CoapPacketBuilder.newCoapPacket(LOCAL_5683).get().uriPath("/test").mid(1).build();

    private static DatagramSocketTransport createDatagramSocketTransport() {
        return new DatagramSocketTransport(0);
    }

    @Test
    public void initializingWithStateException() throws IOException {
        DatagramSocketTransport trans = createDatagramSocketTransport();
        try {
            try {
                trans.sendPacket0(COAP_PACKET);
                fail();
            } catch (Exception e) {
                assertTrue(e instanceof IllegalStateException);
            }

            trans.start(mock(CoapReceiver.class));

        } finally {
            trans.stop();
        }
    }

    @Test
    public void initializeWithProvidedDatagramSocket() throws Exception {

        DatagramSocketAdapter udpSocket = new DatagramSocketAdapter(0);
        DatagramSocketTransport datagramSocketTransport = new DatagramSocketTransport(udpSocket, null);

        datagramSocketTransport.start(mock(CoapReceiver.class));
        assertTrue(udpSocket.isBound());
        assertFalse(udpSocket.isClosed());

        assertEquals(udpSocket.getLocalPort(), datagramSocketTransport.getLocalSocketAddress().getPort());

        datagramSocketTransport.stop();
        assertTrue(udpSocket.isClosed());
    }

    @Test
    public void continueReadingWhenAfterReadingTimeout() throws Exception {
        DatagramSocketTransport datagramSocketTransport = new DatagramSocketTransport(new InetSocketAddress(0), mock(Executor.class));

        //start
        datagramSocketTransport.start(mock(CoapReceiver.class));
        assertTrue(datagramSocketTransport.readingLoop(mock(CoapReceiver.class)));
        assertTrue(datagramSocketTransport.readingLoop(mock(CoapReceiver.class)));

        //stop
        datagramSocketTransport.stop();
        assertFalse(datagramSocketTransport.readingLoop(mock(CoapReceiver.class)));
    }
}
