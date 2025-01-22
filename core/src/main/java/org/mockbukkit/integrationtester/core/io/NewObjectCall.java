package org.mockbukkit.integrationtester.core.io;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;

public record NewObjectCall(long id, String ownerClass) {

    public void send(OutputStream outputStream) {
        try (DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("id", id);
            jsonObject.addProperty("class", ownerClass);
            dataOutputStream.writeUTF(jsonObject.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static NewObjectCall receive(InputStream inputStream) {
        try (DataInputStream dataInputStream = new DataInputStream(inputStream)) {
            JsonObject jsonObject = JsonParser.parseString(dataInputStream.readUTF()).getAsJsonObject();
            return new NewObjectCall(jsonObject.get("id").getAsLong(), jsonObject.get("class").getAsString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
