package org.mockbukkit.integrationtester.core.io;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.mockbukkit.integrationtester.core.util.ObjectUtil;

import java.io.*;

public record MethodCall(String methodName, String owner, Object object, Object... parameters) implements Packet{

    public static final short ID = 1;

    public void send(OutputStream outputStream, ObjectRegistry objectRegistry) throws IOException {
        try (DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            dataOutputStream.writeShort(ID);
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("method", methodName);
            jsonObject.addProperty("owner", owner);
            if (object != null) {
                long objectId = objectRegistry.getKey(object).orElseThrow(() -> new IllegalStateException("Object not found: " + object));
                jsonObject.addProperty("objectId", objectId);
            }
            JsonArray parametersJsom = new JsonArray();
            for (Object parameter : parameters) {
                parametersJsom.add(ObjectUtil.serializeData(parameter, objectRegistry));
            }
            jsonObject.add("parameters", parametersJsom);
            String jsonString = jsonObject.toString();
            dataOutputStream.writeUTF(jsonString);
        }
    }

    public static MethodCall receive(InputStream inputStream, ObjectRegistry objectRegistry) throws IOException {
        try (DataInputStream dataInputStream = new DataInputStream(inputStream)) {
            String string = dataInputStream.readUTF();
            JsonObject jsonObject = JsonParser.parseString(string).getAsJsonObject();
            String methodName = jsonObject.get("method").getAsString();
            String owner = jsonObject.get("owner").getAsString();

            Object object;
            if (jsonObject.has("objectId")) {
                object = objectRegistry.getObject(jsonObject.get("objectId").getAsLong());
            } else {
                object = null;
            }
            Object[] parametersJsom = ObjectUtil.readArray(jsonObject.get("parameters").getAsJsonArray(), objectRegistry);
            return new MethodCall(methodName, owner, object, parametersJsom);
        }
    }


}
