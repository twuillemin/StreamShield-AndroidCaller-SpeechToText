package com.ctminsights.streamshield.util;

import org.jetbrains.annotations.NotNull;

public class WordReceiverSink extends WordReceiver {

    public WordReceiverSink() {
        super(null);
    }

    /* ---------------------------------------------------------- */
    /*                                                            */
    /*                BASE CLASS FUNCTIONS                        */
    /*                                                            */
    /* ---------------------------------------------------------- */

    public void processTextReceived(@NotNull String text) {
        // Do nothing
    }

    public void processEndOfSentence() {
        // Do nothing
    }

    public void processError(@NotNull String text) {
        // Do nothing
    }

    public void processReset() {
        // Do nothing
    }
}
