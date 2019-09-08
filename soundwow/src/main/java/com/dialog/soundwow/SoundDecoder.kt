package com.dialog.soundwow

import android.content.res.AssetFileDescriptor
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaExtractor
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

class SoundDecoder {

    companion object {

        @Throws(java.io.FileNotFoundException::class, java.io.IOException::class, Exception::class)
        fun decode(inputFile: File): SoundInfo {
            val extractor = MediaExtractor()
            var selectedTrack = false
            lateinit var format: MediaFormat

            extractor.setDataSource(inputFile.path)

            val fileSize = inputFile.length().toInt()
            val numTracks = extractor.trackCount

            for (i in 0 until numTracks) {
                format = extractor.getTrackFormat(i)
                val mimeType: String = format.getString(MediaFormat.KEY_MIME) ?: "empty"
                if (mimeType.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    selectedTrack = true
                    break
                }
            }

            if (!selectedTrack) {
                throw Exception("No audio track found in $inputFile")
            }

            return extract(extractor, format, fileSize)
        }

        @Throws(java.io.FileNotFoundException::class, java.io.IOException::class, Exception::class)
        fun decode(inputFile: AssetFileDescriptor): SoundInfo {
            val extractor = MediaExtractor()
            var selectedTrack = false
            lateinit var format: MediaFormat

            extractor.setDataSource(inputFile.fileDescriptor, inputFile.startOffset, inputFile.length)

            val fileSize = inputFile.length.toInt()
            val numTracks = extractor.trackCount

            for (i in 0 until numTracks) {
                format = extractor.getTrackFormat(i)
                val mimeType: String = format.getString(MediaFormat.KEY_MIME) ?: "empty"
                if (mimeType.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    selectedTrack = true
                    break
                }
            }

            if (!selectedTrack) {
                throw Exception("No audio track found in $inputFile")
            }

            return extract(extractor, format, fileSize)
        }

        private fun extract(extractor: MediaExtractor, format: MediaFormat, mFileSize: Int) : SoundInfo {

            val channels: Int = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val sampleRate: Int = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val songDuration: Long = format.getLong(MediaFormat.KEY_DURATION)
            val mimeType: String = format.getString(MediaFormat.KEY_MIME) ?: "unknown"
            // Expected total number of samples per channel.
            val expectedNumSamples = (songDuration / 1000000f * sampleRate + 0.5f).toInt()

            val codec: MediaCodec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(format, null, null, 0)
            codec.start()

            var decodedSamplesSize = 0  // size of the output buffer containing decoded samples.
            var decodedSamplesBuffer = ByteArray(decodedSamplesSize)
            val inputBuffers = codec.inputBuffers
            var outputBuffers = codec.outputBuffers
            var sampleSize: Int
            val info = MediaCodec.BufferInfo()
            var presentationTime: Long
            var totalSizeRead = 0
            var doneReading = false

            // Set the size of the decoded samples buffer to 1MB (~6sec of a stereo stream at 44.1kHz).
            // For longer streams, the buffer size will be increased later on, calculating a rough
            // estimate of the total size needed to store all the samples in order to resize the buffer
            // only once.
            var decodedBytes: ByteBuffer = ByteBuffer.allocate(1 shl 20)
            var firstSampleData: Boolean = true
            while (true) {

                // read data from file and feed it to the decoder input buffers.
                val inputBufferIndex = codec.dequeueInputBuffer(100)
                if (!doneReading && inputBufferIndex >= 0) {

                    sampleSize = extractor.readSampleData(inputBuffers[inputBufferIndex], 0)
                    if (firstSampleData && mimeType == "audio/mp4a-latm" && sampleSize == 2) {
                        // For some reasons on some devices (e.g. the Samsung S3) you should not
                        // provide the first two bytes of an AAC stream, otherwise the MediaCodec will
                        // crash. These two bytes do not contain music data but basic info on the
                        // stream (e.g. channel configuration and sampling frequency), and skipping them
                        // seems OK with other devices (MediaCodec has already been configured and
                        // already knows these parameters).
                        extractor.advance()
                        totalSizeRead += sampleSize
                    } else if (sampleSize < 0) {
                        // All samples have been read.
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, -1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        doneReading = true
                    } else {
                        presentationTime = extractor.sampleTime
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, presentationTime, 0)
                        extractor.advance()
                        totalSizeRead += sampleSize
                    }
                    firstSampleData = false
                }

                // Get decoded stream from the decoder output buffers.
                val outputBufferIndex = codec.dequeueOutputBuffer(info, 100)

                if (outputBufferIndex >= 0 && info.size > 0) {
                    if (decodedSamplesSize < info.size) {
                        decodedSamplesSize = info.size
                        decodedSamplesBuffer = ByteArray(decodedSamplesSize)
                    }
                    outputBuffers[outputBufferIndex].get(decodedSamplesBuffer, 0, info.size)
                    outputBuffers[outputBufferIndex].clear()
                    // Check if buffer is big enough. Resize it if it's too small.
                    if (decodedBytes.remaining() < info.size) {
                        // Getting a rough estimate of the total size, allocate 20% more, and
                        // make sure to allocate at least 5MB more than the initial size.
                        val position = decodedBytes.position()
                        var newSize = (position * (1.0 * mFileSize / totalSizeRead) * 1.2).toInt()
                        if (newSize - position < info.size + 5 * (1 shl 20)) {
                            newSize = position + info.size + 5 * (1 shl 20)
                        }
                        lateinit var newDecodedBytes: ByteBuffer
                        // Try to allocate memory. If we are OOM, try to run the garbage collector.
                        var retry = 10
                        while (retry > 0) {
                            try {
                                newDecodedBytes = ByteBuffer.allocate(newSize)
                                break
                            } catch (oome: OutOfMemoryError) {
                                // setting android:largeHeap="true" in <application> seem to help not
                                // reaching this section.
                                retry--
                            }

                        }
                        if (retry == 0) {
                            // Failed to allocate memory... Stop reading more data and finalize the
                            // instance with the data decoded so far.
                            break
                        }
                        //ByteBuffer newDecodedBytes = ByteBuffer.allocate(newSize);
                        decodedBytes.rewind()
                        newDecodedBytes.put(decodedBytes)
                        decodedBytes = newDecodedBytes
                        decodedBytes.position(position)
                    }
                    decodedBytes.put(decodedSamplesBuffer, 0, info.size)
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    outputBuffers = codec.outputBuffers
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Subsequent data will conform to new format.
                    // We could check that codec.getOutputFormat(), which is the new output format,
                    // is what we expect.
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || decodedBytes.position() / (2 * channels) >= expectedNumSamples) {
                    // We got all the decoded data from the decoder. Stop here.
                    // Theoretically dequeueOutputBuffer(info, ...) should have set info.flags to
                    // MediaCodec.BUFFER_FLAG_END_OF_STREAM. However some phones (e.g. Samsung S3)
                    // won't do that for some files (e.g. with mono AAC files), in which case subsequent
                    // calls to dequeueOutputBuffer may result in the application crashing, without
                    // even an exception being thrown... Hence the second check.
                    // (for mono AAC files, the S3 will actually double each sample, as if the stream
                    // was stereo. The resulting stream is half what it's supposed to be and with a much
                    // lower pitch.)
                    break
                }
            }
            val numSamples = decodedBytes.position() / (channels * 2)  // One sample = 2 bytes.
            decodedBytes.rewind()
            decodedBytes.order(ByteOrder.LITTLE_ENDIAN)
            val decodedSamples: ShortBuffer = decodedBytes.asShortBuffer()
            val averageBitRate: Int = (mFileSize * 8 * (sampleRate.toFloat() / numSamples) / 1000).toInt()

            extractor.release()
            codec.stop()
            codec.release()

            val totalSamples = numSamples * channels
            var reduceTo: Int = numSamples / 50 // 10000
            if (totalSamples % reduceTo != 0) reduceTo += 1
            val reductionStep: Int = totalSamples / reduceTo
            val reducedSamples: ShortBuffer = ShortBuffer.allocate(reduceTo)

            for (i in 0 until reduceTo){
                val currentSampleIndex = i * reductionStep
                var resumedSample: Short = 0
                for (j in 0 until channels) {
                    val sample = Math.abs(decodedSamples.get(currentSampleIndex + j).toInt())
                    if (sample > resumedSample){
                        resumedSample = sample.toShort()
                    }
                }
                reducedSamples.put(resumedSample)
            }

            reducedSamples.rewind()

            return SoundInfo(songDuration, mimeType, averageBitRate, reduceTo, sampleRate, reducedSamples)
        }

    }
}

class SoundInfo(
    val size: Long,
    val format: String,
    val bitRate: Int,
    val samplesCount: Int,
    val sampleRate: Int,
    @Transient var samples: ShortBuffer
) : Serializable {

    companion object {
        private const val serialVersionUID = 20190709L
    }

    private fun writeObject(oos: ObjectOutputStream){
        val buffer: ByteBuffer = ByteBuffer.allocate(samplesCount * 2)
        buffer.asShortBuffer().put(samples)
        oos.defaultWriteObject()
        oos.write(buffer.array())
    }

    private fun readObject(ois: ObjectInputStream){
        ois.defaultReadObject()
        //data = ByteBuffer.wrap(ois.readBytes())
        samples = ByteBuffer.wrap(ois.readBytes()).asShortBuffer()
    }

}