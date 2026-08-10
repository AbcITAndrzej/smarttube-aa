# Stage 11 — raport walidacji statycznej

Data: 2026-08-08
Baza patcha: `smarttube-aa-stage-10-trip-reserve-2026-08-08.zip`

## Wyniki

- `git diff --check`: **OK**.
- XML `stmobile`: **74/74 parsuje się poprawnie**.
- nowe ID i EN/PL stringi Stage 11: **OK**.
- brak duplikatów stringów w głównych plikach EN/PL: **OK**.
- odwołania do nowych zasobów z `DiagnosticsFragment`: **OK**.
- `Media3MigrationPolicy`: czysty test `javac/java`: **STAGE11_POLICY_SMOKE_OK**.
- `Media3MigrationPolicyTest.java`: kompilacja z minimalnymi stubami JUnit: **STAGE11_POLICY_TEST_COMPILE_OK**.
- `MobilePlaybackEngine` + `Media3MigrationPolicy` + `Media3PlaybackEngine`: kompilacja `javac` z minimalnymi stubami API Android/Media3 (w tym lokalny suppress lint dla unstable API): **STAGE11_MEDIA3_ENGINE_STUB_COMPILE_OK**.
- parser fazy `javac` dla kluczowych zmodyfikowanych klas (z pustym sourcepath; missing dependencies oczekiwane): brak parser-style syntax errors: **OK**.
- stare markery regresyjne sprawdzone: Shorts continuation fallback, `touchSlop`, `getTrendingObserve`, transient 403 recovery, Radio paging i SponsorBlock seekbar nadal istnieją: **OK**.
- nowe stałe URL/backendy Stage 11: **brak**.
- Media3 dependency jest ograniczona do flavor `stmobile`: **OK**.
- VOD/Shorts polityka Media3 zwraca zawsze legacy w Stage 11: **OK**.
- master/radio/offline/fallback mają niezależne FeatureFlags i domyślnie ON: **OK**.
- stabilny `SmartTubeAutoMusicService` nie został przepisany na nową sesję: **OK**.
- `MobileMediaSessionManager` pozostaje `MediaSessionCompat`: **OK**.
- kolejność migracji VOD -> direct-source: legacy engine jest zwalniany przed nadpisaniem metadata Radio/Offline: **OK**.
- brak importów/dependency `androidx.media3.session`, `MediaSessionService` i `MediaLibraryService`: **OK**.
- patch Stage 11 nałożony na czysty ZIP Stage 10 tworzy drzewo plików identyczne 1:1 z katalogiem roboczym Stage 11 (z wyłączeniem `.git`): **STAGE11_PATCH_ROUNDTRIP_1TO1_OK**.

## Świadome ograniczenia walidacji

Próba `bash gradlew -Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64 :smarttubetv:testStmobileDebugUnitTest --offline` zatrzymała się przed konfiguracją projektu: wrapper nie ma lokalnej dystrybucji Gradle 7.5 i mimo `--offline` próbuje utworzyć ją z `services.gradle.org`, co kończy się `UnknownHostException`. Pełny build wymaga też zależności Android/Google Maven. Dlatego ostateczna weryfikacja ABI/API biblioteki Media3 1.4.1 i runtime odbywa się przy kompilacji użytkownika.

Nie udajemy, że stub compile zastępuje Android Gradle build — ma jedynie wychwycić składnię i zgodność zaprojektowanego kontraktu z użytym API.

## Krytyczne testy runtime po stronie użytkownika

1. Radio mobile z aktywnym Media3 i widocznym `activeEngine=Media3 ExoPlayer`.
2. Radio DVR: cofanie, LIVE, zmiana stacji.
3. Radio problematyczne: automatyczny legacy fallback bez crasha.
4. Offline mobile: play/pause/seek/next/ENDED bez internetu.
5. Android Auto Radio + Offline, bez drugiej sesji i bez konfliktu audio focus.
6. Android Auto YouTube oraz zwykły mobile VOD nadal legacy.
7. Wyłączenie każdego z czterech FeatureFlags i ponowne przygotowanie źródła.
8. Regression: Shorts, seekbar, zoom/tap, Trending, transient 403, SponsorBlock/DeArrow.

## Oczekiwane logi

Tag logiczny: `P21-Media3`.

Typowe wpisy:

- `open radio using Media3 ...`
- `open offline using Media3 ...`
- `direct engine error source=...`
- `fallback to legacy source=...`
- `released: ...`

Raport Diagnostyki powinien pokazać liczniki aktywacji/błędów/fallbacków.
