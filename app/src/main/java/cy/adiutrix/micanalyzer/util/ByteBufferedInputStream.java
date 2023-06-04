package cy.adiutrix.micanalyzer.util;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

public class ByteBufferedInputStream extends InputStream {

    private static final String TAG = ByteBufferedInputStream.class.getSimpleName();

    // The buffer and its access
    private byte[] buffer;
    private final Object bufferAccessMutex = new Object();

    // The reading and writing positions
    private int readingPosition = 0;
    private int writingPosition = 0;

    // The monitor to unlock thread waiting for reading when data is available
    static final Object monitor = new Object();

    // Indicate that the stream is finished
    private boolean endOfStreamReached = false;

    // Indicate that the stream is closed
    private boolean closed = false;

    public ByteBufferedInputStream(final int capacity) {
        buffer = new byte[capacity];
    }

    /**
     * Add a bytes array to the buffer.
     *
     * @param bytes the bytes to add.
     * @implNote This function is not synchronized itself as it synchronizes on internal data.
     */
    public void addBytes(final byte[] bytes) {
        // Buffer is modified, so lock on it
        synchronized (bufferAccessMutex) {

            // If needed, extend the buffer
            final int sizeNeeded = writingPosition + bytes.length;
            if (buffer.length <= sizeNeeded) {
                // Compute the new size to allocate, double current for low value, otherwise increments by 50 kilobytes
                int futureSize = buffer.length;
                while (futureSize <= sizeNeeded) {
                    if (sizeNeeded < 200000) {
                        futureSize *= 2;
                    } else {
                        futureSize += 50000;
                    }
                }

                // Allocate the new buffer and copy the data
                byte[] futureBuffer = new byte[futureSize];
                System.arraycopy(buffer, 0, futureBuffer, 0, writingPosition);

                // Swap the buffer
                buffer = futureBuffer;
            }

            // Copy the data to the buffer
            System.arraycopy(bytes, 0, buffer, writingPosition, bytes.length);
            writingPosition = writingPosition + bytes.length;

            //Log.d(TAG, String.format("Added %d bytes, buffer size is now: %d", bytes.length, getBufferSize()));
        }

        // Signal data are available. As data were just added we can always wake up the
        // readers. Moreover, readers are checking if data is available when woken up.
        if (writingPosition > readingPosition) {
            synchronized (monitor) {
                monitor.notify();
            }
        }
    }

