# SmartTube-AA — FINALNA checklista paczek, patchy i zmian

Ten dokument zamyka uzgodnioną roadmapę Etapów 01–12 i jest źródłem informacji o całej historii prac od oryginalnego ZIP-a do finalnego Stage 12. Zawiera nazwę każdej paczki, odpowiadający jej patch, bazę patcha i zakres funkcjonalny.

**Finalna rekomendowana baza do dalszego rozwoju:** `smarttube-aa-stage-12-performance-2026-08-09.zip`.

**Ważna kolejność historyczna:** Etap 08 został wykonany przed Etapem 07. Następnie Etap 07 został scalony na Stage 08. Od Stage 09 wszystkie paczki są już ponownie liniowe i kumulatywne.

## 0. Oryginał

### `smarttube-aa-main.zip`
Baza przesłana przez użytkownika.

---

## 1. Pierwszy pakiet naprawczy

### Pełna paczka
`smarttube-aa-fixed-2026-08-08.zip`

### Patch
`FIXES_2026-08-08.patch`

### Zakres

- naprawa continuation / dalszego ładowania Shorts,
- poprawa layoutu dolnego panelu playera i seekbara przy długich etykietach jakości,
- poprawa tap-vs-drag po pinch-zoom (`touchSlop`),
- usunięcie filtra radio `countrycode=PL`; globalne stacje,
- naprawa `Nowe rekomendacje` / Trending, aby nie kopiowało Home,
- ukrycie recoverowalnego, przejściowego 403 w mobilnym UI przy zachowaniu wspólnego mechanizmu recovery.

---

## 2. Pakiet funkcjonalny Player / Radio / Android Auto

### Pełna paczka
`smarttube-aa-features-2026-08-08.zip`

### Patch
`FEATURES_INTEGRATION_2026-08-08.patch`

### Zakres

- `Ustawienia → Odtwarzacz`,
- izolowane preferencje nowego playera mobilnego,
- preferowany język audio i napisów bez automatycznego wymuszania,
- nowy Material Bottom Sheet wyboru ścieżek,
- przełączniki gestów i kontrolek playera,
- wyszukiwarka Radia,
- zachowanie i współdzielenie Ulubionych,
- eksperymentalny Radio DVR/time-shift 1/3/5 minut,
- przewijanie Radia także przez Android Auto,
- LIVE/fail-open do bezpośredniego streamu,
- odizolowana eksperymentalna warstwa AA video/parked-video bez naruszania stabilnego `SmartTubeAutoMusicService`,
- zachowanie wszystkich poprawek z paczki nr 1.

---

## 3. SponsorBlock / DeArrow w nowym mobile UI

### Pełna paczka
`smarttube-aa-enhancements-2026-08-08.zip`

### Patch
`DEARROW_SPONSORBLOCK_MOBILE_ENHANCEMENTS.patch`

### Zakres

- znaczniki SponsorBlock/rozdziałów na nowym mobilnym seekbarze,
- DeArrow community title / thumbnail na natywnych listach mobile,
- original/unlocalized titles na natywnych listach,
- fallback klatek miniaturek YouTube,
- cache metadanych,
- ograniczona równoległość zadań,
- usunięcie konfliktu wspólnego znacznika `deArrowProcessed`,
- przełączniki funkcji w ustawieniach playera,
- zachowanie funkcji i napraw z paczek nr 1–2.

---

## 4. Etap 01 dalszego planu — Diagnostyka + FeatureFlags

### Pełna paczka
`smarttube-aa-stage-01-diagnostics-2026-08-08.zip`

### Patch
`STAGE_01_DIAGNOSTICS_2026-08-08.patch`

### Dokumentacja
`STAGE_01_DIAGNOSTICS_2026-08-08.md`

### Zakres

- nowa sekcja `Ustawienia → Diagnostyka`,
- lokalny raport wersji/urządzenia/playbacku,
- czas `prepare -> READY` i `prepare -> PLAYING`,
- aktualny format video/audio/napisów,
- rodzaj źródła i host bez pełnego podpisanego URL,
- trwałe liczniki błędów, 403, odzyskań 403, restartów i retry,
- stan SponsorBlock / DeArrow,
- statystyki cache/pobierania metadanych DeArrow,
- stan i rozmiar Radio DVR,
- widoczny stan eksperymentalnego AA video,
- procesowy ring-buffer ostatnich zdarzeń,
- przycisk kopiowania raportu,
- reset diagnostyki,
- centralny `MobileFeatureFlags` jako bramka rollback/rollout dla następnych etapów,
- brak nowych połączeń sieciowych w diagnostyce,
- wszystkie funkcje i poprawki z paczek nr 1–3 pozostają w bazie.

