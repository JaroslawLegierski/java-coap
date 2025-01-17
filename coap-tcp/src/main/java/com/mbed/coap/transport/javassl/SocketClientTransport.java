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

import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.transport.BlockingCoapTransport;
import com.mbed.coap.transport.CoapReceiver;
import com.mbed.coap.transport.TransportExecutors;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executor;
import javax.net.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocketClientTransport extends BlockingCoapTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(SSLSocketClientTransport.class);

    protected final InetSocketAddress destination;
    protected OutputStream outputStream;
    protected InputStream inputStream;
    protected Socket socket;
    private final Executor readingWorker;
    protected final SocketFactory socketFactory;
    private final CoapSerializer serializer;
    private final boolean autoReconnect;

    public SocketClientTransport(InetSocketAddress destination, SocketFactory socketFactory, CoapSerializer serializer, boolean autoReconnect) {
        this(destination, socketFactory, serializer, autoReconnect, TransportExecutors.newWorker("client-reader"));
    }

    public SocketClientTransport(InetSocketAddress destination, SocketFactory socketFactory, CoapSerializer serializer, boolean autoReconnect, Executor readingWorker) {
        this.destination = destination;
        this.socketFactory = socketFactory;
        this.serializer = serializer;
        this.autoReconnect = autoReconnect;
        this.readingWorker = readingWorker;
    }

    @Override
    public void start(CoapReceiver coapReceiver) throws IOException {
        start((CoapTcpReceiver) coapReceiver);
    }

    private void start(CoapTcpReceiver coapReceiver) throws IOException {
        connect(coapReceiver);

        TransportExecutors.loop(readingWorker, () -> loopReading(coapReceiver));
    }

    protected void connect(CoapTcpReceiver coapReceiver) throws IOException {
        socket = socketFactory.createSocket(destination.getAddress(), destination.getPort());

        synchronized (this) {
            outputStream = new BufferedOutputStream(socket.getOutputStream());
        }
        inputStream = new BufferedInputStream(socket.getInputStream(), 2048);

        coapReceiver.onConnected((InetSocketAddress) socket.getRemoteSocketAddress());
    }

    private boolean loopReading(CoapTcpReceiver coapReceiver) {
        try {
            if (socket.isClosed()) {
                waitBeforeReconnection();
                LOGGER.debug("reconnecting to " + destination);
                connect(coapReceiver);
            }

            if (!socket.isClosed()) {
                try {
                    final CoapPacket coapPacket = serializer.deserialize(inputStream, ((InetSocketAddress) socket.getRemoteSocketAddress()));
                    coapReceiver.handle(coapPacket);
                } catch (CoapException e) {
                    if (e.getCause() != null && e.getCause() instanceof IOException) {
                        throw ((IOException) e.getCause());
                    }
                    LOGGER.warn("Closing socket connection, due to parsing error: " + e.getMessage());
                    socket.close();
                } catch (EOFException ex) {
                    socket.close();
                }
            }
        } catch (SocketTimeoutException ex) {
            return true;
        } catch (Exception ex) {
            if (!(ex.getMessage() != null && ex.getMessage().startsWith("Socket closed"))) {
                LOGGER.error(ex.toString());
            }
        }
        if (socket.isClosed()) {
            coapReceiver.onDisconnected(destination);
        }

        return autoReconnect || !socket.isClosed();
    }

    protected void waitBeforeReconnection() throws InterruptedException {
        Thread.sleep(100);
    }

    @Override
    public synchronized void sendPacket0(CoapPacket coapPacket) throws CoapException, IOException {
        InetSocketAddress adr = coapPacket.getRemoteAddress();
        if (!adr.equals(this.destination)) {
            throw new IllegalStateException("No connection with: " + adr);
        }
        serializer.serialize(outputStream, coapPacket);
        outputStream.flush();
    }

    @Override
    public InetSocketAddress getLocalSocketAddress() {
        return ((InetSocketAddress) socket.getLocalSocketAddress());
    }

    @Override
    public void stop() {
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            TransportExecutors.shutdown(readingWorker);
        }
    }
}
