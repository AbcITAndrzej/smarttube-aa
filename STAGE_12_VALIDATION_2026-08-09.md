# Stage 12 — raport walidacji

Data: 2026-08-09

Baza: `smarttube-aa-stage-11-media3-2026-08-08`

## Wynik

Walidacja statyczna i izolowane smoke-testy Stage 12 przeszły. Pełnego Android/Gradle builda nie udało się uruchomić w tym środowisku, ponieważ wrapper nie ma lokalnej dystrybucji Gradle 7.5 i próba pobrania `https://services.gradle.org/distributions/gradle-7.5-bin.zip` kończy się `UnknownHostException`.

## Wykonane kontrole

### 1. XML / zasoby

- 160 produkcyjnych XML-i z `smarttubetv/src` + `mobilebenchmark/src` sparsowanych przez parser XML,
- wynik: `APP_XML_ERRORS 0`,
- nowe ID Diagnostyki istnieją,
- nowe stringi Stage 12 istnieją w EN i PL,
- benchmarkowe ID `mobile_list`, `mobile_nav_search`, `mobile_nav_settings` istnieją w zasobach stmobile.

### 2. `git diff --check`

- brak trailing whitespace i błędów patch whitespace.

### 3. Performance Monitor

`MobilePerformanceMonitor.java` skompilowano osobno przez `javac` z minimalnym zestawem stubów Android/FeatureFlags.

Wynik:

`STAGE12_PERFORMANCE_MONITOR_COMPILE_OK`

`javac` zgłosił wyłącznie ostrzeżenia dotyczące użycia `-source/-target 8` na nowym JDK.

### 4. SponsorBlock cache/single-flight

`SponsorBlockService.java` skompilowano osobno z minimalnymi stubami Retrofit/GlobalPreferences/API.

Wynik:

`STAGE12_SPONSORBLOCK_COMPILE_OK`

Sprawdzone zostały również:

- thread-safe LRU access,
- TTL przez `SystemClock.elapsedRealtime()`,
- deterministic cache key dla kategorii,
- leader/join single-flight,
- propagowanie interruption/error,
- brak nowego endpointu.

### 5. DeArrow cache/single-flight

`DeArrowService.kt` skompilowano osobno przez `kotlinc` z minimalnymi stubami interfejsów/API.

Wynik:

`STAGE12_DEARROW_COMPILE_OK`

Jedynym ostrzeżeniem był nieużywany parametr w testowym stubie `RetrofitHelper`.

### 6. Baseline Profile seed

- zweryfikowano format class-rule `L...;`,
- każda top-level klasa z seed `baseline-prof.txt` istnieje w źródłach stmobile,
- wynik: `BASELINE_RULE_CLASS_CHECK OK 0`.

Seed ma zostać zastąpiony profilem wygenerowanym przez `BaselineProfileRule` na realnym urządzeniu.

### 7. Helper profilu

`python3 -m py_compile tools/stage12-install-baseline-profile.py`

Wynik:

`STAGE12_HELPER_PY_COMPILE_OK`

### 8. Audyt nowych endpointów

Przeszukano finalny diff Stage 11 -> Stage 12 pod kątem nowych linii produkcyjnego kodu z `http://` / `https://`.

Wynik:

`STAGE12_NEW_ENDPOINTS 0`

Linki w dokumentacji nie są endpointami runtime.

### 9. Gradle/Macrobenchmark — ograniczenie środowiska

Próba uruchomienia wrappera kończy się przed konfiguracją projektu:

`java.net.UnknownHostException: services.gradle.org`

Dlatego nie deklarujemy tu pełnego `assembleStmobileBenchmark`, `connectedStmobileBenchmarkAndroidTest` ani `assembleStmobileRelease` jako wykonanych. Należy je wykonać po stronie użytkownika z Android SDK i działającym Gradle 7.5.

### 10. Patch round-trip

Finalny patch `STAGE_12_PERFORMANCE_2026-08-09.patch` został przygotowany względem czystego Stage 11, nałożony na kopię Stage 11 i wynik porównano plik po pliku z drzewem Stage 12 (z pominięciem metadanych `.git`). Wynik: identyczność 1:1.

### 11. ZIP integrity

Finalny ZIP został sprawdzony przez `unzip -t` bez błędów i nie zawiera katalogu `.git`.

## Testy wymagane po stronie użytkownika

```bash
./gradlew :smarttubetv:assembleStmobileBenchmark
./gradlew :mobilebenchmark:connectedStmobileBenchmarkAndroidTest
python3 tools/stage12-install-baseline-profile.py
./gradlew :smarttubetv:assembleStmobileRelease
```

Opcjonalny osobny trial R8:

```bash
./gradlew :smarttubetv:assembleStmobileRelease -Pstage12EnableR8=true
```

Po wygenerowaniu APK należy przejść checklistę runtime z `STAGE_12_PERFORMANCE_2026-08-09.md`, szczególnie Home/Shorts/Search/Channel, SponsorBlock/DeArrow, Radio DVR, Offline, AA i Media3 fallback.