---

## 5. Etap 02 dalszego planu — Instant Play / recovery startu filmu

### Pełna paczka
`smarttube-aa-stage-02-instant-play-2026-08-08.zip`

### Patch
`STAGE_02_INSTANT_PLAY_2026-08-08.patch`

### Dokumentacja
`STAGE_02_INSTANT_PLAY_2026-08-08.md`

### Zakres

- mobilny, sesyjny `MobileInstantPlayController`,
- zachowanie wspólnego `ErrorFixerController` jako pierwszej linii recovery,
- opóźnione fallback retry po recoverowalnym 403 (ok. 1,25 s i 3,25 s),
- jeden startup watchdog reload po 8 s bez `READY`,
- retryowalny timeout po 22 s bez `READY`, bez brutalnego ubijania engine,
- izolacja od Radia i stabilnego Android Auto,
- trzy przełączniki `Ustawienia -> Odtwarzacz -> Instant Play`, domyślnie włączone,
- trzy niezależne FeatureFlags rollback/rollout,
- rozszerzone liczniki i stan Instant Play w Diagnostyce,
- brak nowych endpointów sieciowych,
- brak agresywnego prefetchu wygasających podpisanych URL-i,
- zachowanie wszystkich funkcji i poprawek z paczek nr 1–4.

---

## 6. Etap 03 dalszego planu — Paginacja / nieskończone listy / pełny katalog Radia

### Pełna paczka
`smarttube-aa-stage-03-pagination-2026-08-08.zip`

### Patch
`STAGE_03_PAGINATION_2026-08-08.patch`

### Dokumentacja
`STAGE_03_PAGINATION_2026-08-08.md`

### Raport walidacji
`STAGE_03_VALIDATION_2026-08-08.md`

### Zakres

- wspólny, thread-safe `LegacyGroupPaginator` dla continuation Browse/Search/Channel,
- wspólny `LegacyPagedPayloadMapper` zachowujący logiczne półki i deduplikujący elementy,
- zachowanie wcześniejszego recovery wygasłych continuation Shorts,
- prawdziwe load-more dla mobilnego Search,
- prawdziwe load-more dla długich sekcji Channel,
- `hasMore` w payloadach Search/Channel przy zachowaniu kompatybilnych konstruktorów,
- limit do 8 sesji continuation Search i Channel,
- usunięcie historycznego limitu 200 stacji w mobilnym katalogu Radia,
- serwerowe strony Radio Browser po 250 rekordów z `offset`/`limit`,
- progresywne lokalne okno RecyclerView po 120 stacji,
- cache katalogu Radia w `radio_catalog_v3.ndjson` zamiast rosnącego JSON w SharedPreferences,
- automatyczna migracja starego `stations_json`,
- trwały offset i stan końca katalogu,
- lokalne wyszukiwanie po wszystkich dotychczas pobranych stronach,
- trzy domyślnie włączone FeatureFlags: Search paging, Channel paging, Radio full catalog,
- rozbudowana Diagnostyka o continuation/pages/offset/end-state,
- świadome ograniczenie katalogu Android Auto do maks. 200 stacji w celu ochrony stabilnego MediaBrowser/Binder; pełne foldery AA zostają na Radio 2.0,
- brak nowego hosta sieciowego: Radio nadal używa `https://de1.api.radio-browser.info`,
- brak nowej zależności AndroidX Paging 3; użyto natywnego continuation projektu, aby nie spłaszczać wielopółkowych `MediaGroup` i nie ryzykować regresji Shorts,
- zachowanie wszystkich funkcji i poprawek z paczek nr 1–5.

---

## 7. Etap 04 dalszego planu — Smart Player UX

### Pełna paczka
`smarttube-aa-stage-04-smart-player-2026-08-08.zip`

### Patch
`STAGE_04_SMART_PLAYER_2026-08-08.patch`

### Dokumentacja
`STAGE_04_SMART_PLAYER_2026-08-08.md`

### Raport walidacji
`STAGE_04_VALIDATION_2026-08-08.md`

### Zakres

