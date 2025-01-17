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
package com.mbed.coap.packet;

import static com.mbed.coap.utils.Bytes.*;
import static com.mbed.coap.utils.CoapPacketAssertion.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mbed.coap.exception.CoapException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import protocolTests.utils.CoapPacketBuilder;

public class CoapTcpPacketSerializerTest {

    @Test
    public void deserializeAfterSerializeGivesBackACoapPacketWithSameData() throws CoapException, IOException {
        CoapPacket cp = CoapPacketBuilder.newCoapPacket().token(1234L).code(Code.C204_CHANGED).uriPath("/test").payload("some test payload").build();
        cp.setMessageType(null);

        byte[] rawCp = CoapTcpPacketSerializer.serialize(cp);
        CoapPacket cp2 = CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(rawCp));

        assertArrayEquals(rawCp, CoapTcpPacketSerializer.serialize(cp2));
        assertEquals(Opaque.variableUInt(1234L), cp2.getToken());
        assertEquals(Code.C204_CHANGED, cp2.getCode());
        assertEquals("/test", cp2.headers().getUriPath());
        assertEquals("some test payload", cp2.getPayloadString());

        assertSimilar(cp, cp2);
    }

    @Test
    public void simpleNoPayload() throws CoapException, IOException {
        byte[] simpleBytes = new byte[]{0x01, 0x43, 0x7f};
        CoapPacket simplePacket = CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(simpleBytes));

        assertEquals(Code.C203_VALID, simplePacket.getCode());
        assertEquals(Opaque.decodeHex("7f"), simplePacket.getToken());
        assertEquals(0, simplePacket.getPayload().size());
        assertEquals(null, simplePacket.getMethod());
        assertEquals(0, simplePacket.getMessageId()); // not set
        assertEquals(null, simplePacket.getMessageType());

        byte[] bytes2 = CoapTcpPacketSerializer.serialize(simplePacket);
        assertArrayEquals(simpleBytes, bytes2);
        assertEquals(simplePacket, CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(bytes2)));
    }

    @Test
    public void simpleSmallPayload() throws CoapException, IOException {
        assertSimplePacketSerializationAndDeserilization(Opaque.decodeHex("7f"), Opaque.of("666"));
    }

    @Test
    public void extendedLengthPayload() throws CoapException, IOException {
        assertSimplePacketSerializationAndDeserilization(Opaque.decodeHex("7f"), opaqueOfRandom(57));
    }

    @Test
    public void extendedLengthBigPayload() throws CoapException, IOException {
        assertSimplePacketSerializationAndDeserilization(Opaque.decodeHex("7f"), opaqueOfRandom(666));
    }

    @Test
    public void extendedLengthHugePayload() throws CoapException, IOException {
        assertSimplePacketSerializationAndDeserilization(Opaque.decodeHex("7f"), opaqueOfRandom(65807));
    }

    @Test
    public void bothMethodAndCodeUsed() throws CoapException, IOException {
        CoapPacket cp = new CoapPacket(Method.DELETE, null, "", null);
        cp.setCode(Code.C202_DELETED);

        assertThrows(IllegalStateException.class, () ->
                CoapTcpPacketSerializer.serialize(cp)
        );
    }

    @Test
    public void inputStremException() throws CoapException, IOException {
        InputStream is = Mockito.mock(InputStream.class);
        when(is.read()).thenThrow(new IOException());

        assertThrows(IOException.class, () ->
                CoapTcpPacketSerializer.deserialize(null, is)
        );
    }

    @Test
    public void outputStremException() throws CoapException, IOException {
        OutputStream os = Mockito.mock(OutputStream.class);
        doThrow(new IOException()).when(os).write(any());

        CoapPacket cp = new CoapPacket(null, null, "", null);
        assertThrows(IOException.class, () ->
                CoapTcpPacketSerializer.writeTo(os, cp)
        );
    }

    @Test
    public void coapPacketTest3_overTcp() throws CoapException, IOException {
        CoapPacket cp = new CoapPacket(Method.PUT, null, "", null);
        cp.headers().setUriPath("/test2");
        cp.headers().setLocationPath("");
        cp.setPayload("t�m� on varsin miel??$�");
        byte[] rawCp = CoapTcpPacketSerializer.serialize(cp);
        CoapPacket cp2 = CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(rawCp));

        System.out.println(cp);
        System.out.println(cp2);
        assertArrayEquals(rawCp, CoapTcpPacketSerializer.serialize(cp2));
        assertEquals(Method.PUT, cp2.getMethod());
        assertEquals(null, cp2.getMessageType());
        assertEquals("/test2", cp2.headers().getUriPath());
    }

    @Test
    public void should_fail_to_deserialize_when_missing_data_in_a_stream_with_strict_deserizlise() throws Exception {
        CoapPacket cp = CoapPacketBuilder.newCoapPacket().token(1234L).code(Code.C204_CHANGED).uriPath("/test").payload("some test payload").build();
        cp.setMessageType(null);
        byte[] rawCp = CoapTcpPacketSerializer.serialize(cp);


        for (int i = 0; i < rawCp.length; i++) {
            byte[] tooShortRawCp = Arrays.copyOf(rawCp, i);

            assertThatThrownBy(
                    () -> CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(tooShortRawCp))
            ).isExactlyInstanceOf(EOFException.class);
        }
    }

    @Test
    public void coapOverTcpSignaling() throws CoapException, IOException {
        CoapPacket cp = new CoapPacket(null);
        cp.setMessageType(null);
        SignallingHeaderOptions headers = new SignallingHeaderOptions();
        cp.setHeaderOptions(headers);

        cp.setCode(Code.C701_CSM);
        SignalingOptions sign = new SignalingOptions();
        sign.setMaxMessageSize(7);
        sign.setBlockWiseTransfer(true);
        headers.putSignallingOptions(sign);
        cp.headers().setMaxAge(100L);

        byte[] rawCp = CoapTcpPacketSerializer.serialize(cp);
        CoapPacket cp2 = CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(rawCp));

        System.out.println(cp);
        System.out.println(cp2);
        assertArrayEquals(rawCp, CoapTcpPacketSerializer.serialize(cp2));
        assertEquals(Code.C701_CSM, cp2.getCode());
        assertEquals(null, cp2.getMessageType());
        assertEquals(7, ((SignallingHeaderOptions) cp2.headers()).toSignallingOptions(Code.C701_CSM).getMaxMessageSize().intValue());
        assertTrue(((SignallingHeaderOptions) cp2.headers()).toSignallingOptions(Code.C701_CSM).getBlockWiseTransfer());
        assertEquals(100, cp2.headers().getMaxAgeValue());

        assertSimilar(cp, cp2);
    }

    @Test
    public void should_return_empty_optional_if_not_enough_data_to_deserialize() throws CoapException, IOException {
        CoapPacket cp = CoapPacketBuilder.newCoapPacket().token(1234L).code(Code.C204_CHANGED).uriPath("/test").payload("some test payload").build();
        cp.setMessageType(null);

        byte[] rawCp = CoapTcpPacketSerializer.serialize(cp);

        for (int i = 0; i < rawCp.length; i++) {
            Optional<CoapPacket> cp2 = CoapTcpPacketSerializer.deserializeIfEnoughData(null, new ByteArrayInputStream(rawCp, 0, i));
            assertFalse(cp2.isPresent());
        }

        Optional<CoapPacket> cp2 = CoapTcpPacketSerializer.deserializeIfEnoughData(null, new ByteArrayInputStream(rawCp));
        assertTrue(cp2.isPresent());
    }

    @Test
    public void should_throw_if_not_enough_data_to_deserialize() throws CoapException, IOException {
        CoapPacket cp = CoapPacketBuilder.newCoapPacket().token(1234L).code(Code.C204_CHANGED).uriPath("/test").payload("some test payload").build();

        byte[] rawCp = CoapTcpPacketSerializer.serialize(cp);

        assertThrows(EOFException.class, () ->
                CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(rawCp, 0, rawCp.length - 2))
        );
    }


    private void assertSimplePacketSerializationAndDeserilization(Opaque token, Opaque payload) throws CoapException, IOException {
        CoapPacket cp = new CoapPacket(null, null, "", null);
        cp.setToken(token);
        cp.setPayload(payload);

        byte[] rawCp = CoapTcpPacketSerializer.serialize(cp);
        CoapPacket cp2 = CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(rawCp));

        assertArrayEquals(rawCp, CoapTcpPacketSerializer.serialize(cp2));

        assertEquals(null, cp2.getCode());
        assertEquals(token, cp2.getToken());
        assertEquals(payload.size(), cp2.getPayload().size());
        assertEquals(payload, cp2.getPayload());
        assertEquals(null, cp2.getMethod());
        assertEquals(0, cp2.getMessageId());
        assertEquals(null, cp2.getMessageType());
    }


    @Test
    public void testLen() throws IOException, CoapException {
        ByteArrayOutputStream os = createRawPacketHeader(0, 0, null, Code.C203_VALID.getCoapCode(), null);
        CoapPacket pkt = CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(os.toByteArray()));

        assertTrue(pkt.getToken().isEmpty());
        assertEquals(0, pkt.getPayload().size());


        os = createRawPacketHeader(12, 0, null, Code.C205_CONTENT.getCoapCode(), null);
        os.write(0xFF);
        os.write("payload8901".getBytes());

        pkt = CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(os.toByteArray()));

        assertEquals(0, pkt.getToken().size());
        assertEquals(pkt.getPayload(), Opaque.of("payload8901"));


        os = createRawPacketHeader(13, 0, new byte[]{0}, Code.C205_CONTENT.getCoapCode(), null);
        os.write(0xFF);
        os.write("payload89012".getBytes());

        pkt = CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(os.toByteArray()));

        assertEquals(0, pkt.getToken().size());
        assertEquals(pkt.getPayload(), Opaque.of("payload89012"));


        os = createRawPacketHeader(13, 0, new byte[]{2}, Code.C205_CONTENT.getCoapCode(), null);
        os.write(0xFF);
        os.write("payload8901234".getBytes());

        pkt = CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(os.toByteArray()));

        assertEquals(0, pkt.getToken().size());
        assertEquals(pkt.getPayload(), Opaque.of("payload8901234"));


        BasicHeaderOptions opts = new BasicHeaderOptions();
        opts.setUriPath("/aaa/bbb");

        ByteArrayOutputStream optSerializedStream = new ByteArrayOutputStream();
        opts.serialize(optSerializedStream);

        os = createRawPacketHeader(13, 0, new byte[]{(byte) optSerializedStream.size()}, Code.C205_CONTENT.getCoapCode(), null);
        os.write(optSerializedStream.toByteArray());
        os.write(0xFF);
        os.write("payload89012".getBytes());

        pkt = CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(os.toByteArray()));

        assertEquals(0, pkt.getToken().size());
        assertEquals(pkt.getPayload(), Opaque.of("payload89012"));
        assertEquals("/aaa/bbb", pkt.headers().getUriPath());


        // incorrect length (less than written payload), read only part of payload
        // we can't distinguish end of packet over declared length
        os = createRawPacketHeader(13, 0, new byte[]{(byte) (optSerializedStream.size() - 2)}, Code.C205_CONTENT.getCoapCode(), null);
        os.write(optSerializedStream.toByteArray());
        os.write(0xFF);
        os.write("payload89012".getBytes());

        byte[] dataRaw1 = os.toByteArray();

        pkt = CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(dataRaw1));

        assertEquals(0, pkt.getToken().size());
        assertEquals(pkt.getPayload(), Opaque.of("payload890"));
        assertEquals("/aaa/bbb", pkt.headers().getUriPath());
    }

    @Test
    public void testInvalidLen() throws IOException, CoapException {

        ByteArrayOutputStream os = createRawPacketHeader(15, 0, new byte[]{0x10, 0x10}, Code.C201_CREATED.getCoapCode(), null);
        byte[] rawData = os.toByteArray();
        assertThatThrownBy(() ->
                CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(rawData))
        ).isExactlyInstanceOf(EOFException.class);


        BasicHeaderOptions opts = new BasicHeaderOptions();
        opts.setUriPath("/aaa/bbb");

        ByteArrayOutputStream tmpStream = new ByteArrayOutputStream();
        opts.serialize(tmpStream);

        os = createRawPacketHeader(13, 0, new byte[]{(byte) (tmpStream.size() + 1)}, Code.C205_CONTENT.getCoapCode(), null);
        os.write(tmpStream.toByteArray());
        os.write(0xFF);
        os.write("payload89012".getBytes());

        byte[] rawData1 = os.toByteArray();

        assertThatThrownBy(() ->
                CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(rawData1))
        ).isExactlyInstanceOf(EOFException.class);
    }

    @Test
    public void testToken() throws IOException, CoapException {
        ByteArrayOutputStream os = createRawPacketHeader(0, 8, null, Code.C201_CREATED.getCoapCode(), new byte[]{0, 1, 2, 3, 4, 5, 6, 7});
        CoapPacket pkt = CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(os.toByteArray()));

        assertEquals(Opaque.decodeHex("0001020304050607"), pkt.getToken());


        os = createRawPacketHeader(0, 9, null, Code.C201_CREATED.getCoapCode(), new byte[]{0, 1, 2, 3, 4, 5, 6, 7, 8});
        byte[] rawData = os.toByteArray();
        assertThatThrownBy(() ->
                CoapTcpPacketSerializer.deserialize(null, new ByteArrayInputStream(rawData))
        )
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Token length invalid, should be in range 0..8");
    }


    private ByteArrayOutputStream createRawPacketHeader(int lenCode, int tkl, byte[] extLenBytes, int code, byte[] token) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        int len1Tkl = (lenCode & 0x0F) << 4 | (tkl & 0x0F);
        os.write(len1Tkl);
        if (extLenBytes != null && extLenBytes.length != 0) {
            os.write(extLenBytes);
        }
        os.write(code);
        if (token != null && token.length != 0) {
            os.write(token);
        }
        return os;
    }

}
