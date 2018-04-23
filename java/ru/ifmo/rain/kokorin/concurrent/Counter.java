package ru.ifmo.rain.kokorin.concurrent;

class Counter {
    private int counter = 0;

    void increment() {
        counter++;
    }

    int get() {
        return counter;
    }
}