- pionowy gest jasności po lewej stronie zwykłego VOD z przywróceniem poprzedniej jasności po wyjściu z playera,
- pionowy gest głośności po prawej stronie zwykłego VOD,
- celowa izolacja pionowych gestów od Shorts i Radia,
- konfigurowalne double-tap ±5/10/15/30 s przy zachowaniu osobnego przełącznika mechanizmu,
- ekranowa blokada dotyku z osobnym `Odblokuj` i bez automatycznego startu w stanie locked,
- Back podczas blokady najpierw odblokowuje player,
- timer uśpienia: off / 15 / 30 / 45 / 60 min / do końca bieżącego filmu,
- timery minutowe oparte o `SystemClock.elapsedRealtime()`, a tryb `do końca filmu` powiązany z ID/staniem bieżącego materiału; stan zachowywany przez zmianę konfiguracji,
- po wygaśnięciu timera `pause()` zamiast niszczenia playera,
- zapamiętywanie pinch-zoom 1x–4x osobno dla VOD i Shorts,
- zapamiętywana jest wyłącznie skala, bez pan/translation,
- `MobileTrack` rozszerzony o rzeczywiste width/height formatu video,
- Smart Fit wybierający bezpiecznie FIT lub ZOOM na podstawie proporcji video/powierzchni,
- ręczna zmiana resize mode ma pierwszeństwo do końca bieżącego filmu,
- nowa sekcja ustawień Smart Player UX i wszystkie funkcje jako opcje, domyślnie dostępne,
- master rollback flag `SMART_PLAYER_UX` w Diagnostyce,
- raport Diagnostyki rozszerzony o stan ustawień Stage 04,
- nowe overlaye feedbacku procentowego i lock/unlock,
- test `LegacyTrackMapperTest` rozszerzony o weryfikację geometrii/aspect ratio używanej przez Smart Fit,
- brak nowych endpointów i brak zmian w stabilnym Android Auto/Radio DVR,
- zachowanie wszystkich funkcji i poprawek z paczek nr 1–6.


## 8. Etap 05 dalszego planu — Radio 2.0

### Pełna paczka
`smarttube-aa-stage-05-radio-2-2026-08-08.zip`

### Patch
`STAGE_05_RADIO_2_2026-08-08.patch`

### Dokumentacja
`STAGE_05_RADIO_2_2026-08-08.md`

### Raport walidacji
`STAGE_05_VALIDATION_2026-08-08.md`

### Zakres

- nowy mobilny układ Radia 2.0: Wszystkie / Ulubione / Ostatnie, sortowanie, wyszukiwanie, filtr kraju i gatunku/tagu,
- lokalne „Ostatnio słuchane” (MRU do 50 stacji), współdzielone przez telefon i Android Auto,
- zdalne wyszukiwanie Radio Browser z debounce 450 ms i limitem wyników; można je całkowicie wyłączyć,
- pełny katalog Stage 03 pozostaje paginowany, a wyniki zdalnego wyszukiwania są bezpiecznie scalane z lokalnym cache bez psucia kursora katalogu,
- poprawka kursora `nextOffset`: wyniki z search nie powodują pomijania stron pełnego katalogu po restarcie,
- ręczna synchronizacja zachowuje stacje spersonalizowane przez Ulubione/Ostatnie, nawet jeśli pochodzą z dalszych stron lub z wyszukiwania,
- automatyczny failover streamu: po awarii aktywnego URL aplikacja szuka alternatyw tego samego radia, omija już próbowane adresy i przełącza odtwarzanie,
- dodatkowe reconnecty upstream w Radio DVR z zachowaniem zgromadzonego bufora,
- fail-open: gdy DVR/failover nie może pomóc, dotychczasowe zachowanie błędu pozostaje zamiast zapętlania prób,
- etykieta `LIVE` / `LIVE −mm:ss` dla radia cofniętego względem live edge oraz istniejący szybki powrót do LIVE,
- rozbudowany katalog Android Auto: Radio Home, Ulubione, Ostatnie, Kraje, Gatunki i Wszystkie,
- wyszukiwanie stacji z poziomu Android Auto oraz filtrowane katalogi kraju/gatunku,
- przycisk/custom action `LIVE` w MediaSession dla aktywnego radia,
- limity kolejek AA (do 160 pozycji dla filtrów/search, 200 dla list ogólnych) pozostają celowo ograniczone dla stabilności MediaBrowser/Binder,
- poprawione ID elementów AA z hashem kontenera, aby ta sama stacja w różnych kolejkach nie kolidowała z Next/Previous,
- możliwość wznowienia w AA stacji znalezionej przez search/kategorię, nawet jeśli nie mieści się w top 200,
- osobne ustawienia użytkownika dla zdalnego search, Ostatnich, failover, kategorii, rozbudowanego katalogu AA i etykiety LIVE offset; wszystkie domyślnie ON,
- cztery rollback FeatureFlags Stage 05: master Radio 2.0, remote search, stream failover i rozbudowane AA,
- Diagnostyka rozszerzona o zdalne search, liczbę Ostatnich, kandydatów/attempt/success failover oraz ustawienia Stage 05,
- brak nowego dostawcy sieciowego: wykorzystywany jest istniejący Radio Browser oraz oryginalne hosty streamów stacji,
- zachowanie wszystkich funkcji i poprawek z paczek nr 1–7.