    /**
     * Reads the next byte of data from the input stream. The value byte is
     * returned as an <code>int</code> in the range <code>0</code> to
     * <code>255</code>. If no byte is available because the end of the stream
     * has been reached, the value <code>-1</code> is returned. This method
     * blocks until input data is available, the end of the stream is detected,
     * or an exception is thrown.
     *
     * <p> A subclass must provide an implementation of this method.
     *
     * @return the next byte of data, or <code>-1</code> if the end of the
     * stream is reached.
     * @implNote This method is synchronized as it starts by waiting for some data to be available before
     * locking the internal data (to not block the writer).
     */
    @Override
    public synchronized int read() {
        // While no data, meaning that read position == write position
        while (readingPosition >= writingPosition) {

            // If the there is no data and the stream is closed, fine
            if (endOfStreamReached || closed) {
                return -1;
            }

            // Otherwise, simply wait for data. When data are added, the monitor is interrupted.
            try {
                synchronized (monitor) {
                    monitor.wait();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        final int result;

        // Buffer is modified, so lock on it
        synchronized (bufferAccessMutex) {

            // Read the data
            result = buffer[readingPosition++] & 0xff;

            // If a lot of blank (10%) is left to start, compact
            if (readingPosition >= (buffer.length * 0.1)) {
                // Move data at the beginning
                for (int i = readingPosition; i < writingPosition; i++) {
                    buffer[i - readingPosition] = buffer[i];
                }

                // Adjust the position
                writingPosition = writingPosition - readingPosition;
                readingPosition = 0;
            }
        }

        return result;
    }

    /**
     * Reads up to <code>len</code> bytes of data from the input stream into
     * an array of bytes.  An attempt is made to read as many as
     * <code>len</code> bytes, but a smaller number may be read.
     * The number of bytes actually read is returned as an integer.
     *
     * <p> This method blocks until input data is available, end of file is
     * detected, or an exception is thrown.
     *
     * <p> If <code>len</code> is zero, then no bytes are read and
     * <code>0</code> is returned; otherwise, there is an attempt to read at
     * least one byte. If no byte is available because the stream is at end of
     * file, the value <code>-1</code> is returned; otherwise, at least one
     * byte is read and stored into <code>b</code>.
     *
     * <p> The first byte read is stored into element <code>b[off]</code>, the
     * next one into <code>b[off+1]</code>, and so on. The number of bytes read
     * is, at most, equal to <code>len</code>. Let <i>k</i> be the number of
     * bytes actually read; these bytes will be stored in elements
     * <code>b[off]</code> through <code>b[off+</code><i>k</i><code>-1]</code>,
     * leaving elements <code>b[off+</code><i>k</i><code>]</code> through
     * <code>b[off+len-1]</code> unaffected.
     *
     * <p> In every case, elements <code>b[0]</code> through
     * <code>b[off]</code> and elements <code>b[off+len]</code> through
     * <code>b[b.length-1]</code> are unaffected.
     *
     * <p> The <code>read(b,</code> <code>off,</code> <code>len)</code> method
     * for class <code>InputStream</code> simply calls the method
     * <code>read()</code> repeatedly. If the first such call results in an
     * <code>IOException</code>, that exception is returned from the call to
     * the <code>read(b,</code> <code>off,</code> <code>len)</code> method.  If
     * any subsequent call to <code>read()</code> results in a
     * <code>IOException</code>, the exception is caught and treated as if it
     * were end of file; the bytes read up to that point are stored into
     * <code>b</code> and the number of bytes read before the exception
     * occurred is returned. The default implementation of this method blocks
     * until the requested amount of input data <code>len</code> has been read,
     * end of file is detected, or an exception is thrown. Subclasses are
     * encouraged to provide a more efficient implementation of this method.
     *
     * @param b   the buffer into which the data is read.
     * @param off the start offset in array <code>b</code>
     *            at which the data is written.
     * @param len the maximum number of bytes to read.
     * @return the total number of bytes read into the buffer, or
     * <code>-1</code> if there is no more data because the end of
     * the stream has been reached.
     * @throws IOException               If the first byte cannot be read for any reason
     *                                   other than end of file, or if the input stream has been closed, or if
     *                                   some other I/O error occurs.
     * @throws NullPointerException      If <code>b</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException If <code>off</code> is negative,
     *                                   <code>len</code> is negative, or <code>len</code> is greater than
     *                                   <code>b.length - off</code>
     * @see InputStream#read()
     */
    public int read(byte[] b, int off, int len) throws IOException {
        if (closed) {
            throw new IOException("Stream is closed");
        }

        return super.read(b, off, len);
    }

    @Override
    public synchronized void close() {
        Log.d(TAG, "Closing the input stream");

        // When stream is finished, inform all the waiting threads that are blocked.
        this.closed = true;

        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    @Override
    public int available() {
        return writingPosition - readingPosition;
    }

    /**
     * Allows to define that the end of stream has been reached without actually closing the stream.
     * This allows client to receive a proper -1 instead of an exception
     */
    public void setEndOfStreamReached(){
        // When stream is finished, inform all the waiting threads that are blocked.
        this.endOfStreamReached = true;

        synchronized (monitor) {
            monitor.notifyAll();
        }
    }

    public int getBufferSize() {
        return buffer.length;
    }

    public byte[] getBufferedData() {
        final byte[] copy;

        // Avoid update during copy
        synchronized (bufferAccessMutex) {
            final int size = available();
            copy = new byte[size];
            System.arraycopy(buffer, readingPosition, copy, 0, size);
        }

        return copy;
    }

    public void restart() {
        synchronized (bufferAccessMutex) {
            writingPosition = 0;
            readingPosition = 0;
            closed = false;
            endOfStreamReached = false;
        }
    }
}
