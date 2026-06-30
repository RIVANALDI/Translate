package com.example.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun startRecording(): File? {
        try {
            val outputDir = context.cacheDir
            outputFile = File.createTempFile("voice_record_", ".mp4", outputDir)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile!!.absolutePath)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)

                prepare()
                start()
            }
            Log.d("AudioRecorder", "Recording started successfully: ${outputFile?.absolutePath}")
            return outputFile
        } catch (e: IOException) {
            Log.e("AudioRecorder", "prepare() failed for MediaRecorder", e)
            return null
        } catch (e: IllegalStateException) {
            Log.e("AudioRecorder", "MediaRecorder is in an invalid state for operation", e)
            return null
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start MediaRecorder", e)
            return null
        }
    }

    fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            Log.d("AudioRecorder", "Recording stopped and released.")
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping MediaRecorder", e)
        } finally {
            mediaRecorder = null
        }
    }
}
