package org.mockbukkit.integrationtester.core.io;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mockbukkit.integrationtester.core.util.ObjectUtil;

import javax.annotation.Nullable;
import java.io.*;

public record FieldFetchCall(String className, @Nullable Object owner, String field) implements Packet {

    public static final short ID = 0;

    public void send(OutputStream outputStream, ObjectRegistry registry) {
        try (DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            dataOutputStream.writeShort(ID);
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("field", field);
            jsonObject.addProperty("class", className);
            if (owner != null) {
                jsonObject.add("owner", ObjectUtil.serializeData(owner, registry));
            }
            dataOutputStream.writeUTF(jsonObject.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static FieldFetchCall receive(InputStream inputStream, ObjectRegistry registry) {
        try (DataInputStream dataInputStream = new DataInputStream(inputStream)) {
            JsonObject jsonObject = JsonParser.parseString(dataInputStream.readUTF()).getAsJsonObject();
            return new FieldFetchCall(jsonObject.get("class").getAsString(), jsonObject.has("owner") ? ObjectUtil.readObject(jsonObject.get("owner"), registry) : null, jsonObject.get("field").getAsString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
