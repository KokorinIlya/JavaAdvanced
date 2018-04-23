package ru.ifmo.rain.kokorin.walk;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class RecursiveWalk {
    private static void processFiles(BufferedReader reader, BufferedWriter writer) {
        FileVisitor visitor = new FileVisitor(writer);
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    Files.walkFileTree(Paths.get(line), visitor);
                } catch (InvalidPathException | IOException e) {
                    writer.write("00000000 " + line);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            System.out.println("Cannot process file " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args == null || args.length != 2 || args[0] == null || args[1] == null) {
            System.out.println("ERROR - 2 non-null arguments required");
            return;
        }
        String inputFileName = args[0];
        String outputFileName = args[1];
        Path pathToInputFile;
        Path pathToOutputFile;

        try {
            pathToInputFile = Paths.get(inputFileName);
            pathToOutputFile = Paths.get(outputFileName);
        } catch (InvalidPathException e) {
            System.out.println("ERROR - Incorrect path to file: " + e.getMessage());
            return;
        }

        try {
            if (pathToOutputFile.getParent() != null) {
                Files.createDirectories(pathToOutputFile.getParent());
            }
        } catch (IOException e) {
            System.out.println("Couldn't access output file " + e.getMessage());
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(pathToInputFile)) {
            try (BufferedWriter writer = Files.newBufferedWriter(pathToOutputFile)) {
                processFiles(reader, writer);
            } catch (IOException e) {
                System.out.println("ERROR - Couldn't process output file " + e.getMessage());
            }
        } catch (IOException e) {
            System.out.println("ERROR - couldn't open input file");
        }
        //System.out.println("Processing completed!");
    }
}
