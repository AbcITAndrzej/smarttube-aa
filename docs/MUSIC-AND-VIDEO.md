# SmartTube AA Music i SmartTube AA Video EXP

Repozytorium zawiera **jeden wspólny kod źródłowy**, z którego powstają dwa osobno instalowane warianty aplikacji.

## SmartTube AA Music

- Pakiet: `app.smarttube.mobile`
- Wariant Gradle: `StmobileDebug`
- Przeznaczenie: muzyka YouTube, playlisty, radio internetowe i biblioteka offline.
- Android Auto korzysta z interfejsu muzycznego opartego o `SmartTubeAutoMusicService`.
- Jest to podstawowy i obecnie aktywnie publikowany wariant projektu.
- Aktualny build z `main`: **aa1.39-test14**.

Aktualne buildy Music są dostępne w GitHub Actions:

- [Build SmartTube AA APK](https://github.com/AbcITAndrzej/smarttube-aa/actions/workflows/build-smarttube-aa.yml)
- [Zweryfikowany build #49](https://github.com/AbcITAndrzej/smarttube-aa/actions/runs/33733156005)

## SmartTube AA Video EXP

- Pakiet: `app.smarttube.mobile.carvideo`
- Wariant Gradle: `StmobileCarvideo`
- Przeznaczenie: eksperymentalny ekran aplikacji na wyświetlaczu Android Auto podczas postoju.
- Nie zastępuje wersji Music i może być zainstalowany obok niej.
- Ma osobne dane aplikacji dzięki innemu identyfikatorowi pakietu.
- Funkcja zależy od wersji Androida, Android Auto i polityki danego urządzenia.

Kod Video EXP **jest nadal obecny w repozytorium**, ale aktualny workflow aa1.39 uruchamiany z `main` buduje obecnie tylko wariant Music. Ostatni publiczny Release z gotowym APK Video EXP to **aa1.23**:

- [SmartTube AA Video EXP — ostatni opublikowany APK](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest/download/SmartTube-AA-Video-EXP-latest.apk)
- [Ostatni GitHub Release](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest)

## Który APK pobrać

- Zwykły użytkownik: **SmartTube AA Music UNIVERSAL** z najnowszego udanego buildu `main`.
- Telefon arm64: można wybrać mniejszy wariant **ARM64-v8a**.
- Starsze urządzenia 32-bit: wariant **armeabi-v7a**.
- Video EXP: tylko do dodatkowych testów obrazu na postoju; obecnie jego najnowszy publiczny APK pochodzi z wydania aa1.23.

## Ważne

Music i Video EXP nie są dwoma osobnymi repozytoriami GitHub. Są dwoma wariantami tej samej bazy kodu w `AbcITAndrzej/smarttube-aa`.

Video EXP nie jest przeznaczone do oglądania podczas jazdy. System samochodu może ograniczyć lub zablokować obraz po rozpoczęciu jazdy.
