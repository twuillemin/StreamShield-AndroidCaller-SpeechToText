package com.ctminsights.streamshield.util;

import static com.ctminsights.streamshield.util.TextViewUpdaterHandler.SPEECH_OUTPUT_ACTION_APPEND;
import static com.ctminsights.streamshield.util.TextViewUpdaterHandler.SPEECH_OUTPUT_ACTION_APPEND_LINE;
import static com.ctminsights.streamshield.util.TextViewUpdaterHandler.SPEECH_OUTPUT_ACTION_CLEAR;

import android.os.Handler;
import android.os.Message;

import org.jetbrains.annotations.NotNull;

public class WordReceiverTextViewUpdater extends WordReceiver {

    private final Handler outputUpdateHandler;

    public WordReceiverTextViewUpdater(
            @NotNull final Handler outputUpdateHandler
    ) {
        super(null);
        this.outputUpdateHandler = outputUpdateHandler;
    }

    public WordReceiverTextViewUpdater(
            @NotNull final WordReceiver nextStage,
            @NotNull final Handler outputUpdateHandler
    ) {
        super(nextStage);
        this.outputUpdateHandler = outputUpdateHandler;
    }


    /* ---------------------------------------------------------- */
    /*                                                            */
    /*                BASE CLASS FUNCTIONS                        */
    /*                                                            */
    /* ---------------------------------------------------------- */

    public void processTextReceived(@NotNull String text) {
        appendToOutput("/" + text);

        if (nextStage != null) {
            nextStage.putText(text);
        }
    }

    public void processEndOfSentence() {
        appendAsLineToOutput("");

        if (nextStage != null) {
            nextStage.signalEndOfSentence();
        }
    }

    public void processError(@NotNull String text) {
        appendAsLineToOutput("Error :" + text);
        appendAsLineToOutput("");

        if (nextStage != null) {
            nextStage.signalError(text);
        }
    }

    public void processReset() {
        clearOutput();

        if (nextStage != null) {
            nextStage.reset();
        }
    }


    /* ---------------------------------------------------------- */
    /*                                                            */
    /*                MESSAGE SENDING TO THE UI                   */
    /*                                                            */
    /* ---------------------------------------------------------- */

    private void clearOutput() {
        outputUpdateHandler.sendEmptyMessage(SPEECH_OUTPUT_ACTION_CLEAR);
    }

    private void appendToOutput(final @NotNull String text) {
        outputUpdateHandler.sendMessage(
                Message.obtain(
                        outputUpdateHandler,
                        SPEECH_OUTPUT_ACTION_APPEND,
                        text
                )
        );
    }

    private void appendAsLineToOutput(final @NotNull String text) {
        outputUpdateHandler.sendMessage(
                Message.obtain(
                        outputUpdateHandler,
                        SPEECH_OUTPUT_ACTION_APPEND_LINE,
                        text
                )
        );
    }
}
