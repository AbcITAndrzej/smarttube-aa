# SmartTube AA Music i SmartTube AA Video EXP

Repozytorium zawiera **jeden wspólny kod źródłowy**, z którego powstają dwa osobno instalowane warianty aplikacji.

## SmartTube AA Music

- Pakiet: `app.smarttube.mobile`
- Wariant Gradle: `StmobileDebug`
- Aktualna linia: **aa1.39-test15**
- Przeznaczenie: muzyka YouTube, playlisty, radio internetowe, biblioteka offline i normalna obsługa multimedialna Android Auto.
- Android Auto korzysta z `SmartTubeAutoMusicService`.
- Jest to podstawowy, zalecany wariant projektu.

Bezpośrednie APK:

- [Music UNIVERSAL](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-main/SmartTube-AA-Music-universal.apk)
- [Music ARM64-v8a](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-main/SmartTube-AA-Music-arm64-v8a.apk)
- [Music armeabi-v7a](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-main/SmartTube-AA-Music-armeabi-v7a.apk)
- [Release `latest-main`](https://github.com/AbcITAndrzej/smarttube-aa/releases/tag/latest-main)

## SmartTube AA Video EXP

- Pakiet: `app.smarttube.mobile.carvideo`
- Wariant Gradle: `StmobileCarvideo`
- Aktualna linia: **aa1.39-test15**
- Przeznaczenie: eksperymentalny ekran aplikacji i obrazu na wyświetlaczu Android Auto podczas postoju.
- Nie zastępuje Music i może być zainstalowany obok niego.
- Ma osobne dane aplikacji dzięki innemu identyfikatorowi pakietu.
- Funkcja zależy od wersji Androida, Android Auto oraz polityki konkretnego samochodu/urządzenia.

Bezpośrednie APK:

- [Video EXP UNIVERSAL](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-video-exp/SmartTube-AA-Video-EXP-universal.apk)
- [Video EXP ARM64-v8a](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-video-exp/SmartTube-AA-Video-EXP-arm64-v8a.apk)
- [Video EXP armeabi-v7a](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/latest-video-exp/SmartTube-AA-Video-EXP-armeabi-v7a.apk)
- [Release `latest-video-exp`](https://github.com/AbcITAndrzej/smarttube-aa/releases/tag/latest-video-exp)

## Co jest wspólne

Oba warianty są budowane z tej samej bazy aa1.39 i korzystają z tego samego współczesnego pipeline odtwarzania:

- `audioTrack.id`,
- `isAutoDubbed`,
- `xtags`,
- logiczne grupowanie ścieżek audio,
- polski lektor / dubbing, jeśli YouTube udostępnia taką ścieżkę,
- poprawione tory SABR audio/video,
- cross-track SABR fix,
- napisy i logowanie do konta.

Stary Video EXP aa1.23 nie miał kompletnego obecnego pipeline multi-audio. Dlatego zachowanie listy lektorów mogło różnić się od nowszego Music.

## Ustawienia

Music i Video EXP korzystają obecnie z tego samego spójnego, przewijanego ekranu ustawień:

1. **KONTA** na samej górze,
2. pozycje mobile / Android Auto,
3. pozostałe oryginalne kategorie SmartTube.

Nie ma już osobnego bloku kolorowych przycisków nad drugą listą ustawień.

## Automatyczne buildy

Po zmianie na `main` uruchamiają się dwa osobne workflowy:

- [Build SmartTube AA Music APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml) → `latest-main`,
- [Build SmartTube AA Video EXP APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa-video-exp.yml) → `latest-video-exp`.

Oba publikują zwykłe pliki `.apk`, bez konieczności rozpakowywania ZIP-a.

## Który APK pobrać

- Zwykły użytkownik: **SmartTube AA Music UNIVERSAL**.
- Nowszy telefon ARM64: można wybrać mniejszy wariant **ARM64-v8a**.
- Starsze urządzenia 32-bit: **armeabi-v7a**.
- **Video EXP**: tylko do dodatkowych testów obrazu na postoju.

## Ważne

Music i Video EXP nie są dwoma osobnymi repozytoriami GitHub. Są dwoma wariantami tej samej bazy kodu w `AbcITAndrzej/smarttube-aa`.

Video EXP nie jest przeznaczone do oglądania podczas jazdy. System samochodu może ograniczyć lub zablokować obraz po rozpoczęciu jazdy.
