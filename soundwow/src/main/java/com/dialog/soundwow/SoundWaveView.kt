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
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.view.GestureDetectorCompat
import java.io.File
import java.lang.ref.WeakReference
import java.nio.ShortBuffer

class SoundWaveView : View {

    private lateinit var gestureDetector: GestureDetectorCompat

    private val blockSettings: BlockSettings = BlockSettings()

    var samplesGrouper: SamplesGrouper = MaxSamplesGrouper()
    var blockDrawer: BlockDrawer = LineBlockDrawer()

    var seekListener: OnSeekListener? = null

    private var blocksAmount: Int = 0

    private var topOrigin: Float = 0f
    private var bottomOrigin: Float = 0f
    private var normalizeFactor: Float = 1f

    private var userDragging: Boolean = false

    private var currentPercent: Float = 0f
    private var currentBlock: Int = 0
        set(value){
            field = value
            invalidate()
        }

    var blockValues: ShortArray = ShortArray(0)
        set(value){
            field = value
            invalidate()
        }

    var soundInfo: SoundInfo? = null // decoded sound
        set(value){
            field = value
            computeBlockData()
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

    var progress: Float
        get() = currentPercent
        set(value){
            currentPercent = value
            computeBlockProgress()
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

    // load sound file to the view
    fun setSound(file: File) {
        DecodeSoundFileTask(this).execute(file)
    }

    // load sound asset to the view
    fun setSound(assetFileDescriptor: AssetFileDescriptor) {
        DecodeSoundAssetTask(this).execute(assetFileDescriptor)
    }

    // use attributes to compute available blocks
    private fun computeBlocks() {
        blocksAmount = (width - blockSettings.margin) / (blockSettings.size + blockSettings.margin) // it can produce remaing space at the end

        topOrigin = if (blockSettings.displayNegative) height / 2f - blockSettings.minHeight else (height - blockSettings.minHeight).toFloat()
        bottomOrigin = if (blockSettings.displayNegative) height / 2f + blockSettings.minHeight else height.toFloat()

        normalizeFactor = topOrigin / Short.MAX_VALUE

        computeBlockProgress()
        computeBlockData()
    }

    // use computed blocks and progress to compute reproduced blocks
    private fun computeBlockProgress() {
        currentBlock = (blocksAmount * currentPercent).toInt()
    }

    // use song data and computed block to compute data per block
    private fun computeBlockData() {
        if (blocksAmount == 0) return

        blockValues = ShortArray(blocksAmount){ 0 }

        val soundInfo: SoundInfo = soundInfo ?: return

        val samplesPerBlock = (soundInfo.samplesCount * soundInfo.channels) / blocksAmount
        val taskParams = ComputeBlockValuesTaskParams(soundInfo.samples, blocksAmount, samplesPerBlock, normalizeFactor)
        ComputeBlockValuesTask(this).execute(taskParams)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        computeBlocks()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width == 0 || blocksAmount < 1) return

        blockDrawer.drawBlocks(canvas, blockSettings, blockValues, topOrigin, bottomOrigin, currentBlock)
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
                    progress = selectedProgress
                    seekListener?.onSeekUpdate(selectedProgress)
                }
            }
            MotionEvent.ACTION_UP -> {
                if ( userDragging ) {
                    val normalizedX: Float = if (event.x < 0) 0f else if(event.x > width) width.toFloat() else event.x
                    val selectedProgress = normalizedX / width
                    userDragging = false
                    progress = selectedProgress
                    seekListener?.onSeekComplete(selectedProgress)
                }
            }
        }

        return true
    }

    // helper class to handle long press
    private inner class SoundWaveGestureListener : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent?): Boolean {
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            super.onLongPress(event)
            userDragging = true
            progress = event.x / width
            seekListener?.onSeekBegin(event.x / width)
        }

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            progress = event.x / width
            seekListener?.onSeekComplete(event.x / width)
            return super.onSingleTapUp(event)
        }
    }

    /* INTERNAL CLASSES */

    // class to group the custom attributes for the view
    class BlockSettings(
        var size: Int = 1,
        var margin: Int = 0,
        var minHeight: Int = 1,
        var displayNegative: Boolean = true,
        var playedColor: Int = Color.GREEN,
        var notPlayedColor: Int = Color.RED
    )

    /* INTERFACES */

    // used to compute the high and low values for every block
    interface SamplesGrouper {
        fun groupSamples(samples: ShortBuffer, blocksAmount: Int, samplesAmount: Int, normalizeFactor: Float): ShortArray
    }

    // used to draw the blocks
    interface BlockDrawer {
        fun drawBlocks(
            canvas: Canvas,
            blockSettings: BlockSettings,
            blockValues: ShortArray,
            topOrigin: Float,
            bottomOrigin: Float,
            currentBlock: Int
        )
    }

    // used to listen seek events from external class
    interface OnSeekListener {
        fun onSeekBegin(progress: Float)
        fun onSeekUpdate(progress: Float)
        fun onSeekComplete(progress: Float)
    }

}

