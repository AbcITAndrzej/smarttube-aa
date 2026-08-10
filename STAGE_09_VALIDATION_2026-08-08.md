# Stage 09 — raport walidacji statycznej

Data: 2026-08-08
Baza patcha: `smarttube-aa-stage-07-listen-save-after-stage-08-2026-08-08.zip`

## Zakres kontroli

### XML / zasoby

- wszystkie **74/74** XML-e w `smarttubetv/src/stmobile/res` parsują się poprawnie,
- `smarttubetv/src/main/AndroidManifest.xml` parsuje się poprawnie,
- `values/mobile_native_strings.xml`: 377 nazwanych zasobów, 0 duplikatów,
- `values-pl/mobile_native_strings.xml`: 376 nazwanych zasobów, 0 duplikatów,
- różnica 1 wpisu EN/PL jest historycznym zasobem obecnym przed Stage 09,
- nowe ID/stringi Stage 09 są zadeklarowane i używane przez odpowiadające im layouty/fragmenty.

### Java

- `git diff --check`: PASS,
- kontrola parserowa `javac -proc:none` zmienionych plików: brak błędów gramatycznych typu `; expected`, `illegal start`, `unclosed string`, `reached end of file`, itp.; pozostałe komunikaty wynikają z braku Android SDK/classpath,
- `AndroidAutoOfflineRoutingTest` skompilowany i uruchomiony na minimalnych stubach JUnit/OfflineRepository: **STAGE9_PURE_TESTS_OK**,
- testy obejmują: jawne Offline bez fallbacku do sieci, auto-fallback tylko bez sieci, semantykę resume po powrocie internetu, dopasowanie snapshotu `offline:`, wyszukanie kolejnego lokalnego elementu i wrap,
- `AndroidAutoPreferences` skompilowane na minimalnych stubach Android SharedPreferences; smoke test domyślnych wartości: **STAGE9_PREFS_SMOKE_OK**.

### Odtwarzanie / izolacja

- `LegacyMobilePlaybackRepository` pozwala teraz na `offline:<mediaId>` także w headless repository Android Auto,
- nadal nie tworzy własnej publicznej MediaSession; stabilną sesję utrzymuje `SmartTubeAutoMusicService`,
- jawna pozycja z drzewa Offline nigdy nie spada po cichu do streamingu online,
- automatyczny fallback wymaga kompletnej lokalnej kopii,
- Stage 09 nie uruchamia `OfflineListenSaveService` ani `OfflinePlaylistDownloadService`,
- Radio/Radio DVR nie zostały podpięte do offline audio VOD,
- eksperymentalny komponent AA Video nie został zmodyfikowany przez Stage 09.

### Resume / kolejka

- zapisywany jest osobny `PREF_PLAYBACK_ID`, więc `offline:<mediaId>` można wznowić po restarcie,
- jawne źródło Offline pozostaje lokalne po restarcie,
- automatyczny fallback z normalnego źródła nie pozostaje wymuszony po odzyskaniu internetu,
- dla lokalnego źródła pomijana jest sieciowa hydratacja kolejki,
- przy braku internetu auto-next może ominąć elementy bez lokalnego pliku,
- pojedyncze kolejki Offline AA są ograniczone do 160 elementów.

### Magazyn

- dodane `peekAvailableFile()` / `hasAvailableFile()` nie aktualizują LRU podczas przeglądania,
- `resolveAvailableFile()` nadal aktualizuje LRU przy faktycznym odtworzeniu,
- brak nowej bazy i brak nowych kolumn URL,
- Stage 09 nie zapisuje signed URL.

### Sieć

- brak nowego zahardkodowanego `http://` / `https://` endpointu w kodzie produkcyjnym Stage 09,
- stan łączności pochodzi z Android `ConnectivityManager`,
- dla API 23+ wymagane są `NET_CAPABILITY_INTERNET` i `NET_CAPABILITY_VALIDATED`,
- legacy path używa `NetworkInfo.isConnected()`,
- główny manifest ma teraz jawne `android.permission.ACCESS_NETWORK_STATE`,
- kontrola stanu sieci jest osłonięta fail-safe `try/catch`, żeby brak/uszkodzona usługa systemowa nie wywracała AA.

## Gradle / pełny build

Pełny Android/AGP build nie został wykonany w tym środowisku: wrapper projektu wymaga Gradle 7.5, a lokalna dystrybucja i Android SDK nie są dostępne. Wcześniejsze próby wrappera w tym środowisku kończyły się próbą pobrania `https://services.gradle.org/distributions/gradle-7.5-bin.zip`.

Do wykonania po stronie użytkownika:

```bash
./gradlew :smarttubetv:testStmobileDebugUnitTest
./gradlew :smarttubetv:assembleStmobileDebug
```

## Kontrole patcha / wydania

- patch Stage 09 obejmuje **18 plików**,
- patch został zastosowany przez `patch -p1` do świeżo rozpakowanej paczki `smarttube-aa-stage-07-listen-save-after-stage-08-2026-08-08.zip`: **PASS**,
- porównanie `diff -qr` patchowanego drzewa z docelowym Stage 09 (z pominięciem lokalnego `.git`): **identyczne 1:1**,
- przed finalnym przekazaniem pełny ZIP jest dodatkowo sprawdzany przez `unzip -t`, a jego SHA-256 jest publikowane obok paczki.
