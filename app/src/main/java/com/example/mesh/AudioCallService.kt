package com.example.mesh

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class MeshCallTransportMode {
    DIRECT_STREAM,        // 1-Hop Direct BLE low-latency PCM frame streaming
    MULTI_HOP_PTT_BURST   // 2+ Hops Auto-Fallback Walkie-Talkie / Push-to-Talk
}

/**
 * Handles Voice Note recording/playback (MediaRecorder/MediaPlayer) and
 * Bluetooth Mesh Voice Calling (1-Hop Direct AudioRecord/AudioTrack PCM streaming
 * + Multi-Hop Push-to-Talk Walkie-Talkie bursts).
 */
class AudioCallService(
    private val context: Context
) {
    companion object {
        private const val TAG = "EchoAudioCall"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_BYTES = 640 // 20ms @ 16kHz 16-bit mono
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Voice Note Recorder State
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordFile: File? = null
    private var recordingStartMs: Long = 0L
    private var amplitudeJob: Job? = null
    private val recordedWaveform = mutableListOf<Float>()

    private val _isRecordingVoiceNote = MutableStateFlow(false)
    val isRecordingVoiceNote: StateFlow<Boolean> = _isRecordingVoiceNote.asStateFlow()

    private val _recordingDurationSec = MutableStateFlow(0)
    val recordingDurationSec: StateFlow<Int> = _recordingDurationSec.asStateFlow()

    private val _liveWaveformBars = MutableStateFlow<List<Float>>(emptyList())
    val liveWaveformBars: StateFlow<List<Float>> = _liveWaveformBars.asStateFlow()

    // Voice Note Player State
    private var voiceNotePlayer: MediaPlayer? = null
    private var playbackProgressJob: Job? = null

    private val _playingMessageId = MutableStateFlow<Int?>(null)
    val playingMessageId: StateFlow<Int?> = _playingMessageId.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    // Direct 1-Hop Call Streaming State
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var directStreamJob: Job? = null

    private val _txAudioFrames = MutableStateFlow(0)
    val txAudioFrames: StateFlow<Int> = _txAudioFrames.asStateFlow()

    private val _rxAudioFrames = MutableStateFlow(0)
    val rxAudioFrames: StateFlow<Int> = _rxAudioFrames.asStateFlow()

    private val _callAudioLevel = MutableStateFlow(0f)
    val callAudioLevel: StateFlow<Float> = _callAudioLevel.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    // Multi-Hop Walkie-Talkie PTT State
    private var pttBuffer = ByteArrayOutputStream()
    private var pttCaptureJob: Job? = null
    private var pttStartMs: Long = 0L

    private val _isPttTransmitting = MutableStateFlow(false)
    val isPttTransmitting: StateFlow<Boolean> = _isPttTransmitting.asStateFlow()

    private val _isPttReceiving = MutableStateFlow(false)
    val isPttReceiving: StateFlow<Boolean> = _isPttReceiving.asStateFlow()

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ------------------------------------------------------------------
    // 1. VOICE NOTE RECORDING (MediaRecorder -> compressed AAC/Opus)
    // ------------------------------------------------------------------

    fun startVoiceNoteRecording(): Boolean {
        if (_isRecordingVoiceNote.value) return false
        recordedWaveform.clear()
        _liveWaveformBars.value = emptyList()
        _recordingDurationSec.value = 0

        val outFile = File(context.cacheDir, "echo_vn_${System.currentTimeMillis()}.m4a")
        currentRecordFile = outFile
        recordingStartMs = System.currentTimeMillis()

        val startedHardware = if (hasRecordAudioPermission()) {
            try {
                val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                recorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(16000)
                    setAudioEncodingBitRate(24000) // Low-bitrate for BLE mesh chunks
                    setOutputFile(outFile.absolutePath)
                    prepare()
                    start()
                }
                mediaRecorder = recorder
                true
            } catch (e: Exception) {
                Log.w(TAG, "Hardware MediaRecorder unavailable, using synthetic voice capture", e)
                mediaRecorder = null
                false
            }
        } else {
            false
        }

        _isRecordingVoiceNote.value = true
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            var tick = 0
            while (isActive && _isRecordingVoiceNote.value) {
                val amp = if (startedHardware && mediaRecorder != null) {
                    runCatching {
                        (mediaRecorder!!.maxAmplitude / 16000f).coerceIn(0.12f, 1.0f)
                    }.getOrDefault(0.25f)
                } else {
                    (0.25f + 0.55f * abs(sin(tick * 0.45f))).coerceIn(0.15f, 0.95f)
                }
                recordedWaveform.add(amp)
                _liveWaveformBars.value = recordedWaveform.takeLast(28)
                _recordingDurationSec.value = ((System.currentTimeMillis() - recordingStartMs) / 1000L).toInt()
                tick++
                delay(120)
            }
        }
        return true
    }

    data class RecordedVoiceNote(
        val filePath: String,
        val audioBytes: ByteArray,
        val durationSec: Int,
        val waveform: List<Float>
    )

    fun stopVoiceNoteRecording(cancel: Boolean = false): RecordedVoiceNote? {
        if (!_isRecordingVoiceNote.value) return null
        _isRecordingVoiceNote.value = false
        amplitudeJob?.cancel()

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            runCatching { mediaRecorder?.release() }
        } finally {
            mediaRecorder = null
        }

        val file = currentRecordFile
        currentRecordFile = null

        if (cancel || file == null) {
            file?.delete()
            return null
        }

        val durationSec = (((System.currentTimeMillis() - recordingStartMs) / 1000L).toInt()).coerceAtLeast(1)
        val bytes = if (file.exists() && file.length() > 128) {
            file.readBytes()
        } else {
            // Fallback valid WAV clip if emulator mic produced empty file
            val wav = generateSyntheticToneWav(durationMs = (durationSec.coerceAtMost(4)) * 600, freqHz = 360.0)
            file.writeBytes(wav)
            wav
        }

        val normalizedBars = downsampleWaveform(recordedWaveform, targetBars = 24)
        return RecordedVoiceNote(
            filePath = file.absolutePath,
            audioBytes = bytes,
            durationSec = durationSec,
            waveform = normalizedBars
        )
    }

    // ------------------------------------------------------------------
    // 2. VOICE NOTE WAVEFORM PLAYER
    // ------------------------------------------------------------------

    fun togglePlayVoiceNote(messageId: Int, filePath: String?, fallbackDurationSec: Int) {
        if (_playingMessageId.value == messageId) {
            stopVoiceNotePlayback()
            return
        }
        stopVoiceNotePlayback()

        val file = filePath?.let { File(it) }
        _playingMessageId.value = messageId
        _playbackProgress.value = 0f

        if (file != null && file.exists() && file.length() > 64) {
            try {
                voiceNotePlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    prepare()
                    start()
                    setOnCompletionListener {
                        stopVoiceNotePlayback()
                    }
                }
                playbackProgressJob = scope.launch {
                    while (isActive && voiceNotePlayer != null && _playingMessageId.value == messageId) {
                        val dur = runCatching { voiceNotePlayer?.duration ?: 1 }.getOrDefault(1).coerceAtLeast(1)
                        val pos = runCatching { voiceNotePlayer?.currentPosition ?: 0 }.getOrDefault(0)
                        _playbackProgress.value = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                        delay(80)
                    }
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "MediaPlayer failed for $filePath, running visual playback timer", e)
            }
        }

        // Visual progress timer fallback if file is simulated or unavailable
        playbackProgressJob = scope.launch {
            val totalMs = (fallbackDurationSec.coerceAtLeast(1)) * 1000L
            val started = System.currentTimeMillis()
            while (isActive && _playingMessageId.value == messageId) {
                val elapsed = System.currentTimeMillis() - started
                val p = (elapsed.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                _playbackProgress.value = p
                if (p >= 1f) break
                delay(70)
            }
            stopVoiceNotePlayback()
        }
    }

    fun stopVoiceNotePlayback() {
        playbackProgressJob?.cancel()
        try {
            voiceNotePlayer?.stop()
            voiceNotePlayer?.release()
        } catch (_: Exception) {
        } finally {
            voiceNotePlayer = null
            _playingMessageId.value = null
            _playbackProgress.value = 0f
        }
    }

    // ------------------------------------------------------------------
    // 3. 1-HOP DIRECT VOICE STREAMING (AudioRecord + AudioTrack PCM)
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    fun startDirectAudioStream(
        isMutedProvider: () -> Boolean,
        onEncodedFrameReady: (pcmBytes: ByteArray, sequence: Int) -> Unit
    ) {
        stopDirectAudioStream()
        _txAudioFrames.value = 0
        _rxAudioFrames.value = 0

        if (hasRecordAudioPermission()) {
            runCatching {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(FRAME_BYTES * 2)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                ).apply {
                    if (state == AudioRecord.STATE_INITIALIZED) startRecording()
                }
            }
        }

        runCatching {
            val minOut = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(FRAME_BYTES * 2)
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minOut)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .apply {
                    if (state == AudioTrack.STATE_INITIALIZED) play()
                }
        }

        directStreamJob = scope.launch {
            val buffer = ByteArray(FRAME_BYTES)
            var seq = 0
            while (isActive) {
                val muted = isMutedProvider()
                if (!muted && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val frame = buffer.copyOf(read)
                        seq++
                        _txAudioFrames.value = seq
                        var peak = 0
                        var i = 0
                        while (i + 1 < read) {
                            val sample = ((frame[i + 1].toInt() shl 8) or (frame[i].toInt() and 0xFF)).toShort().toInt()
                            val a = abs(sample)
                            if (a > peak) peak = a
                            i += 2
                        }
                        _callAudioLevel.value = (peak / 20000f).coerceIn(0.08f, 1.0f)
                        onEncodedFrameReady(frame, seq)
                    }
                } else {
                    _callAudioLevel.value = 0.05f
                }
                delay(40)
            }
        }
    }

    fun playIncomingDirectAudioFrame(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return
        _rxAudioFrames.value = _rxAudioFrames.value + 1
        runCatching {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.write(pcmBytes, 0, pcmBytes.size)
            }
        }
    }

    fun stopDirectAudioStream() {
        directStreamJob?.cancel()
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        _callAudioLevel.value = 0f
    }

    fun toggleSpeakerphone() {
        val next = !_isSpeakerOn.value
        _isSpeakerOn.value = next
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = next
        }
    }

    // ------------------------------------------------------------------
    // 4. MULTI-HOP AUTO-FALLBACK WALKIE-TALKIE (Push-To-Talk / PTT)
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    fun startPttBurst() {
        if (_isPttTransmitting.value) return
        _isPttTransmitting.value = true
        pttBuffer = ByteArrayOutputStream()
        pttStartMs = System.currentTimeMillis()
        playRadioSquelchTone(freqHz = 980.0, durationMs = 70)

        var pttRecord: AudioRecord? = null
        if (hasRecordAudioPermission()) {
            runCatching {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(FRAME_BYTES * 2)
                pttRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                ).apply {
                    if (state == AudioRecord.STATE_INITIALIZED) startRecording()
                }
            }
        }

        pttCaptureJob?.cancel()
        pttCaptureJob = scope.launch {
            val buf = ByteArray(FRAME_BYTES)
            var tick = 0
            try {
                while (isActive && _isPttTransmitting.value) {
                    if (pttRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        val read = pttRecord?.read(buf, 0, buf.size) ?: 0
                        if (read > 0) {
                            pttBuffer.write(buf, 0, read)
                            var peak = 0
                            var i = 0
                            while (i + 1 < read) {
                                val sample = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
                                val a = abs(sample)
                                if (a > peak) peak = a
                                i += 2
                            }
                            _callAudioLevel.value = (peak / 20000f).coerceIn(0.15f, 1.0f)
                        }
                    } else {
                        delay(60)
                        _callAudioLevel.value = 0.2f
                    }
                    tick++
                }
            } finally {
                runCatching { pttRecord?.stop() }
                runCatching { pttRecord?.release() }
            }
        }
    }

    fun releasePttBurst(): Pair<ByteArray, Int>? {
        if (!_isPttTransmitting.value) return null
        _isPttTransmitting.value = false
        pttCaptureJob?.cancel()
        _callAudioLevel.value = 0f
        playRadioSquelchTone(freqHz = 720.0, durationMs = 80)

        val bytes = pttBuffer.toByteArray()
        val durationMs = (System.currentTimeMillis() - pttStartMs).toInt().coerceAtLeast(300)
        return bytes to durationMs
    }

    fun notifyIncomingPttBurst(audioBytes: ByteArray = ByteArray(0), durationMs: Int = 1200) {
        scope.launch {
            _isPttReceiving.value = true
            _callAudioLevel.value = 0.78f
            playRadioSquelchTone(freqHz = 840.0, durationMs = 90)
            if (audioBytes.size >= 320) {
                runCatching {
                    val track = AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(audioBytes.size)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                    track.write(audioBytes, 0, audioBytes.size)
                    track.play()
                    val playWait = (audioBytes.size.toLong() * 1000L) / (SAMPLE_RATE * 2L)
                    delay(playWait.coerceIn(300L, 10000L))
                    track.stop()
                    track.release()
                }
            } else {
                delay(durationMs.toLong().coerceIn(400L, 3000L))
            }
            _isPttReceiving.value = false
            _callAudioLevel.value = 0f
        }
    }

    private fun playRadioSquelchTone(freqHz: Double, durationMs: Int) {
        scope.launch {
            runCatching {
                val count = (SAMPLE_RATE * durationMs / 1000)
                val samples = ShortArray(count) { i ->
                    val env = if (i < count / 5) i.toDouble() / (count / 5) else 1.0
                    (sin(2.0 * PI * freqHz * i / SAMPLE_RATE) * 4500 * env).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                delay(durationMs.toLong() + 40L)
                track.stop()
                track.release()
            }
        }
    }

    private fun generateSyntheticToneWav(durationMs: Int, freqHz: Double): ByteArray {
        val sampleCount = (SAMPLE_RATE * durationMs / 1000).coerceAtLeast(SAMPLE_RATE / 2)
        val dataBytes = sampleCount * 2
        val out = ByteArrayOutputStream(44 + dataBytes)
        // Minimal 16-bit mono PCM WAV header
        out.write("RIFF".toByteArray())
        out.write(intToLeBytes(36 + dataBytes))
        out.write("WAVEfmt ".toByteArray())
        out.write(intToLeBytes(16))
        out.write(shortToLeBytes(1)) // PCM
        out.write(shortToLeBytes(1)) // Mono
        out.write(intToLeBytes(SAMPLE_RATE))
        out.write(intToLeBytes(SAMPLE_RATE * 2))
        out.write(shortToLeBytes(2))
        out.write(shortToLeBytes(16))
        out.write("data".toByteArray())
        out.write(intToLeBytes(dataBytes))
        for (i in 0 until sampleCount) {
            val sample = (sin(2.0 * PI * freqHz * i / SAMPLE_RATE) * 3200).toInt().toShort()
            out.write(shortToLeBytes(sample))
        }
        return out.toByteArray()
    }

    private fun intToLeBytes(v: Int): ByteArray = byteArrayOf(
        (v and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 24) and 0xFF).toByte()
    )

    private fun shortToLeBytes(v: Short): ByteArray = byteArrayOf(
        (v.toInt() and 0xFF).toByte(),
        ((v.toInt() shr 8) and 0xFF).toByte()
    )

    private fun downsampleWaveform(raw: List<Float>, targetBars: Int): List<Float> {
        if (raw.isEmpty()) {
            return List(targetBars) { idx -> (0.25f + 0.65f * abs(sin(idx * 0.6f))).coerceIn(0.2f, 1f) }
        }
        if (raw.size <= targetBars) {
            val padded = raw.toMutableList()
            while (padded.size < targetBars) padded.add(0.25f)
            return padded
        }
        val step = raw.size.toFloat() / targetBars
        return List(targetBars) { i ->
            val idx = (i * step).toInt().coerceIn(0, raw.lastIndex)
            raw[idx].coerceIn(0.15f, 1f)
        }
    }

    fun release() {
        stopVoiceNoteRecording(cancel = true)
        stopVoiceNotePlayback()
        stopDirectAudioStream()
        pttCaptureJob?.cancel()
    }
}
