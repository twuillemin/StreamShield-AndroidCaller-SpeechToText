package com.ctminsights.streamshield.util;

import android.content.Context;
import android.util.Log;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.IOException;

public class SpeechRecognizer implements RecognitionListener {

    private static final String TAG = SpeechRecognizer.class.getSimpleName();

    private Model model;
    private SpeechStreamService speechStreamService;
    private final WordReceiver wordReceiver;
    private final ByteBufferedInputStream buffer;
    private final float sampleRate;
    private final float numberOfChannels;

    // For the stereo to mono conversion
    private long stereoToMonoByteCount = 0;
    private byte[] stereoToMonoTemp = null;

    public SpeechRecognizer(
            @NotNull final Context context,
            @NotNull final WordReceiver wordReceiver,
            final int sampleRate,
            final int numberOfChannels
    ) {
        this.wordReceiver = wordReceiver;
        this.buffer = new ByteBufferedInputStream(1024);
        this.sampleRate = (float) sampleRate;
        this.numberOfChannels = numberOfChannels;

        LibVosk.setLogLevel(LogLevel.DEBUG);

        // Init voice recognition  model();
        StorageService.unpack(
                context,
                "model-en-us",
                "model",
                (model) -> this.model = model,
                (exception) -> setErrorState("Failed to unpack the model: " + exception.getMessage()));
    }

    @Override
    public void onPartialResult(final @Nullable String hypothesis) {
        if (hypothesis == null) {
            return;
        }

        try {
            JSONObject jObject = new JSONObject(hypothesis);
            final Object hypothesisRawValue = jObject.get("partial");
            if (hypothesisRawValue instanceof String) {
                final String hypothesisValue = (String) hypothesisRawValue;

                if (!hypothesisValue.isBlank()) {
                    Log.d(TAG, "Partial result received: " + hypothesis.replace('\n', ' '));
                    wordReceiver.putText(hypothesisValue);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Unable to read partial result", e);
        }
    }

    @Override
    public void onResult(final @Nullable String hypothesis) {
        if (hypothesis == null) {
            return;
        }

        try {
            JSONObject jObject = new JSONObject(hypothesis);
            final Object hypothesisRawValue = jObject.get("text");
            if (hypothesisRawValue instanceof String) {
                final String hypothesisValue = (String) hypothesisRawValue;

                if (!hypothesisValue.isBlank()) {
                    Log.d(TAG, "Result received: " + hypothesisValue.replace('\n', ' '));
                    wordReceiver.signalEndOfSentence();
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Unable to read result", e);
        }
    }

    @Override
    public void onFinalResult(final @Nullable String hypothesis) {
        if (hypothesis == null) {
            return;
        }

        try {
            JSONObject jObject = new JSONObject(hypothesis);
            final Object hypothesisRawValue = jObject.get("text");
            if (hypothesisRawValue instanceof String) {
                final String hypothesisValue = (String) hypothesisRawValue;
                if (!hypothesisValue.isBlank()) {
                    Log.d(TAG, "Final result received: " + hypothesisValue.replace('\n', ' '));
                    wordReceiver.signalEndOfSentence();
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Unable to read result", e);
        }

        if (speechStreamService != null) {
            speechStreamService.stop();
            speechStreamService = null;
        }
    }

    @Override
    public void onError(final @Nullable Exception e) {
        if (e == null) {
            return;
        }

        if (e.getMessage() == null) {
            setErrorState(e.toString());
        } else {
            setErrorState(e.getMessage());
        }
    }

    @Override
    public void onTimeout() {
        wordReceiver.signalError("Timeout");
    }

    public void addBytes(final @NotNull byte[] bytes) {
        if (numberOfChannels == 1) {
            addBytesFromMono(bytes);
        } else if (numberOfChannels == 2) {
            addBytesFromStereo(bytes);
        } else {
            throw new RuntimeException("Only mono or stereo data are supported");
        }
    }

    private void addBytesFromMono(final @NotNull byte[] bytes) {
        buffer.addBytes(bytes);
    }

    private synchronized void addBytesFromStereo(final @NotNull byte[] bytes) {
        // Keep the same temp array if possible to avoid allocating to frequently
        if (stereoToMonoTemp == null || stereoToMonoTemp.length < bytes.length) {
            stereoToMonoTemp = new byte[bytes.length];
        }

        int tempUsedSize = 0;

        for (byte aByte : bytes) {
            if (stereoToMonoByteCount % 4 == 0 || (stereoToMonoByteCount - 1) % 4 == 0) {
                stereoToMonoTemp[tempUsedSize] = aByte;
                tempUsedSize++;
            }
            stereoToMonoByteCount++;
        }

        buffer.addBytes(stereoToMonoTemp, 0, tempUsedSize);
    }

    public void stop() {
        Log.i(TAG, "Stop recognizing a stream");

        final SpeechStreamService currentService = speechStreamService;

        // Do not stop an already stopped process
        if (currentService == null) {
            Log.e(TAG, "Stop has been called on an already stopped instance");

            return;
        }

        // Finish the stream before stopping the service
        buffer.setEndOfStreamReached();

        // Stop the voice recognition services
        currentService.stop();
        speechStreamService = null;

        // Close the stream before stopping the service
        buffer.close();

        Log.i(TAG, "Stream recognizing has stopped properly");
    }

    public void start() {
        Log.i(TAG, "Start to recognize a Stream (sample rate: " + sampleRate + ")");

        if (speechStreamService != null) {
            Log.e(TAG, "start was called on an already running speech recognizer");
            throw new RuntimeException("The SpeechRecognizer instance has already been started");
        }

        wordReceiver.reset();

        if (this.model == null) {
            wordReceiver.signalError("Recognition model is not loaded already");
            return;
        }

        try {
            // Restart the buffer
            buffer.restart();

            // Re-initialize the byte counter
            stereoToMonoByteCount = 0;

            final Recognizer recognizer = new Recognizer(model, sampleRate);
            Log.i(TAG, "Recognizer created");

            speechStreamService = new SpeechStreamService(recognizer, buffer, sampleRate);
            Log.i(TAG, "SpeechStreamService created");

            speechStreamService.start(this);
            Log.i(TAG, "SpeechStreamService started");
        } catch (final IOException e) {
            setErrorState(e.toString());
            speechStreamService = null;
        }
    }

    private void setErrorState(final @NotNull String message) {
        wordReceiver.signalError(message);

        if (speechStreamService != null) {
            speechStreamService.stop();
            speechStreamService = null;
        }
    }
}
