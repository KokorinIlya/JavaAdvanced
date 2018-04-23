package ru.ifmo.rain.kokorin.concurrent;

import info.kgeorgiy.java.advanced.concurrent.ListIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IterativeParallelism implements ListIP {
    private final ParallelMapper threadPool;

    public IterativeParallelism() {
        threadPool = null;
    }

    public IterativeParallelism(ParallelMapper threadPool) {
        this.threadPool = threadPool;
    }

    private <T, R> R abstractAction(int threadsCount, final List<? extends T> list,
                                    final Function<Stream<? extends T>, ? extends R> partialResultsGetter,
                                    final Function<Stream<? extends R>, ? extends R> partialResultsJoiner)
            throws InterruptedException {
        if (threadsCount <= 0) {
            throw new IllegalArgumentException("Cannot evaluate using 0 or less threads");
        }
        threadsCount = Math.min(threadsCount, list.size());
        int remainder = list.size() % threadsCount;
        int elementsForOneThread = list.size() / threadsCount;
        final List<Thread> threads = new ArrayList<>(threadsCount);
        List<Stream<? extends T>> parts = new ArrayList<>();

        int curElem = 0;
        for (int i = 0; i < threadsCount; i++) {
            int leftBorder = curElem;
            int curLength;
            if (remainder > 0) {
                curLength = elementsForOneThread + 1;
                remainder--;
            } else {
                curLength = elementsForOneThread;
            }
            int rightBorder = leftBorder + curLength;
            curElem += curLength;
            parts.add(list.subList(leftBorder, rightBorder).stream());
        }

        final List<R> partialAnswers;
        if (threadPool != null) {
            partialAnswers = threadPool.map(partialResultsGetter, parts);
        } else {
            partialAnswers = new ArrayList<>(threadsCount);
            for (int i = 0; i < threadsCount; i++) {
                partialAnswers.add(null);
            }
            for (int i = 0; i < threadsCount; i++) {
                final int threadNum = i;
                Thread thread = new Thread(
                        () -> partialAnswers.set(threadNum,
                                partialResultsGetter.apply(parts.get(threadNum)))
                );
                threads.add(thread);
                thread.start();
            }
        }

        ConcurrentUtils.joinAll(threads);
        return partialResultsJoiner.apply(partialAnswers.stream());
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> values,
                         Comparator<? super T> comparator) throws InterruptedException {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot choose min/max from list with no elements");
        }
        Function<Stream<? extends T>, ? extends T> maxByComp = stream -> stream.max(comparator).get();
        return abstractAction(
                threads,
                values,
                maxByComp,
                maxByComp
        );
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> values,
                         Comparator<? super T> comparator) throws InterruptedException {
        return maximum(threads, values, comparator.reversed());
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> values,
                           Predicate<? super T> predicate) throws InterruptedException {
        return !any(threads, values, predicate.negate());
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> values,
                           Predicate<? super T> predicate) throws InterruptedException {
        if (values.isEmpty()) {
            return false;
        }
        return abstractAction(
                threads,
                values,
                stream -> stream.anyMatch(predicate),
                stream -> stream.anyMatch(Boolean::booleanValue)
        );
    }

    @Override
    public String join(int threads, List<?> values) throws InterruptedException {
        if (values.isEmpty()) {
            return "";
        }
        return abstractAction(
                threads,
                values,
                stream -> stream.map(Object::toString).collect(Collectors.joining()),
                stream -> stream.collect(Collectors.joining())
        );
    }

    private <T, U> List<U> performAndToList(int threads, List<? extends T> values,
                                            Function<Stream<? extends T>, Stream<? extends U>> f)
            throws InterruptedException {
        if (values.isEmpty()) {
            return new ArrayList<>();
        }
        return abstractAction(
                threads,
                values,
                stream -> f.apply(stream).collect(Collectors.toList()),
                stream -> stream.flatMap(Collection::stream).collect(Collectors.toList())
        );
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> values, Predicate<? super T> predicate)
            throws InterruptedException {
        return performAndToList(
                threads,
                values,
                stream -> stream.filter(predicate)
        );
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values, Function<? super T, ? extends U> f)
            throws InterruptedException {
        return performAndToList(
                threads,
                values,
                stream -> stream.map(f)
        );
    }
}
