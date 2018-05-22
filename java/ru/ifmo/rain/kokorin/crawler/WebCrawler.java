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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.function.Predicate;

public class WebCrawler implements Crawler {

    private final static String ERROR_MSG = "running:\n" +
            "WebCrawler url [depth [downloads [extractors [perHost]]]]";

    private final Downloader downloader;
    private final int perHost;
    private final ExecutorService downloadersPool;
    private final ExecutorService extractorsPool;
    private final ConcurrentMap<String, HostTasksQueue> hostsInfo;
    private final Predicate<String> needToDownload;

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost,
                      Predicate<String> predicate) {
        this.downloader = downloader;
        this.perHost = perHost;
        downloadersPool = Executors.newFixedThreadPool(downloaders);
        extractorsPool = Executors.newFixedThreadPool(extractors);
        needToDownload = predicate;
        hostsInfo = new ConcurrentHashMap<>();
    }

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this(downloader, downloaders, extractors, perHost, s -> true);
    }

    private Optional<String> getHost(String url, Map<String, IOException> errors) {
        try {
            return Optional.of(URLUtils.getHost(url));
        } catch (MalformedURLException e) {
            errors.put(url, e);
            return Optional.empty();
        }
    }

    private void downloadTask(
            String url,
            int depthLeft,
            Map<String, IOException> processedWithExceptions,
            Set<String> processedAll,
            Phaser waiter,
            HostTasksQueue curQueue
    ) {
        try {
            Document curPage = downloader.download(url);
            processedAll.add(url);

            if (depthLeft > 1) {
                Runnable extractTask = () -> {
                    try {
                        curPage.extractLinks().forEach(
                                link -> downloadImpl(
                                        link,
                                        depthLeft - 1,
                                        processedWithExceptions,
                                        processedAll,
                                        waiter
                                )
                        );
                    } catch (IOException e) {
                        processedWithExceptions.put(url, e);
                    } finally {
                        waiter.arrive();
                    }
                };
                waiter.register();
                extractorsPool.submit(extractTask);
            }
        } catch (IOException e) {
            processedWithExceptions.put(url, e);
        } finally {
            waiter.arrive();
            curQueue.runNextTask();
        }
    }

    private void downloadImpl(
            String url,
            int depthLeft,
            Map<String, IOException> processedWithExceptions,
            Set<String> processedAll,
            Phaser waiter
    ) {
        if (!needToDownload.test(url) || !processedAll.add(url)) {
            return;
        }

        getHost(url, processedWithExceptions).ifPresent(
                curHost -> {
                    HostTasksQueue curQueue = hostsInfo.computeIfAbsent(
                            curHost,
                            hostName -> new HostTasksQueue(downloadersPool, perHost)
                    );

                    waiter.register();
                    curQueue.addTask(
                            () -> downloadTask(
                                    url,
                                    depthLeft,
                                    processedWithExceptions,
                                    processedAll,
                                    waiter,
                                    curQueue
                            )
                    );
                }
        );
    }

    @Override
    public Result download(String url, int depth) {
        ConcurrentHashMap<String, IOException> processedWithExceptions = new ConcurrentHashMap<>();
        Set<String> processedAll = ConcurrentHashMap.newKeySet();
        Phaser waiter = new Phaser();
        waiter.register();
        downloadImpl(url, depth, processedWithExceptions, processedAll, waiter);
        waiter.arriveAndAwaitAdvance();
        processedAll.removeAll(processedWithExceptions.keySet());
        return new Result(new ArrayList<>(processedAll), processedWithExceptions);
    }

    @Override
    public void close() {
        downloadersPool.shutdownNow();
        extractorsPool.shutdownNow();
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