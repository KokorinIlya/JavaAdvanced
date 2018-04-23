package ru.ifmo.rain.kokorin.crawler;

import info.kgeorgiy.java.advanced.crawler.CachingDownloader;
import info.kgeorgiy.java.advanced.crawler.Crawler;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import info.kgeorgiy.java.advanced.crawler.Result;
import info.kgeorgiy.java.advanced.crawler.URLUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class BookDownloader {
    public static void main(String[] args) throws Exception {
        String host = URLUtils.getHost("https://e.lanbook.com/books");
        Path dirForDownloaded = Paths.get("downloaded-books");
        Files.createDirectories(dirForDownloaded);
    }
}
