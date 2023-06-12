package com.ctminsights.streamshield.util;

import static java.lang.Integer.min;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import kotlin.Pair;

/**
 * Receive a text, debounce it and issue individual words to the next stage.
 */
public class WordReceiverDebouncer extends WordReceiver {

    // Keep the previous words. Should only be used inside the thread.
    private List<Pair<String, Boolean>> previousWordsAndEmitted = new ArrayList<>();

    public WordReceiverDebouncer(@NotNull final WordReceiver nextStage) {
        super(nextStage);
    }


    /* ---------------------------------------------------------- */
    /*                                                            */
    /*                BASE CLASS FUNCTIONS                        */
    /*                                                            */
    /* ---------------------------------------------------------- */

    public void processTextReceived(@NotNull String text) {
        processNewPartialResult(text);
    }

    public void processEndOfSentence() {
        previousWordsAndEmitted.clear();
        if (nextStage != null) {
            nextStage.signalEndOfSentence();
        }
    }

    public void processError(@NotNull String text) {
        if (nextStage != null) {
            nextStage.signalError(text);
        }
    }

    public void processReset() {
        previousWordsAndEmitted.clear();
        if (nextStage != null) {
            nextStage.reset();
        }
    }

    /* ---------------------------------------------------------- */
    /*                                                            */
    /*                LOCAL FEATURES                              */
    /*                                                            */
    /* ---------------------------------------------------------- */

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

        if (nextStage != null) {
            // Emit the words
            toEmit.forEach(nextStage::putText);
        }
    }
}
