# Stage 08 — Playlisty offline

Data: 2026-08-08

## Baza i kolejność etapów

Ten etap został wdrożony **bezpośrednio na Stage 06** (`smarttube-aa-stage-06-offline-foundation-2026-08-08`), ponieważ użytkownik poprosił od razu o **Etap 8**. Etap 07 — „słucham i zapisuję” — pozostaje osobnym, niewdrożonym jeszcze etapem. Stage 08 nie udaje Stage 07 i nie włącza automatycznego zapisywania słuchanych utworów.

Pełny ZIP Stage 08 jest kumulatywny i zachowuje wszystkie wcześniejsze poprawki oraz funkcje od początku projektu do Stage 06.

## Cel

Stage 08 dodaje jawne, kontrolowane przez użytkownika pobieranie całych playlist do prywatnego magazynu audio i późniejsze odtwarzanie ich na telefonie/tablecie bez połączenia z siecią.

Najważniejsza zasada architektoniczna:

```text
użytkownik otwiera playlistę
        ↓
pełna playlista zostaje dociągnięta z istniejącego API
        ↓
Pobierz offline
        ↓
trwała kolejka SQLite
        ↓
ForegroundService dataSync
        ↓
just-in-time MediaItemService.getFormatInfo(videoId)
        ↓
wybór jednego bezpośredniego audio-only streamu
        ↓
.part → atomowy commit → .audio
        ↓
Offline → Playlisty offline → Odtwarzaj offline
```

## 1. Nowa trwała kolejka playlist

Dodano osobną bazę:

`smarttube_mobile_offline_playlists.db`

Tabele:

- `offline_playlists` — stan całej playlisty,
- `offline_playlist_items` — poszczególne utwory i ich kolejność.

Stany playlisty:

- `QUEUED`,
- `DOWNLOADING`,
- `PAUSED`,
- `AVAILABLE`,
- `PARTIAL`,
- `FAILED`.

Stany pojedynczego elementu:

- `PENDING`,
- `DOWNLOADING`,
- `AVAILABLE`,
- `FAILED`.

Kolejka jest trwała. Po ponownym uruchomieniu procesu element przerwany w stanie `DOWNLOADING` wraca do `PENDING`, a zachowany plik `.part` może być wykorzystany do wznowienia transferu.

Ponowne kliknięcie „Pobierz offline” nie niszczy aktywnej kolejki. Operacja jest idempotentna; terminalna kolejka `PARTIAL/FAILED` jest przestawiana na retry, a aktywna lub ukończona zostaje zachowana.

## 2. Foreground downloader

Dodano:

`OfflinePlaylistDownloadService`

Jest to osobny `foregroundServiceType="dataSync"`. Nie zastępuje mobilnego background playbacku ani `SmartTubeAutoMusicService` Android Auto.

Cechy:

- jeden transfer naraz — mniej presji na pamięć i mniej równoległych podpisanych URL-i,
- trwała kolejka w SQLite,
- pauza,
- wznowienie,
- retry,
- anulowanie/usunięcie playlisty,
- automatyczne wznowienie pracy po powrocie dozwolonej sieci,
- `START_STICKY`, dzięki czemu system może odtworzyć usługę po zabiciu procesu,
- trwały `.part` używany do HTTP Range resume,
- aktualizacja postępu w bazie i notyfikacji.

## 3. Podpisane URL-e nie są zapisywane

To ważny element projektu.

Baza przechowuje tylko m.in.:

- ID materiału,
- tytuł,
- autora,
- miniaturę,
- czas trwania,
- stan,
- licznik bajtów,
- kolejność na playliście.

Gdy rozpoczyna się pobieranie konkretnego elementu, serwis dopiero wtedy wykonuje:

`MediaItemService.getFormatInfo(mediaId)`

Następnie wybiera odpowiednią ścieżkę audio i używa jej aktualnego URL-a tylko w pamięci procesu. URL streamu nie jest zapisywany w SQLite ani SharedPreferences.

Przy 403/410 downloader ponownie pobiera `FormatInfo`, otrzymując nowy aktualny podpisany URL, i próbuje transfer ponownie.

## 4. Wybór ścieżki audio

Dodano `OfflineAudioFormatSelector`.

Akceptowane są tylko bezpośrednie, skończone formaty:

- MIME zaczynające się od `audio/`,
- z niepustym URL-em,
- `isOtf() == false`.

Priorytety:

1. język zgodny z preferowanym językiem audio mobilnego playera,
2. ścieżka bez DRC,
3. MP4/AAC jako najbardziej kompatybilny fallback,
4. WebM,
5. wyższy bitrate jako ostatnie kryterium.

