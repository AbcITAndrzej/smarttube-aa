# Stage 12 — benchmarki, Baseline Profile i końcowa optymalizacja

Data: 2026-08-09

Baza: `smarttube-aa-stage-11-media3-2026-08-08`

Stage 12 kończy roadmapę 01–12. Zmiany są celowo mierzalne i odwracalne: nowe pomiary są lokalne, R8 pozostaje opt-in, a stare ścieżki playbacku nie są przebudowywane tylko po to, żeby „optymalizacja” wyglądała większa.

## 1. Macrobenchmark

Dodany został osobny moduł `:mobilebenchmark` (`com.android.test`), mierzący aplikację `app.smarttube.mobile` z zewnątrz, tak jak robi to użytkownik.

Scenariusze:

- `StartupBenchmark.coldStartup()` — cold start, `StartupTimingMetric`, 5 iteracji,
- `StartupBenchmark.warmStartup()` — warm start, `StartupTimingMetric`, 5 iteracji,
- `HomeScrollBenchmark.homeScroll()` — rzeczywiste przewijanie RecyclerView Home i `FrameTimingMetric`,
- `BaselineProfileGenerator.generateMobileCriticalJourneys()` — Home -> scroll -> Search -> Settings.

Aplikacja ma osobny build type `benchmark`, odziedziczony po `release`, a `profileable android:shell=true` jest dodane tylko przez `src/benchmark/AndroidManifest.xml`. Produkcyjny manifest nie jest przez to rozszerzany.

## 2. Baseline Profile przy obecnym AGP 7.4.2

Projekt pozostaje na AGP 7.4.2, dlatego Stage 12 nie udaje, że posiada nowoczesny Baseline Profile Gradle Plugin. Dla AGP 7.3–7.4 Android wspiera generowanie profilu przez test benchmarkowy i ręczne skopiowanie wygenerowanego HRF do `src/main/baseline-prof.txt`.

Dodano:

- `androidx.profileinstaller:profileinstaller:1.3.1` tylko dla flavoru `stmobile`,
- startowy `smarttubetv/src/main/baseline-prof.txt`,
- generator `BaselineProfileRule`,
- `tools/stage12-install-baseline-profile.py`, który znajduje najnowszy wygenerowany `baseline-prof.txt` i kopiuje go do aplikacji.

Seed profilu obejmuje najbardziej krytyczne klasy mobilne: Activity/Navigation, Home/Search, adapter, playback repository/engine, Media3 engine, diagnostykę i performance monitor. **Seed nie zastępuje profilu wygenerowanego na urządzeniu.** Po uruchomieniu generatora należy go zastąpić wynikiem rzeczywistej sesji.

Oficjalna dokumentacja: https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile

## 3. Startup Profile — świadomie odłożony do upgrade toolchainu

Nie dodano sztucznego `startup-prof.txt`, ponieważ obecny AGP 7.4.2 nie zapewnia nowoczesnej ścieżki DEX-layout Startup Profiles. Oficjalne narzędzia rekomendują AGP 8.2+ dla Startup Profiles.

Dlatego obecny Stage 12 dostarcza:

- benchmark startupu,
- Baseline Profile,
- `reportFullyDrawn`,
- lokalne timingi,

ale **nie deklaruje wdrożonego Startup Profile DEX-layout**. To powinno wejść razem z kontrolowanym upgrade AGP/Gradle w przyszłości.

Oficjalna dokumentacja: https://developer.android.com/topic/performance/startupprofiles/dex-layout-optimizations

## 4. Lokalny Performance Monitor

Dodano `MobilePerformanceMonitor`. Nie wysyła danych, nie zapisuje telemetryki na serwer i nie tworzy nowego endpointu.

Zbiera w pamięci procesu:

- proces -> `MobileNativeActivity.onCreate`,
- proces -> pierwszy zaobserwowany frame,
- proces -> pierwsza wypełniona strona Home,
- `reportFullyDrawn()` przy pierwszym gotowym Home,
- ostatni i najgorszy czas renderowania browse,
- ostatni i najgorszy czas renderowania snapshotu playera,
- liczbę próbek frame gap >24 ms / >50 ms / >100 ms,
- najgorszy frame gap,
- Java heap,
- native heap,
- wolną pamięć urządzenia i low-memory state,
- ostatni `onTrimMemory` level.

Frame-gap sampler jest lekki i można go osobno wyłączyć.

## 5. Trace sections

Dwie krytyczne ścieżki dostały sekcje Android Trace:

- `ST:BrowseRender`
- `ST:PlaybackRender`

Dzięki temu przy późniejszym Perfetto/system trace można skorelować widoczne przycięcie z konkretną pracą aplikacji, zamiast zgadywać po samych logach.

## 6. Diagnostyka Stage 12

W `Ustawienia -> Diagnostyka` dodano:

- `Etap 12 • Wydajność`,
- `Lokalny monitor wydajności`, domyślnie ON,
- `Próbkowanie płynności klatek`, domyślnie ON.

Raport diagnostyczny pokazuje wszystkie lokalne timingi, frame gaps i pamięć. Reset diagnostyki resetuje również sesyjne liczniki wydajności.

## 7. Optymalizacja listy Home / continuation

`MobileMediaAdapter.submitSections()` nie wykonuje już zawsze pełnego `rows.clear() + notifyDataSetChanged()`.

Nowe zachowanie:

