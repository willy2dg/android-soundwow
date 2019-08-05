package com.dialog.soundwow

import android.annotation.SuppressLint
import androidx.annotation.RequiresApi
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.AsyncTask
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.GestureDetectorCompat
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ShortBuffer

class SoundWaveView : View {

    private lateinit var gestureDetector: GestureDetectorCompat

    private val blockSettings: BlockSettings = BlockSettings()
    private var samplesGrouper: SamplesGrouper = MaxSamplesGrouper()
    private var blockDrawer: BlockDrawer = LineBlockDrawer()

    var seekListener: OnSeekListener? = null

    private var blocksResume: Array<BlockResume> = emptyArray()

    private var currentPercent: Float = 0f
    private var currentBlock: Int = 0

    private var blocksAmount: Int = 0

    private var topOrigin: Float = 0f
    private var bottomOrigin: Float = 0f

    private var normalizeFactor: Float = 1f

    private var userDragging: Boolean = false

    var soundInfo: SoundInfo? = null // decoded sound
        set(value){
            field = value
            computeBlockData()
            invalidate()
        }

    var blockMargin: Int
        get() = blockSettings.margin
        set(value){
            blockSettings.margin = value
            computeBlocks()
        }

    var blockSize: Int
        get() = blockSettings.size
        set(value){
            blockSettings.size = value
            computeBlocks()
        }

    var blockMinHeight: Int
        get() = blockSettings.minHeight
        set(value){
            blockSettings.minHeight = value
            computeBlocks()
        }

    var displayNegative: Boolean
        get() = blockSettings.displayNegative
        set(value){
            blockSettings.displayNegative = value
            computeBlocks()
        }

    var playedColor: Int
        get() = blockSettings.playedColor
        set(value){
            blockSettings.playedColor = value
            invalidate()
        }

    var notPlayedColor: Int
        get() = blockSettings.notPlayedColor
        set(value){
            blockSettings.notPlayedColor = value
            invalidate()
        }

    constructor(context: Context) : super(context) {
        init(null)
    }

    constructor(context: Context, attributes: AttributeSet?) : super(context, attributes) {
        init(attributes)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(attrs)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(attrs)
    }

    private fun init(attributes: AttributeSet?) {

        gestureDetector = GestureDetectorCompat(context, SoundWaveGestureListener())

        if ( attributes == null ) return

        val typedArray = context.obtainStyledAttributes(attributes, R.styleable.SoundWaveView)
        blockSettings.displayNegative = typedArray.getBoolean(R.styleable.SoundWaveView_display_negative, true)
        blockSettings.margin = typedArray.getInt(R.styleable.SoundWaveView_block_margin, 0)
        blockSettings.size = typedArray.getInt(R.styleable.SoundWaveView_block_size, 1)
        blockSettings.minHeight = typedArray.getInt(R.styleable.SoundWaveView_block_min_height, 1)
        blockSettings.playedColor = typedArray.getColor(R.styleable.SoundWaveView_played_block_color, Color.GREEN)
        blockSettings.notPlayedColor = typedArray.getColor(R.styleable.SoundWaveView_not_played_block_color, Color.RED)
        typedArray.recycle()
    }

    fun setSound(file: File) {
        DecodeSoundFileTask(this).execute(file)
    }

    fun setSound(assetFileDescriptor: AssetFileDescriptor) {
        DecodeSoundAssetTask(this).execute(assetFileDescriptor)
    }

    fun setProgress(percent: Float) {
        currentPercent = percent
        computeBlockProgress()
        invalidate()
    }

    // use attributes to compute available blocks
    private fun computeBlocks() {
        blocksAmount = (width - blockSettings.margin) / (blockSettings.size + blockSettings.margin).toInt() // it can produce remaing space at the end

        topOrigin = if (blockSettings.displayNegative) height / 2f - blockSettings.minHeight else (height - blockSettings.minHeight).toFloat()
        bottomOrigin = if (blockSettings.displayNegative) height / 2f + blockSettings.minHeight else height.toFloat()

        normalizeFactor = topOrigin / Short.MAX_VALUE

        computeBlockProgress()
        computeBlockData()
        invalidate()
    }

    // use computed blocks and progress to compute reproduced blocks
    private fun computeBlockProgress() {
        currentBlock = (blocksAmount * currentPercent).toInt()
    }

    // use song data and computed block to compute data per block
    private fun computeBlockData() {
        if (blocksAmount == 0) return

        blocksResume = Array(blocksAmount) { BlockResume(topOrigin.toShort(), bottomOrigin.toShort()) }

        if (soundInfo == null) return

        val samplesPerBlock = (soundInfo!!.samplesCount * soundInfo!!.channels) / blocksAmount // TODO check if there are more space that samples

        for (i in 0 until blocksAmount) {
            val blockResume = samplesGrouper.groupSamples(soundInfo!!.samples, samplesPerBlock, blockSettings.displayNegative)
            blockResume.positiveResume = (topOrigin - blockResume.positiveResume * normalizeFactor).toShort()
            blockResume.negativeResume = (bottomOrigin + Math.abs(blockResume.negativeResume * normalizeFactor)).toShort()
            blocksResume[i] = blockResume
        }

        soundInfo!!.samples.rewind()
    }

