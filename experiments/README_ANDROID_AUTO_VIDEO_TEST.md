# Android Auto Video / AA Suite — isolated test branch

## What this IS

- Experimental, independently buildable reference app from **iAlias/AASuite**, pinned as a Git submodule to commit `e449095fa56269c091a6bc16b08c0dad72b14bb5` (2026-10-17).
- Original source: https://github.com/iAlias/AASuite
- License: MIT; copyright (c) 2026 iAlias. The complete upstream LICENSE is in the submodule.
- A **separate test application**, not a merge into WaveAA Music or WaveAA Video EXP.
- It is **not** a clone of AA Car Play Video: https://github.com/aacarplayvideo/aacarplayvideo publishes only product documentation, NOT the closed-source Android app.

## Architecture to test

AASuite uses Android Auto Car App Library with a navigation-style service and `ACCESS_SURFACE`, forwarding
`MediaProjection -> VirtualDisplay -> car Surface` for mirroring. Accessibility maps display taps to phone gestures.
Its YouTube TV mode puts `youtube.com/tv` in a WebView rendered through a Presentation on a VirtualDisplay.
YouTube phone-to-TV-code pairing is not native Google Cast/Chromecast.

## Get the complete source locally

```bash
git clone --branch experiment/android-auto-surface-aasuite-2026-10 --recurse-submodules https://github.com/AbcITAndrzej/smarttube-aa.git
cd smarttube-aa/experiments/aasuite
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

If you already cloned the repo:

```bash
git fetch origin
git switch experiment/android-auto-surface-aasuite-2026-10
git submodule update --init --recursive
cd experiments/aasuite
./gradlew :app:assembleDebug
```

You can also open `experiments/aasuite` as an **independent Gradle project** in Android Studio.
This submodule is pinned: later upstream changes won't silently change your test.

## What to verify on the bench / while parked

1. App builds independently and starts on the phone.
2. Android Auto test host (DHU) launches it. Check permissions and compatibility before using a real car.
3. MediaProjection permission starts and stops appropriately; no stale notification after stopping.
4. Browser renders, links open, rotate/resize works without a black surface.
5. Touch maps to the correct locations, including wide car screens.
6. YouTube TV pairing and audio work. Verify without assuming DRM streaming services will work.
7. Check wired and wireless Android Auto separately. Head unit behavior may differ.

## Distribution and safety

AASuite's author states it is for personal/internal testing and not eligible for Play Store public distribution
with browser/video features exposed as navigation. Play Console internal testing may be required for the car
launcher to show it; adb installing alone may not make it appear. This is NOT evidence that Play approves such a
navigation-category workaround.

**Video is for parked use or a passenger-only safe display. Never watch it as a driver in motion.**

No application sources, Gradle configuration, publication settings or signing keys of WaveAA are changed in this branch.
