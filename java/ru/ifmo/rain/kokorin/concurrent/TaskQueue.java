package ru.ifmo.rain.kokorin.concurrent;

import java.util.ArrayDeque;
import java.util.Queue;

class TaskQueue {
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private static final int MAX_SIZE = 4194304;

    synchronized Runnable getAndRemoveOne() throws InterruptedException {
        while (tasks.isEmpty()) {
            wait();
        }
        Runnable task = tasks.poll();
        notifyAll();
        return task;
    }

    synchronized void addTask(Runnable task) throws InterruptedException {
        while (tasks.size() == MAX_SIZE) {
            wait();
        }
        tasks.add(task);
        notifyAll();
    }
}
