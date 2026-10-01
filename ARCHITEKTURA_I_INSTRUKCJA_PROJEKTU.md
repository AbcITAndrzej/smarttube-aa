# DOKUMENTACJA ARCHITEKTURY I PRZEWODNIK DEWELOPERSKI: SmartTube AA

> **Status projektu:** Wersja stabilna `aa1.49` (`versionCode 2433`)  
> **Repozytorium GitHub:** [AbcITAndrzej/smarttube-aa](https://github.com/AbcITAndrzej/smarttube-aa)  
> **Kluczowy cel:** Szybka, bezbolesna i precyzyjna analiza projektu dla programistów i asystentów AI w przyszłych zadaniach.

---

## 1. Czym jest SmartTube AA i dlaczego jest to projekt łączony (hybrydowy)?

Projekt **SmartTube AA** to zaawansowana hybryda łącząca:
1. **Bazowy silnik odtwarzania i API YouTube z oficjalnego SmartTube:**
   - Oryginalny projekt autorstwa Yuliskova: [https://github.com/yuliskov/SmartTube](https://github.com/yuliskov/SmartTube).
   - Z oficjalnego SmartTube pochodzi silnik komunikacji z YouTube InnerTube (`MediaServiceCore/youtubeapi`), obsługa strumieni SABR (Server-driven Adaptive Bitrate streaming, parsowanie pakietów UMP/Protobuf), integracja z Cronet oraz mechanizmy obchodzenia zabezpieczeń strumieni (poToken/BotGuard).
2. **Natywny interfejs mobilny pod smartfony (zamiast interfejsu Android TV Leanback):**
   - Oficjalny SmartTube powstał pod telewizory (Android TV / Leanback UI).
   - Wersja AA zastępuje Leanback dedykowanym interfejsem dla telefonów: nawigacja mobilna, obsługa dotyku, tryb pełnoekranowy (immersive fullscreen), obsługa wycięć w ekranie (display cutout/notch) oraz adaptacja motywów.
3. **Pełna integracja z Android Auto (samochód) i odtwarzaniem w tle:**
   - Usługa multimedialna `SmartTubeAutoMusicService` dziedzicząca po `MediaBrowserServiceCompat`.
   - Zgodność z protokołem Android Auto: przeglądanie playlist, sterowanie z ekranu samochodu i przycisków kierownicy.
   - Zaawansowane sterowanie z ekranu blokady (`MobileMediaSessionManager`): wysyłanie bitmap okładki, metadane utworu, przewijanie (seeking), przyciski poprzedni/następny.
   - Wbudowany tryb Radio / Autoodtwarzanie.

---

## 2. Dwa równoległe warianty APK (Flavors / Build Types)

Repozytorium buduje dwa odrębne pakiety, aby eksperymenty wideo w samochodzie nie wpływały na stabilność podstawowej aplikacji muzycznej:

| Wariant | Identyfikator pakietu (`applicationId`) | Przeznaczenie |
| :--- | :--- | :--- |
| **SmartTube AA Music** | `app.smarttube.mobile` | **Podstawowa, stabilna wersja produkcyjna.** Dedykowana do telefonu, odtwarzania w tle i Android Auto Music (dźwięk, playlisty, radio). |
| **SmartTube AA Video EXP** | `app.smarttube.mobile.carvideo` | **Wersja eksperymentalna.** Posiada odblokowany obraz wideo w Android Auto podczas postoju pojazdu (parked car video). |

---

## 3. Kluczowa struktura katalogów i modułów

```
D:\APK\_SMARTUBE_\SmartTube-AA-MAIN\
├── smarttubetv/                # Główny moduł aplikacji Android
│   ├── src/stmobile/           # Źródła warstwy mobilnej i Android Auto
│   │   └── java/.../mobile/
│   │       ├── nativeui/       # Fragmenty, widoki, MobileNativeActivity
│   │       └── background/     # MobileMediaSessionManager, SmartTubeAutoMusicService
│   └── build.gradle            # Wersjonowanie (versionCode, versionNameSuffix), podpisywanie
├── MediaServiceCore/           # Moduł komunikacji z serwisami YouTube
│   └── youtubeapi/             # Klienci InnerTube (WEB, TV, iOS), formaty wideo/audio, poToken
├── exoplayer-amzn-2.10.6/      # Zmodyfikowany ExoPlayer z obsługą protokołu SABR
│   └── library/sabr/           # Parsowanie formatów SABR, UMP, adaptery demuxerów Matroska/MP4
│       └── .../parser/
│           ├── adapter/        # SabrMatroskaAdapter (WebM/Opus audio)
│           ├── misc/           # SabrExtractorInput (czytanie chunków i obsługa SABR)
│           └── SabrStream.java # Stan strumienia SABR, NextRequestPolicy
├── common/                     # Wspólne kontrolery i polityki błędów
│   └── .../exoplayer/errors/   # SabrDefaultLoadErrorHandlingPolicy (zarządzanie opóźnieniami ponawiania)
├── updates/                    # Manifesty automatycznych aktualizacji in-app
│   ├── music.json              # Autoupdate dla wariantu Music
│   └── video-exp.json          # Autoupdate dla wariantu Video EXP
└── .github/workflows/          # CI/CD GitHub Actions
    └── build-production-update-fix.yml # Automatyczna kompilacja, podpis i publikacja wydań GitHub Release
```

---

## 4. Ostatnio rozwiązana usterka: Zawieszanie po ~1 minucie (Wydanie aa1.49 / versionCode 2433)

### Objawy przed naprawą:
- Utwór w SmartTube AA na telefonie lub w Android Auto (np. `KaRRamBa - Pocałuj mnie w d*pę`) odtwarzał się płynnie do ok. 66–70 sekundy.
- Nagle odtwarzacz wchodził w stan buforowania (`state=BUFFERING(6)` / `STATE_BUFFERING`), dźwięk cichł, a utwór już nigdy nie wznawiał odtwarzania. W Android Auto znikała playlista.

### Przyczyna źródłowa (Root Cause):
1. YouTube dla strumieni audio SABR (Opus 160 kbps, format itag 251) po zbuforowaniu początkowej partii danych przesyła pakiet `NextRequestPolicy` z parametrem `backoff_time_ms: ~4000` (throttling/pacing) i **zerową liczbą bajtów audio**.
2. W `SabrExtractorInput.java` brak danych traktowany był jako zwykły koniec chunka (`break`), a `SabrMatroskaAdapter.java` połykał sygnał i zwracał `RESULT_END_OF_INPUT`.
3. `DefaultSabrChunkSource.java` uważał, że chunk natychmiast się zakończył i natychmiast wysyłał zapytanie o kolejny chunk.
4. W ciągu 2 sekund aplikacja wysyłała do YouTube ponad 200 zapytań (`rn=1` do `rn=212`).
5. YouTube widząc zalew zapytań w trakcie trwania okna backoff zwracał błąd formatu strumienia (`SabrStreamError: Received format # FormatId`), MatroskaAdapter utylizował wejście z wynikiem `-1`, a ExoPlayer zamarzał na stałe w pętli buforowania.

### Dlaczego oficjalny SmartTube tego nie miał w tej postaci?
- Oficjalny SmartTube na Android TV (autorstwa Yuliskova, commit `66cf8bb`) próbował obsługiwać `RefreshPlayerResponse` i przeładowywać cały odtwarzacz, co wywoływało pętlę przeładowań na telewizorach i zostało cofnięte.
- W wersji mobilnej/AA **nie wolno przeładowywać playera w trakcie odtwarzania w tle**, ponieważ przerywa to sesję audio.
- W SmartTube AA należało wdrożyć czyste przekazanie czasu uśpienia (`backoff delay`) do polityki błędów ExoPlayera bez restartu odtwarzacza.

### Zastosowane rozwiązanie:
1. **[`SabrExtractorInput.java`](file:///D:/APK/_SMARTUBE_/SmartTube-AA-MAIN/exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/parser/misc/SabrExtractorInput.java)**:
   Gdy serwer zwraca `sabrPart == null`, a w danym chunku nie odebrano jeszcze mediów (`!mediaSeen`), sprawdzany jest `sabrStream.getBackoffTimeMs()`. Jeśli `backoffMs > 0`, rzucany jest dedykowany `IOException(BACKOFF_MARKER + backoffMs)`.
2. **[`SabrMatroskaAdapter.java`](file:///D:/APK/_SMARTUBE_/SmartTube-AA-MAIN/exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/parser/adapter/SabrMatroskaAdapter.java)**:
   Przepuszcza w górę wyjątek z `BACKOFF_MARKER` zamiast go połykać i maskować jako `RESULT_END_OF_INPUT`.
3. **[`DefaultSabrChunkSource.java`](file:///D:/APK/_SMARTUBE_/SmartTube-AA-MAIN/exoplayer-amzn-2.10.6/library/sabr/src/main/java/com/google/android/exoplayer2/source/sabr/DefaultSabrChunkSource.java)**:
   W `onChunkLoadError` przekazuje błąd z `BACKOFF_MARKER` do polityki ponawiania (`return false;`).
4. **[`SabrDefaultLoadErrorHandlingPolicy.java`](file:///D:/APK/_SMARTUBE_/SmartTube-AA-MAIN/common/src/main/java/com/liskovsoft/smartyoutubetv2/common/exoplayer/errors/SabrDefaultLoadErrorHandlingPolicy.java)**:
   Parsuje żądany czas oczekiwania w ms i nakazuje ExoPlayerowi odczekać ten czas przed ponowną próbą załadowania chunka.
   Odtwarzacz odczekuje ~4 sekundy w tle (grając z wcześniej zbuforowanych kilku sekund), po czym ponawia zapytanie, odbiera audio i kontynuuje odtwarzanie bez żadnego zacięcia.

---

## 5. Jak testować aplikację na żywym sprzęcie i w Android Auto

### Sprzęt testowy:
- Telefon: **Samsung Galaxy S26 Ultra** (ID ADB: `RFCY1197K8L`).
- Ścieżka do ADB: `C:\Users\DELL\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

### Testowanie z Android Auto Desktop Head Unit (DHU):
1. Na pulpicie systemu Windows znajduje się gotowy skrót: **`Android Auto (DHU).lnk`** (wskazuje na skrypt `01-SMARTUBE-RUN-DHU-ADB.bat`).
2. Skrypt automatycznie:
   - Wykrywa urządzenie `RFCY1197K8L`,
   - Przekierowuje port 5277 (`adb forward tcp:5277 tcp:5277`),
   - Uruchamia emulator samochodowej jednostki multimedialnej DHU na ekranie PC.
3. W telefonie po podłączeniu DHU pojawia się ikona SmartTube w menu aplikacji Android Auto.

### Zarządzanie ekranem telefonu podczas testów:
- Podczas długich testów audio ekran telefonu można wygasić przez ADB, aby nie drenować baterii:
  ```powershell
  & C:\Users\DELL\AppData\Local\Android\Sdk\platform-tools\adb.exe -s RFCY1197K8L shell input keyevent 26
  ```

---

## 6. Procedura kompilacji, instalacji i publikacji wydań

### 1) Kompilacja lokalna APK (Debug)
```powershell
.\gradlew.bat :smarttubetv:assembleStmobileDebug --console=plain
```
Gotowy plik APK znajduje się w:
`smarttubetv\build\outputs\apk\stmobile\debug\SmartTube_mobile_32.04-mobile-p13-aaX.XX_arm64-v8a.apk`

### 2) Instalacja na telefonie przez ADB
```powershell
& C:\Users\DELL\AppData\Local\Android\Sdk\platform-tools\adb.exe -s RFCY1197K8L install -r smarttubetv\build\outputs\apk\stmobile\debug\SmartTube_mobile_32.04-mobile-p13-aa1.49_arm64-v8a.apk
```

### 3) Publikacja oficjalnego wydania (GitHub Actions)
Repozytorium posiada automatyczny pipeline publikacji wydań z podpisem oficjalnym kluczem produkcyjnym:
1. Zwiększ numer wersji:
   - `smarttubetv/build.gradle`: zaktualizuj `versionCode` (np. `2433`) i `versionNameSuffix` (np. `-mobile-p13-aa1.49`).
   - `updates/music.json`: dodaj wpis nowego wydania oraz zaktualizuj `downloadUrl`.
   - `updates/video-exp.json`: dodaj wpis nowego wydania oraz zaktualizuj `downloadUrl`.
   - `.github/workflows/build-production-update-fix.yml`: zaktualizuj tag, wersję i treść changeloga.
2. Zatwierdź i wyślij:
   ```bash
   git add .
   git commit -m "Opis zmian (aaX.XX)"
   git tag aaX.XX
   git push origin main
   git push origin aaX.XX
   ```
3. GitHub Actions automatycznie:
   - Kompiluje oba warianty (Music i Video EXP),
   - Podpisuje je kluczem produkcyjnym ze zmiennej secret,
   - Publikuje oficjalne wydanie GitHub Release pod tagiem `aaX.XX`,
   - Aplikacje zainstalowane u użytkowników wykrywają nowe wydanie przez przycisk *„Sprawdź aktualizacje”*.
