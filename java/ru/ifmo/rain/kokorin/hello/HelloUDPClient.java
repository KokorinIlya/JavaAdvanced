package ru.ifmo.rain.kokorin.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HelloUDPClient implements HelloClient {

    private ExecutorService workers;
    private static int TIMEOUT = 1000;

    public void run(String address, int port, String prefix, int threads, int perThread) {
        workers = Executors.newFixedThreadPool(threads);
        addTasks(address, port, prefix, threads, perThread);

        /*
        Способ дождаться завершения всех потоков ExecutorService

        Альтернативой является применение Phaser (добавляем задачу в пул - делаем register,
        при завершении таски делаем arrive)

        В конце делаем arriveAndAwait, ожидая
         */
        workers.shutdown();
        try {
            workers.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException ignored) {
        }
    }

    private void addTasks(String address, int port, String prefix, int threads, int perThread) {
        InetAddress serverAddress;
        try {
            serverAddress = InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            System.err.println("Invalid server address " + e.getMessage());
            return;
        }

        InetSocketAddress addressAndPort = new InetSocketAddress(serverAddress, port);

        for (int i = 0; i < threads; i++) {
            final int threadNum = i;

            Runnable task = () -> {
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setSoTimeout(TIMEOUT);

                    for (int requestNum = 0; requestNum < perThread; requestNum++) {

                        byte[] bufferToReceive = new byte[socket.getReceiveBufferSize()];
                        DatagramPacket packetToReceive = Utils.makePacketToReceive(bufferToReceive);

                        String request = prefix + threadNum + "_" + requestNum;
                        byte[] requestBuffer = request.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket requestPacket = Utils.makePacketToSend(
                                requestBuffer,
                                addressAndPort
                        );

                        while (!Thread.currentThread().isInterrupted()) {
                            try {
                                socket.send(requestPacket);
                            } catch (IOException e) {
                                System.err.println("Error sending datagram " + e.getMessage() + "\n" +
                                        "Retrying");
                                continue;
                            }

                            try {
                                socket.receive(packetToReceive);
                                String response = Utils.getStringFromPacket(packetToReceive);

                                System.out.println("Response received " + response);

                                if (!response.contains(request)) {
                                    System.out.println("Response rejected " + response);
                                    continue;
                                }

                                System.out.println("Response accepted:" + response);
                                System.out.println();
                                break;
                            } catch (IOException e) {
                                System.err.println("Error receiving datagram " + e.getMessage() + "\n" +
                                        "Retrying");
                            }
                        }

                    }
                } catch (SocketException e) {
                    System.err.println("Socket № " + threadNum + " cannot be created: " + e.getMessage());
                }

            };

            workers.submit(task);
        }

    }

    private static String ERROR_MSG = "Running:\n" +
            "HelloUDPClient <host name or address> <port number> " +
            "<request prefix> <number of threads> <number of requests per thread>";

    public static void main(String[] args) {
        if (args == null || args.length != 5 ||
                args[0] == null || args[1] == null || args[2] == null || args[3] == null || args[4] == null) {
            System.out.println(ERROR_MSG);
            return;
        }
        String hostName = args[0];
        int port;
        String prefix = args[2];
        int threads;
        int perThread;

        try {
            port = Integer.parseInt(args[1]);
            threads = Integer.parseInt(args[3]);
            perThread = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            System.err.println("Error parsing number " + e.getMessage());
            return;
        }

        new HelloUDPClient().run(hostName, port, prefix, threads, perThread);

    }
}
