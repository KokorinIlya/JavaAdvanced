package ru.ifmo.rain.kokorin.walk;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.TERMINATE;

class FileVisitor extends SimpleFileVisitor<Path> {
    private final BufferedWriter outputFile;
    private static final int START_VALUE = 0x811c9dc5;
    private static final int MULTIPLIER = 0x01000193;
    private static final int BUFFER_LEN = 1024;


    FileVisitor(BufferedWriter writer) {
        outputFile = writer;
    }

    private FileVisitResult writeToFile(String result) {
        try {
            outputFile.write(result);
            outputFile.newLine();
            return CONTINUE;
        } catch (IOException e) {
            return TERMINATE;
        }
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
        try (BufferedInputStream reader = new BufferedInputStream(Files.newInputStream(file))) {
            int h = START_VALUE;
            byte[] buffer = new byte[BUFFER_LEN];
            int result;
            while ((result = reader.read(buffer)) != -1) {
                for (int i = 0; i < result; i++) {
                    h *= MULTIPLIER;
                    h ^= buffer[i] & 0xff;
                }
            }
            return writeToFile(String.format("%08x %s", h, file));
        } catch (IOException e) {
            return writeToFile("00000000 " + file.toString());
        }
    }

    @Override
    public FileVisitResult visitFileFailed(Path file,
                                           IOException exc) {
        return writeToFile("00000000 " + file.toString());
    }
}