## 9. Etap 06 dalszego planu — fundament Offline / lokalny cache audio

### Pełna paczka
`smarttube-aa-stage-06-offline-foundation-2026-08-08.zip`

### Patch
`STAGE_06_OFFLINE_FOUNDATION_2026-08-08.patch`

### Dokumentacja
`STAGE_06_OFFLINE_FOUNDATION_2026-08-08.md`

### Raport walidacji
`STAGE_06_VALIDATION_2026-08-08.md`

### Zakres

- nowa sekcja `Ustawienia -> Offline`,
- oddzielny `OfflineMediaRepository` bez wykonywania requestów sieciowych,
- oddzielna baza SQLite `smarttube_mobile_offline.db`,
- model lifecycle `DOWNLOADING / AVAILABLE / FAILED / EXPIRED`,
- metadane audio: mediaId, title, author, thumbnail, duration, MIME/codec, progress i timestamps,
- celowy brak trwałego zapisu podpisanych URL-i streamu,
- prywatny magazyn `<noBackupFilesDir>/offline_audio_v1`, bez uprawnień do pamięci współdzielonej,
- nazwy plików jako SHA-256 mediaId (`.part` / `.audio`) zamiast jawnego ID,
- API do rezerwacji, zapisu częściowego, postępu, atomowego commit, odczytu, delete i cleanup,
- limit offline 1/2/5/10 GB, domyślnie 2 GB,
- rezerwa wolnego miejsca 256/512 MB/1 GB, domyślnie 512 MB,
- automatyczne czyszczenie domyślnie ON,
- eviction: EXPIRED -> FAILED -> najstarsze AVAILABLE (LRU); aktywne DOWNLOADING nie są usuwane,
- ręczne `cleanup now` i `clear all` z potwierdzeniem,
- lokalne statystyki stanów i zajętości,
- rollout FeatureFlag `OFFLINE_FOUNDATION`, domyślnie ON,
- Diagnostyka rozszerzona o stan repozytorium, limity, rezerwę, zajętość i liczniki stanów,
- testy `OfflineFileKeyTest`, `OfflineMediaPreferencesTest`, `OfflineMediaRepositoryTest`,
- brak zmian w VOD/Shorts/Radio/Android Auto; Stage 06 nie pobiera jeszcze automatycznie audio,
- brak nowych endpointów lub zewnętrznego serwera,
- zachowanie wszystkich funkcji i poprawek z paczek nr 1–8.


## 10. Etap 08 dalszego planu — Playlisty offline (wdrożony przed Etapem 07)

### Pełna paczka
`smarttube-aa-stage-08-offline-playlists-2026-08-08.zip`

### Patch
`STAGE_08_OFFLINE_PLAYLISTS_2026-08-08.patch`

Patch jest nakładany bezpośrednio na `smarttube-aa-stage-06-offline-foundation-2026-08-08`, ponieważ użytkownik poprosił o Etap 08 przed realizacją Etapu 07.

### Dokumentacja
`STAGE_08_OFFLINE_PLAYLISTS_2026-08-08.md`

### Raport walidacji
`STAGE_08_VALIDATION_2026-08-08.md`

### Zakres

