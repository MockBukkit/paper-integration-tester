package org.mockbukkit.integrationtester.testserver;

import org.bukkit.plugin.java.JavaPlugin;
import org.mockbukkit.integrationtester.core.io.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;

public class PaperIntegrationTesterPlugin extends JavaPlugin {

    private ServerSocket socketServer;
    private ObjectRegistry registry = new ObjectRegistry();

    @Override
    public void onEnable() {
        try {
            this.socketServer = new ServerSocket(12345);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        getServer().getScheduler().runTaskTimer(this, this::tick, 0, 1);
        try (Socket socket = socketServer.accept()) {
            socket.getOutputStream().write(PacketVersion.VERSION);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void tick() {
        try (Socket socket = socketServer.accept()) {
            try (InputStream inputStream = socket.getInputStream()) {
                Packet packet = PacketFactory.fromStream(inputStream, registry);
                if (packet instanceof FieldFetchCall fieldFetchCall) {
                    handleFieldFetch(fieldFetchCall, socket);
                }
                if(packet instanceof MethodCall methodCall) {
                    handleMethodCall(methodCall, socket);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleMethodCall(MethodCall methodCall, Socket socket) {

    }

    private void handleFieldFetch(FieldFetchCall fieldFetchCall, Socket socket) {
        try {
            Class<?> clazz = Class.forName(fieldFetchCall.className());
            Object owner = fieldFetchCall.owner();
            String fieldName = fieldFetchCall.field();
            Field field = clazz.getField(fieldName);
            Object fieldValue = field.get(owner);
            if(!registry.hasObject(fieldValue)) {
                registry.putObject();
            }
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }
}
