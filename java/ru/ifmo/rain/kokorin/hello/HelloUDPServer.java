package ru.ifmo.rain.kokorin.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HelloUDPServer implements HelloServer {

    private DatagramSocket socket;
    private ExecutorService senders;
    private ExecutorService listener;
    private boolean closed = false;
    private static int QUEUE_CAPACITY = 1000;
    private int requestBufferSize;

    public void start(int port, int threads) {
        try {
            socket = new DatagramSocket(port);
            requestBufferSize = socket.getReceiveBufferSize();
        } catch (SocketException e) {
            System.err.println("Error creating socket for port № " + port + "stopping");
            return;
        }

        listener = Executors.newSingleThreadExecutor();
        senders = new ThreadPoolExecutor(
                threads,
                threads,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                new ThreadPoolExecutor.DiscardPolicy()
        );

        Runnable listenerTask = () -> {
            while (!Thread.currentThread().isInterrupted()) {
                DatagramPacket requestPacket = Utils.makePacketToReceive(requestBufferSize);

                try {
                    socket.receive(requestPacket);
                    senders.submit(
                            () -> sendResponse(requestPacket)
                    );
                } catch (IOException e) {
                    if (!closed) {
                        System.out.println("Error receiving datagram: " + e.getMessage());
                    }
                }
            }
        };

        listener.submit(listenerTask);
    }

    private void sendResponse(DatagramPacket requestPacket) {
        String requestMessage = Utils.getStringFromPacket(requestPacket);
        String responseMessage = "Hello, " + requestMessage;
        byte[] responseBuffer = responseMessage.getBytes(StandardCharsets.UTF_8);
        DatagramPacket responsePacket = Utils.makePacketToSend(responseBuffer, requestPacket.getSocketAddress());

        try {
            socket.send(responsePacket);
        } catch (IOException e) {
            if (!closed) {
                System.out.println("Error sending datagram: " + e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        listener.shutdownNow();

        senders.shutdownNow();
        try {
            senders.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }

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