- jawne `Pobierz offline` na stronie kompletnej playlisty,
- trwała kolejka `smarttube_mobile_offline_playlists.db`,
- stany playlist QUEUED/DOWNLOADING/PAUSED/AVAILABLE/PARTIAL/FAILED,
- stany elementów PENDING/DOWNLOADING/AVAILABLE/FAILED,
- osobny `OfflinePlaylistDownloadService` jako foreground `dataSync`,
- pojedynczy transfer naraz, trwały queue i automatyczne odzyskiwanie przerwanych zadań,
- `START_STICKY` i NetworkCallback do wznowienia po powrocie dozwolonej sieci,
- domyślnie pobieranie tylko przez Wi-Fi/Ethernet; użytkownik może zezwolić na inne sieci,
- HTTP Range resume z zachowaniem `.part`,
- just-in-time `MediaItemService.getFormatInfo(mediaId)` oraz ponowne pobranie świeżego URL po 403/410,
- brak trwałego zapisu podpisanych URL-i streamu,
- wybór jednego finite audio-only formatu z preferencją języka, non-DRC i kompatybilnego kodeka,
- odrzucanie live/OTF/unplayable,
- Stage 06 `.part -> .audio` jako atomowy commit,
- przypinanie audio należącego do jawnej playlisty: automatyczne LRU nie może zepsuć pobranych playlist,
- współdzielenie jednej kopii audio przez kilka playlist i referencyjne bezpieczne usuwanie,
- idempotentne ponowne kliknięcie Download bez nadpisywania aktywnej kolejki,
- `Ustawienia -> Offline -> Zarządzaj playlistami offline`,
- progress, pause/resume/retry/delete i lokalne statystyki,
- lokalne `Odtwarzaj offline` na telefonie z identyfikatorem `offline:<mediaId>`,
- kolejka offline Next/Previous i auto-next bez sieci,
- zwykłe kliknięcia treści online nie są automatycznie zastępowane audio-only,
- FeatureFlag `OFFLINE_PLAYLISTS`, domyślnie ON,
- rozszerzona Diagnostyka Stage 08,
- brak nowego stałego endpointu/serwera pośredniego; używany jest istniejący MediaItemService i bezpośredni aktualny host audio źródła,
- Android Auto offline celowo pozostaje na Stage 09,
- Etap 07 „słucham i zapisuję” **nie został wdrożony w tej paczce** i nadal pozostaje osobnym etapem,
- zachowanie wszystkich funkcji i poprawek od początku projektu do Stage 06.


## 11. Etap 07 dalszego planu — „Słucham i zapisuję” (wdrożony po Etapie 08)

### Pełna paczka
`smarttube-aa-stage-07-listen-save-after-stage-08-2026-08-08.zip`

Ta pełna paczka jest nową wersją kumulatywną i zawiera również cały Etap 08. Stage 07 został celowo scalony **na Stage 08**, a nie na Stage 06, żeby nie utracić wcześniej wykonanych playlist offline.

### Patch
`STAGE_07_LISTEN_SAVE_AFTER_STAGE_08_2026-08-08.patch`

Patch należy nakładać na `smarttube-aa-stage-08-offline-playlists-2026-08-08`.

### Dokumentacja
`STAGE_07_LISTEN_SAVE_2026-08-08.md`

### Raport walidacji
`STAGE_07_VALIDATION_2026-08-08.md`

### Zakres

- opcjonalne `Offline -> Słucham i zapisuję`, świadomie domyślnie OFF ze względu na transfer i pamięć,
- Wi-Fi/Ethernet-only domyślnie ON; można zezwolić na inne sieci,
- próg faktycznego PLAYING 5/15/30/60 s, domyślnie 15 s; pauza nie nabija czasu,
- limit ostatnich automatycznych zapisów 20/50/100, domyślnie 50,
- opcja dokończenia aktywnego zapisu po przełączeniu na inny materiał, domyślnie ON,
- `OfflineListenSaveController` podłączony tylko do nowego mobilnego playbacku,
- jawne wykluczenie Radio/live/upcoming/Shorts/offline playback/headless Android Auto,
- trwała kolejka/history w osobnej SQLite `smarttube_mobile_offline_listen.db`, bez signed URL,
- zapis PENDING przed próbą startu foreground service, aby chwilowe ograniczenia FGS nowszego Androida nie gubiły zadania,
- osobny `OfflineListenSaveService` jako foreground dataSync,
- just-in-time `MediaItemService.getFormatInfo`, audio-only selector i brak video,
- świeży signed URL po 403/410, maks. 3 próby,
- HTTP Range resume do istniejącego `.part`,
- wspólny Stage 06 store i atomowy `.part -> .audio`,
- wspólny `OfflineDownloadCoordinator` z Etapem 08: jeden background transfer offline naraz,
- referencyjne współdzielenie pliku między Stage 07 i playlistami Stage 08; usuwanie jednej własności nie psuje drugiej,
- ekran `Ostatnio zapisane automatycznie` z kolejką, postępem, usuwaniem i `Odtwórz offline`,
- odtwarzanie przez istniejące `offline:<mediaId>` bez sieci,
- master FeatureFlag `OFFLINE_LISTEN_SAVE`, domyślnie ON jako techniczna bramka; preferencja użytkownika nadal domyślnie OFF,
- Diagnostyka rozszerzona o effective/pref/wifi/complete/threshold/recent limit i liczniki PENDING/DOWNLOADING/AVAILABLE/FAILED,
- poprawka lifecycle: `stopService()` ponownie ustawia aktywny wpis jako PENDING, zamiast zostawiać DOWNLOADING do restartu procesu,
- prune limitu wykonywany poza UI thread,
- testy `OfflineDownloadCoordinatorTest`, `OfflineListenSaveEntryTest` oraz rozszerzone `OfflineMediaPreferencesTest`,
- brak nowego stałego serwera: wykorzystywany jest istniejący MediaItemService i aktualny bezpośredni host audio/CDN,
- świadome ograniczenie: jest to osobny background audio-only transfer, nie zero-copy tee/cache-through aktualnie odtwarzanych bajtów; prawdziwy cache-through pozostaje na etap DataSource/Media3,
- zachowanie całego Stage 08 oraz wszystkich wcześniejszych funkcji i poprawek.


