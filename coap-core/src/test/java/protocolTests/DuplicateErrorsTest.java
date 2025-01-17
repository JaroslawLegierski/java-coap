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
package protocolTests;

import static com.mbed.coap.utils.FutureHelpers.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.Method;
import com.mbed.coap.server.CoapServer;
import com.mbed.coap.server.CoapServerBuilder;
import com.mbed.coap.server.RouterService;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import protocolTests.utils.MockCoapTransport;

/**
 * Test for deduplication of error requests
 */
public class DuplicateErrorsTest {
    private CountDownLatch latch;
    CoapServer server;
    private MockCoapTransport.MockClient client;

    @BeforeEach
    public void setUp() throws IOException {
        MockCoapTransport serverTransport = new MockCoapTransport();
        client = serverTransport.client();
        server = CoapServerBuilder.newBuilder().transport(serverTransport)
                .route(RouterService.builder()
                        .get("/failed", __ -> failedFuture(new NullPointerException("failed")))
                )
                .duplicatedCoapMessageCallback(
                        request -> {
                            if (latch != null) {
                                latch.countDown();
                            }
                        })
                .build()
                .start();
    }

    @AfterEach
    public void tearDown() {
        server.stop();
    }

    @Test
    public void testNotFoundResourceCon_get() throws IOException, CoapException, InterruptedException {
        testIt(Method.GET, MessageType.Confirmable, 11, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
        testIt(Method.GET, MessageType.Confirmable, 13, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
    }

    @Test
    public void testNotFoundResourceCon_put() throws IOException, CoapException, InterruptedException {
        testIt(Method.PUT, MessageType.Confirmable, 22, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
        testIt(Method.PUT, MessageType.Confirmable, 25, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
    }

    @Test
    public void testNotFoundResourceCon_post() throws IOException, CoapException, InterruptedException {
        testIt(Method.POST, MessageType.Confirmable, 33, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
        testIt(Method.POST, MessageType.Confirmable, 36, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
    }

    @Test
    public void testNotFoundResourceCon_delete() throws IOException, CoapException, InterruptedException {
        testIt(Method.DELETE, MessageType.Confirmable, 44, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
        testIt(Method.DELETE, MessageType.Confirmable, 47, "/test", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
    }

    @Test
    public void testNotFoundResourceNon_get() throws IOException, CoapException, InterruptedException {
        testIt(Method.GET, MessageType.NonConfirmable, 55, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
        testIt(Method.GET, MessageType.NonConfirmable, 58, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
    }

    @Test
    public void testNotFoundResourceNon_put() throws IOException, CoapException, InterruptedException {
        testIt(Method.PUT, MessageType.NonConfirmable, 66, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
        testIt(Method.PUT, MessageType.NonConfirmable, 69, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
    }

    @Test
    public void testNotFoundResourceNon_post() throws IOException, CoapException, InterruptedException {
        testIt(Method.POST, MessageType.NonConfirmable, 77, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
        testIt(Method.POST, MessageType.NonConfirmable, 70, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
    }

    @Test
    public void testNotFoundResourceNon_delete() throws IOException, CoapException, InterruptedException {
        testIt(Method.DELETE, MessageType.NonConfirmable, 88, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
        testIt(Method.DELETE, MessageType.NonConfirmable, 81, "/test", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
    }

    @Test
    public void testFailedResourceCon_get() throws InterruptedException, CoapException, IOException {
        testIt(Method.GET, MessageType.Confirmable, 110, "/failed", Code.C500_INTERNAL_SERVER_ERROR, MessageType.Acknowledgement, true);
        testIt(Method.GET, MessageType.Confirmable, 114, "/failed", Code.C500_INTERNAL_SERVER_ERROR, MessageType.Acknowledgement, true);
    }

    @Test
    public void testFailedResourceNon_get() throws InterruptedException, CoapException, IOException {
        testIt(Method.GET, MessageType.NonConfirmable, 150, "/failed", Code.C500_INTERNAL_SERVER_ERROR, MessageType.NonConfirmable, false);
        testIt(Method.GET, MessageType.NonConfirmable, 154, "/failed", Code.C500_INTERNAL_SERVER_ERROR, MessageType.NonConfirmable, false);
    }

    @Test
    public void testNoMethodResourceCon_get() throws InterruptedException, CoapException, IOException {
        testIt(Method.POST, MessageType.Confirmable, 110, "/failed", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
        testIt(Method.PUT, MessageType.Confirmable, 114, "/failed", Code.C404_NOT_FOUND, MessageType.Acknowledgement, true);
    }

    @Test
    public void testNoMethodResourceNon_get() throws InterruptedException, CoapException, IOException {
        testIt(Method.POST, MessageType.NonConfirmable, 150, "/failed", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
        testIt(Method.PUT, MessageType.NonConfirmable, 154, "/failed", Code.C404_NOT_FOUND, MessageType.NonConfirmable, false);
    }

    private void testIt(Method reqMethod, MessageType reqType, int reqMsgId, String reqUri, Code expectedRespCode, MessageType expectedRespType, boolean reqAndRespMsgIdMatch) throws IOException, CoapException, InterruptedException {
        CoapPacket req = new CoapPacket(reqMethod, reqType, reqUri, server.getLocalSocketAddress());
        req.setMessageId(reqMsgId);
        testIt(req, expectedRespCode, expectedRespType, reqAndRespMsgIdMatch);
    }

    private void testIt(CoapPacket req, Code expectedRespCode, MessageType expectedRespType, boolean reqAndRespMsgIdMatch) throws IOException, CoapException, InterruptedException {
        latch = new CountDownLatch(1);

        client.send(req);
        CoapPacket resp1 = client.receive();
        System.out.println(resp1);

        client.send(req);
        CoapPacket resp2 = client.receive();
        System.out.println(resp2);
        assertEquals(resp1, resp2);
        if (reqAndRespMsgIdMatch) {
            assertEquals(req.getMessageId(), resp1.getMessageId());
        } else {
            assertNotEquals(req.getMessageId(), resp1.getMessageId());
        }
        assertEquals(expectedRespType, resp1.getMessageType());
        assertEquals(expectedRespCode, resp1.getCode());
        assertTrue(latch.await(1000, TimeUnit.MILLISECONDS));
    }

}
