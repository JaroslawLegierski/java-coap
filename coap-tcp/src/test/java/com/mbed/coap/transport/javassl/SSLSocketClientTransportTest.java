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
package com.mbed.coap.transport.javassl;

import static com.mbed.coap.packet.CoapRequest.*;
import static com.mbed.coap.server.CoapServerBuilderForTcp.*;
import static org.awaitility.Awaitility.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mbed.coap.client.CoapClient;
import com.mbed.coap.client.CoapClientBuilder;
import com.mbed.coap.client.CoapClientBuilderForTcp;
import com.mbed.coap.server.CoapServer;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

public class SSLSocketClientTransportTest {

    public static final char[] SECRET = "secret".toCharArray();
    private static KeyStore SRV_KS = SSLUtils.ksFrom("/test-server.jks", SECRET);
    private static KeyStore CLI_KS = SSLUtils.ksFrom("/test-client.jks", SECRET);
    SSLContext srvSslContext = SSLUtils.sslContext(SRV_KS, SECRET);
    SSLContext clientSslContext = SSLUtils.sslContext(CLI_KS, SECRET);


    @Test
    public void successfulConnection() throws Exception {

        CoapServer srv = newBuilderForTcp()
                .transport(new SingleConnectionSSLSocketServerTransport(srvSslContext, 0, CoapSerializer.TCP))
                .build().start();


        InetSocketAddress serverAdr = new InetSocketAddress("localhost", srv.getLocalSocketAddress().getPort());
        CoapClient client = CoapClientBuilder.clientFor(serverAdr,
                newBuilderForTcp().transport(new SSLSocketClientTransport(serverAdr, clientSslContext.getSocketFactory(), CoapSerializer.TCP, false)).build().start()
        );

        //        assertNotNull(client.ping().get());
        assertNotNull(client.sendSync(get("/test")));


        client.close();
        srv.stop();

    }

    @Test
    public void successful_reconnection() throws Exception {

        CoapServer srv = newBuilderForTcp()
                .transport(new SingleConnectionSSLSocketServerTransport(srvSslContext, 0, CoapSerializer.TCP))
                .build().start();


        int serverPort = srv.getLocalSocketAddress().getPort();
        InetSocketAddress serverAdr = new InetSocketAddress("localhost", serverPort);
        CoapClient client = CoapClientBuilderForTcp.clientFor(serverAdr,
                newBuilderForTcp().transport(new SSLSocketClientTransport(serverAdr, clientSslContext.getSocketFactory(), CoapSerializer.TCP, true)).build().start()
        );

        assertNotNull(client.ping().get());

        //re-start server
        srv.stop();
        System.out.println("----- STOPPED");
        srv = newBuilderForTcp()
                .transport(new SingleConnectionSSLSocketServerTransport(srvSslContext, serverPort, CoapSerializer.TCP))
                .build().start();


        //eventually, reconnected
        await().ignoreExceptions().untilAsserted(() -> {
                    assertNotNull(client.ping().get());
                }
        );

        client.close();
        srv.stop();

    }
}