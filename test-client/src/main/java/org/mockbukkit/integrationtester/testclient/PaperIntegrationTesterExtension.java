package org.mockbukkit.integrationtester.testclient;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.jupiter.api.extension.TestInstancePreDestroyCallback;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

public class PaperIntegrationTesterExtension implements TestInstancePostProcessor, TestInstancePreDestroyCallback {

    private PaperIntegrationTester integrationTester;

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        this.integrationTester = new PaperIntegrationTester();
        injectIntoFields(testInstance, integrationTester);
    }

    private void injectIntoFields(Object testInstance, PaperIntegrationTester integrationTester) throws IllegalAccessException {
        Class<?> testClass = testInstance.getClass();
        List<Field> fields = List.of(testClass.getDeclaredFields());
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!field.isAnnotationPresent(Inject.class)) {
                continue;
            }
            field.set(testInstance, integrationTester);
        }
    }

    @Override
    public void preDestroyTestInstance(ExtensionContext context) throws Exception {
        this.integrationTester.close();
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Inject {

    }
}
