package org.mockbukkit.integrationtester.testclient;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import org.mockbukkit.integrationtester.core.io.ObjectRegistry;
import org.mockbukkit.integrationtester.core.io.PacketVersion;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class PaperIntegrationTester implements AutoCloseable {

    public static @Nullable PaperIntegrationTester instance = null;


    private final GenericContainer<?> container;
    public final ObjectRegistry registry = new ObjectRegistry(compileClassRemapping());
    private final Socket socket;

    public PaperIntegrationTester(Class<?>... plugins) {
        container = new GenericContainer<>(DockerImageName.parse("itzg/minecraft-server"))
                .withExposedPorts(12345)
                .withEnv("EULA", "TRUE");
        synchronized (container) {
            new Thread(container::start).start();
        }
        try {
            this.socket = new Socket("0.0.0.0", container.getMappedPort(12345));
            try (InputStream inputStream = socket.getInputStream()) {
                byte version = (byte) inputStream.read();
                if (version != PacketVersion.VERSION) {
                    throw new IllegalStateException("Mismatching packet version for server and client.");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        synchronized (container) {
            try {
                container.stop();
            } finally {
                container.close();
            }
        }
        socket.close();
    }

    public InputStream getInput() throws IOException {
        return socket.getInputStream();
    }

    public OutputStream getOutput() throws IOException {
        return socket.getOutputStream();
    }


    private Map<String, Class<?>> compileClassRemapping() {
        try (InputStream inputStream = PaperIntegrationTester.class.getResourceAsStream("/classRemapping.json")) {
            try (Reader reader = new InputStreamReader(inputStream)) {
                JsonElement json = JsonParser.parseReader(reader);
                Map<String, Class<?>> remapping = new HashMap<>();
                for (Map.Entry<String, JsonElement> element : json.getAsJsonObject().asMap().entrySet()) {
                    remapping.put(element.getKey(), Class.forName(element.getValue().getAsString()));
                }
                return remapping;
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}
