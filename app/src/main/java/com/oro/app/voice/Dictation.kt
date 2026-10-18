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

    /** Record until [stopWhistle] (or 60 s), then transcribe on device. Suspends for the whole session. */
    @SuppressLint("MissingPermission")
    suspend fun recordWithWhistle() = withContext(Dispatchers.IO) {
        if (!hasMicPermission()) { _state.value = State.Error("Allow microphone access to dictate."); return@withContext }
        val rate = 16_000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate))
        }.getOrNull()
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            _state.value = State.Error("The microphone is busy."); return@withContext
        }
        // Primitive buffer: a boxed list of a minute of samples is ~1M objects on a cheap phone.
        val pcm = ShortArray(rate * MAX_SECONDS)
        var size = 0
        val chunk = ShortArray(rate / 10)
        recording = true
        _state.value = State.Listening("", 0f)
        try {
            rec.startRecording()
            while (recording && coroutineContext.isActive && size < pcm.size) {
                val n = rec.read(chunk, 0, minOf(chunk.size, pcm.size - size))
                if (n <= 0) continue
                var peak = 0
                for (i in 0 until n) { pcm[size + i] = chunk[i]; peak = maxOf(peak, kotlin.math.abs(chunk[i].toInt())) }
                size += n
                _state.value = State.Listening("", (peak / 12_000f).coerceIn(0f, 1f))
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
            recording = false
        }
        if (!coroutineContext.isActive) return@withContext
        _state.value = State.Transcribing
        if (size < rate / 4) { _state.value = State.Done(""); return@withContext }
        val floats = FloatArray(size) { pcm[it] / 32768f }
        _state.value = Whistle.transcribe(context, floats).fold(
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
