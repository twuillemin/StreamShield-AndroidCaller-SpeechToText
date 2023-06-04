package cy.adiutrix.micanalyzer;

import static cy.adiutrix.micanalyzer.util.TextViewUpdaterHandler.SPEECH_OUTPUT_ACTION_APPEND_LINE;
import static cy.adiutrix.micanalyzer.util.TextViewUpdaterHandler.SPEECH_OUTPUT_ACTION_CLEAR;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.jetbrains.annotations.NotNull;
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

import cy.adiutrix.micanalyzer.util.ByteBufferedInputStream;

public class SpeechRecognizer implements RecognitionListener {

    private static final String TAG = SpeechRecognizer.class.getSimpleName();

    private Model model;
    private SpeechStreamService speechStreamService;
    private long byteCount;
    private final Handler outputUpdateHandler;
    private final ByteBufferedInputStream buffer;
    private final float sampleRate;
    private final float numberOfChannels;

    public SpeechRecognizer(
            @NotNull final Context context,
            @NotNull final Handler outputUpdateHandler,
            final int sampleRate,
            final int numberOfChannels
    ) {
        this.outputUpdateHandler = outputUpdateHandler;
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
                (exception) -> setErrorState("Failed to unpack the model" + exception.getMessage()));
    }

    @Override
    public void onPartialResult(final String hypothesis) {
        Log.i(TAG, "Partial result received: " + hypothesis);

        try {
            JSONObject jObject = new JSONObject(hypothesis);
            final Object hypothesisRawValue = jObject.get("partial");
            if (hypothesisRawValue instanceof String) {
                final String hypothesisValue = (String) hypothesisRawValue;
                if (!hypothesisValue.isBlank()) {
                    append("/" + hypothesisValue);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Unable to read partial result", e);
        }
    }

    @Override
    public void onResult(final String hypothesis) {
        Log.i(TAG, "Result received: " + hypothesis);

        try {
            JSONObject jObject = new JSONObject(hypothesis);
            final Object hypothesisRawValue = jObject.get("text");
            if (hypothesisRawValue instanceof String) {
                final String hypothesisValue = (String) hypothesisRawValue;
                if (!hypothesisValue.isBlank()) {
                    appendLine("/\n=>" + hypothesisValue);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Unable to read result", e);
        }
    }

    @Override
    public void onFinalResult(final String hypothesis) {
        Log.i(TAG, "Final result received: " + hypothesis);

        try {
            JSONObject jObject = new JSONObject(hypothesis);
            final Object hypothesisRawValue = jObject.get("text");
            if (hypothesisRawValue instanceof String) {
                final String hypothesisValue = (String) hypothesisRawValue;
                if (!hypothesisValue.isBlank()) {
                    appendLine("\n=====>" + hypothesisValue);
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
    public void onError(final Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        appendLine("TIMEOUT");
    }

    public void addBytes(byte[] bytes) {
        if (numberOfChannels == 1) {
            addBytesFromMono(bytes);
        } else if (numberOfChannels == 2) {
            addBytesFromStereo(bytes);
        } else {
            throw new RuntimeException("Only mono or stereo data are supported");
        }
    }

    private void addBytesFromMono(byte[] bytes) {
        buffer.addBytes(bytes);
    }

    private void addBytesFromStereo(byte[] bytes) {
        final byte[] temp = new byte[bytes.length];
        int tempUsedSize = 0;

        for (byte aByte : bytes) {
            if (byteCount % 4 == 0 || (byteCount - 1) % 4 == 0) {
                temp[tempUsedSize] = aByte;
                tempUsedSize++;
            }
            byteCount++;
        }

        final byte[] resized = new byte[tempUsedSize];
        System.arraycopy(temp, 0, resized, 0, tempUsedSize);

        buffer.addBytes(resized);
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

        clearOutput();

        if (this.model == null) {
            appendLine(String.valueOf(R.string.recognition_model_not_loaded_already));
            return;
        }

        try {
            // Restart the buffer
            buffer.restart();

            // Reinit the byte counter
            byteCount = 0;

            final Recognizer recognizer = new Recognizer(model, sampleRate);
            Log.i(TAG, "Recognizer created");

            speechStreamService = new SpeechStreamService(recognizer, buffer, sampleRate);
            Log.i(TAG, "SpeechStreamService created");

            speechStreamService.start(this);
            Log.i(TAG, "SpeechStreamService started");
        } catch (IOException e) {
            setErrorState(e.getMessage());
            speechStreamService = null;
        }
    }

    private void setErrorState(String message) {
        appendLine(message);

        if (speechStreamService != null) {
            speechStreamService.stop();
            speechStreamService = null;
        }
    }

    private void clearOutput() {
        this.outputUpdateHandler.sendEmptyMessage(SPEECH_OUTPUT_ACTION_CLEAR);
    }

    private void append(final @NotNull String text) {
        this.outputUpdateHandler.sendMessage(
                Message.obtain(
                        this.outputUpdateHandler,
                        SPEECH_OUTPUT_ACTION_APPEND_LINE,
                        text
                )
        );
    }

    private void appendLine(final @NotNull String text) {
        this.outputUpdateHandler.sendMessage(
                Message.obtain(
                        this.outputUpdateHandler,
                        SPEECH_OUTPUT_ACTION_APPEND_LINE,
                        text
                )
        );
    }
}
