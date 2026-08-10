# Stage 11 — stopniowa, odwracalna migracja playbacku do AndroidX Media3

Data: 2026-08-08
Baza: `smarttube-aa-stage-10-trip-reserve-2026-08-08.zip`

## Cel

Ten etap **nie robi big-bang migracji całego SmartTube**. Projekt ma mocno zmodyfikowany, dojrzały playback YouTube oparty o lokalny fork ExoPlayer 2 i `PlaybackPresenter`/`ExoPlayerController`. Jednoczesna wymiana VOD, Shorts, Radia, Offline, sesji mobilnej i Android Auto byłaby zbyt ryzykowna.

Stage 11 wprowadza więc wspólną granicę silnika i pierwszy produkcyjny fragment Media3:

- **Radio** może używać Media3,
- **lokalne audio Offline** może używać Media3,
- **YouTube VOD i Shorts pozostają na dotychczasowym ExoPlayer 2**,
- stabilna **MediaSessionCompat Android Auto pozostaje bez przebudowy**,
- eksperymentalny moduł AA Video pozostaje odizolowany i bez zmian,
- każdy direct-source uruchomiony przez Media3 może automatycznie wrócić do legacy ExoPlayer 2 po błędzie.

To daje realne użycie Media3, ale jednocześnie zachowuje bardzo prosty rollback. Przy przejściu z legacy VOD do Radio/Offline stary engine jest zwalniany **przed** podmianą metadata na direct-source, aby singleton `PlaybackPresenter` nie zobaczył nowego elementu podczas callbacku `onEngineReleased()`.

## Dlaczego Media3

Google traktuje AndroidX Media3 jako aktualny dom ExoPlayera i zaleca migrację z samodzielnego `com.google.android.exoplayer2`. Media3 daje wspólny kontrakt `Player` oraz docelowo prostsze połączenie z `MediaSession`/`MediaController`.

Dokumentacja:

- https://developer.android.com/media/media3/exoplayer/migration-guide
- https://developer.android.com/media/media3
- https://developer.android.com/jetpack/androidx/releases/media3

## Wersja biblioteki

Dla `stmobile` przypięto:

```gradle
stmobileImplementation 'androidx.media3:media3-exoplayer:1.4.1'
stmobileImplementation 'androidx.media3:media3-exoplayer-hls:1.4.1'
```

To jest **świadomy konserwatywny pin**, a nie deklaracja, że 1.4.1 jest najnowsza. Obecna linia Media3 jest znacznie nowsza, ale projekt nadal używa `compileSdk 34`, AGP 7.4.2 i `stmobile minSdk 21`. Media3 1.5.0 wprowadziło wymagania związane z `compileSdk = 35`, a Media3 1.9.0 podniosło `minSdk` do 23. Najpierw oddzielamy silnik i stabilizujemy zachowanie, a aktualizację toolchainu/Media3 wykonamy jako osobną, mierzalną zmianę.

## Architektura

### `MobilePlaybackEngine`

Nowy mały kontrakt neutralny względem konkretnego playera. Zawiera tylko transport i stan:

- open direct URI,
- play/pause,
- seek,
- position/duration/buffered,
- loading/READY/ENDED,
- speed/pitch/volume,
- release.

Celowo **nie przenosi** jeszcze specyficznych dla YouTube funkcji jakości, napisów, SponsorBlock, selekcji formatów ani `PlaybackPresenter`.

### `LegacyEngineBridge`

Adapter wewnątrz `LegacyMobilePlaybackRepository`, który opakowuje istniejący:

- `SimpleExoPlayer`,
- `ExoPlayerController`.

Dzięki temu wspólny kod transportu może operować na `MobilePlaybackEngine`, niezależnie czy aktywny jest legacy czy Media3.

### `Media3PlaybackEngine`

Pierwszy faktyczny silnik AndroidX Media3:

