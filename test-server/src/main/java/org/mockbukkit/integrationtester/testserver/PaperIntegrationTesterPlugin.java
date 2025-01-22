package org.mockbukkit.integrationtester.testserver;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.Socket;

public class PaperIntegrationTesterPlugin extends JavaPlugin {

    short version = 0;

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskTimer(this, this::tick, 0, 1);
    }

    private void tick() {
        try (Socket socket = new Socket("0.0.0.0", 66666)) {
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
