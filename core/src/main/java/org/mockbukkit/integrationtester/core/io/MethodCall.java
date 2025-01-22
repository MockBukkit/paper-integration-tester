package org.mockbukkit.integrationtester.core.io;

import com.google.common.primitives.Primitives;
import com.google.gson.*;

import java.io.*;
import java.util.*;

public record MethodCall(String methodName, String owner, Object object, Object... parameters) {

    public void send(OutputStream outputStream, ObjectRegistry objectRegistry) throws IOException {
        try (DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("method", methodName);
            jsonObject.addProperty("owner", owner);
            long objectId = objectRegistry.getKey(object).orElseThrow(() -> new IllegalStateException("Object not found: " + object));
            jsonObject.addProperty("objectId", objectId);
            JsonArray parametersJsom = new JsonArray();
            for (Object parameter : parameters) {
                insertData(parameter, parametersJsom, objectRegistry);
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
            Object object = objectRegistry.getObject(jsonObject.get("objectId").getAsLong());
            Object[] parametersJsom = readArray(jsonObject.get("parameters").getAsJsonArray(), objectRegistry);
            return new MethodCall(methodName, owner, object, parametersJsom);
        }
    }

    private static Object[] readArray(JsonArray jsonElements, ObjectRegistry objectRegistry) {
        List<Object> objectList = new ArrayList<>();
        for (JsonElement element : jsonElements) {
            JsonObject jsonObject = element.getAsJsonObject();
            objectList.add(switch (jsonObject.get("type").getAsString()) {
                case "marked" -> objectRegistry.getObject(jsonObject.get("objectId").getAsLong());
                case "array" -> readArray(jsonObject.get("elements").getAsJsonArray(), objectRegistry);
                case "collection" ->
                        toCollection(readArray(jsonObject.get("elements").getAsJsonArray(), objectRegistry), jsonObject.get("class_type").getAsString());
                case "primitive" -> readPrimitive((JsonPrimitive) jsonObject.get("value"), jsonObject.get("class_type").getAsString());
                default ->
                        throw new IllegalStateException("Unexpected type: " + element.getAsJsonObject().get("type").getAsString());
            });
        }
        return objectList.toArray();
    }

    private static Object readPrimitive(JsonPrimitive value, String classType) {
        return switch (classType) {
            case "java.lang.Boolean" -> value.getAsBoolean();
            case "java.lang.Byte" -> value.getAsByte();
            case "java.lang.Short" -> value.getAsShort();
            case "java.lang.Integer" -> value.getAsInt();
            case "java.lang.Long" -> value.getAsLong();
            case "java.lang.Float" -> value.getAsFloat();
            case "java.lang.Double" -> value.getAsDouble();
            case "java.lang.Character" -> value.getAsCharacter();
            case "java.lang.String" -> value.getAsString();
            default -> throw new IllegalStateException("Unexpected value: " + classType);
        };
    }

    private static Collection<Object> toCollection(Object[] objects, String classType) {
        try {
            Class<?> clazz = Class.forName(classType);
            Collection<Object> collection;
            if (List.class.isAssignableFrom(clazz)) {
                collection = new ArrayList<>();
            } else if (Set.class.isAssignableFrom(clazz)) {
                collection = new HashSet<>();
            } else {
                throw new IllegalStateException("Unexpected type: " + classType);
            }
            collection.addAll(Arrays.asList(objects));
            return collection;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }


    private void insertData(Object object, JsonArray jsonArray, ObjectRegistry objectRegistry) {
        Optional<Long> objectId = objectRegistry.getKey(object);
        if (objectId.isPresent()) {
            JsonObject parameterJsom = new JsonObject();
            parameterJsom.addProperty("objectId", objectId.get());
            parameterJsom.addProperty("type", "marked");
            jsonArray.add(parameterJsom);
            return;
        }
        if (object instanceof Object[] objectArray) {
            JsonArray elementJson = new JsonArray();
            for (Object element : objectArray) {
                insertData(element, elementJson, objectRegistry);
            }
            JsonObject parameterJson = new JsonObject();
            parameterJson.add("elements", elementJson);
            parameterJson.addProperty("type", "array");
            jsonArray.add(parameterJson);
            return;
        }
        if (object instanceof Collection<?> objectList) {
            JsonArray elementJson = new JsonArray();
            for (Object element : objectList) {
                insertData(element, elementJson, objectRegistry);
            }
            JsonObject parameterJson = new JsonObject();
            parameterJson.add("elements", elementJson);
            parameterJson.addProperty("type", "collection");
            parameterJson.addProperty("class_type", object.getClass().getName());
            jsonArray.add(parameterJson);
            return;
        }
        if (Primitives.isWrapperType(object.getClass()) || object instanceof String) {
            JsonObject parameterJson = new JsonObject();
            parameterJson.addProperty("type", "primitive");
            parameterJson.add("value", createPrimitive(object));
            parameterJson.addProperty("class_type", object.getClass().getName());
            jsonArray.add(parameterJson);
            return;
        }
        throw new UnsupportedOperationException("Unknown object: " + object);
    }

    private JsonElement createPrimitive(Object object) {
        if (Short.class.isAssignableFrom(object.getClass())) {
            return new JsonPrimitive((short) object);
        }
        if (Integer.class.isAssignableFrom(object.getClass())) {
            return new JsonPrimitive((int) object);
        }
        if (Long.class.isAssignableFrom(object.getClass())) {
            return new JsonPrimitive((long) object);
        }
        if (Float.class.isAssignableFrom(object.getClass())) {
            return new JsonPrimitive((float) object);
        }
        if (Double.class.isAssignableFrom(object.getClass())) {
            return new JsonPrimitive((double) object);
        }
        if (Boolean.class.isAssignableFrom(object.getClass())) {
            return new JsonPrimitive((boolean) object);
        }
        if (Byte.class.isAssignableFrom(object.getClass())) {
            return new JsonPrimitive((byte) object);
        }
        if (Character.class.isAssignableFrom(object.getClass())) {
            return new JsonPrimitive((char) object);
        }
        if (object instanceof String string) {
            return new JsonPrimitive(string);
        }
        throw new IllegalArgumentException("Unsupported primitive type: " + object.getClass());
    }
}
