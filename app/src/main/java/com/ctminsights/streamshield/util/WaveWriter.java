package com.ctminsights.streamshield.util;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class WaveWriter {
    private static final String TAG = WaveWriter.class.getSimpleName();

    private final Context context;
    private final long sampleRate;
    private final int bitsPerSample;
    private final int numberOfChannels;
    private final ByteBufferedInputStream buffer;

    private boolean isRunning;
    private Thread readerThread;

    public WaveWriter(
            final @NotNull Context context,
            final int sampleRate,
            final int bitsPerSample,
            final int numberOfChannels) {
        this.context = context;
        this.sampleRate = sampleRate;
        this.bitsPerSample = bitsPerSample;
        this.numberOfChannels = numberOfChannels;

        this.buffer = new ByteBufferedInputStream(1024);

        isRunning = false;
    }

    public void addBytes(final @NotNull byte[] bytes) {
        buffer.addBytes(bytes);
    }

    public void stop() {
        Log.i(TAG, "Stop to record a wave file");

        // Stop the writing
        isRunning = false;

        final Thread currentThread = this.readerThread;

        // Do not stop an already stopped process
        if (currentThread == null) {
            Log.e(TAG, "Stop has been called on an already stopped instance");

            return;
        }

        // Finish the stream before stopping the service
        buffer.setEndOfStreamReached();

        // Remove the current thread from the instance
        this.readerThread = null;

        try {
            // Wait for the thread to finish
            currentThread.join(1000);
        } catch (final Exception e) {
            Log.e(TAG, "The writing thread did not finished in the expected time");
        }

        // Close the buffer
        buffer.close();

        Log.i(TAG, "Wave file recording has stopped properly");
    }

    public void start() {
        Log.i(TAG, "Start to record a wave file");

        if (isRunning) {
            Log.e(TAG, "startRecording was called on an already running recorder");
            throw new RuntimeException("The WaveRecorder instance has already been started");
        }

        final int frameSize = numberOfChannels * bitsPerSample / 8;

        // Set running mode
        isRunning = true;

        final Runnable readerRunnable = () -> {

            try (final OutputStream tempFile = context.openFileOutput("call.temp.wav", MODE_PRIVATE)) {
                // Write the header with an empty size (for now)
                writeWaveFileHeader(tempFile, 0);

                // Clear the buffer if it still holds previous data
                buffer.restart();

                final byte[] bytes = new byte[frameSize];

                long totalBytesRead = 0;

                while (isRunning) {
                    try {
                        final int bytesRead = buffer.read(bytes);

                        if (bytesRead > 0) {
                            tempFile.write(bytes, 0, bytesRead);
                            totalBytesRead += bytesRead;
                        } else if (bytesRead < 0) {
                            isRunning = false;
                        }
                    } catch (final IOException e) {
                        Log.e(TAG, "Unable to write data to temporary file", e);
                        // Can not write anymore
                        break;
                    }
                }

                try {
                    tempFile.flush();
                    tempFile.close();
                } catch (IOException e) {
                    Log.e(TAG, "Unable to close properly the temporary file", e);
                    return;
                }

                try (final InputStream source = context.openFileInput("call.temp.wav");
                     final OutputStream targetToUpdate = context.openFileOutput("call.wav", MODE_PRIVATE)) {
                    // Write the header
                    writeWaveFileHeader(targetToUpdate, totalBytesRead);

                    // Copy the data
                    final byte[] data = new byte[frameSize];
                    while (source.read(data) != -1) {
                        targetToUpdate.write(data);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Unable to rewrite the update file header", e);
                    return;
                }

                // Let the buffer clean
                buffer.restart();

            } catch (Exception e) {
                Log.e(TAG, "Unable to open temp file for writing", e);
            }
        };

        readerThread = new Thread(readerRunnable);
        readerThread.start();
    }


    private void writeWaveFileHeader(
            final @NotNull OutputStream out,
            final long audioDataLength) throws IOException {
        // Local buffer for header construction
        final byte[] header = new byte[44];

        // Size of the overall file - 8 bytes, in bytes (32-bit integer).
        final long waveFileFileSize = audioDataLength + 44 - 8;
        // Byte rate: (Sample Rate * BitsPerSample * Channels) / 8.
        final long byteRate = (sampleRate * bitsPerSample * numberOfChannels) / 8;
        // Block align: (BitsPerSample * Channels) / 8
        final int blockAlign = (bitsPerSample * numberOfChannels) / 8;

        // Marks the file as a RIFF/WAVE file.
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        // 	Size of the overall file - 8 bytes, in bytes (32-bit integer)
        header[4] = (byte) (waveFileFileSize & 0xff);
        header[5] = (byte) ((waveFileFileSize >> 8) & 0xff);
        header[6] = (byte) ((waveFileFileSize >> 16) & 0xff);
        header[7] = (byte) ((waveFileFileSize >> 24) & 0xff);
        // File Type Header. For our purposes, it always equals “WAVE”.
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        // Format chunk marker: "fmt ". Includes trailing space.
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        // Length of format data as listed above : RIFF + size + WAVE + fmt : 16 bytes
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        // Type of format (1 is PCM) - 2 byte integer
        header[20] = 1;
        header[21] = 0;
        // Number of Channels - 2 byte integer
        header[22] = (byte) numberOfChannels;
        header[23] = 0;
        // Sample Rate: 32 byte integer. Common values are 44100 (CD), 48000 (DAT). Sample Rate = Number of Samples per second, or Hertz.
        header[24] = (byte) (sampleRate & 0xff);
        header[25] = (byte) ((sampleRate >> 8) & 0xff);
        header[26] = (byte) ((sampleRate >> 16) & 0xff);
        header[27] = (byte) ((sampleRate >> 24) & 0xff);
        // ByteRate: 32 byte integer. (Sample Rate * BitsPerSample * Channels) / 8.
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        // Block Align: (BitsPerSample * Channels) / 8
        header[32] = (byte) (blockAlign & 0xff);
        header[33] = (byte) ((blockAlign >> 8) & 0xff);
        // Bits per sample
        header[34] = (byte) (bitsPerSample & 0xff);
        header[35] = (byte) ((bitsPerSample >> 8) & 0xff);
        // “data” chunk header. Marks the beginning of the data section.
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        // Size of the data section.
        header[40] = (byte) (audioDataLength & 0xff);
        header[41] = (byte) ((audioDataLength >> 8) & 0xff);
        header[42] = (byte) ((audioDataLength >> 16) & 0xff);
        header[43] = (byte) ((audioDataLength >> 24) & 0xff);

        out.write(header, 0, 44);
    }
}
