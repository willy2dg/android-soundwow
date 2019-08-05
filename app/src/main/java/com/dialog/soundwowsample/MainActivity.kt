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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val player1 = SimplePlayer(this, R.raw.test1)
        val player2 = SimplePlayer(this, R.raw.test2)
        val player3 = SimplePlayer(this, R.raw.test3)
        val player4 = SimplePlayer(this, R.raw.test3)

        player4.soundWaveView.blockSize = 10
        player4.soundWaveView.blockMargin = 4

        container.addView(player1.containerView)
        container.addView(player2.containerView)
        container.addView(player3.containerView)
    }

}

class SimplePlayer(context: Context, assetId: Int) {

    val mediaPlayer: MediaPlayer = MediaPlayer.create(context, assetId)

    val containerView: LinearLayout = LinearLayout(context)
    val playerButton: Button = Button(context)
    val soundWaveView: SoundWaveView = SoundWaveView(context)

    var playing: Boolean = false

    fun progressToMilliseconds(progress: Float): Int{
        return (progress * mediaPlayer.duration).toInt()
    }

    init{

        val waveViewUpdateHandler = Handler()
        val waveViewUpdateRunnable = object: Runnable {
            override fun run() {
                val percent = mediaPlayer.currentPosition.toFloat() / mediaPlayer.duration.toFloat()
                soundWaveView.setProgress(percent)
                waveViewUpdateHandler.postDelayed(this, 100)
            }
        }

        playerButton.height = 300
        playerButton.setBackgroundResource(R.drawable.ic_action_play)
        playerButton.setOnClickListener { view ->
            if (playing) {
                mediaPlayer.pause()
                playerButton.setBackgroundResource(R.drawable.ic_action_play)
                waveViewUpdateHandler.removeCallbacks(waveViewUpdateRunnable)
            } else {
                mediaPlayer.start()
                playerButton.setBackgroundResource(R.drawable.ic_action_pause)
                waveViewUpdateHandler.postDelayed(waveViewUpdateRunnable, 0)
            }
            playing = !playing
        }

        soundWaveView.setBackgroundColor(Color.BLUE)
        soundWaveView.seekListener = object: SoundWaveView.OnSeekListener {
            override fun onSeekBegin(progress: Float) {

            }

            override fun onSeekUpdate(progress: Float) {

            }

            override fun onSeekComplete(progress: Float) {
                mediaPlayer.seekTo(progressToMilliseconds(progress))
            }
        }

        soundWaveView.setSound(context.resources.openRawResourceFd(assetId))

        val containerParams =  LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 300)
        containerParams.topMargin = 200
        containerParams.bottomMargin = 200
        containerView.layoutParams = containerParams

        containerView.addView(playerButton)
        containerView.addView(soundWaveView)

    }

}