package com.tradingSystem.Jasdaq.Engine.net;

import org.springframework.stereotype.Component;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.*;

@Component
public class MulticastBroadcaster {

    private static final String GROUP = "230.0.0.0";
    private static final int PORT = 5000;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void broadcastAsync(String message) {
        executor.submit(() -> {

            try (MulticastSocket socket = new MulticastSocket()) {

                InetAddress group = InetAddress.getByName(GROUP);
                byte[] data = message.getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length, group, PORT);
                socket.send(packet);

            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }

}