Live i materiały oznaczone jako unplayable nie są zapisywane jako finite offline audio.

## 5. Resume przez HTTP Range

Jeżeli istnieje `.part`, downloader wysyła:

`Range: bytes=<istniejący_rozmiar>-`

Jeżeli serwer odpowie `206`, dopisuje dane do `.part`.

Jeżeli odpowie pełnym `200`, downloader rozpoczyna ten plik od początku, zamiast błędnie doklejać pełną odpowiedź do częściowego pliku.

Po zakończeniu Stage 06 wykonuje atomowy commit `.part → .audio` i dopiero wtedy element dostaje stan `AVAILABLE`.

Dodatkowe zabezpieczenia Stage 08:

- po uzyskaniu rzeczywistego `Content-Length` ponownie sprawdzany jest limit Offline i minimalna rezerwa wolnego miejsca, zanim downloader zacznie dopisywać dane,
- jeżeli ani metadane formatu, ani odpowiedź HTTP nie podają skończonego rozmiaru, download jest bezpiecznie przerywany zamiast pozwalać na nieograniczony zapis,
- jeśli po odtworzeniu procesu zachowany `.part` ma już co najmniej oczekiwany rozmiar, jest commitowany lokalnie bez wykonywania błędnego Range requestu za EOF,
- jeśli odświeżony podpisany URL prowadzi do reprezentacji o innym MIME/kodeku/znanej długości niż rozpoczęty `.part`, częściowy plik jest zerowany i pobieranie tej reprezentacji zaczyna się od początku, aby nie skleić dwóch różnych formatów.

## 6. Sieć

W `Ustawienia → Offline` dodano:

- `Pobieranie playlist offline` — domyślnie **ON**,
- `Tylko Wi‑Fi / Ethernet` — domyślnie **ON**.

Przy aktywnej polityce Wi‑Fi downloader nie rozpoczyna transferu przez sieć komórkową. Jeżeli Wi‑Fi zniknie w trakcie pobierania, aktywny request jest anulowany, element wraca do `PENDING`, `.part` zostaje zachowany, a usługa czeka na dozwoloną sieć. `NetworkCallback` ponownie uruchamia kolejkę po odzyskaniu łączności.

Użytkownik może wyłączyć ograniczenie Wi‑Fi i zezwolić na pobieranie przez aktualną sieć internetową.

## 7. Zarządzanie miejscem

Stage 08 korzysta z limitów przygotowanych w Stage 06.

Dodatkowo element należący do jawnie pobranej playlisty jest **przypięty**. Automatyczne LRU nie może usunąć pliku, który nadal jest potrzebny przez playlistę offline.

Jeżeli kilka playlist zawiera ten sam `mediaId`, korzystają ze wspólnej kopii audio. Usunięcie jednej playlisty nie usuwa wspólnego pliku, dopóki odwołuje się do niego inna playlista.

Jeżeli wszystkie przypięte playlisty zajmą ustalony limit, nowy download zakończy się czytelnym błędem braku pojemności zamiast po cichu kasować starszą playlistę użytkownika.

## 8. UI pobierania playlisty

Na stronie szczegółów playlisty pojawia się akcja `Pobierz offline`.

Duże playlisty w tym projekcie są stronicowane. Stage 08 nie pobiera tylko pierwszych kilkunastu pozycji. `LegacyBrowseRepository` dociąga continuation w tle, a przycisk pobierania korzysta z kompletnego payloadu playlisty.

Elementy live, nieodtwarzalne i duplikaty ID są pomijane przez kolejkę offline.

## 9. Ekran „Playlisty offline”

`Ustawienia → Offline → Zarządzaj playlistami offline`

Dla każdej playlisty pokazujemy:

- nazwę,
- stan,
- liczbę ukończonych / wszystkich,
- liczbę błędów,
- zapisane bajty,
- progress bar,
- `Odtwarzaj offline`, gdy istnieje co najmniej jeden gotowy element,
- `Wstrzymaj` / `Wznów` / retry,
- `Usuń`.

Akcje są ułożone tak, aby długie tłumaczenia nie rozsypywały layoutu na wąskich telefonach.

## 10. Lokalne odtwarzanie offline na telefonie

Dodano jawny wewnętrzny identyfikator:

`offline:<mediaId>`

Dzięki temu zwykłe kliknięcie filmu online nie zostaje automatycznie zastąpione audio-only z cache. Lokalny plik jest używany tylko wtedy, gdy użytkownik uruchamia zawartość z ekranu Offline.

`LegacyMobilePlaybackRepository` rozpoznaje `offline:` i:

