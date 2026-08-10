package com.liskovsoft.smarttube.mobilebenchmark

import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Measures launcher-to-first/full-display for the mobile flavor with the packaged Baseline Profile. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }

    @Test
    fun warmStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }
}
