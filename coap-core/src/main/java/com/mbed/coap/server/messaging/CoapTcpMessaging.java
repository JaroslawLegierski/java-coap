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
package com.mbed.coap.server.messaging;

import static com.mbed.coap.utils.FutureHelpers.*;
import com.mbed.coap.exception.CoapException;
import com.mbed.coap.packet.CoapPacket;
import com.mbed.coap.packet.CoapRequest;
import com.mbed.coap.packet.CoapResponse;
import com.mbed.coap.packet.Code;
import com.mbed.coap.packet.MessageType;
import com.mbed.coap.packet.SeparateResponse;
import com.mbed.coap.packet.SignalingOptions;
import com.mbed.coap.transport.CoapTransport;
import com.mbed.coap.transport.TransportContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Coap server for reliable transport (draft-ietf-core-coap-tcp-tls-09)
 */
public class CoapTcpMessaging extends CoapMessaging {
    private static final Logger LOGGER = LoggerFactory.getLogger(CoapTcpMessaging.class);

    private final ConcurrentMap<DelayedTransactionId, CompletableFuture<CoapPacket>> transactions = new ConcurrentHashMap<>();
    private final CoapTcpCSMStorage csmStorage;
    private final CoapTcpCSM ownCapability;

    public CoapTcpMessaging(CoapTransport coapTransport, CoapTcpCSMStorage csmStorage, boolean useBlockWiseTransfer, int maxMessageSize) {
        super(coapTransport);
        this.csmStorage = csmStorage;
        this.ownCapability = new CoapTcpCSM(maxMessageSize, useBlockWiseTransfer);
    }

    @Override
    protected boolean handlePing(CoapPacket packet) {
        if (packet.getCode() == null && packet.getMethod() == null) {
            LOGGER.debug("CoAP ping received.");
            // ignoring normal CoAP ping, according to https://tools.ietf.org/html/draft-ietf-core-coap-tcp-tls-09#section-5.4
            return true;
        }

        return handleSignal(packet);
    }

    @Override
    protected void handleNotProcessedMessage(CoapPacket packet) {
        LOGGER.warn("Can not process CoAP message [{}]", packet);
    }

    private boolean handleSignal(CoapPacket packet) {
        if (packet.getCode() == null || !packet.getCode().isSignaling()) {
            return false;
        }

        if (packet.getCode() == Code.C701_CSM) {
            SignalingOptions signalingOpts = packet.headers().toSignallingOptions(packet.getCode());
            CoapTcpCSM remoteCapabilities = CoapTcpCSM.BASE;
            if (signalingOpts != null) {
                Long maxMessageSize = signalingOpts.getMaxMessageSize();
                Boolean blockWiseTransferBERT = signalingOpts.getBlockWiseTransfer();
                remoteCapabilities = CoapTcpCSM.BASE.withNewOptions(maxMessageSize, blockWiseTransferBERT);
            }
            csmStorage.put(packet.getRemoteAddress(), CoapTcpCSM.min(ownCapability, remoteCapabilities));

        } else if (packet.getCode() == Code.C702_PING) {
            CoapPacket pongResp = new CoapPacket(Code.C703_PONG, MessageType.Acknowledgement, packet.getRemoteAddress());
            pongResp.setToken(packet.getToken());
            sendPacket(pongResp, packet.getRemoteAddress(), TransportContext.EMPTY);
        } else if (packet.getCode() == Code.C703_PONG) {
            //handle this as reply
            return false;
        } else if (packet.getCode() == Code.C705_ABORT) {
            onDisconnected(packet.getRemoteAddress());

        } else {
            LOGGER.debug("[{}] Ignored signal message: {}", packet.getRemoteAddrString(), packet.getCode());
        }

        return true;
    }