1. pierwszy feed -> `notifyItemRangeInserted`,
2. typowe continuation będące czystym appendem -> szybkie `notifyItemRangeInserted` tylko dla nowych wierszy,
3. inne zmiany -> `DiffUtil`, z porównaniem stabilnego klucza i rzeczywistej zawartości.

To zmniejsza niepotrzebne rebindowanie widocznych kart i jest szczególnie ważne dla Stage 03, gdzie Home/Search/Channel doklejają kolejne strony.

## 8. SponsorBlock — mniej duplikujących requestów

`SponsorBlockService` dostał procesowy cache:

- pozytywny TTL: 15 min,
- negatywny TTL: 2 min,
- LRU max 192 wpisy,
- klucz zawiera serwer main/alt, `videoId` i posortowane kategorie,
- `single-flight`: równoległe requesty tego samego klucza współdzielą jeden request HTTP,
- błędy sieciowe nadal są propagowane i nie są utrwalane jako sukces.

Diagnostyka pokazuje `entries`, `inFlight`, `hits`, `misses`, `joins`.

Nie zmieniono endpointu SponsorBlock.

## 9. DeArrow — single-flight na istniejącym cache

DeArrow miał już cache z poprzednich etapów. Stage 12 zachowuje:

- pozytywny TTL 6 h,
- negatywny TTL 2 min,
- LRU max 512,

oraz dodaje `single-flight` dla tego samego `videoId`. Jeżeli legacy processor i nowy native mobile processor poproszą prawie równocześnie o ten sam branding, tylko pierwszy wykonuje sieć, a pozostali czekają na jego wynik.

Błędy nie są cache'owane. Diagnostyka pokazuje hit/miss/in-flight/join.

## 10. R8 — testowalny, ale nie wymuszony

Projekt ma dużo starego kodu, refleksji, własnych modułów i wieloletnich obejść kompatybilności. Dlatego Stage 12 **nie włącza R8/minify w release po cichu**.

Domyślne zachowanie Stage 11 pozostaje bez zmian.

Do kontrolowanego testu dodano:

```bash
./gradlew :smarttubetv:assembleStmobileRelease -Pstage12EnableR8=true
```

Dopiero po testach wszystkich krytycznych ścieżek można rozważyć zmianę tego defaultu.

## 11. Audyt sieci/TLS

Powstał osobny `STAGE_12_NETWORK_TLS_AUDIT_2026-08-09.md`.

Najważniejszy wniosek: wspólna warstwa `SharedModules/sharedutils` ma historyczne obejścia TLS dla bardzo starych Androidów, w tym trust manager niewalidujący łańcucha certyfikatów na API <=24. Stage 12 tego nie zmienia, bo ten kod jest współdzielony przez wiele flavorów i wymaga testów urządzeń Android 5/6/7 oraz usług, których nie można tutaj wykonać.

Jest to jawnie oznaczone jako kolejny temat bezpieczeństwa/kompatybilności, a nie ukryte pod hasłem „optymalizacji”.

## 12. Brak nowych serwerów

Stage 12 nie dodaje żadnego nowego zewnętrznego endpointu ani telemetryki. Benchmarki i Performance Monitor są lokalne. SponsorBlock i DeArrow używają tych samych hostów co wcześniej, tylko ograniczają powtarzające się zapytania.

## 13. Jak uruchomić benchmarki po stronie użytkownika

Na komputerze z działającym Android SDK, Gradle 7.5 i urządzeniem API 23+:

```bash
./gradlew :smarttubetv:assembleStmobileBenchmark
./gradlew :mobilebenchmark:connectedStmobileBenchmarkAndroidTest
```

Następnie skopiuj rzeczywisty profil do aplikacji:

```bash
python3 tools/stage12-install-baseline-profile.py
```

Potem zbuduj normalny release/debug według własnego workflow i ponownie wykonaj pomiary.

Dla realnych liczb wydajności używaj fizycznego urządzenia, najlepiej tego samego modelu przy porównywaniu dwóch wersji.

## 14. Minimalna checklista runtime Stage 12

1. Cold start 5× — aplikacja startuje bez crasha, Home się wypełnia.
2. Warm start 5×.
3. Scroll Home przynajmniej 30–60 s.
4. Shorts continuation nadal ładuje kolejne pozycje.
5. Search/Channel continuation nadal dokleja dane.
6. Otwórz Diagnostykę — pola Stage 12 mają wartości.
7. Wyłącz frame sampling — po chwili licznik nie powinien dalej rosnąć.
8. Włącz ponownie — sampler ma wznowić pracę bez restartu Activity.
9. Otwórz kilka razy ten sam film/listę z SponsorBlock/DeArrow — `hits/joins` powinny rosnąć.
10. Radio + DVR + Offline + AA po Stage 11 — regresji brak.
11. Wygeneruj Baseline Profile na urządzeniu i zastąp seed.
12. Opcjonalnie zbuduj R8 trial i przetestuj pełny smoke-test przed ewentualnym włączeniem minify w przyszłości.

## 15. Zachowane wcześniejsze funkcje

Stage 12 jest kumulatywny. Nie usuwa:

- sześciu pierwszych poprawek (Shorts, seekbar, zoom/tap, globalne Radio, Trending, 403),
- ustawień Playera, preferencji audio/napisów i nowego UI track picker,
- SponsorBlock/DeArrow mobile,
- Diagnostyki/FeatureFlags,
- Instant Play,
- paginacji,
- Smart Player UX,
- Radio 2.0/DVR,
- całego Offline 06–10,
- Android Auto Offline,
- Trip Reserve,
- pierwszej odwracalnej fali Media3.
