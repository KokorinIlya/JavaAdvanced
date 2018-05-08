package ru.ifmo.rain.kokorin.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class HelloUDPServer implements HelloServer {

    private DatagramSocket socket;
    private ExecutorService workers;
    private boolean closed = false;

    public void start(int port, int threads) {
        workers = Executors.newFixedThreadPool(threads);

        try {
            socket = new DatagramSocket(port);
        } catch (SocketException e) {
            System.err.println("Error creating socket for port № " + port + "stopping");
            return;
        }

        Runnable task = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    byte[] receiveBuffer = new byte[socket.getReceiveBufferSize()];
                    DatagramPacket receivePacket = Utils.makePacketToReceive(receiveBuffer);
                    socket.receive(receivePacket);

                    String request = Utils.getStringFromPacket(receivePacket);

                    System.out.println("Request received: " + request);

                    String response = "Hello, " + request;
                    byte[] responseBuffer = response.getBytes(StandardCharsets.UTF_8);

                    DatagramPacket packetToSend = Utils.makePacketToSend(
                            responseBuffer,
                            receivePacket.getSocketAddress()
                    );

                    socket.send(packetToSend);

                    System.out.println("Response send: " + response);
                } catch (IOException e) {
                    if (!closed) {
                        System.err.println("Error working with datagram: " + e.getMessage());
                    }
                }
            }
        };

        Stream.iterate(0, i -> i + 1).limit(threads).forEach(i -> workers.submit(task));
    }

    @Override
    public void close() {
        workers.shutdownNow();
        socket.close();
        closed = true;
    }

    private static final String ERROR_MSG = "Running:\n" +
            "HelloUDPServer <port> <number of threads>";

    public static void main(String[] args) {
        if (args == null || args.length != 2 || args[0] == null || args[1] == null) {
            System.out.println(ERROR_MSG);
            return;
        }

        int port;
        int threads;

        try {
            port = Integer.parseInt(args[0]);
            threads = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Error parsing number " + e.getMessage());
            return;
        }

        new HelloUDPServer().start(port, threads);
    }
}