    @Override
    public CompletableFuture<CoapResponse> send(CoapRequest req) {
        CoapPacket packet;
        if (req.isPing()) {
            packet = new CoapPacket(Code.C702_PING, null, req.getPeerAddress());
        } else {
            packet = CoapPacket.from(req);
        }

        if (verifyPayloadSize(packet)) {
            return failedFuture(new CoapException("Request payload size is too big and no block transfer support is enabled for " + packet.getRemoteAddress() + ": " + packet.getPayload().size()));
        }

        DelayedTransactionId transId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
        CompletableFuture<CoapPacket> promise = new CompletableFuture<>();
        transactions.put(transId, promise);


        sendPacket(packet, packet.getRemoteAddress(), req.getTransContext())
                .whenComplete((wasSent, maybeError) -> {
                    if (maybeError != null) {
                        removeTransactionExceptionally(transId, (Exception) maybeError);
                    }
                });

        return promise.thenApply(CoapPacket::toCoapResponse);

    }

    @Override
    public CompletableFuture<Boolean> send(final SeparateResponse resp) {
        CoapPacket packet = CoapPacket.from(resp);

        if (verifyPayloadSize(packet)) {
            return failedFuture(new CoapException("Request payload size is too big and no block transfer support is enabled for " + packet.getRemoteAddress() + ": " + packet.getPayload().size()));
        }

        return sendPacket(packet, resp.getPeerAddress(), resp.getTransContext());
    }

    @Override
    public void sendResponse(CoapPacket request, CoapPacket response, TransportContext transContext) {
        sendPacket(response, response.getRemoteAddress(), transContext);
    }

    @Override
    protected boolean handleDelayedResponse(CoapPacket packet) {
        return false;
    }

    @Override
    protected boolean handleResponse(CoapPacket packet) {
        DelayedTransactionId transId = new DelayedTransactionId(packet.getToken(), packet.getRemoteAddress());
        CompletableFuture<CoapPacket> promise = transactions.remove(transId);

        if (promise != null) {
            promise.complete(packet);
            return true;
        }

        return false;
    }

    @Override
    protected void handleObservation(CoapPacket packet, TransportContext transContext) {
        SeparateResponse obs = packet.toCoapResponse().toSeparate(packet.getToken(), packet.getRemoteAddress(), transContext);
        observationHandler.apply(obs);
    }

    @Override
    public void onConnected(InetSocketAddress remoteAddress) {
        CoapPacket packet = new CoapPacket(remoteAddress);
        packet.setCode(Code.C701_CSM);

        packet.headers().putSignallingOptions(
                SignalingOptions.capabilities(ownCapability.getMaxMessageSizeInt(), ownCapability.isBlockTransferEnabled())
        );
        LOGGER.info("[" + remoteAddress + "] CoAP sent [" + packet.toString(false, false, false, true) + "]");
        coapTransporter.sendPacket(packet, remoteAddress, TransportContext.EMPTY);

        super.onConnected(remoteAddress);
    }

    @Override
    public void onDisconnected(InetSocketAddress remoteAddress) {
        csmStorage.remove(remoteAddress);

        Set<DelayedTransactionId> trans = transactions.keySet();
        for (DelayedTransactionId transId : trans) {
            if (transId.hasRemoteAddress(remoteAddress)) {
                removeTransactionExceptionally(transId, new IOException("Socket closed"));
            }
        }

        super.onDisconnected(remoteAddress);
    }

    private void removeTransactionExceptionally(DelayedTransactionId transId, Exception error) {
        CompletableFuture<CoapPacket> promise = transactions.remove(transId);
        if (promise != null) {
            promise.completeExceptionally(error);
        }
    }

    private boolean verifyPayloadSize(CoapPacket packet) {
        int payloadLen = packet.getPayload().size();
        int maxMessageSize = csmStorage.getOrDefault(packet.getRemoteAddress()).getMaxMessageSizeInt();

        return payloadLen > maxMessageSize;
    }

    @Override
    protected void stop0() {
        // no additional stop hooks
    }

}