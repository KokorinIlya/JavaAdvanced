package ru.ifmo.rain.kokorin.concurrent;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class ParallelMapperImpl implements ParallelMapper {
    private final List<Thread> workers;
    private final TaskQueue tasks = new TaskQueue();

    public ParallelMapperImpl(int threads) {
        workers = new ArrayList<>(threads);
        if (threads <= 0) {
            throw new IllegalArgumentException("Cannot create ParallelMapper using 0 or less threads");
        }

        for (int i = 0; i < threads; i++) {
            Thread curThread = new Thread(
                    () -> {
                        try {
                            while (!Thread.currentThread().isInterrupted()) {
                                tasks.getAndRemoveOne().run();
                            }
                        } catch (InterruptedException ignored) {
                        } finally {
                            Thread.currentThread().interrupt();
                        }
                    }
            );
            workers.add(curThread);
            curThread.start();
        }
    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> f, List<? extends T> args)
            throws InterruptedException {
        final List<R> answers = new ArrayList<>(Collections.nCopies(args.size(), null));
        final Counter readyAnswers = new Counter();

        for (int i = 0; i < args.size(); i++) {
            final int finalI = i;

            Runnable newTask = () -> {
                T argument = args.get(finalI);
                R result = f.apply(argument);
                answers.set(finalI, result);

                synchronized (readyAnswers) {
                    readyAnswers.increment();
                    if (readyAnswers.get() == args.size()) {
                        readyAnswers.notify();
                    }
                }
            };

            tasks.addTask(newTask);
        }

        synchronized (readyAnswers) {
            while (readyAnswers.get() < args.size()) {
                readyAnswers.wait();
            }
        }
        return answers;
    }

    @Override
    public void close() {
        for (Thread thread : workers) {
            thread.interrupt();
        }
        try {
            ConcurrentUtils.joinAll(workers);
        } catch (InterruptedException ignored) {
        }
    }
}
