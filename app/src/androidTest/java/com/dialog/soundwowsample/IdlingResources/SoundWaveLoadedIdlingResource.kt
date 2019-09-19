package com.dialog.soundwowsample.IdlingResources

import androidx.test.espresso.IdlingResource.ResourceCallback
import androidx.test.InstrumentationRegistry.getTargetContext
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import android.app.Activity
import androidx.core.view.ViewCompat.getTranslationY
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.dialog.soundwow.SoundWaveView
import com.dialog.soundwowsample.R


class SoundWaveLoadedIdlingResource : IdlingResource {

    private var resourceCallback: ResourceCallback? = null
    private var isIdle: Boolean = false

    val currentActivity: Activity?
        //get() = InstrumentationRegistry.getInstrumentation().context.applicationContext as Activity
        get() = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).iterator().next()

    override fun getName(): String {
        return SoundWaveLoadedIdlingResource::class.java.name
    }

    override fun isIdleNow(): Boolean {
        if (isIdle) return true

        val soundWaveView = currentActivity?.findViewById<SoundWaveView>(R.id.sound_wave_view_1)

        isIdle = soundWaveView?.soundSamplesResume != null

        if (isIdle) resourceCallback?.onTransitionToIdle()

        return isIdle
    }

    override fun registerIdleTransitionCallback(resourceCallback: ResourceCallback) {
        this.resourceCallback = resourceCallback
    }
}