- `androidx.media3.exoplayer.ExoPlayer`,
- `MediaItem` dla `file://`, HTTP/HTTPS i HLS,
- audio attributes `USAGE_MEDIA` / music,
- Media3 zarządza audio focus,
- automatyczne `audio becoming noisy`,
- speed/pitch/volume,
- listener READY/BUFFERING/ENDED/error.

Klasa ma lokalne `@SuppressLint("UnsafeOptInUsageError")`, ponieważ `ExoPlayer` jest częścią powierzchni Media3 oznaczonej jako `@UnstableApi`; ograniczamy suppress tylko do adaptera Media3 zamiast wyłączać kontrolę w całym projekcie.

Stage 11 używa go jako **audio-only direct-source engine**. Nie podpinamy go do starego `com.google.android.exoplayer2.ui.PlayerView`, ponieważ nie migrujemy jeszcze mobilnego VOD/video UI.

### `Media3MigrationPolicy`

Czysta, testowalna polityka rollout:

```text
VOD       -> legacy
RADIO     -> Media3, gdy master + radio flag
OFFLINE   -> Media3, gdy master + offline flag
```

## Macierz migracji Stage 11

| Obszar | Silnik po Stage 11 | Uwagi |
|---|---|---|
| Mobilny YouTube VOD | Legacy ExoPlayer 2 | bez zmiany |
| Shorts | Legacy ExoPlayer 2 | bez zmiany; zachowany wcześniejszy continuation fix |
| Mobilne Radio | Media3 domyślnie | legacy fallback po błędzie |
| Mobilne Offline | Media3 domyślnie | legacy fallback z zachowaniem pozycji |
| Android Auto — YouTube/audio | Legacy ExoPlayer 2 | stabilna ścieżka bez migracji |
| Android Auto — Radio | Media3 domyślnie | ta sama headless repository, stabilna MediaSessionCompat |
| Android Auto — Offline | Media3 domyślnie | lokalny plik, stabilna MediaSessionCompat |
| Radio DVR/time-shift | działa ponad aktywnym direct engine | Media3 lub fallback legacy |
| eksperymentalne AA Video | bez zmian | pełna izolacja |

## Fail-safe / rollback

To jest najważniejsza część Stage 11.

Jeżeli Media3 zgłosi błąd podczas Radio lub Offline:

1. zapisywany jest błąd w Diagnostyce,
2. przechwytywana jest aktualna pozycja,
3. Media3 jest zwalniane,
4. inicjalizowany jest dotychczasowy ExoPlayer 2,
5. **ten sam direct URI** jest ponawiany w legacy,
6. dla Offline odtwarzanie próbuje zachować pozycję,
7. dla Radia start jest ponawiany od bieżącego live/direct source,
8. dopiero jeżeli legacy również nie odtworzy źródła, uruchamiają się istniejące mechanizmy Radio DVR/failover/error.

Domyślnie fallback jest ON.

Dodatkowo każdy fragment migracji można wyłączyć bez usuwania kodu:

- `MEDIA3_ENGINE` — master,
- `MEDIA3_RADIO`,
- `MEDIA3_OFFLINE`,
- `MEDIA3_LEGACY_FALLBACK`.

Przełączniki są w `Ustawienia -> Diagnostyka -> Etap 11 • migracja Media3` i domyślnie są **ON**. Zmiana obowiązuje przy następnym przygotowaniu źródła; nie wymuszamy ryzykownej wymiany playera w środku aktualnie odtwarzanego materiału.

## Android Auto — czego świadomie NIE zmieniono

`SmartTubeAutoMusicService` nadal jest stabilnym `MediaBrowserServiceCompat`/`MediaSessionCompat`. Stage 11 zmienia tylko to, jaki silnik dostarcza audio dla bezpośredniego Radia/Offline we wspólnym headless `LegacyMobilePlaybackRepository`.

Nie tworzymy:

