package com.oro.app.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Which recogniser the composer's mic uses. */
enum class DictationEngine(val label: String) {
    /** Android's recogniser (Google's on-device model when available). */
    SYSTEM("System"),
    /** Cactus Whistle, fully on this phone. */
    WHISTLE("Whistle")
}

/**
 * Voice dictation for the prompt bar.
 *
 * System: Android's [SpeechRecognizer], preferring the offline model, with
 * live partial results. Whistle: record 16 kHz mono PCM, then transcribe on
 * device in one pass when the user taps stop. Both deliver text through the
 * same [state] so the composer does not care which ran.
 */
class Dictation(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data class Listening(val partial: String, val level: Float) : State
        data object Transcribing : State
        data class Done(val text: String) : State
        data class Error(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    private var recognizer: SpeechRecognizer? = null
    @Volatile private var recording = false

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun reset() { _state.value = State.Idle }

    // ---------------------------------------------------------------- system

    fun startSystem() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = State.Error("No speech recogniser on this phone. Try Whistle in Settings > Voice dictation.")
            return
        }
        stopSystem()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { _state.value = State.Listening("", 0f) }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) {
                val s = _state.value
                if (s is State.Listening) _state.value = s.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { _state.value = State.Transcribing }
            override fun onError(error: Int) {
                _state.value = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> State.Done("")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> State.Error("Allow microphone access to dictate.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        State.Error("The system recogniser needs its offline language pack. Download it in Android settings, or use Whistle.")
                    else -> State.Error("Speech recognition failed ($error)")
                }
                stopSystem()
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                _state.value = State.Done(text)
                stopSystem()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                val s = _state.value
                _state.value = State.Listening(text, (s as? State.Listening)?.level ?: 0f)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        r.startListening(intent)
        _state.value = State.Listening("", 0f)
    }

    fun finishSystem() { recognizer?.stopListening() }

    fun stopSystem() {
        recognizer?.let { runCatching { it.cancel(); it.destroy() } }
        recognizer = null
    }

    // --------------------------------------------------------------- whistle

    /**
     * Record with Whistle until [stopWhistle] (or [MAX_SECONDS]). Live: every
     * second of audio goes to the engine's streaming mode on a separate
     * coroutine, so words appear while the user is still talking and the
     * microphone never waits on the model. The final text is what the
     * engine commits when the stream stops.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordWithWhistle() = withContext(Dispatchers.IO) {
        if (!hasMicPermission()) { _state.value = State.Error("Allow microphone access to dictate."); return@withContext }
        val stream = Whistle.stream(context)
        runCatching { stream.start() }.onFailure { _state.value = State.Error(it.message ?: "Whistle could not start"); return@withContext }
        val rate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate * 2))
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            _state.value = State.Error("The microphone is busy."); return@withContext
        }
        val chunks = kotlinx.coroutines.channels.Channel<FloatArray>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        var level = 0f
        var live = ""
        val worker = launch(Dispatchers.Default) {
            for (c in chunks) {
                live = runCatching { stream.feed(c) }.getOrDefault(live)
                _state.value = State.Listening(live, level)
            }
        }
        val buf = ShortArray(rate / 10)
        val second = FloatArray(rate)
        var filled = 0
        var total = 0
        recording = true
        _state.value = State.Listening("", 0f)
        try {
            rec.startRecording()
            while (recording && coroutineContext.isActive && total < rate * MAX_SECONDS) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var peak = 0
                for (i in 0 until n) {
                    second[filled++] = buf[i] / 32768f
                    peak = maxOf(peak, kotlin.math.abs(buf[i].toInt()))
                    if (filled == second.size) { chunks.trySend(second.copyOf()); filled = 0 }
                }
                total += n
                level = (peak / 12_000f).coerceIn(0f, 1f)
                _state.value = State.Listening(live, level)
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
            recording = false
        }
        if (filled > rate / 5) chunks.trySend(second.copyOf(filled))
        chunks.close()
        _state.value = State.Transcribing
        worker.join()
        if (!coroutineContext.isActive) return@withContext
        _state.value = runCatching { stream.stop() }.fold(
            onSuccess = { State.Done(it) },
            onFailure = { State.Error(it.message ?: "Transcription failed") }
        )
    }

    fun stopWhistle() { recording = false }

    fun cancel(job: Job?) {
        recording = false
        job?.cancel()
        stopSystem()
        _state.value = State.Idle
    }

    companion object {
        const val MAX_SECONDS = 60
    }
}
