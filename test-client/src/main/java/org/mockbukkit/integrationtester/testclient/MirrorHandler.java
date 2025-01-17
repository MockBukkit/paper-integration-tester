package org.mockbukkit.integrationtester.testclient;

public class MirrorHandler {

    public static <T> T handle(String methodName, Object object, Object... parameters) {
        return null;
    }

    public static <T> T handleStatic(String functionName, Class<?> clazz, Object... parameters) {
        return null;
    }

    public static <T> T handleField(String fieldName, Object fieldOwner) {
        return null;
    }

    public static <T> T handleStaticField(String fieldName, Class<?> fieldOwner) {
        return null;
    }

    public static void trackNew(Object newObject, Object... parameters) {

    }

}
