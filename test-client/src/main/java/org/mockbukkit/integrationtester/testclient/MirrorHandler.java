package org.mockbukkit.integrationtester.testclient;

public class MirrorHandler {

    public static <T> T handle(String methodName, String className, Object object, Object... parameters) {
        return null;
    }

    public static <T> T handleStatic(String functionName, String className, Object... parameters) {
        return null;
    }

    public static <T> T handleField(String fieldName, String className, Object fieldOwner) {
        return null;
    }

    public static <T> T handleStaticField(String fieldName, String fieldOwner) {
        return null;
    }

    public static void trackNew(Object newObject, String className, Object... parameters) {

    }

}
