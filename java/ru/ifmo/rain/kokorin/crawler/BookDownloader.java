package ru.ifmo.rain.kokorin.crawler;

import info.kgeorgiy.java.advanced.crawler.CachingDownloader;
import info.kgeorgiy.java.advanced.crawler.Crawler;
import info.kgeorgiy.java.advanced.crawler.URLUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.stream.Collectors;

public class BookDownloader {
    private final static String MAIN_PAGE_URL = "https://e.lanbook.com/books";
    private final static String BOOK_URL = "https://e.lanbook.com/book/";
    private final static String BIBL_RECORD_BEGIN = "<div id=\"bibliographic_record\">";
    private final static String BIBL_RECORD_END = "</div>";
    private final static byte OK_MARKER = '+';
    private final static int CODES[] = {917, 918, 1537};
    private static final String HEADER_TEMPLATE = "https://e.lanbook.com/books/%d";
    private static final String PAGE_TEMPLATE = "https://e.lanbook.com/books/%d?page=";
    private static final String ERROR_MSG = "running:\n" +
            "BookDownloader --all <folder for storing downloaded files> <file for storing result>\n" +
            "BookDownloader --download <folder for storing downloaded files>\n" +
            "BookDownloader --parse <folder for storing downloaded files> <file for storing result>";

    private static int MAX_YEAR = Calendar.getInstance().get(Calendar.YEAR);
    private static int MIN_YEAR = MAX_YEAR - 4;

    private static boolean containsCode(String url) {
        for (int code : CODES) {
            if (url.equals(String.format(HEADER_TEMPLATE, code)) ||
                    url.startsWith(String.format(PAGE_TEMPLATE, code))) {
                return true;
            }
        }
        return false;
    }

    private static void download(Path dirForDownloadedFiles) {
        try {
            Files.createDirectories(dirForDownloadedFiles);
        } catch (IOException e) {
            System.err.println("Cannot create directory for downloaded files" + e.getMessage());
            return;
        }
        String host;
        try {
            host = URLUtils.getHost("https://e.lanbook.com/books");
        } catch (MalformedURLException e) {
            System.err.println("Cannot get host of e.lanbook.com " + e.getMessage());
            return;
        }

        try (
                Crawler crawler = new WebCrawler(
                        new CachingDownloader(dirForDownloadedFiles), 20, 20, 100,
                        url -> {
                            try {
                                if (!URLUtils.getHost(url).equals(host)) {
                                    return false;
                                }
                            } catch (MalformedURLException e) {
                                return false;
                            }
                            return url.equals(MAIN_PAGE_URL) ||
                                    url.contains(BOOK_URL) ||
                                    containsCode(url);

                        }
                )
        ) {
            crawler.download(MAIN_PAGE_URL, Integer.MAX_VALUE);
        } catch (IOException e) {
            System.err.println("Error downloading " + e.getMessage());
        }
    }

    private static void parse(Path dirForDownloadedFiles, Path pathToRes) {
        try {
            Files.createDirectories(pathToRes.toAbsolutePath().getParent());
            Files.createDirectories(dirForDownloadedFiles);
        } catch (IOException e) {
            System.err.println("Cannot create directory" + e.getMessage());
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(pathToRes)) {
            Files.list(dirForDownloadedFiles).forEach(
                    page -> {
                        if (!Files.isRegularFile(page) || page.equals(pathToRes)) {
                            return;
                        }
                        try (InputStream is = Files.newInputStream(page)) {
                            int x = is.read();
                            if (x == -1 || (byte) x != OK_MARKER) {
                                return;
                            }
                        } catch (IOException e) {
                            System.err.println("Error trying to open file " + e.getMessage());
                            return;
                        }
                        String htmlCode;
                        try (BufferedReader reader = Files.newBufferedReader(page)) {
                            htmlCode = reader.lines().collect(Collectors.joining());
                        } catch (IOException e) {
                            System.err.println("Error while reading file " + page.getFileName().toString());
                            System.err.println(e.getMessage());
                            return;
                        }
                        String oneString = htmlCode.replaceAll(">\\p{javaWhitespace}+<", "><");
                        for (int year = MIN_YEAR; year <= MAX_YEAR; year++) {
                            if (oneString.contains("<dt>Год:</dt><dd>" + Integer.toString(year) + "</dd>")) {
                                int beginIndex = oneString.indexOf(BIBL_RECORD_BEGIN);
                                if (beginIndex == -1) {
                                    return;
                                }
                                int endIndex = oneString.indexOf(BIBL_RECORD_END, beginIndex);
                                if (endIndex == -1) {
                                    return;
                                }
                                String bookInfo = oneString.substring(beginIndex + BIBL_RECORD_BEGIN.length(), endIndex);
                                try {
                                    writer.write(bookInfo.trim());
                                    writer.newLine();
                                } catch (IOException e) {
                                    System.err.println("Cannot write information about book " + bookInfo + "to file");
                                    System.err.println("I/O error occurred " + e.getMessage());
                                }
                                return;
                            }
                        }
                    }
            );
        } catch (IOException e) {
            System.err.println("Error while writing to file " + e.getMessage());
        }
    }

    public static void main(String[] args) {

        if (args == null || args.length < 1 || args.length > 3 || args[0] == null) {
            System.out.println(ERROR_MSG);
            return;
        }

        if (args[0].equals("--all")) {
            if (args.length != 3 || args[1] == null || args[2] == null) {
                System.out.println(ERROR_MSG);
                return;
            }
            Path dirForDownloadedFiles = Paths.get(args[1]);
            Path pathToRes = Paths.get(args[2]);
            download(dirForDownloadedFiles);
            parse(dirForDownloadedFiles, pathToRes);
            return;
        }

        if (args[0].equals("--download")) {
            if (args.length != 2 || args[1] == null) {
                System.out.println(ERROR_MSG);
                return;
            }
            Path dirForDownloadedFiles = Paths.get(args[1]);
            download(dirForDownloadedFiles);
            return;
        }

        if (args[0].equals("--parse")) {
            if (args.length != 3 || args[1] == null || args[2] == null) {
                System.out.println(ERROR_MSG);
                return;
            }
            Path dirForDownloadedFiles = Paths.get(args[1]);
            Path pathToRes = Paths.get(args[2]);
            parse(dirForDownloadedFiles, pathToRes);
            return;
        }

        System.out.println(ERROR_MSG);
    }
}
