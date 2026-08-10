package com.liskovsoft.smarttube.mobilebenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AGP 7.4-compatible Baseline Profile generator.
 *
 * The generated HRF file must be copied manually to smarttubetv/src/main/baseline-prof.txt; use
 * tools/stage12-install-baseline-profile.py after a connected-device run.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateMobileCriticalJourneys() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE
    ) {
        pressHome()
        startActivityAndWait()

        device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "mobile_list")), 8_000)
        device.findObject(By.res(TARGET_PACKAGE, "mobile_list"))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            repeat(3) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
        }

        // Exercise Search construction without depending on a network result.
        device.findObject(By.res(TARGET_PACKAGE, "mobile_nav_search"))?.click()
        device.waitForIdle()

        // Exercise Settings/Diagnostics screen construction, another frequent cold-path.
        device.findObject(By.res(TARGET_PACKAGE, "mobile_nav_settings"))?.click()
        device.waitForIdle()
    }
}