- nie pyta sieci o źródło,
- rozwiązuje prywatny `.audio`,
- otwiera go jako `file://` przez istniejący ExoPlayer,
- buduje kolejkę z gotowych elementów playlisty,
- Next/Previous działa w obrębie tej kolejki,
- po naturalnym końcu utworu przechodzi do następnego dostępnego elementu,
- na końcu playlisty nie zapętla automatycznie od początku.

## 11. Android Auto — celowo jeszcze bez playlist offline

Stage 08 nie podłącza `offline:` do headless automotive repository. Próba użycia tego trybu w headless AA jest blokowana.

Powód: stabilny `SmartTubeAutoMusicService` ma pozostać nienaruszony do dedykowanego **Stage 09 — Offline w Android Auto**. Wtedy dodamy browse tree Offline i poprawne przełączanie online/local po stronie MediaSession.

## 12. Stage 07 — czego tutaj NIE ma

Nie ma jeszcze:

- automatycznego „słucham i zapisuję”,
- cache-through zwykłego słuchania,
- automatycznego zapisywania ostatnich utworów.

To pozostaje Stage 07. Stage 08 działa wyłącznie po świadomym naciśnięciu `Pobierz offline`.

## 13. Feature flag i diagnostyka

Dodano FeatureFlag:

`OFFLINE_PLAYLISTS`

Domyślnie **ON**.

Efektywne działanie wymaga równocześnie:

- Stage 06 `OFFLINE_FOUNDATION`,
- preferencji `foundation_enabled`,
- `OFFLINE_PLAYLISTS`,
- preferencji `playlist_downloads_enabled`.

Diagnostyka pokazuje m.in.:

- efektywny stan funkcji,
- preferencję downloadów,
- politykę Wi‑Fi,
- liczbę playlist,
- queued/downloading/paused/available/partial/failed.

## 14. Zewnętrzne połączenia

Stage 08 **nie dodaje nowego serwera pośredniego SmartTube** ani nowego stałego hosta API.

Korzysta z już istniejącego pipeline projektu:

1. `MediaItemService`/YouTube API używanego już przez player pobiera aktualne `FormatInfo`,
2. downloader łączy się bezpośrednio z adresem audio zwróconym w `MediaFormat` (host streamu może się zmieniać, np. hosty CDN używane przez źródło),
3. URL jest używany tylko do bieżącego transferu i nie jest utrwalany.

W nowych klasach produkcyjnych Stage 08 nie ma na sztywno wpisanego nowego `http://`/`https://` endpointu.

## 15. Ograniczenia pierwszej wersji

- pobieramy audio-only, nie obraz 1080p/4K,
- zapisujemy tylko bezpośredni finite audio format; live/OTF są odrzucane,
- nie wszystkie materiały muszą zawsze udostępniać zgodny bezpośredni audio stream; taki element dostaje `FAILED` i może być ponowiony,
- zmiana zawartości już pobranej playlisty nie jest jeszcze automatycznie synchronizowana; ponowne kliknięcie podczas aktywnego/ukończonego zadania jest bezpiecznie idempotentne,
- Android Auto offline jest dopiero Stage 09,
- automatyczne „słucham i zapisuję” jest nadal Stage 07.

## Checklista testu na urządzeniu

1. `Ustawienia → Offline` — sprawdź dwa nowe przełączniki i ekran zarządzania playlistami.
2. Otwórz małą playlistę, poczekaj na komplet listy i wybierz `Pobierz offline`.
3. Sprawdź notyfikację pobierania i wzrost progressu.
4. Wstrzymaj, uruchom ponownie aplikację i wznów — pobieranie powinno kontynuować z `.part`.
5. Przy `Tylko Wi‑Fi` wyłącz Wi‑Fi — transfer powinien się zatrzymać bez utraty `.part`; włącz Wi‑Fi — powinien ruszyć dalej.
6. Po ukończeniu przełącz telefon w tryb samolotowy.
7. `Offline → Playlisty offline → Odtwarzaj offline` — utwory powinny grać bez sieci.
8. Sprawdź Next/Previous i automatyczne przejście do kolejnego elementu.
9. Usuń jedną z dwóch playlist zawierających ten sam utwór — druga nie może stracić wspólnego pliku.
10. Sprawdź limit pamięci: przypięte playlisty nie powinny być usuwane przez automatyczny cleanup.
11. Sprawdź Diagnostykę i liczby stanów Stage 08.
12. Sprawdź zwykłe VOD, Shorts, Radio i Android Auto — Stage 08 nie powinien zmienić ich normalnego działania.