## 12. Etap 09 dalszego planu — Offline w Android Auto

### Pełna paczka
`smarttube-aa-stage-09-offline-android-auto-2026-08-08.zip`

Ta paczka jest kumulatywna. Bazuje na `smarttube-aa-stage-07-listen-save-after-stage-08-2026-08-08.zip`, więc zawiera również cały Etap 08 oraz wszystkie poprzednie poprawki od początku rozmowy.

### Patch
`STAGE_09_OFFLINE_ANDROID_AUTO_2026-08-08.patch`

Patch należy nakładać na `smarttube-aa-stage-07-listen-save-after-stage-08-2026-08-08`.

### Dokumentacja
`STAGE_09_OFFLINE_ANDROID_AUTO_2026-08-08.md`

### Raport walidacji
`STAGE_09_VALIDATION_2026-08-08.md`

### Zakres

- nowy folder `Offline` w stabilnym katalogu Android Auto,
- podfoldery `Ostatnio zapisane`, `Playlisty offline` i `Ulubione offline`,
- maksymalnie 160 pozycji w pojedynczej kolejce AA dla bezpieczeństwa MediaBrowser/Binder,
- jawny wybór z drzewa Offline zawsze używa lokalnego `offline:<mediaId>` i nigdy nie spada po cichu do streamingu sieciowego,
- headless `LegacyMobilePlaybackRepository` może odtwarzać prywatne pliki `.audio` z Etapów 06–08,
- `SmartTubeAutoMusicService` pozostaje jedynym właścicielem publicznej `MediaSessionCompat`,
- automatyczne użycie lokalnej kopii przed startem, gdy brak zwalidowanego internetu i plik już istnieje,
- fallback bieżącego źródła online na lokalny plik po utracie łączności, z próbą zachowania aktualnej pozycji,
- po wyczerpaniu normalnego retry lokalna kopia może uratować playback także przy formalnie „połączonej”, ale faktycznie martwej sieci,
- auto-next podczas braku internetu może pominąć online-only elementy i przejść do kolejnej lokalnej kopii,
- osobny `PREF_PLAYBACK_ID` dla poprawnego resume `offline:<mediaId>`,
- jawne źródło Offline pozostaje lokalne po restarcie, ale automatyczny fallback z kolejki online nie „przykleja się” po odzyskaniu internetu,
- brak sieciowej hydratacji kolejki dla źródeł Offline,
- `OfflineMediaRepository.peekAvailableFile()` / `hasAvailableFile()` umożliwiają budowę katalogu bez sztucznego odświeżania LRU,
- ustawienia AA: `Pokaż bibliotekę Offline` i `Automatycznie używaj lokalnej kopii po utracie internetu`, domyślnie ON,
- techniczny FeatureFlag `OFFLINE_ANDROID_AUTO`, domyślnie ON,
- Diagnostyka rozszerzona o stan Stage 09 i lokalne eventy `P17-AA-Offline`,
- jawne `android.permission.ACCESS_NETWORK_STATE` w głównym manifeście,
- brak nowego serwera/endpointu, brak nowych downloadów i brak signed URL w trwałym storage,
- Radio/Radio DVR oraz eksperymentalny AA Video pozostają odizolowane i nie są przebudowywane przez Stage 09,
- znane ograniczenie: URL miniatury jest zachowany jako metadata, ale bajty miniatur nie są jeszcze gwarantowane offline,
- zachowanie pełnego Stage 07+08 i wszystkich wcześniejszych etapów/poprawek.


## 13. Etap 10 dalszego planu — Inteligentny „zapas na podróż”

### Pełna paczka
`smarttube-aa-stage-10-trip-reserve-2026-08-08.zip`

Paczka jest kumulatywna i bazuje na `smarttube-aa-stage-09-offline-android-auto-2026-08-08.zip`, a więc zachowuje wszystkie poprawki i funkcje od początku rozmowy oraz kompletne Etapy 01–09 (w tym Stage 07 scalony wcześniej na Stage 08).

