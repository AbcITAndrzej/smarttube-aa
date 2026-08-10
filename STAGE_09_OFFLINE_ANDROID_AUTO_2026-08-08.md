# Etap 09 — Offline w Android Auto

Data: 2026-08-08
Baza: `smarttube-aa-stage-07-listen-save-after-stage-08-2026-08-08.zip`
Patch: `STAGE_09_OFFLINE_ANDROID_AUTO_2026-08-08.patch`

## Cel

Udostępnić w stabilnej integracji Android Auto pliki audio zapisane przez Etapy 06–08 oraz zapewnić bezpieczny fallback na lokalną kopię, gdy podczas jazdy zniknie internet. Etap 09 **nie pobiera niczego sam** i nie modyfikuje eksperymentalnego modułu AA Video.

## Architektura

```text
Android Auto / MediaBrowser
        |
        v
SmartTubeAutoMusicService                 <- nadal jedyna publiczna sesja AA
        |
        +--> Online / Radio               <- dotychczasowe ścieżki
        |
        +--> Offline
              +-- Ostatnio zapisane
              +-- Playlisty offline
              +-- Ulubione offline
                        |
                        v
              OfflineMediaRepository
                        |
                        v
              offline:<mediaId>
                        |
                        v
              LegacyMobilePlaybackRepository.prepareOffline()
                        |
                        v
              prywatny plik .audio -> ExoPlayer
```

`SmartTubeAutoMusicService` pozostaje właścicielem `MediaSessionCompat`, kolejki, metadanych oraz komend AA. Repozytorium playbacku dostaje jedynie lokalny identyfikator `offline:<mediaId>` i otwiera prywatny plik audio. Nie powstaje druga sesja multimedialna.

## 1. Nowa biblioteka `Offline` w Android Auto

W głównym katalogu AA dochodzi pozycja `Offline`. W niej są trzy foldery:

- `Ostatnio zapisane` — maks. 160 kompletnych lokalnych plików, sortowanych przez istniejący Stage 06 według ostatniego użycia/aktualizacji,
- `Playlisty offline` — tylko playlisty mające co najmniej jeden fizycznie dostępny element,
- `Ulubione offline` — lokalne pliki, dla których aplikacja zna stan Like/Ulubione.

Pojedyncza kolejka AA jest ograniczona do **160 elementów**, aby nie zwiększać ryzyka dużych transakcji `MediaBrowser/Binder`. Pełne dane nadal pozostają w lokalnej bazie i można wejść w inne playlisty/foldery.

### Ulubione offline

Etap 09 nie tworzy nowej, osobnej bazy ulubionych. Wykorzystuje znany lokalnie stan Like z istniejącej integracji SmartTube. Jeśli pełny katalog polubień nie został jeszcze rozgrzany, lista może opierać się na stanie zapisanym wcześniej lokalnie.

## 2. Odtwarzanie bez internetu

Kliknięcie elementu wewnątrz drzewa `Offline` jest traktowane jako **jawne żądanie lokalne**.

Ważna zasada bezpieczeństwa:

> wybór z folderu Offline nigdy nie zamienia się po cichu w streaming sieciowy.

Jeżeli plik został usunięty pomiędzy wyświetleniem listy a kliknięciem, player próbuje ścieżki `offline:` i zgłasza lokalny błąd. Nie wysyła requestu do YouTube tylko dlatego, że sieć akurat jest dostępna.

## 3. Automatyczny fallback po utracie internetu

Opcja `Automatycznie używaj lokalnej kopii po utracie internetu` jest domyślnie włączona.

### Przed startem utworu

Jeżeli:

- odtwarzany jest zwykły materiał audio/VOD,
- internet nie jest zwalidowany przez `ConnectivityManager`,
- istnieje kompletny lokalny plik tego samego `mediaId`,

AA od razu uruchamia `offline:<mediaId>` zamiast rozpoczynać niepotrzebną próbę sieciową.

### W trakcie odtwarzania

Jeśli źródło online przestanie działać:

1. przy rzeczywistym braku zwalidowanej sieci aplikacja od razu sprawdza lokalny plik,
2. jeśli Android nadal raportuje sieć, normalny mechanizm recovery dostaje jedną próbę,
3. po wyczerpaniu retry lokalna kopia może zostać użyta nawet wtedy, gdy system formalnie widzi połączenie — chroni to przed tunelami, garażami i martwym handoverem,
4. fallback startuje od możliwie tej samej pozycji czasu.

Nie ma nowego downloadu w tej ścieżce. Fallback jest możliwy wyłącznie, gdy plik był już kompletny.

## 4. Automatyczne przechodzenie do kolejnego pliku

Jeżeli AA działa bez internetu i w bieżącej kolejce są pozycje online oraz offline:

- auto-next szuka następnej pozycji posiadającej kompletną lokalną kopię,
- może pominąć online-only elementy,
- respektuje `Repeat all`,
- gdy nie ma kolejnego lokalnego elementu, nie rozpoczyna nieskończonej pętli prób.

Dla kolejki z folderu `Offline` wszystkie wystawiane elementy zostały wcześniej zweryfikowane jako lokalnie dostępne.

## 5. Resume / restart Android Auto

Dodano trwałe `PREF_PLAYBACK_ID`, dzięki czemu zapamiętujemy zarówno surowy `mediaId`, jak i faktyczny identyfikator playbacku.

Zasady resume:

