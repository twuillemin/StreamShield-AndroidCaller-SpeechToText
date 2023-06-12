package com.ctminsights.streamshield.util;

import com.joom.xxhash.XxHash64;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WordReceiverSigner extends WordReceiver {

    // Keep the previous words. Should only be used inside the thread.
    final private List<String> previousWords = new ArrayList<>();

    public WordReceiverSigner(@NotNull final WordReceiver nextStage) {
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
        previousWords.clear();
        if (nextStage != null) {
            nextStage.reset();
        }
    }

    /* ---------------------------------------------------------- */
    /*                                                            */
    /*                LOCAL FEATURES                              */
    /*                                                            */
    /* ---------------------------------------------------------- */

    private void processNewPartialResult(final @NotNull String word) {

        previousWords.add(word);

        if (previousWords.size() < 7) {
            return;
        }

        // Get the last 3 words
        final String word1 = previousWords.get(4);
        final String word2 = previousWords.get(5);
        final String word3 = previousWords.get(6);

        // Prepare for new words
        previousWords.clear();

        // Emit the hash
        final String toHash = String.format("%s %s %s", word1, word2, word3);
        final long hash = XxHash64.hashForArray(toHash.getBytes(StandardCharsets.UTF_8));

        // TODO send the hash somewhere

        if (nextStage != null) {
            final String output = String.format(Locale.getDefault(), "%s: %X", toHash, hash);
            nextStage.putText(output);
        }
    }
}