### Patch
`STAGE_10_TRIP_RESERVE_2026-08-08.patch`

Patch należy nakładać na `smarttube-aa-stage-09-offline-android-auto-2026-08-08`.

### Dokumentacja
`STAGE_10_TRIP_RESERVE_2026-08-08.md`

### Raport walidacji
`STAGE_10_VALIDATION_2026-08-08.md`

### Zakres

- opcjonalny Smart Trip Reserve, świadomie domyślnie OFF ze względu na transfer i pamięć,
- techniczny FeatureFlag `OFFLINE_TRIP_RESERVE`, domyślnie ON,
- utrzymywanie ostatnio słuchanych utworów: 10/20/30/50, domyślnie 30,
- opcjonalny zapas Liked Music: 10/20/50/100, domyślnie 20,
- ostatnie playlisty: 0/1/2/3, domyślnie 2,
- limit 25/50/100 utworów z jednej ostatniej playlisty, domyślnie 50,
- Wi-Fi/Ethernet-only domyślnie ON,
- próg 10 sekund rzeczywistego PLAYING przed wpisaniem materiału do historii podróży,
- wykluczenie Radio/live/upcoming/Shorts/offline playback,
- historia działa również dla headless playbacku AA, bez tworzenia nowej MediaSession,
- lokalna SQLite `smarttube_mobile_offline_trip_reserve.db`, bez signed URL/stream URL,
- maks. 400 ostatnich materiałów i 40 ostatnich playlist w historii,
- wykluczenie zmiennych auto-mixów `RD...` i `UL...`,
- syntetyczne, izolowane ID: `trip:recent`, `trip:favorites`, `trip:playlist:<id>`,
- Stage 10 jest plannerem; faktyczny audio-only transfer deleguje do istniejącego Stage 08 downloadera,
- zachowane HTTP Range, `.part`, 403/410 refresh i atomowy commit z wcześniejszych etapów,
- brak drugiej kopii tego samego audio, gdy materiał jest współdzielony przez ręczną playlistę, Listen & Save i Trip Reserve,
- zapas Liked Music pobierany przez istniejący publiczny `ContentService.getMusicObserve()` zamiast nowego endpointu,
- maks. jedna automatyczna synchronizacja ulubionych na ok. 6 h plus ręczne „Przygotuj zapas teraz”,
- Like/Unlike z Android Auto po potwierdzeniu może wymusić odświeżenie planera ulubionych,
- wyłączenie Stage 10 zwalnia syntetyczne piny playlist, nie niszczy brutalnie współdzielonych plików i pozostawia historię do przyszłego ponownego włączenia,
- osobny tryb Wi-Fi dla Trip Reserve niezależnie od jawnych pobrań playlist Stage 08,
- rozszerzona Diagnostyka i logi `P20-TripReserve`,
- syntetyczne playlisty są widoczne jako zarządzane automatycznie i nie pokazują ręcznych przycisków kasowania/pauzy, które planner natychmiast odtworzyłby,
- brak nowego zewnętrznego serwera lub własnego endpointu,
- zachowanie Android Auto Offline Stage 09, Radio 2.0, Instant Play, Smart Player, SponsorBlock/DeArrow oraz wszystkich sześciu początkowych poprawek.


## Kolejne planowane etapy po Stage 10

14. Etap 11 — stopniowa migracja playbacku do Media3.
15. Etap 12 — benchmarki, Baseline Profiles i końcowa optymalizacja.

---

## 14. Etap 11 dalszego planu — stopniowa migracja playbacku do Media3

### Pełna paczka
`smarttube-aa-stage-11-media3-2026-08-08.zip`

Paczka jest kumulatywna i bazuje na `smarttube-aa-stage-10-trip-reserve-2026-08-08.zip`.

### Patch
`STAGE_11_MEDIA3_MIGRATION_2026-08-08.patch`

Patch należy nakładać na `smarttube-aa-stage-10-trip-reserve-2026-08-08`.

### Dokumentacja
`STAGE_11_MEDIA3_MIGRATION_2026-08-08.md`

### Raport walidacji
`STAGE_11_VALIDATION_2026-08-08.md`

### Zakres

