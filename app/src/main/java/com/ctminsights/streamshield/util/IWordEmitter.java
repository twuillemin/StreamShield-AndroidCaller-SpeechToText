package com.ctminsights.streamshield.util;

import org.jetbrains.annotations.NotNull;

public interface IWordEmitter {
    /**
     * Receive a partial result from the source. Any partial result should include the previous results plus the new data.
     * For example "aaa", then "aaa bbb" then "aaa bbb ccc".
     *
     * @param partialResult The result to append.
     */
    void putPartialResult(@NotNull String partialResult);

    /**
     * Signal to the word emitter that the current stream of words ended.
     */
    void endOfSentence();

    /**
     * Forward an error from the previous layers to the word emitter.
     *
     * @param error The error message.
     */
    void signalError(@NotNull String error);

    /**
     * Restart completely the state of the word emitter.
     */
    void reset();

    /**
     * Shutdown the word emitter.
     */
    void start();

    /**
     * Shutdown the word emitter.
     */
    void stop();
}
