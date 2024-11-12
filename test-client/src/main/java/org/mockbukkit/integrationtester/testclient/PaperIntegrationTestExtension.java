package org.mockbukkit.integrationtester.testclient;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.extension.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;


public class PaperIntegrationTestExtension implements TestInstancePostProcessor, TestInstancePreDestroyCallback {

    @Override
    public void postProcessTestInstance(Object testInstance, ExtensionContext context) throws Exception {
        // TODO: start docker test container
        ServerCommunicator serverCommunicator = new ServerCommunicator();
        injectSenderIntoFields(testInstance, context, serverCommunicator);
    }

    @Override
    public void preDestroyTestInstance(ExtensionContext context) {
        // TODO: stop docker test container
    }

    private void injectSenderIntoFields(Object testInstance, ExtensionContext context, ServerCommunicator serverCommunicator) throws IllegalAccessException
    {
        final Optional<Class<?>> classOptional = context.getTestClass();
        if (classOptional.isEmpty()) {
            return;
        }

        final List<Field> serverMockFields = FieldUtils.getAllFieldsList(classOptional.get())
                .stream()
                .filter(field -> field.getType() == ServerCommunicator.class)
                .filter(field -> field.getAnnotation(ServerCommunicatorInjector.class) != null)
                .toList();

        for (final Field field : serverMockFields)
        {
            final String name = field.getName();
            FieldUtils.writeDeclaredField(testInstance, name, serverCommunicator, true);
        }
    }
}