- drugiej publicznej MediaSession,
- równoległego Media3 `MediaLibraryService`,
- nowego Android Auto browse tree,
- nowego komponentu AA.

To chroni Etapy 5 i 9 oraz istniejącą integrację samochodową przed regresją. Migracja sesji będzie oddzielnym podetapem po potwierdzeniu stabilności silnika.

## Mobile MediaSession

`MobileMediaSessionManager` celowo nadal używa `MediaSessionCompat`. Aktywny direct engine (legacy lub Media3) jest właścicielem audio focus, a kompatybilna sesja telefonu wystawia metadata/transport/notification bez drugiego konkurencyjnego focus request.

## Radio 2.0 / DVR

Radio z Etapu 5 nadal działa tak samo na poziomie funkcjonalnym:

- time-shift/DVR,
- powrót LIVE,
- seek w buforze,
- failover alternatywnego streamu,
- ostatnie/ulubione/kraje/gatunki,
- Android Auto browse.

Jedyna zmiana to transport direct audio. `replaceDirectSource()` kieruje nowy URL do aktualnie wybranego silnika. Gdy DVR padnie, direct stream nadal jest fail-open. Gdy Media3 padnie, najpierw jest legacy fallback.

## Offline / Etapy 6–10

Nie zmieniono formatu plików ani baz danych Offline. Media3 otwiera istniejące prywatne `.audio` przez lokalny URI.

Zachowane są:

- OfflineMediaRepository,
- Listen & Save,
- Playlisty Offline,
- Offline w AA,
- Smart Trip Reserve,
- storage limits i LRU,
- signed-URL policy downloaderów.

Stage 11 **nie pobiera nic nowego** i nie tworzy kolejnego cache/downloadera.

## Sieć

Stage 11 nie dodaje nowego backendu ani endpointu aplikacji.

Media3 dla Radia wykonuje request bezpośrednio do tego samego URL stacji (lub lokalnego `127.0.0.1` Radio DVR proxy), który wcześniej dostawał legacy ExoPlayer. Offline używa lokalnego `file://`.

YouTube VOD nie jest kierowane przez Media3 w tym etapie, więc nie zmienia się krytyczna obsługa podpisanych URL-i/403/formatów YouTube.

## Diagnostyka

Raport zawiera teraz:

- `engine` dla aktywnego playbacku,
- `Media3Migration: master/radio/offline/legacyFallback`,
- `activeEngine`,
- `source`,
- ostatni event Media3 (`READY`, `ERROR`, `FALLBACK`),
- `media3_activations`,
- `media3_errors`,
- `media3_legacy_fallbacks`.

Log tag: `P21-Media3`.

To pozwala po testach na urządzeniu stwierdzić, czy Radio/Offline rzeczywiście weszło na Media3, czy odtworzyło się dopiero po rollbacku.

## Kluczowo zmienione/dodane pliki

- `smarttubetv/build.gradle`
- `playbackengine/MobilePlaybackEngine.java`
- `playbackengine/Media3PlaybackEngine.java`
- `playbackengine/Media3MigrationPolicy.java`
- `LegacyMobilePlaybackRepository.java`
- `MobileMediaSessionManager.java`
- `MobileFeatureFlags.java`
- `MobileDiagnosticsStore.java`
- `DiagnosticsFragment.java`
- `mobile_diagnostics_fragment.xml`
- `values/mobile_native_strings.xml`
- `values-pl/mobile_native_strings.xml`
- `Media3MigrationPolicyTest.java`

## Zachowane poprzednie poprawki

Stage 11 jest kumulatywny. Nadal zawiera między innymi:

- Shorts continuation/reload fallback,
- poprawiony layout seekbara/czasu po zmianie jakości,
- zoom + tap/touchSlop,
- globalne Radio i pełny katalog,
- oddzielne Trending/rekomendacje,
- recovery transient 403 + Instant Play,
- Player Settings i track picker,
- SponsorBlock/DeArrow native mobile,
- Diagnostykę,
- wspólną paginację,
- Smart Player UX,
- Radio 2.0/DVR,
- cały stack Offline z Etapów 6–10,
- stabilne Android Auto i eksperymentalne AA Video jako osobne ścieżki.

