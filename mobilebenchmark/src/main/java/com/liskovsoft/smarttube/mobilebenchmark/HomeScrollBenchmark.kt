package com.liskovsoft.smarttube.mobilebenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Measures actual RecyclerView frame timing while repeatedly scrolling the mobile Home feed. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class HomeScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun homeScroll() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
        device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "mobile_list")), 8_000)
        val list = device.findObject(By.res(TARGET_PACKAGE, "mobile_list")) ?: return@measureRepeated
        list.setGestureMargin(device.displayWidth / 5)
        repeat(4) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(2) {
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }
}
