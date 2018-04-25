package ru.ifmo.rain.kokorin.crawler;

import info.kgeorgiy.java.advanced.crawler.CachingDownloader;
import info.kgeorgiy.java.advanced.crawler.Crawler;
import info.kgeorgiy.java.advanced.crawler.Document;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import info.kgeorgiy.java.advanced.crawler.Result;
import info.kgeorgiy.java.advanced.crawler.URLUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;

public class WebCrawler implements Crawler {

    private final static String ERROR_MSG = "running:\n" +
            "WebCrawler url [depth [downloads [extractors [perHost]]]]";

    private final ExecutorService downloadersThreadPool;
    private final ExecutorService extractorsThreadPool;
    private final Downloader downloader;
    private final int perHost;
    private final Map<String, Semaphore> hostInfo = new ConcurrentHashMap<>();
    private final Predicate<String> predicate;

    public WebCrawler(Downloader downloader,
                      int downloaders, int extractors, int perHost) {
        this(downloader, downloaders, extractors, perHost, s -> true);
    }

    public WebCrawler(Downloader downloader,
                      int downloaders, int extractors, int perHost,
                      Predicate<String> predicate) {
        this.downloader = downloader;
        downloadersThreadPool = Executors.newFixedThreadPool(downloaders);
        extractorsThreadPool = Executors.newFixedThreadPool(extractors);
        this.perHost = perHost;
        this.predicate = predicate;
    }

    @Override
    public Result download(String url, int depth) {
        final Set<String> processedAll = ConcurrentHashMap.newKeySet();
        final Map<String, IOException> processedWithException = new ConcurrentHashMap<>();
        Phaser waiter = new Phaser(0);
        waiter.register();
        downloadImpl(url, depth, processedAll, processedWithException, waiter);
        waiter.arriveAndAwaitAdvance();
        processedAll.removeAll(processedWithException.keySet());
        return new Result(new ArrayList<>(processedAll), processedWithException);
    }

    private Optional<String> getHost(String url, Map<String, IOException> errors) {
        try {
            return Optional.of(URLUtils.getHost(url));
        } catch (MalformedURLException e) {
            errors.put(url, e);
            return Optional.empty();
        }
    }

    private void downloadImpl(String urlFrom, int depthLeft,
                              Set<String> processed,
                              Map<String, IOException> processedWithException,
                              Phaser waiter) {
        if (!predicate.test(urlFrom) || !processed.add(urlFrom)) {
            return;
        }

        getHost(urlFrom, processedWithException).ifPresent(
                curHost -> {
                    Runnable downloadTask = () -> {
                        try {
                            Document downloadedPage = downloader.download(urlFrom);

                            Runnable extractorTask = () -> {
                                try {
                                    if (depthLeft != 1) {
                                        try {
                                            downloadedPage.extractLinks()
                                                    .forEach(curLink ->
                                                            downloadImpl(
                                                                    curLink, depthLeft - 1,
                                                                    processed, processedWithException,
                                                                    waiter
                                                            )
                                                    );
                                        } catch (IOException e) {
                                            processedWithException.put(urlFrom, e);
                                        }
                                    }
                                } finally {
                                    waiter.arrive();
                                }
                            };

                            waiter.register();
                            extractorsThreadPool.submit(extractorTask);
                        } catch (IOException e) {
                            processedWithException.put(urlFrom, e);
                        } finally {
                            waiter.arrive();
                            hostInfo.get(curHost).release();
                        }
                    };
                    hostInfo.computeIfAbsent(curHost, host -> new Semaphore(perHost));
                    Semaphore semaphore = hostInfo.get(curHost);
                    semaphore.acquireUninterruptibly();
                    waiter.register();
                    downloadersThreadPool.submit(downloadTask);
                }
        );
    }

    @Override
    public void close() {
        downloadersThreadPool.shutdownNow();
        extractorsThreadPool.shutdownNow();
    }

    private static int getArg(String[] args, int index, int defaultValue) throws NumberFormatException {
        if (index < args.length) {
            return Integer.parseInt(args[index]);
        } else {
            return defaultValue;
        }
    }

    public static void main(String[] args) {
        if (args == null || args.length < 1 || args.length > 5) {
            System.out.println(ERROR_MSG);
            return;
        }
        String url = args[0];
        int depth;
        int downloads;
        int extractors;
        int perHost;
        try {
            depth = getArg(args, 1, 2);
            downloads = getArg(args, 2, 2);
            extractors = getArg(args, 3, 2);
            perHost = getArg(args, 4, 10);
        } catch (NumberFormatException e) {
            System.out.println("Couldn't convert string to number: " + e.getMessage());
            return;
        }
        try (Crawler crawler = new WebCrawler(new CachingDownloader(), downloads, extractors, perHost)) {
            Result result = crawler.download(url, depth);
            System.out.println("Downloaded correctly " + result.getDownloaded().size() + " pages:");
            for (String downloaded : result.getDownloaded()) {
                System.out.println(downloaded);
            }
            System.out.println("Downloaded with errors " + result.getErrors().size() + " pages:");
            for (Map.Entry<String, IOException> entry : result.getErrors().entrySet()) {
                System.out.println("Url - " + entry.getKey() + ", Error - " + entry.getValue().getMessage());
            }
        } catch (IOException e) {
            System.out.println("Couldn't crawl pages " + e.getMessage());
        }
    }
}