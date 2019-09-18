package com.dialog.soundwowsample

import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.dialog.soundwow.SoundWaveView
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var player1: SimplePlayer
    private lateinit var player2: SimplePlayer
    private lateinit var player3: SimplePlayer
    private lateinit var player4: SimplePlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        player1 = SimplePlayer(this, R.id.sound_wave_view_1, R.raw.test1)
        player2 = SimplePlayer(this, R.id.sound_wave_view_2, R.raw.test2)
        player3 = SimplePlayer(this, R.id.sound_wave_view_3, R.raw.test3)
        player4 = SimplePlayer(this, R.id.sound_wave_view_4, R.raw.test3)

        player4.soundWaveView.blockSize = 10
        player4.soundWaveView.blockMargin = 4

        container.addView(player1.containerView)
        container.addView(player2.containerView)
        container.addView(player3.containerView)
        container.addView(player4.containerView)

        val inflatedSoundView = layoutInflater.inflate(R.layout.sound_view, container, false) as SoundWaveView
        container.addView(inflatedSoundView)

        if (savedInstanceState == null) {
            player1.loadSoundWaveView()
            player2.loadSoundWaveView()
            player3.loadSoundWaveView()
            player4.loadSoundWaveView()
            inflatedSoundView.setSound(resources.openRawResourceFd(R.raw.test2))
        }
    }

    override fun onSaveInstanceState(savedInstance: Bundle) {
        super.onSaveInstanceState(savedInstance)

        if (player1.playing) player1.pause()
        if (player2.playing) player2.pause()
        if (player3.playing) player3.pause()
        if (player4.playing) player4.pause()

        savedInstance.putInt("player1position", player1.mediaPlayer.currentPosition)
        savedInstance.putInt("player2position", player2.mediaPlayer.currentPosition)
        savedInstance.putInt("player3position", player3.mediaPlayer.currentPosition)
        savedInstance.putInt("player4position", player4.mediaPlayer.currentPosition)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        player1.mediaPlayer.seekTo(savedInstanceState.getInt("player1position"))
        player2.mediaPlayer.seekTo(savedInstanceState.getInt("player2position"))
        player3.mediaPlayer.seekTo(savedInstanceState.getInt("player3position"))
        player4.mediaPlayer.seekTo(savedInstanceState.getInt("player4position"))
    }
}

class SimplePlayer(private val context: Context, private val viewId: Int, private val assetId: Int) {

    val mediaPlayer: MediaPlayer = MediaPlayer.create(context, assetId)

    val containerView: LinearLayout = LinearLayout(context)
    val playerButton: Button = Button(context)
    val soundWaveView: SoundWaveView = SoundWaveView(context)

    var playing: Boolean = false

    val waveViewUpdateHandler = Handler()
    val waveViewUpdateRunnable = object: Runnable {
        override fun run() {
            // fix percent, since it never reaches the duration time
            var percent = (mediaPlayer.currentPosition.toFloat() / mediaPlayer.duration.toFloat()) / 0.995f
            if (percent > 1f) percent = 1f
            soundWaveView.progress = percent
            waveViewUpdateHandler.postDelayed(this, 100)
        }
    }

    fun progressToMilliseconds(progress: Float): Int{
        return (progress * mediaPlayer.duration).toInt()
    }

    fun loadSoundWaveView() {
        soundWaveView.setSound(context.resources.openRawResourceFd(assetId))
    }

    fun play() {
        mediaPlayer.start()
        onPlayerStart()
    }

    fun pause() {
        mediaPlayer.pause()
        onPlayerStop()
    }

    private fun onPlayerStart() {
        playing = true
        waveViewUpdateHandler.postDelayed(waveViewUpdateRunnable, 0)
        playerButton.setBackgroundResource(R.drawable.ic_action_pause)
        playerButton.setOnClickListener { pause() }
    }

    private fun onPlayerStop() {
        playing = false
        waveViewUpdateHandler.removeCallbacks(waveViewUpdateRunnable)
        playerButton.setBackgroundResource(R.drawable.ic_action_play)
        playerButton.setOnClickListener { play() }
    }

    init{

        mediaPlayer.setOnCompletionListener {
            onPlayerStop()
        }

        playerButton.height = 300
        playerButton.setBackgroundResource(R.drawable.ic_action_play)
        playerButton.setOnClickListener { play() }

        soundWaveView.id = viewId
        soundWaveView.setBackgroundColor(Color.BLUE)
        soundWaveView.seekListener = object: SoundWaveView.OnSeekListener {

            var pausedBySeeking = false

            override fun onSeekBegin(progress: Float) {
                if (playing) {
                    pause()
                    pausedBySeeking = true
                }
            }

            override fun onSeekUpdate(progress: Float) {

            }

            override fun onSeekComplete(progress: Float) {
                mediaPlayer.seekTo(progressToMilliseconds(progress))
                if (!playing && pausedBySeeking) play()
                pausedBySeeking = false
            }
        }

        val containerParams =  LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 300)
        containerParams.topMargin = 200
        containerParams.bottomMargin = 200
        containerView.layoutParams = containerParams

        containerView.addView(playerButton)
        containerView.addView(soundWaveView)

    }

}