- odwracalna warstwa `MobilePlaybackEngine` oddzielająca UI/repository od konkretnego silnika,
- adapter legacy zachowujący dotychczasowy ExoPlayer 2 dla VOD/Shorts,
- nowy `Media3PlaybackEngine` jako pierwsza fala migracji,
- Media3 domyślnie dla Radia i lokalnego Offline audio,
- VOD/Shorts nadal na sprawdzonej ścieżce legacy,
- zachowanie istniejącego `SmartTubeAutoMusicService` i `MediaSessionCompat` Android Auto,
- automatyczny fallback Media3 -> legacy dla źródeł Radio/Offline,
- zachowanie pozycji Offline przy fallbacku, gdy jest to możliwe,
- obsługa position/buffer/seek/play/pause/speed/pitch/volume/audio focus/`becoming noisy`,
- mapowanie stanów BUFFERING/READY/ENDED/error,
- osobne FeatureFlags: master Media3, Radio, Offline i fallback,
- rozszerzona Diagnostyka o aktywny silnik i liczniki Media3,
- bezpieczne przejście VOD -> direct Radio/Offline bez pozostawiania starego ownera `PlaybackPresenter`,
- brak przymusowej migracji całej aplikacji w jednym commicie,
- zachowanie wszystkich funkcji i poprawek od początku projektu do Stage 10.

---

## 15. Etap 12 — benchmarki, Baseline Profile i końcowa optymalizacja

### Pełna paczka
`smarttube-aa-stage-12-performance-2026-08-09.zip`

Paczka jest finalną kumulatywną wersją roadmapy 01–12 i bazuje na `smarttube-aa-stage-11-media3-2026-08-08.zip`.

### Patch
`STAGE_12_PERFORMANCE_2026-08-09.patch`

Patch należy nakładać na `smarttube-aa-stage-11-media3-2026-08-08`.

### Dokumentacja
`STAGE_12_PERFORMANCE_2026-08-09.md`

### Raport walidacji
`STAGE_12_VALIDATION_2026-08-09.md`

### Audyt sieci/TLS
`STAGE_12_NETWORK_TLS_AUDIT_2026-08-09.md`

### Zakres

- osobny moduł `:mobilebenchmark` oparty o Macrobenchmark,
- benchmark cold startup i warm startup przez `StartupTimingMetric`,
- benchmark przewijania Home przez `FrameTimingMetric`,
- `BaselineProfileRule` z krytycznymi ścieżkami Home -> scroll -> Search -> Settings,
- build type `benchmark` dla stmobile oraz manifest `profileable` tylko dla tego wariantu,
- `androidx.profileinstaller` dla lokalnego stosowania Baseline Profile na obecnym AGP 7.4,
- seed `smarttubetv/src/main/baseline-prof.txt` oraz skrypt instalujący wygenerowany profil z wyników benchmarku,
- świadome niewłączanie nowoczesnego Startup Profile DEX-layout na starym AGP 7.4; pozostawione do upgrade toolchainu,
- opt-in R8 trial przez `-Pstage12EnableR8=true`, domyślnie OFF, więc release zachowuje poprzednie zachowanie,
- lokalny `MobilePerformanceMonitor` bez telemetrii i bez uploadu,
- proces -> Activity, first frame i Home TTFD/reportFullyDrawn,
- trace sections `ST:BrowseRender` oraz `ST:PlaybackRender` do Perfetto/system trace,
- lekki Choreographer sampler frame gaps z progami >24/>50/>100 ms,
- lokalne statystyki Java/native heap, device memory i trim-memory,
- przełączniki Stage 12 w Diagnostyce: monitor oraz frame sampling,
- reset liczników wydajności razem z diagnostyką,
- `MobileMediaAdapter` zoptymalizowany pod continuation: append fast-path bez pełnego rebinda oraz DiffUtil dla zmian niebędących czystym append,
- SponsorBlock: procesowy TTL/LRU cache + single-flight dla identycznych równoległych requestów,
- DeArrow: zachowany cache TTL/LRU + single-flight dla równoległych requestów tego samego filmu,
- statystyki cache hit/miss/in-flight/join SponsorBlock i DeArrow w Diagnostyce,
- brak nowych endpointów i brak nowej telemetrii sieciowej,
- osobny audyt starej wspólnej warstwy OkHttp/TLS; ryzykowne legacy TLS na API <=24 zostało udokumentowane, ale nie zmienione bez testów kompatybilności wszystkich flavorów,
- zachowanie wszystkich sześciu pierwszych poprawek, wszystkich funkcji Player/Radio/AA, SponsorBlock/DeArrow i pełnych Etapów 01–11.

---

## Stan końcowy roadmapy

Roadmapa zaplanowana w tej rozmowie (Etapy 01–12) jest kompletna w paczce Stage 12. Kolejne prace powinny być prowadzone jako nowa seria zmian/etapów, bazująca na finalnym Stage 12 oraz na wynikach realnych benchmarków i testów APK wykonanych na urządzeniu.
