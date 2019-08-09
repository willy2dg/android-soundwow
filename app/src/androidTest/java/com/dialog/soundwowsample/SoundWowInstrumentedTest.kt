package com.dialog.soundwowsample

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.dialog.soundwowsample.IdlingResources.SoundWaveLoadedIdlingResource

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Rule
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.CoordinatesProvider
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.platform.app.InstrumentationRegistry
import com.dialog.soundwow.SoundDecoder
import com.dialog.soundwow.SoundWaveView
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.junit.Assert
import androidx.test.espresso.action.MotionEvents
import android.view.MotionEvent
import android.widget.ScrollView
import androidx.test.espresso.UiController
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @get:Rule
    var activityScenarioRule = ActivityScenarioRule<MainActivity>(MainActivity::class.java)

    @Test
    fun soundWaveViewTest() {
        val soundWaveLoadedIdlingResource = SoundWaveLoadedIdlingResource()

        // Wait for the view to be loaded
        IdlingRegistry.getInstance().register(soundWaveLoadedIdlingResource)
        IdlingRegistry.getInstance().unregister(soundWaveLoadedIdlingResource)

        // Tap on the center of the view and check view progress
        onView(withId(R.id.sound_wave_view_1)).perform(centerClick())
        onView(withId(R.id.sound_wave_view_1)).check(matches(isProgress(0.5f)))

        // Hold touch and drag from 1/3 to 2/3, check correct view progress
        onView(withId(R.id.sound_wave_view_1)).perform(holdAndDrag(5))
        onView(withId(R.id.sound_wave_view_1)).check(matches(isProgress(0.66f)))

        // Swipe up over the view and check if scroll works properly
        onView(withId(R.id.sound_wave_view_1)).perform(swipeUp())
        onView(withId(R.id.scroll)).check(matches(isScrolledDown()))
    }

}

// Tap on the center of the view
fun centerClick(): ViewAction {
    return GeneralClickAction(
        Tap.SINGLE,
        CoordinatesProvider { view ->
            val screenPos = IntArray(2)
            view.getLocationOnScreen(screenPos)

            val screenX = (screenPos[0] + view.width * 0.5).toFloat()
            val screenY = (screenPos[1] + view.height * 0.5).toFloat()

            floatArrayOf(screenX, screenY)
        },
        Press.FINGER
    )
}

// Hold touch and drag from 1/3 to 2/3 view width
fun holdAndDrag(steps: Int = 1): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View> {
            return isDisplayed()
        }

        override fun getDescription(): String {
            return "Send touch events."
        }

        override fun perform(uiController: UiController, view: View) {
            // Get view absolute position
            val location = IntArray(2)
            view.getLocationOnScreen(location)

            // Drag centered vertically
            val dragY = location[1] + view.height * 0.5f
            // Drag horizontally from 1/3 to 2/3
            val dragStartX = location[0] + view.width * 0.33f
            val dragEndX = location[0] + view.width * 0.66f
            val dragStep = (dragEndX - dragStartX) / steps

            // Offset coordinates by view position
            val coordinates = floatArrayOf(dragStartX, dragY)
            val precision = floatArrayOf(1f, 1f)

            // Send down event and "hold"
            val down = MotionEvents.sendDown(uiController, coordinates, precision).down
            uiController.loopMainThreadForAtLeast(1000)

            // Perform movement steps
            for ( i in 0 until steps ) {
                coordinates[0] += dragStep
                MotionEvents.sendMovement(uiController, down, coordinates)
                uiController.loopMainThreadForAtLeast(100)
            }

            // Release the touch
            MotionEvents.sendUp(uiController, down, coordinates)
        }
    }
}

// Check sound view progress with optional threshold
fun isProgress(targetProgress: Float, threshold: Float = 0.01f) : Matcher<View> {
    return object : BoundedMatcher<View, SoundWaveView>(SoundWaveView::class.java) {

        override fun describeTo(description: Description) {
            description.appendText("Checking progress to be $targetProgress, with a threshold of $threshold")
        }

        override fun matchesSafely(item: SoundWaveView): Boolean {
            val progress = item.progress
            return progress > targetProgress - threshold && progress < targetProgress + threshold
        }

    }
}

// Check if scroll view has been scrolled down
fun isScrolledDown() : Matcher<View> {
    return object : BoundedMatcher<View, ScrollView>(ScrollView::class.java) {

        override fun describeTo(description: Description) {
            description.appendText("Checking scroll offset")
        }

        override fun matchesSafely(item: ScrollView): Boolean {
            return item.scrollY != 0
        }

    }
}