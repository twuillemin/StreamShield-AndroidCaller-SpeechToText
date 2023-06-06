package cy.adiutrix.micanalyzer

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.ctminsights.streamshield.util.SpeechRecognizer
import com.ctminsights.streamshield.util.TextViewUpdaterHandler
import com.ctminsights.streamshield.util.WaveWriter
import com.ctminsights.streamshield.util.WordEmitterTextViewUpdater
import java.nio.ByteBuffer


class MainActivity : AppCompatActivity() {

    companion object {
        private val TAG = MainActivity::class.java.simpleName

        //private const val RECORDER_BPP = 16
        private const val RECORDER_SAMPLE_RATE = 44100
        private const val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO
        private const val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_DURATION_MS = 10

        private const val PERMISSION_START_RECORDING = 1000
    }

    private var recordingThread: Thread? = null
    private var isRecording = false

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var wordEmitter: WordEmitterTextViewUpdater
    private lateinit var waveWriter: WaveWriter

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val numberOfChannels = getNumberOfChannelsForEncoding(RECORDER_CHANNELS)
        val bitsPerSample = getBitsPerSampleForEncoding(RECORDER_AUDIO_ENCODING)
        waveWriter = WaveWriter(this, RECORDER_SAMPLE_RATE, bitsPerSample, numberOfChannels)

        val textView = findViewById<TextView>(R.id.textView)
        val textViewUpdaterHandler = TextViewUpdaterHandler.createTextViewHandler(textView)
        wordEmitter = WordEmitterTextViewUpdater(textViewUpdaterHandler)
        speechRecognizer = SpeechRecognizer(this, wordEmitter, RECORDER_SAMPLE_RATE, numberOfChannels)

        setButtonHandlers()
        enableButtons(false)
    }

    private fun setButtonHandlers() {
        (findViewById<View>(R.id.btnStart) as Button).setOnClickListener(btnClick)
        (findViewById<View>(R.id.btnStop) as Button).setOnClickListener(btnClick)
    }

    private fun enableButtons(isRecording: Boolean) {
        enableButton(R.id.btnStart, !isRecording)
        enableButton(R.id.btnStop, isRecording)
    }

    private fun enableButton(id: Int, isEnable: Boolean) {
        (findViewById<View>(id) as Button).isEnabled = isEnable
    }

    private val btnClick = View.OnClickListener { v ->
        when (v.id) {
            R.id.btnStart -> {
                Log.i(TAG, "Start Recording")
                startRecording()
                enableButtons(true)
            }

            R.id.btnStop -> {
                Log.i(TAG, "Stop Recording")
                stopRecording()
                enableButtons(false)
            }
        }
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                listOf(Manifest.permission.RECORD_AUDIO).toTypedArray(),
                PERMISSION_START_RECORDING
            )

            return
        }

        //
        // Configure the buffer of the Android AudioRecord
        //
        // The default size
        val minBufferSize = AudioRecord.getMinBufferSize(
            RECORDER_SAMPLE_RATE,
            RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING
        )

        Log.i(TAG, "Recorder minBufferSize: $minBufferSize")

        val audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            RECORDER_SAMPLE_RATE,
            RECORDER_CHANNELS,
            RECORDER_AUDIO_ENCODING,
            minBufferSize
        )

        Log.i(TAG, "Recorder created: $minBufferSize")

        //
        // Configure the internal buffer to read from the AudioRecorder
        //
        val bitsPerSample = getBitsPerSampleForEncoding(RECORDER_AUDIO_ENCODING)
        val numberOfChannels = getNumberOfChannelsForEncoding(RECORDER_CHANNELS)
        val bytesPerFrame = numberOfChannels * bitsPerSample / 8
        val framesPerBuffer = RECORDER_SAMPLE_RATE / (1000 / BUFFER_DURATION_MS)
        val captureBufferSize = bytesPerFrame * framesPerBuffer
        //val buffer = ByteArray(captureBufferSize)
        val buffer = ByteBuffer.allocateDirect(captureBufferSize)

        val thread = Thread({ exploitAudioData(audioRecorder, buffer) }, "AudioRecorder Thread")

        // Keep the values in the instance
        this.recordingThread = thread
        this.isRecording = true

        // Start the thread
        thread.start()
    }

    private fun exploitAudioData(
        recorder: AudioRecord,
        buffer: ByteBuffer,
    ) {
        val expectedSize = buffer.capacity()
        val tmpBuffer = ByteArray(expectedSize)

        wordEmitter.start()
        waveWriter.start()
        speechRecognizer.start()

        recorder.startRecording()

        while (isRecording) {
            val bytesRead = recorder.read(buffer, expectedSize)

            if (bytesRead == expectedSize) {
                // Grab a copy of the data without altering the buffer
                buffer.mark()
                buffer.get(tmpBuffer)
                buffer.reset()

                // Process the copied data
                waveWriter.addBytes(tmpBuffer)
                speechRecognizer.addBytes(tmpBuffer)

                // Simply consume the buffer in a similar way of sending it to the phone call
                buffer.clear()

            } else {
                Log.w(TAG, "Underflow read")
            }
        }

        waveWriter.stop()
        speechRecognizer.stop()
        wordEmitter.stop()


        recorder.stop()
        recorder.release()
    }

    private fun stopRecording() {
        val currentThread = this.recordingThread
        if (currentThread != null) {
            // Stop the thread loop
            isRecording = false

            // Wait for the thread to finish
            currentThread.join(0)

            recordingThread = null
        }
    }

    @Suppress("SameParameterValue")
    private fun getBitsPerSampleForEncoding(encoding: Int): Int {
        return when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_8BIT -> 8
            else -> throw RuntimeException("Unsupported AudioFormat.encoding")
        }
    }

    @Suppress("SameParameterValue")
    private fun getNumberOfChannelsForEncoding(channels: Int): Int {
        return when (channels) {
            AudioFormat.CHANNEL_IN_STEREO -> 2
            AudioFormat.CHANNEL_IN_MONO -> 1
            else -> throw RuntimeException("Unsupported AudioFormat.channels")
        }
    }

    @Override
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_START_RECORDING -> {
                if (grantResults.any { it == PackageManager.PERMISSION_DENIED }) {
                    Toast.makeText(this, "All requested permissions are mandatory to use the recorder", LENGTH_LONG).show()
                } else {
                    startRecording()
                }
            }

            else -> Toast.makeText(this, "Unknown permission request", LENGTH_LONG).show()
        }
    }
}