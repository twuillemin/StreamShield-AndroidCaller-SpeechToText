package com.ctminsights.streamshield.util;

import org.jetbrains.annotations.NotNull;

public class WordEmitterSink implements IWordEmitter {

    public void putPartialResult(@NotNull String partialResult){
        // Do nothing
    }

    @Override
    public void endOfSentence() {
        // Do nothing
    }

    public void signalError(@NotNull String error){
        // Do nothing
    }

    public void reset(){
        // Do nothing
    }

    public void start(){
        // Do nothing
    }

    public void stop(){
        // Do nothing
    }
}