## Checklista testów po kompilacji

### Rollout / rollback

1. Otwórz Diagnostykę i potwierdź, że 4 przełączniki Stage 11 są ON.
2. Wyłącz master i uruchom Radio — raport ma pokazać legacy ExoPlayer 2.
3. Włącz master + Radio — nowa stacja powinna pokazać `Media3 ExoPlayer`.
4. Wyłącz tylko `Media3 dla Radia` — Offline może nadal używać Media3, Radio legacy.
5. Wyłącz tylko `Media3 dla Offline` — Radio może nadal używać Media3, Offline legacy.
6. Ustawienia zmieniaj między utworami/stacjami; aktualny stream nie musi zostać natychmiast przełączony.

### Radio

7. Uruchom zwykłą stację i testuj play/pause.
8. Cofnij Radio DVR, przewiń kilka razy i wróć LIVE.
9. Zmień stację podczas działania Media3.
10. Zostaw Radio w tle/AA na dłużej.
11. Przetestuj kilka kodeków/stacji, w tym problematyczny stream. Jeśli Media3 go odrzuci, przy włączonym fallbacku playback powinien spróbować legacy bez crasha.
12. W Diagnostyce sprawdź licznik `media3_legacy_fallbacks`.
13. Wymuś błąd/wyłącz stream i sprawdź, czy istniejący Radio 2.0 failover nadal może znaleźć alternatywę po rollbacku.

### Offline

14. Odtwórz lokalny element z „Ostatnio zapisane”.
15. Odtwórz lokalną Playlistę Offline.
16. Seek do środka lokalnego utworu.
17. Pause/resume i następny/poprzedni w kolejce Offline.
18. Odłącz internet — lokalny plik musi działać identycznie.
19. Sprawdź przejście do następnego utworu po `ENDED`.

### Android Auto

20. AA YouTube/Music nadal działa przez dotychczasową ścieżkę.
21. Radio w AA uruchamia Media3 tylko dla direct audio i nie tworzy drugiej sesji.
22. Offline w AA odtwarza lokalny plik przez Media3 i zachowuje browse tree Stage 9.
23. Utrata internetu + Stage 9 fallback na lokalną kopię nadal działa.
24. Sterowanie kierownicą/head-unit: play/pause/seek/next/previous.
25. Nie może pojawić się podwójny dźwięk ani konflikt dwóch MediaSession.

### Regresja VOD

26. Zwykły film: jakość, audio, napisy, zoom, seekbar, SponsorBlock.
27. Shorts: dalsze przewijanie continuation.
28. Film z transient 403: zachowanie Instant Play/recovery bez nowej regresji.
29. Trending/Nowe rekomendacje nadal różnią się od Home.
30. Eksperymentalne AA Video działa/pozostaje wyłączone zgodnie z własnym przełącznikiem — Stage 11 go nie dotyka.

## Co pozostaje do kolejnej fali Media3

Po runtime testach Stage 11 można bezpiecznie robić dalsze podetapy:

1. aktualizacja toolchainu i Media3 do nowszej linii,
2. migracja mobilnej `MediaSessionCompat` -> Media3 `MediaSession`,
3. migracja stabilnego Android Auto browse/session do Media3 `MediaLibraryService` dopiero po testach kompatybilności,
4. osobny adapter VOD/Shorts z zachowaniem format selector/SponsorBlock/Instant Play,
5. na samym końcu wymiana video UI na Media3 Player/View/Compose, jeśli nadal będzie potrzebna.

Nie warto łączyć tych zmian w jeden commit — aktualny rollback Stage 11 jest celowo prosty.
