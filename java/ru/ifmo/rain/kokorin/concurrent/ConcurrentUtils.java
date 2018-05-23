package ru.ifmo.rain.kokorin.concurrent;

import java.util.List;

class ConcurrentUtils {
    private static Pair<Integer, InterruptedException> joinRemaining(List<Thread> threads, int begin) {
        int i = begin;
        try {
            for (i = begin; i < threads.size(); i++) {
                threads.get(i).join();
            }
        } catch (InterruptedException e) {
            return new Pair<>(i, e);
        }
        return new Pair<>(threads.size(), null);
    }

    static void joinAll(List<Thread> threads) throws InterruptedException {
        InterruptedException exception = null;
        int i = 0;
        do {
            Pair<Integer, InterruptedException> p = joinRemaining(threads, i);
            i = p.getKey();
            if (i != threads.size()) {
                if (exception == null) {
                    exception = p.getValue();
                } else {
                    exception.addSuppressed(p.getValue());
                }
            }
        } while (i < threads.size());
        if (exception != null) {
            throw exception;
        }
    }
}