    private inner class SoundWaveGestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent?): Boolean {
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            super.onLongPress(event)
            userDragging = true
            setProgress(event.x / width)
            seekListener?.onSeekBegin(event.x / width)
        }

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            setProgress(event.x / width)
            seekListener?.onSeekComplete(event.x / width)
            return super.onSingleTapUp(event)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if ( userDragging ) {
                    val normalizedX: Float = if (event.x < 0) 0f else if(event.x > width) width.toFloat() else event.x
                    val selectedProgress = normalizedX / width
                    parent.requestDisallowInterceptTouchEvent(true)
                    setProgress(selectedProgress)
                    seekListener?.onSeekUpdate(selectedProgress)
                }
            }
            MotionEvent.ACTION_UP -> {
                if ( userDragging ) {
                    val normalizedX: Float = if (event.x < 0) 0f else if(event.x > width) width.toFloat() else event.x
                    val selectedProgress = normalizedX / width
                    userDragging = false
                    setProgress(selectedProgress)
                    seekListener?.onSeekComplete(selectedProgress)
                }
            }
        }

        return true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        computeBlocks()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || blocksAmount < 1) return

        blockDrawer.drawBlocks(canvas, blockSettings, blocksResume, currentBlock)
    }

    class BlockResume(var positiveResume: Short, var negativeResume: Short = 0)

    class BlockSettings(
        var size: Int = 1,
        var margin: Int = 0,
        var minHeight: Int = 1,
        var displayNegative: Boolean = true,
        var playedColor: Int = Color.GREEN,
        var notPlayedColor: Int = Color.RED
    )

    interface SamplesGrouper {
        fun groupSamples(samples: ShortBuffer, samplesAmount: Int, computeNegative: Boolean): BlockResume
    }

    interface BlockDrawer {
        fun drawBlocks(
            canvas: Canvas,
            blockSettings: BlockSettings,
            blocksResume: Array<BlockResume>,
            currentBlock: Int
        )
    }

    interface OnSeekListener {
        fun onSeekBegin(progress: Float)
        fun onSeekUpdate(progress: Float)
        fun onSeekComplete(progress: Float)
    }

}

private class DecodeSoundFileTask(waveView: SoundWaveView) : AsyncTask<File, Void, SoundInfo>() {

    private val waveViewReference: WeakReference<SoundWaveView> = WeakReference(waveView)

    override fun doInBackground(vararg files: File): SoundInfo {
        return SoundDecoder.decode(files[0])
    }

    override fun onPostExecute(result: SoundInfo) {
        super.onPostExecute(result)

        val waveView: SoundWaveView? = waveViewReference.get()

        waveView?.soundInfo = result
    }
}

private class DecodeSoundAssetTask(waveView: SoundWaveView) : AsyncTask<AssetFileDescriptor, Void, SoundInfo>() {

    private val waveViewReference: WeakReference<SoundWaveView> = WeakReference(waveView)

    override fun doInBackground(vararg assetFileDescriptors: AssetFileDescriptor): SoundInfo {
        return SoundDecoder.decode(assetFileDescriptors[0])
    }

    override fun onPostExecute(result: SoundInfo) {
        super.onPostExecute(result)

        val waveView: SoundWaveView? = waveViewReference.get()

        waveView?.soundInfo = result
    }
}

private class MaxSamplesGrouper : SoundWaveView.SamplesGrouper {

    override fun groupSamples(samples: ShortBuffer, samplesAmount: Int, computeNegative: Boolean): SoundWaveView.BlockResume {

        if ( computeNegative ) {
            var maxSample: Short = 0
            var minSample: Short = 0

            for ( i in 0 until samplesAmount ) {
                val sample: Short = samples.get()
                if (sample > maxSample) maxSample = sample
                if (sample < minSample) minSample = sample
            }

            return SoundWaveView.BlockResume(maxSample, minSample)
        } else {
            var maxSample: Short = 0

            for ( i in 0 until samplesAmount ) {
                val sample: Short = samples.get()
                if ( sample > maxSample ) maxSample = sample
            }

            return SoundWaveView.BlockResume(maxSample, 0)
        }
    }
}

private class AverageSamplesGrouper : SoundWaveView.SamplesGrouper {

    override fun groupSamples(samples: ShortBuffer, samplesAmount: Int, computeNegative: Boolean): SoundWaveView.BlockResume {
        if ( computeNegative ) {
            var positiveAltitude: Long = 0
            var negativeAltitude:Long = 0
            var positiveCount: Int = 0
            var negativeCount: Int = 0

            for ( i in 0 until samplesAmount ) {

                val sample: Short = samples.get()
                if (sample < 0) {
                    negativeAltitude += sample
                    negativeCount++
                } else {
                    positiveAltitude += sample
                    positiveCount++
                }
            }

            if (positiveCount > 0) positiveAltitude /= positiveCount
            if (negativeCount > 0) negativeAltitude /= negativeCount

            return SoundWaveView.BlockResume(positiveAltitude.toShort(), negativeAltitude.toShort())
        } else {
            var positiveAltitude: Long = 0

            for ( i in 0 until samplesAmount ) {
                positiveAltitude += Math.abs(samples.get().toInt())
            }

            if ( samplesAmount > 0 ) positiveAltitude /= samplesAmount

            return SoundWaveView.BlockResume(positiveAltitude.toShort(), 0)
        }
    }
}

private class LineBlockDrawer : SoundWaveView.BlockDrawer {

    override fun drawBlocks(
        canvas: Canvas,
        blockSettings: SoundWaveView.BlockSettings,
        blocksResume: Array<SoundWaveView.BlockResume>,
        currentBlock: Int
    ) {

        val paint = Paint()
        paint.strokeWidth = blockSettings.size.toFloat()
        paint.isAntiAlias = true
        paint.color = blockSettings.playedColor

        for (i in 0 until blocksResume.size) {

            if (i >= currentBlock) paint.color = blockSettings.notPlayedColor

            val left: Float = (blockSettings.margin + i * (blockSettings.size + blockSettings.margin)).toFloat()
           // val right: Float = left + blockSettings.size

            canvas.drawLine(
                left,
                blocksResume[i].positiveResume.toFloat(),
                left,
                blocksResume[i].negativeResume.toFloat(),
                paint
            )
        }
    }
}