- pozycja wybrana jawnie z `Offline` pozostaje lokalna również po restarcie usługi,
- lokalny fallback z kolejki online jest ponownie wymuszany tylko wtedy, gdy nadal nie ma internetu,
- po powrocie internetu automatyczny fallback nie „przykleja” użytkownika na stałe do lokalnego pliku,
- dla źródła offline nie wykonujemy w tle internetowej hydratacji kolejki,
- Radio zachowuje dotychczasowy osobny mechanizm resume.

## 6. Ustawienia

`Ustawienia -> Android Auto`:

- `Pokaż bibliotekę Offline w Android Auto` — domyślnie **ON**,
- `Automatycznie używaj lokalnej kopii po utracie internetu` — domyślnie **ON**.

Wyłączenie biblioteki wyłącza także możliwość automatycznego fallbacku w runtime.

`Ustawienia -> Diagnostyka`:

- `Etap 9 Offline w Android Auto — główny przełącznik` — techniczny FeatureFlag, domyślnie **ON**.

Raport diagnostyczny pokazuje:

- master flag,
- stan biblioteki AA Offline,
- stan auto-fallback,
- liczbę gotowych lokalnych plików,
- liczbę gotowych/częściowo gotowych playlist.

Zdarzenia Stage 09 są również logowane lokalnie jako `P17-AA-Offline` i mogą znaleźć się w buforze diagnostycznym Stage 01.

## 7. Bezpieczeństwo magazynu

Dodano `OfflineMediaRepository.peekAvailableFile()` / `hasAvailableFile()`.

AA używa ich podczas budowania list, aby samo **przeglądanie** biblioteki nie aktualizowało pola LRU `lastAccess`. Dopiero faktyczne odtworzenie przez `resolveAvailableFile()` oznacza plik jako użyty.

Jeżeli rekord mówi `AVAILABLE`, ale fizycznego pliku już nie ma, istniejący Stage 06 oznacza go jako wygasły zamiast udawać, że jest dostępny.

## 8. Sieć

Etap 09 nie dodaje żadnego nowego endpointu ani serwera.

Do wykrywania stanu połączenia używany jest wyłącznie systemowy `ConnectivityManager`. Do głównego manifestu dodano jawnie normalne uprawnienie:

`android.permission.ACCESS_NETWORK_STATE`

Brak runtime promptu dla użytkownika.

## 9. Czego Stage 09 celowo nie robi

- nie uruchamia pobierania,
- nie zapisuje video — korzysta z audio-only przygotowanego przez Stage 06/07/08,
- nie zapisuje signed URL,
- nie dodaje osobnego serwera,
- nie zmienia Radio/Radio DVR,
- nie zmienia eksperymentalnego AA Video,
- nie dodaje jeszcze wyszukiwania wewnątrz biblioteki Offline w AA,
- nie gwarantuje miniaturek bez internetu — Stage 06 zapisuje URL miniatury, ale nie jej bajty; Android Auto pokaże obraz tylko wtedy, gdy system/loader ma go w cache,
- nie zmienia mechanizmu pobierania Stage 07/08.

## 10. Checklista testów po kompilacji

### Biblioteka

- [ ] Włącz `Ustawienia -> Android Auto -> Pokaż bibliotekę Offline`.
- [ ] W AA widoczny jest folder `Offline`.
- [ ] `Ostatnio zapisane` pokazuje kompletne pliki.
- [ ] `Playlisty offline` pokazują tylko playlisty z gotowymi elementami.
- [ ] `Ulubione offline` pokazują lokalne pozycje oznaczone jako polubione.

### Pełny offline

- [ ] Pobierz playlistę przez Stage 08.
- [ ] Wyłącz Wi‑Fi i dane komórkowe / użyj trybu samolotowego z działającym połączeniem AA.
- [ ] Wejdź `Offline -> Playlisty offline`.
- [ ] Start utworu następuje bez requestu sieciowego.
- [ ] Pause/Play działa.
- [ ] Seek działa na lokalnym pliku.
- [ ] Next/Previous działa.
- [ ] Auto-next przechodzi do kolejnego lokalnego utworu.

### Fallback z kolejki online

- [ ] Przygotuj lokalną kopię utworu, który występuje także w normalnej kolejce online.
- [ ] Uruchom go online.
- [ ] Odłącz internet podczas odtwarzania.
- [ ] Po błędzie strumienia AA przechodzi na lokalny plik możliwie od tej samej pozycji.
- [ ] Jeśli następny element ma kopię lokalną, kolejka kontynuuje.
- [ ] Jeśli kolejny element nie ma kopii, auto-next nie zapętla błędów.

### Resume

- [ ] Uruchom utwór z folderu Offline.
- [ ] Zatrzymaj/ubij proces i ponownie połącz AA bez internetu.
- [ ] Resume używa lokalnego pliku.
- [ ] W kolejce online wymuś fallback lokalny, następnie przywróć internet i zrestartuj usługę.
- [ ] Resume może wrócić do normalnego źródła online — fallback nie pozostaje wymuszony na zawsze.

### Rollback

- [ ] Wyłącz user switch biblioteki Offline — folder znika po odświeżeniu AA.
- [ ] Wyłącz auto-fallback — jawny folder Offline nadal działa, ale zwykłe kolejki online nie przełączają się automatycznie.
- [ ] Wyłącz FeatureFlag Stage 09 w Diagnostyce — integracja Offline AA jest całkowicie zablokowana.

## 11. Logi do zgłoszenia problemu

Szukaj przede wszystkim:

- `P17-AA-Offline`,
- `P13-AA-Playback`,
- `P13-AA-Recovery`,
- `P13-AA-Queue`,
- `P13-AA-Resume`.

Najlepiej dołączyć pełny raport z `Ustawienia -> Diagnostyka -> Kopiuj raport`.
