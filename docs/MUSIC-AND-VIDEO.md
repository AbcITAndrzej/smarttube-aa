# SmartTube AA Music i SmartTube AA Video EXP

Repozytorium zawiera **jeden wspólny kod źródłowy**, z którego powstają dwa osobno instalowane warianty aplikacji.

## SmartTube AA Music

- Pakiet: `app.smarttube.mobile`
- Wariant Gradle: `StmobileDebug`
- Aktualna linia: **aa1.50** (versionCode 2434)
- Przeznaczenie: muzyka YouTube, playlisty, radio internetowe, biblioteka offline i — opcjonalnie — Android Auto.
- Android Auto korzysta z `SmartTubeAutoMusicService`.
- Jest to podstawowy, zalecany wariant projektu.

Bezpośredni APK:

- [Music ARM64-v8a aa1.50](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/aa1.50/SmartTube-AA-Music-aa1.50-arm64-v8a.apk)
- [Wydanie aa1.50](https://github.com/AbcITAndrzej/smarttube-aa/releases/tag/aa1.50)

## SmartTube AA Video EXP

- Pakiet: `app.smarttube.mobile.carvideo`
- Wariant Gradle: `StmobileCarvideo`
- Aktualna linia: **aa1.50** (versionCode 2434)
- Przeznaczenie: eksperymentalny obraz na wyświetlaczu Android Auto podczas postoju. Nie jest potrzebny do słuchania muzyki.
- Nie zastępuje Music i może być zainstalowany obok niego.
- Ma osobne dane aplikacji dzięki innemu identyfikatorowi pakietu.
- Funkcja zależy od wersji Androida, Android Auto oraz polityki konkretnego samochodu/urządzenia.

Bezpośredni APK:

- [Video EXP ARM64-v8a aa1.50](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/aa1.50/SmartTube-AA-Video-EXP-aa1.50-arm64-v8a.apk)
- [Wydanie aa1.50](https://github.com/AbcITAndrzej/smarttube-aa/releases/tag/aa1.50)

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

## Gdzie jest aktualna paczka

Publiczne pliki są na wydaniu [aa1.50](https://github.com/AbcITAndrzej/smarttube-aa/releases/tag/aa1.50). Starsze tagi `latest-main` i `latest-video-exp` nie są tym wydaniem.

## Który APK pobrać

- Zwykły użytkownik: **SmartTube AA Music ARM64-v8a** z wydania aa1.50.
- **Video EXP**: tylko wtedy, gdy chcesz eksperymentalny obraz na postoju. Do muzyki nie jest potrzebny.

## Ważne

Music i Video EXP nie są dwoma osobnymi repozytoriami GitHub. Są dwoma wariantami tej samej bazy kodu w `AbcITAndrzej/smarttube-aa`.

Video EXP nie jest przeznaczone do oglądania podczas jazdy. System samochodu może ograniczyć lub zablokować obraz po rozpoczęciu jazdy.
