package org.mockbukkit.integrationtester.core.io;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PacketFactory {

    public static Packet fromStream(InputStream stream, ObjectRegistry registry) {
        try (DataInputStream dataInputStream = new DataInputStream(stream)) {
            short id = dataInputStream.readShort();
            return switch (id) {
                case MethodReturnCall.ID -> MethodReturnCall.receive(dataInputStream, registry);
                case MethodCall.ID -> MethodCall.receive(dataInputStream, registry);
                case FieldFetchCall.ID -> FieldFetchCall.receive(dataInputStream);
                default -> throw new IllegalArgumentException("Unknown packet id: " + id);
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
