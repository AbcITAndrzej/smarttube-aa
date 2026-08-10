# Walidacja pakietu – 2026-08-08

## Wykonane automatycznie

- parsowanie wszystkich 64 plików XML w `smarttubetv/src/stmobile/res`: **OK**, 0 błędów,
- kontrola 176 odwołań zasobów w nowych/zmienionych plikach (`R.string`, `R.id`, `R.layout`, `R.drawable`, `R.array`, `R.dimen`, `R.color`, `R.style`): **0 brakujących zasobów** w statycznym skanerze,
- `javac -source 8 -target 8 -proc:none` na zmienionych plikach: środowisko nie ma Android/AndroidX classpath, więc występują oczekiwane `package ... does not exist` / `cannot find symbol`; nie znaleziono błędów parsera typu `';' expected`, `illegal start`, `reached end of file`, `unclosed ...`,
- `git diff --check`: **OK**, bez błędów whitespace,
- finalny patch obejmuje 38 plików i przechodzi `git apply --check` na czystym drzewie bazowym; po aplikacji wszystkie 38 zmienionych plików jest bajtowo zgodnych z drzewem roboczym,
- ZIP jest po utworzeniu otwierany i testowany pod kątem integralności.

## Czego nie można było wykonać w tym środowisku

Pełny Gradle build / Android Lint / Robolectric nie został uruchomiony, ponieważ:

- brak Android SDK (`ANDROID_HOME`),
- wrapper wymaga Gradle 7.5,
- lokalna dystrybucja Gradle 7.5 nie jest dostępna,
- uruchomienie `./gradlew --version` potwierdziło próbę pobrania `gradle-7.5-bin.zip`, ale środowisko nie ma dostępu sieciowego do `services.gradle.org`.

Dlatego przed instalacją uruchom lokalnie:

```bash
./gradlew :smarttubetv:testStmobileDebugUnitTest
./gradlew :smarttubetv:assembleStmobileDebug
```

oraz, jeżeli używasz CI, pełny lint/build dla `stmobile`.

## Obszary wymagające szczególnego testu na urządzeniu

1. `RadioDvrProxy` na różnych serwerach Icecast/Shoutcast i różnych kodekach.
2. Zachowanie seekbara Android Auto – UI i szczegóły renderowania MediaSession zależą od hosta/wersji AA.
3. Eksperymentalny `CAR_LAUNCHER` – obecność powierzchni zależy od wsparcia platformy/hosta; brak widoczności nie powinien naruszać stabilnego MediaBrowserService.
4. Rotacja, PiP i zoom po zmianach ustawień mobilnego playera.
5. Wielojęzyczne materiały, w których kod języka jest regionalny (`pl-PL`, `en-US`) albo label jest niestandardowy.
