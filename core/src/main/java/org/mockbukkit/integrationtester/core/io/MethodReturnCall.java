package org.mockbukkit.integrationtester.core.io;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.mockbukkit.integrationtester.core.util.ObjectUtil;

import java.io.*;

public record MethodReturnCall(Object value) implements Packet {

    public static final short ID = 2;

    public void send(OutputStream outputStream, ObjectRegistry objectRegistry) {
        try (DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            dataOutputStream.writeShort(ID);
            JsonElement jsonElement = ObjectUtil.serializeData(value, objectRegistry);
            dataOutputStream.writeUTF(jsonElement.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static MethodReturnCall receive(InputStream inputStream, ObjectRegistry objectRegistry) {
        try (DataInputStream dataInputStream = new DataInputStream(inputStream)) {
            JsonElement jsonElement = JsonParser.parseString(dataInputStream.readUTF());
            return new MethodReturnCall(ObjectUtil.readObject(jsonElement, objectRegistry));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
