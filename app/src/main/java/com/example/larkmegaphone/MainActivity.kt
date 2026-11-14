package com.example.larkmegaphone

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private var isRunning = false
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private lateinit var statusText: TextView
    private lateinit var btnToggle: Button

    private val SAMPLE_RATE = 48000
    private val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prosty interfejs wygenerowany w locie
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 100, 50, 50)
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        statusText = TextView(this).apply {
            text = "Status: Zatrzymany\nWepnij Hollyland i połącz BT z głośnikiem."
            textSize = 18f
            setPadding(0, 0, 0, 50)
            gravity = android.view.Gravity.CENTER
        }

        btnToggle = Button(this).apply {
            text = "URUCHOM MEGAFON"
            textSize = 18f
            setOnClickListener {
                if (isRunning) stopAudio() else checkPermissionsAndStart()
            }
        }

        layout.addView(statusText)
        layout.addView(btnToggle)
        setContentView(layout)
    }

    private fun checkPermissionsAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        } else {
            startAudioStream()
        }
    }

    private fun startAudioStream() {
        thread {
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
                
                // Wymuszenie pobierania z mikrofonu USB (Hollyland Lark A1)
                val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true

                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                var usbDevice: AudioDeviceInfo? = null
                for (device in devices) {
                    if (device.type == AudioDeviceInfo.TYPE_USB_DEVICE || device.type == AudioDeviceInfo.TYPE_USB_HEADSET) {
                        usbDevice = device
                        break
                    }
                }

                audioRecord = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_IN)
                            .setEncoding(ENCODING)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize * 2)
                    .build()

                if (usbDevice != null) {
                    audioRecord?.preferredDevice = usbDevice
                }

                val trackMinSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_OUT)
                            .setEncoding(ENCODING)
                            .build()
                    )
                    .setBufferSizeInBytes(trackMinSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioRecord?.startRecording()
                audioTrack?.play()
                isRunning = true

                runOnUiThread {
                    statusText.text = "Status: DZIAŁA NA ŻYWO\n(Mikrofon USB aktywny -> Wyjście BT)"
                    btnToggle.text = "ZATRZYMAJ"
                }

                val buffer = ByteArray(minBufferSize)
                while (isRunning) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        audioTrack?.write(buffer, 0, readSize)
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    statusText.text = "Błąd: ${e.message}"
                    stopAudio()
                }
            }
        }
    }

    private fun stopAudio() {
        isRunning = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {}

        runOnUiThread {
            statusText.text = "Status: Zatrzymany"
            btnToggle.text = "URUCHOM MEGAFON"
        }
    }
}