// AsyncTaks to decode the sound from File
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

// AsyncTask to decode the sound from AssetFileDescriptor
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

// AsyncTask to compute block values
private class ComputeBlockValuesTask(waveView: SoundWaveView) : AsyncTask<ComputeBlockValuesTaskParams, Void, ShortArray>() {

    private val waveViewReference: WeakReference<SoundWaveView> = WeakReference(waveView)

    override fun doInBackground(vararg params: ComputeBlockValuesTaskParams): ShortArray? {
        val waveView: SoundWaveView = waveViewReference.get() ?: return null
        return waveView.samplesGrouper.groupSamples(params[0].samples, params[0].blocksAmount, params[0].samplesPerBlock, params[0].normalizeFactor )
    }

    override fun onPostExecute(result: ShortArray?) {
        super.onPostExecute(result)

        val waveView: SoundWaveView = waveViewReference.get() ?: return
        waveView.blockValues = result ?: return
    }
}

private class ComputeBlockValuesTaskParams(val samples: ShortBuffer, val blocksAmount: Int, val samplesPerBlock: Int, val normalizeFactor: Float)

// Class to compute block values taking the high sample for each block
private class MaxSamplesGrouper : SoundWaveView.SamplesGrouper {

    override fun groupSamples(
        samples: ShortBuffer,
        blocksAmount: Int,
        samplesAmount: Int,
        normalizeFactor: Float
    ): ShortArray {

        val result = ShortArray(blocksAmount)

        for (i in 0 until blocksAmount) {
            var maxSample: Short = 0

            for (j in 0 until samplesAmount) {
                val sample: Short = samples.get()
                if (sample > maxSample) maxSample = sample
            }

            result[i] = (maxSample * normalizeFactor).toShort()
        }

        samples.rewind()

        return result
    }
}

// Class to compute block values averaging the samples
private class AverageSamplesGrouper : SoundWaveView.SamplesGrouper {

    override fun groupSamples(
        samples: ShortBuffer,
        blocksAmount: Int,
        samplesAmount: Int,
        normalizeFactor: Float
    ): ShortArray {

        val result = ShortArray(blocksAmount)

        for (i in 0 until blocksAmount) {
            var altitude: Long = 0

            for (j in 0 until samplesAmount) {
                altitude += Math.abs(samples.get().toInt())
            }

            if (samplesAmount > 0) altitude /= samplesAmount

            result[i] = (altitude * normalizeFactor).toShort()
        }

        samples.rewind()

        return result
    }
}

// Default block drawer, can be used to draw lines or rectangles
private class LineBlockDrawer : SoundWaveView.BlockDrawer {

    override fun drawBlocks(
        canvas: Canvas,
        blockSettings: SoundWaveView.BlockSettings,
        blockValues: ShortArray,
        topOrigin: Float,
        bottomOrigin: Float,
        currentBlock: Int
    ) {

        val paint = Paint()
        paint.strokeWidth = blockSettings.size.toFloat()
        paint.isAntiAlias = true
        paint.color = blockSettings.playedColor

        for (i in 0 until blockValues.size) {

            if (i >= currentBlock) paint.color = blockSettings.notPlayedColor

            val left: Float = (blockSettings.margin + i * (blockSettings.size + blockSettings.margin)).toFloat()
           // val right: Float = left + blockSettings.size

            canvas.drawLine(
                left,
                topOrigin - blockValues[i],
                left,
                bottomOrigin + blockValues[i],
                paint
            )
        }
    }
}