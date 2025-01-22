package org.mockbukkit.integrationtester.core.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NewObjectCallTest {

    private PipedInputStream input;
    private PipedOutputStream output;

    @BeforeEach
    void setup() throws IOException {
        this.input = new PipedInputStream();
        this.output = new PipedOutputStream(input);
    }

    @Test
    void sendAndReceive() {
        NewObjectCall target = new NewObjectCall(20, "Hello World");
        target.send(output);
        NewObjectCall created = NewObjectCall.receive(input);
        assertEquals(target, created);
    }
}