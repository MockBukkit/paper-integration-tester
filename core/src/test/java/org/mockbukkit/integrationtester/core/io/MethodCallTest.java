package org.mockbukkit.integrationtester.core.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MethodCallTest {

    private PipedInputStream input;
    private PipedOutputStream output;
    private static final ObjectRegistry registry1 = new ObjectRegistry();
    private static final ObjectRegistry registry2 = new ObjectRegistry();

    @BeforeEach
    void setup() throws IOException {
        this.input = new PipedInputStream();
        this.output = new PipedOutputStream(input);
    }

    @ParameterizedTest
    @MethodSource("methodCalls")
    void sendAndReceive(MethodCall methodCall) throws IOException {
        methodCall.send(output, registry1);
        MethodCall output = MethodCall.receive(input, registry1);
        assertEquals(methodCall.methodName(), output.methodName());
        assertEquals(methodCall.owner(), output.owner());
        assertTrue(Arrays.deepEquals(methodCall.parameters(), output.parameters()));
        assertSame(methodCall.object(), output.object());
    }

    public static Stream<Arguments> methodCalls() {
        Object object1 = new Object();
        registry1.putObject(0L, object1);
        TestObject object2 = new TestObject();
        registry1.putObject(1L, object2);
        TestObject object3 = new TestObject();
        registry1.putObject(2L, object3);
        List<TestObject> testObjects = new ArrayList<>();
        Object objects = new TestObject[]{object2, object3};
        testObjects.add(object3);
        testObjects.add(object2);
        return Stream.of(
                Arguments.arguments(new MethodCall("hello", "world", object1, object2, object3)),
                Arguments.arguments(new MethodCall("hello", "world!", object1, objects)),
                Arguments.arguments(new MethodCall("hello", "world!", object1, testObjects)),
                Arguments.arguments(new MethodCall("hello", "world!", object1, true))
        );
    }

    public static class TestObject {

    }
}