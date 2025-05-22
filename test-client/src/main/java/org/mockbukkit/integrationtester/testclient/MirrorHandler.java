package org.mockbukkit.integrationtester.testclient;

import org.mockbukkit.integrationtester.core.io.MethodCall;
import org.mockbukkit.integrationtester.core.io.MethodReturnCall;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MirrorHandler {

    public static <T> T handle(String methodName, String className, Object object, Object... parameters) {
        MethodCall methodCall = new MethodCall(className, methodName, object, parameters);
        if (PaperIntegrationTester.instance == null) {
            throw new IllegalStateException("You have not started PaperIntegrationTester!");
        }
        try (OutputStream out = PaperIntegrationTester.instance.getOutput()) {
            methodCall.send(out, PaperIntegrationTester.instance.registry);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream inputStream = PaperIntegrationTester.instance.getInput()) {
            MethodReturnCall methodReturnCall = MethodReturnCall.receive(inputStream, PaperIntegrationTester.instance.registry);
            return (T) methodReturnCall.value();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
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
