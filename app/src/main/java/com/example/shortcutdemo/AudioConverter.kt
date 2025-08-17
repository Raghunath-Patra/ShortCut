package com.example.shortcutdemo

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioConverter {
    private const val TAG = "AudioConverter"

    /**
     * Convert raw PCM audio file to WAV format
     */
    fun convertPcmToWav(
        pcmFile: File,
        wavFile: File,
        sampleRate: Int = 44100,
        channels: Int = 2,
        bitsPerSample: Int = 16
    ): Boolean {
        return try {
            val pcmSize = pcmFile.length().toInt()
            val wavSize = pcmSize + 44 // PCM data + WAV header

            FileOutputStream(wavFile).use { wavOutput ->
                // Write WAV header
                writeWavHeader(wavOutput, pcmSize, sampleRate, channels, bitsPerSample)
                
                // Copy PCM data
                FileInputStream(pcmFile).use { pcmInput ->
                    pcmInput.copyTo(wavOutput)
                }
            }
            
            Log.d(TAG, "Successfully converted PCM to WAV: ${wavFile.absolutePath}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error converting PCM to WAV", e)
            false
        }
    }

    private fun writeWavHeader(
        output: FileOutputStream,
        pcmSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val wavSize = pcmSize + 36

        val header = ByteBuffer.allocate(44)
        header.order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        header.put("RIFF".toByteArray()) // ChunkID
        header.putInt(wavSize) // ChunkSize
        header.put("WAVE".toByteArray()) // Format

        // fmt subchunk
        header.put("fmt ".toByteArray()) // Subchunk1ID
        header.putInt(16) // Subchunk1Size (16 for PCM)
        header.putShort(1.toShort()) // AudioFormat (1 for PCM)
        header.putShort(channels.toShort()) // NumChannels
        header.putInt(sampleRate) // SampleRate
        header.putInt(byteRate) // ByteRate
        header.putShort(blockAlign.toShort()) // BlockAlign
        header.putShort(bitsPerSample.toShort()) // BitsPerSample

        // data subchunk
        header.put("data".toByteArray()) // Subchunk2ID
        header.putInt(pcmSize) // Subchunk2Size

        output.write(header.array())
    }

    /**
     * Get audio file info for debugging
     */
    fun getAudioFileInfo(file: File): String {
        return if (file.exists()) {
            "Audio file: ${file.name}, Size: ${file.length()} bytes, " +
            "Duration: ~${estimateAudioDuration(file.length(), 44100, 2, 16)} seconds"
        } else {
            "Audio file does not exist"
        }
    }

    private fun estimateAudioDuration(
        fileSizeBytes: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): Double {
        val bytesPerSecond = sampleRate * channels * bitsPerSample / 8
        return fileSizeBytes.toDouble() / bytesPerSecond
    }
}