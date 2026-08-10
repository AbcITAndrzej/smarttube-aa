# SmartTube-AA — Etap 07: „Słucham i zapisuję”

Data: 2026-08-08

## Baza i kolejność

Etap 07 został wykonany **po Etapie 08**, ponieważ Etap 08 został wcześniej zrealizowany na prośbę użytkownika z pominięciem Etapu 07. Żeby niczego nie cofnąć, ta wersja została zbudowana na pełnej paczce:

`smarttube-aa-stage-08-offline-playlists-2026-08-08`

Dlatego patch Etapu 07 należy nakładać na Stage 08, a pełny ZIP Etapu 07 zawiera jednocześnie wszystkie funkcje Stage 08 oraz wszystko z wcześniejszych etapów.

## Cel

Opcjonalne automatyczne przygotowywanie lokalnej kopii **tylko audio** materiału, którego użytkownik faktycznie słucha w nowym mobilnym playerze. Po zapisaniu materiał może być odtworzony lokalnie bez połączenia z Internetem.

## Ustawienia użytkownika

`Ustawienia -> Offline -> Słucham i zapisuję`

- `Automatycznie zapisuj słuchane audio` — **domyślnie OFF**; świadomy opt-in, ponieważ funkcja zużywa transfer i pamięć.
- `Tylko Wi‑Fi / Ethernet` — domyślnie ON.
- `Dokończ rozpoczęty zapis po przełączeniu na inny utwór` — domyślnie ON.
- próg faktycznego słuchania: 5 / 15 / 30 / 60 s, domyślnie 15 s.
- limit ostatnich automatycznych zapisów: 20 / 50 / 100, domyślnie 50.
- `Zarządzaj ostatnio zapisanymi` — lista kolejki, postępu i lokalnych plików.

FeatureFlag `OFFLINE_LISTEN_SAVE` jest domyślnie ON jako techniczna bramka rollout/rollback, ale funkcja nie działa dopóki użytkownik nie włączy jej w ustawieniach Offline.

## Jak działa przepływ

1. `LegacyMobilePlaybackRepository` przekazuje snapshot odtwarzania do `OfflineListenSaveController`.
2. Kontroler zlicza **rzeczywisty czas PLAYING**, nie czas ścienny. Pauza nie nabija progu, a duże przerwy procesowe są odcinane.
3. Po osiągnięciu progu tworzony jest `OfflineMediaDescriptor` z mediaId, tytułem, autorem, miniaturą i czasem trwania.
4. Żądanie jest najpierw trwale zapisane w `smarttube_mobile_offline_listen.db` jako `PENDING`. Dzięki temu chwilowa odmowa uruchomienia foreground service przez Androida nie gubi zadania.
5. `OfflineListenSaveService` pobiera świeży `MediaItemFormatInfo` dopiero tuż przed transferem.
6. `OfflineAudioFormatSelector` wybiera skończony, bezpośredni format audio-only z uwzględnieniem preferowanego języka audio.
7. Dane są zapisywane do wspólnego magazynu Stage 06 jako `.part`, z możliwością wznowienia HTTP Range.
8. Po poprawnym zakończeniu następuje atomowe przejście `.part -> .audio` i stan `AVAILABLE`.
9. Podpisany URL streamu **nigdy nie jest zapisywany w SQLite**. Po 403/410 pobierany jest świeży format i URL, maksymalnie trzy próby.
10. Element pojawia się w `Ostatnio zapisane automatycznie` i może być odtworzony przez istniejący lokalny identyfikator `offline:<mediaId>`.

## Co jest celowo wykluczone

Automatyczny zapis nie uruchamia się dla:

- Radia,
- transmisji live i upcoming,
- Shorts,
- materiałów oznaczonych jako unplayable,
- już lokalnego playbacku offline,
- headless playbacku używanego przez stabilne Android Auto.

Android Auto Offline pozostaje osobnym Etapem 09.

## Współpraca z Etapem 08

Stage 07 i Stage 08 korzystają z tego samego prywatnego magazynu audio Stage 06, ale mają osobne kolejki/metadane.

Dodany `OfflineDownloadCoordinator` zapewnia **jeden background transfer offline w procesie**. Jawne pobieranie playlist i pasywny zapis nie zapisują jednocześnie do tego samego `.part` i nie konkurują bez kontroli o transfer/dysk.

Własność pliku jest referencyjna:

- usunięcie playlisty nie kasuje audio, jeśli nadal należy do `Słucham i zapisuję`,
- usunięcie automatycznego zapisu nie kasuje audio, jeśli wykorzystuje je playlista offline,
- limit 20/50/100 usuwa najstarszą własność Stage 07, ale zachowuje pliki nadal używane przez playlisty.

