package org.mockbukkit.integrationtester.core.util;

import com.google.common.primitives.Primitives;
import com.google.gson.*;
import org.mockbukkit.integrationtester.core.io.ObjectRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class ObjectUtil {

    private ObjectUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static Object[] readArray(JsonArray jsonElements, ObjectRegistry objectRegistry) {
        List<Object> objectList = new ArrayList<>();
        for (JsonElement element : jsonElements) {
            objectList.add(readObject(element, objectRegistry));
        }
        return objectList.toArray();
    }

    public static Object readObject(JsonElement jsonElement, ObjectRegistry objectRegistry) {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        return switch (jsonObject.get("type").getAsString()) {
            case "marked" -> {
                long id = jsonObject.get("objectId").getAsLong();
                if (objectRegistry.hasKey(id)) {
                    yield objectRegistry.getObject(id);
                }
                Object object = createNew(jsonObject.get("class_type").getAsString());
                objectRegistry.putObject(id, object);
                yield object;
            }
            case "array" -> readArray(jsonObject.get("elements").getAsJsonArray(), objectRegistry);
            case "collection" ->
                    toCollection(readArray(jsonObject.get("elements").getAsJsonArray(), objectRegistry), jsonObject.get("class_type").getAsString());
            case "primitive" ->
                    readPrimitive((JsonPrimitive) jsonObject.get("value"), jsonObject.get("class_type").getAsString());
            default ->
                    throw new IllegalStateException("Unexpected type: " + jsonElement.getAsJsonObject().get("type").getAsString());
        };
    }

    private static Object createNew(String className) {
        Class<?> clazz = classRemapping.get(className);
        try {
            Constructor<?> constructor = clazz.getConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static Object readPrimitive(JsonPrimitive value, String classType) {
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

    public static Collection<Object> toCollection(Object[] objects, String classType) {
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


    public static JsonElement serializeData(Object object, ObjectRegistry objectRegistry) {
        Optional<Long> objectId = objectRegistry.getKey(object);
        if (objectId.isPresent()) {
            JsonObject parameterJsom = new JsonObject();
            parameterJsom.addProperty("objectId", objectId.get());
            parameterJsom.addProperty("type", "marked");
            parameterJsom.addProperty("class_type", object.getClass().getName());
            return parameterJsom;
        }
        if (object instanceof Object[] objectArray) {
            JsonArray elementJson = new JsonArray();
            for (Object element : objectArray) {
                elementJson.add(serializeData(element, objectRegistry));
            }
            JsonObject parameterJson = new JsonObject();
            parameterJson.add("elements", elementJson);
            parameterJson.addProperty("type", "array");
            return parameterJson;
        }
        if (object instanceof Collection<?> objectList) {
            JsonArray elementJson = new JsonArray();
            for (Object element : objectList) {
                elementJson.add(serializeData(element, objectRegistry));
            }
            JsonObject parameterJson = new JsonObject();
            parameterJson.add("elements", elementJson);
            parameterJson.addProperty("type", "collection");
            parameterJson.addProperty("class_type", object.getClass().getName());
            return parameterJson;
        }
        if (Primitives.isWrapperType(object.getClass()) || object instanceof String) {
            JsonObject parameterJson = new JsonObject();
            parameterJson.addProperty("type", "primitive");
            parameterJson.add("value", createPrimitive(object));
            parameterJson.addProperty("class_type", object.getClass().getName());
            return parameterJson;
        }
        throw new UnsupportedOperationException("Unknown object: " + object);
    }

    public static JsonPrimitive createPrimitive(Object object) {
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
