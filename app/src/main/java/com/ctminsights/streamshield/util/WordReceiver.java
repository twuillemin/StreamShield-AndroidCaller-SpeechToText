package com.ctminsights.streamshield.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import kotlin.Pair;

public abstract class WordReceiver {
    private static final int EVENT_TEXT = 1;
    private static final int EVENT_END_OF_SENTENCE = 2;
    private static final int EVENT_ERROR = 3;
    private static final int EVENT_RESET = 4;

    private final Queue<Pair<Integer, String>> events = new ConcurrentLinkedQueue<>();

    // The monitor to unlock thread waiting for reading when data is available
    private final Object monitor = new Object();

    // The flag to exit the thread
    private boolean processResults = true;
    private Thread processorThread = null;

    protected final WordReceiver nextStage;

    public WordReceiver(
            @Nullable final WordReceiver nextStage
    ) {
        this.nextStage = nextStage;
    }

    /* ---------------------------------------------------------- */
    /*                                                            */
    /*                BASE FEATURES IMPLEMENTATION                */
    /*                                                            */
    /* ---------------------------------------------------------- */

    public void putText(@NotNull String text) {
        if (text.isBlank()) {
            return;
        }

        events.add(new Pair<>(EVENT_TEXT, text));
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    public void signalEndOfSentence() {
        events.add(new Pair<>(EVENT_END_OF_SENTENCE, ""));
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    public void signalError(@NotNull String error) {
        events.add(new Pair<>(EVENT_ERROR, error));
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    public void reset() {
        events.add(new Pair<>(EVENT_RESET, ""));
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    public void start() {
        if (processorThread != null && processorThread.isAlive()) {
            return;
        }

        // Ensure no remaining events
        this.events.clear();

        final Runnable runnable = () -> {
            while (processResults) {
                // Wait for some results to be available
                try {
                    synchronized (monitor) {
                        monitor.wait();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                while (!events.isEmpty() && processResults) {
                    final Pair<Integer, String> event = events.remove();

                    switch (event.component1()) {
                        case EVENT_TEXT: {
                            processTextReceived(event.component2());
                            break;
                        }
                        case EVENT_END_OF_SENTENCE: {
                            processEndOfSentence();
                            break;
                        }
                        case EVENT_ERROR: {
                            processError(event.component2());
                            break;
                        }
                        case EVENT_RESET: {
                            processReset();
                            break;
                        }
                    }
                }
            }
        };

        processResults = true;
        processorThread = new Thread(runnable);
        processorThread.start();
    }

    public void stop() {
        if (processorThread != null) {
            this.processResults = false;

            synchronized (monitor) {
                monitor.notifyAll();
            }

            try {
                this.processorThread.join(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            this.processorThread = null;
        }
    }

    public abstract void processTextReceived(@NotNull String text);

    public abstract void processEndOfSentence();

    public abstract void processError(@NotNull String text);

    public abstract void processReset();

}
