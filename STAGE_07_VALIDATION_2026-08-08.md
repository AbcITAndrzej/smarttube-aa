# Stage 07 — raport walidacji statycznej

Data: 2026-08-08
Baza: `smarttube-aa-stage-08-offline-playlists-2026-08-08`
Wynik: Stage 07 scalony na najnowszej bazie, bez cofania Stage 08.

## Wykonane kontrole

- wszystkie XML-e w `smarttubetv/src/stmobile`: **75/75 poprawnie parsuje się jako XML**,
- brak duplikatów zasobów w `values/mobile_native_strings.xml` i `values-pl/mobile_native_strings.xml`,
- różnica EN/PL ograniczona do historycznego `aa_app_name` obecnego tylko w EN,
- skan odwołań aplikacyjnych `R.string/R.id/R.drawable/R.layout/...`: **0 brakujących zasobów w zmienionych plikach**,
- parserowa kontrola `javac` zmienionych plików: brak błędów typu `';' expected`, `illegal start`, `unclosed string`, `reached end of file`, itp.; pozostałe komunikaty wynikają z braku pełnego Android classpath w środowisku,
- osobna kompilacja czystych klas Stage 07 i nowych testów na minimalnych stubach JUnit: **OK**,
- ręczne uruchomienie nowych testów `OfflineDownloadCoordinatorTest` + `OfflineListenSaveEntryTest`: **STAGE7_PURE_TESTS_OK**,
- kontrola zmian sieciowych: brak nowego zahardkodowanego endpointu produkcyjnego; jedyny dodany `https://...` poza schematami XML występuje w sztucznym URL miniatury w teście,
- foreground service Stage 07 ma `android:foregroundServiceType="dataSync"`, a manifest zawiera `FOREGROUND_SERVICE_DATA_SYNC`,
- Stage 07 jest blokowany dla `headlessPlaybackAllowed`, więc stabilny Android Auto nie uruchamia pasywnego zapisu,
- Radio/live/Shorts/offline playback są jawnie wykluczone w `OfflineListenSaveController`,
- podpisane media URL-e nie mają kolumny w `smarttube_mobile_offline_listen.db` i są rozwiązywane just-in-time,
- przy `stopService()` aktywny wpis jest ponownie ustawiany na `PENDING`, aby nie utknął w `DOWNLOADING` w tym samym procesie,
- żądanie jest zapisywane jako `PENDING` przed próbą uruchomienia foreground service; chwilowa odmowa FGS przez nowszy Android nie gubi kolejki,
- zmiana limitu ostatnich zapisów wykonuje prune poza wątkiem UI,
- Stage 07 i Stage 08 używają wspólnego `OfflineDownloadCoordinator`, więc nie zapisują równolegle do wspólnego store,
- usuwanie playlisty respektuje referencję Stage 07, a usuwanie Stage 07 respektuje referencje playlist.

## Gradle / pełny build

Próba:

`bash gradlew :smarttubetv:testStmobileDebugUnitTest --offline`

nie mogła wystartować, ponieważ wrapper nie ma lokalnie Gradle 7.5 i próbuje pobrać:

`https://services.gradle.org/distributions/gradle-7.5-bin.zip`

Środowisko zwraca `UnknownHostException: services.gradle.org`. Pełny build Android/AGP musi zostać wykonany po stronie użytkownika.

## Świadome ograniczenie architektoniczne

Stage 07 jest osobnym audio-only downloaderem uruchamianym po osiągnięciu progu słuchania, a nie prawdziwym tee/cache-through bajtów odtwarzanych przez ExoPlayer. Może więc zużyć dodatkowy transfer audio. Zero-copy cache-through zostawiono na etap migracji/podmiany DataSource/Media3, aby nie destabilizować obecnego playbacku.
