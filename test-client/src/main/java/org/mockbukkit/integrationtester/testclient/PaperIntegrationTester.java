package org.mockbukkit.integrationtester.testclient;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class PaperIntegrationTester implements AutoCloseable {


    private final GenericContainer<?> container;

    public PaperIntegrationTester(Class<?>... plugins) {
        container = new GenericContainer<>(DockerImageName.parse("itzg/minecraft-server"))
                .withExposedPorts(25565)
                .withEnv("EULA", "TRUE")
                .withCopyFileToContainer();
        synchronized (container) {
            new Thread(container::start).start();
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
    }
}