## Zarządzanie pamięcią

Stage 07 używa limitów i rezerwy miejsca z Etapu 06. Automatyczne zapisy nie są „przypięte” tak mocno jak jawnie pobrane playlisty, dzięki czemu ogólny LRU może je usuwać w razie presji na pamięć. Lista Stage 07 wykrywa brak pliku i nie udaje wtedy, że materiał jest nadal dostępny offline.

## Ważne: cache-through vs. dodatkowy transfer

Ta implementacja **nie jest jeszcze tee/cache-through na bajtach już czytanych przez ExoPlayer**. Po osiągnięciu progu Stage 07 rozpoczyna osobny, audio-only transfer w tle.

To oznacza, że funkcja może wygenerować dodatkowy transfer audio względem aktualnego playbacku. Ograniczamy go przez:

- opt-in użytkownika,
- Wi‑Fi/Ethernet domyślnie,
- próg faktycznego słuchania,
- brak video,
- pomijanie plików już dostępnych offline,
- pojedynczy background transfer,
- wspólny plik z Etapem 08 zamiast duplikowania magazynu.

Prawdziwy zero-copy/cache-through wymagałby wejścia w `DataSource`/cache pipeline playera. Najbezpieczniej zrobić to podczas późniejszej stopniowej migracji do Media3, zamiast ryzykować destabilizację obecnego ExoPlayera.

## Sieć i prywatność

Etap 07 **nie dodaje nowego stałego serwera ani pośrednika**. Używa istniejącego `MediaItemService` do pobrania informacji o formatach i aktualnego bezpośredniego hosta/CDN źródła audio zwróconego dla danego materiału.

Trwale przechowywane są wyłącznie metadane i lokalny plik. Krótkotrwały podpisany URL istnieje tylko w pamięci podczas transferu.

## Diagnostyka

Raport `Ustawienia -> Diagnostyka` pokazuje:

- effective ON/OFF,
- preferencję użytkownika,
- Wi‑Fi only,
- complete after switch,
- threshold,
- recent limit,
- PENDING / DOWNLOADING / AVAILABLE / FAILED.

W Diagnostyce jest też master FeatureFlag Stage 07.

## Nowe / kluczowe klasy

- `OfflineListenSaveController`
- `OfflineListenSaveService`
- `OfflineListenSaveRepository`
- `OfflineListenSaveDatabase`
- `OfflineListenSaveEntry`
- `OfflineListenSaveState`
- `OfflineDownloadCoordinator`
- `OfflineListenSavedFragment`

## Testy dodane / rozszerzone

- `OfflineDownloadCoordinatorTest`
- `OfflineListenSaveEntryTest`
- `OfflineMediaPreferencesTest` — domyślne i niepoprawne wartości Stage 07.

## Checklista testu na urządzeniu

1. Zbuduj i uruchom wariant stmobile.
2. Sprawdź, że `Słucham i zapisuję` jest domyślnie wyłączone.
3. Włącz funkcję, pozostaw `Tylko Wi‑Fi` i próg 15 s.
4. Uruchom zwykły skończony film z muzyką; pauza przed progiem nie powinna uruchamiać zapisu.
5. Po 15 s faktycznego PLAYING sprawdź notyfikację/progress i `Offline -> Ostatnio zapisane automatycznie`.
6. Po zakończeniu wyłącz sieć i użyj `Odtwórz offline`.
7. Włącz `Dokończ po przełączeniu`, przeklikaj na następny materiał i sprawdź, że pierwszy transfer może się dokończyć.
8. Wyłącz `Dokończ po przełączeniu`; po zmianie materiału aktywny pasywny zapis powinien zostać anulowany/usunięty.
9. Włącz `Tylko Wi‑Fi`, przełącz na dane komórkowe i sprawdź, że transfer nie startuje / zostaje wznowiony później.
10. Pobierz ten sam utwór jako część playlisty Stage 08, a potem usuń wpis Stage 07 — plik playlisty ma pozostać.
11. Odwrotnie: usuń playlistę z plikiem używanym także przez Stage 07 — automatyczny zapis ma pozostać.
12. Ustaw limit 20 i wygeneruj więcej pozycji; najstarsze automatyczne własności powinny być przycinane bez psucia playlist.
13. Sprawdź raport Diagnostyki i master flag Stage 07.
14. Sprawdź Radio, Shorts i stabilny Android Auto — Stage 07 nie powinien się tam uruchamiać.
