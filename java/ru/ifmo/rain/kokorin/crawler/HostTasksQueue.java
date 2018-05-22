package ru.ifmo.rain.kokorin.crawler;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;

class HostTasksQueue {
    private Queue<Runnable> tasksWaiting;
    private int freeTasks;
    private ExecutorService hostDownloaders;

    HostTasksQueue(ExecutorService hostDownloaders, int perHost) {
        tasksWaiting = new ArrayDeque<>();
        freeTasks = perHost;
        this.hostDownloaders = hostDownloaders;
    }

    synchronized void addTask(Runnable downloadFromHostTask) {
        if (freeTasks == 0) {
            tasksWaiting.add(downloadFromHostTask);
        } else {
            freeTasks -= 1;
            hostDownloaders.submit(downloadFromHostTask);
        }
    }

    synchronized void runNextTask() {
        if (tasksWaiting.isEmpty()) {
            freeTasks += 1;
        } else {
            hostDownloaders.submit(tasksWaiting.poll());
        }
    }
}
