package ru.ifmo.rain.kokorin.hello;

import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;

public class Utils {
    public static String getStringFromPacket(DatagramPacket packet) {
        return new String(
                packet.getData(),
                packet.getOffset(),
                packet.getLength(),
                StandardCharsets.UTF_8
        );
    }

    public static DatagramPacket makePacketToSend(byte[] buffer, SocketAddress address) {
        return new DatagramPacket(
                buffer,
                0,
                buffer.length,
                address
        );
    }

    public static DatagramPacket makePacketToReceive(byte[] buffer) {
        return new DatagramPacket(buffer, buffer.length);
    }
}
