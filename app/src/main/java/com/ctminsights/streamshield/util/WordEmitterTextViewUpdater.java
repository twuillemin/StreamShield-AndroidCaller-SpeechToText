package com.ctminsights.streamshield.util;

import static com.ctminsights.streamshield.util.TextViewUpdaterHandler.SPEECH_OUTPUT_ACTION_APPEND;
import static com.ctminsights.streamshield.util.TextViewUpdaterHandler.SPEECH_OUTPUT_ACTION_APPEND_LINE;
import static com.ctminsights.streamshield.util.TextViewUpdaterHandler.SPEECH_OUTPUT_ACTION_CLEAR;
import static java.lang.Integer.min;

import android.os.Handler;
import android.os.Message;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import kotlin.Pair;

public class WordEmitterTextViewUpdater implements IWordEmitter {

    private static final int EVENT_PARTIAL = 1;
    private static final int EVENT_END_OF_SENTENCE = 2;
    private static final int EVENT_ERROR = 3;
    private static final int EVENT_RESET = 4;

    private final Queue<Pair<Integer, String>> events = new ConcurrentLinkedQueue<>();

    // The monitor to unlock thread waiting for reading when data is available
    private final Object monitor = new Object();

    // The flag to exit the thread
    private boolean processResults = true;
    private Thread processorThread = null;

    private final Handler outputUpdateHandler;

    // Keep the previous words. Should only be used inside the thread.
    private List<Pair<String, Boolean>> previousWordsAndEmitted = new ArrayList<>();

    public WordEmitterTextViewUpdater(
            @NotNull final Handler outputUpdateHandler
    ) {
        this.outputUpdateHandler = outputUpdateHandler;
    }

    /* ---------------------------------------------------------- */
    /*                                                            */
    /*                INTERFACE IMPLEMENTATION                    */
    /*                                                            */
    /* ---------------------------------------------------------- */

    public void putPartialResult(@NotNull String partialResult) {
        if (partialResult.isBlank()) {
            return;
        }

        events.add(new Pair<>(EVENT_PARTIAL, partialResult));
        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    public void endOfSentence() {
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

    /* ---------------------------------------------------------- */
    /*                                                            */
    /*                THREADED FUNCTIONS                          */
    /*                                                            */
    /* ---------------------------------------------------------- */
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
                        case EVENT_PARTIAL: {
                            processNewPartialResult(event.component2());
//                            appendToOutput("/" + event.component2());
                            break;
                        }
                        case EVENT_END_OF_SENTENCE: {
                            previousWordsAndEmitted.clear();
                            appendAsLineToOutput("");
                            break;
                        }
                        case EVENT_ERROR: {
                            appendAsLineToOutput("Error :" + event.component2());
                            appendAsLineToOutput("");
                        }
                        case EVENT_RESET: {
                            previousWordsAndEmitted.clear();
                            clearOutput();
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

    private void processNewPartialResult(final @NotNull String newResult) {
        final String[] updatedWords = newResult.split(" ");

        final int similarSize = min(previousWordsAndEmitted.size(), updatedWords.length);

        // Compute the common part of the new list
        final List<Pair<String, Boolean>> updatedCommonItems = IntStream.range(0, similarSize)
                .mapToObj(i -> {
                    final String previousWord = previousWordsAndEmitted.get(i).component1();
                    final boolean isPreviousEmitted = previousWordsAndEmitted.get(i).component2();
                    final String updatedWord = updatedWords[i];

                    // If the word was previously emitted, nothing to do.
                    if (isPreviousEmitted) {
                        return previousWordsAndEmitted.get(i);
                    } else {
                        // The world was not emitted, second time seen, mark as emitted
                        if (previousWord.equals(updatedWord)) {
                            return new Pair<>(previousWord, true);
                        }
                        // Probably fixed by having more sound available, fix the current word
                        else {
                            return new Pair<>(updatedWord, false);
                        }
                    }
                })
                .collect(Collectors.toList());

        // Find the word to emit
        final List<String> toEmit = IntStream.range(0, similarSize)
                .mapToObj(i -> {
                    if (previousWordsAndEmitted.get(i).component2() != updatedCommonItems.get(i).component2()) {
                        return updatedCommonItems.get(i).component1();
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Items to add
        final List<Pair<String, Boolean>> newItems = IntStream.range(similarSize, updatedWords.length)
                .mapToObj(i -> new Pair<>(updatedWords[i], false))
                .collect(Collectors.toList());


        // Make the new list
        previousWordsAndEmitted = Stream.concat(updatedCommonItems.stream(), newItems.stream())
                .collect(Collectors.toList());

        // Emit the words
        toEmit.forEach(word -> appendToOutput("/" + word));
    }

    /* ---------------------------------------------------------- */
    /*                                                            */
    /*                MESSAGE SENDING TO THE UI                   */
    /*                                                            */
    /* ---------------------------------------------------------- */

    private void clearOutput() {
        this.outputUpdateHandler.sendEmptyMessage(SPEECH_OUTPUT_ACTION_CLEAR);
    }

    private void appendToOutput(final @NotNull String text) {
        this.outputUpdateHandler.sendMessage(
                Message.obtain(
                        this.outputUpdateHandler,
                        SPEECH_OUTPUT_ACTION_APPEND,
                        text
                )
        );
    }

    private void appendAsLineToOutput(final @NotNull String text) {
        this.outputUpdateHandler.sendMessage(
                Message.obtain(
                        this.outputUpdateHandler,
                        SPEECH_OUTPUT_ACTION_APPEND_LINE,
                        text
                )
        );
    }
}
