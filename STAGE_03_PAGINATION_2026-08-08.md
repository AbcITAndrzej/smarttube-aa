# SmartTube-AA — Etap 03: paginacja, nieskończone listy i pełny katalog Radia

Data: 2026-08-08
Baza: `smarttube-aa-stage-02-instant-play-2026-08-08`

## Cel etapu

Etap 03 porządkuje dalsze ładowanie danych w nowym mobilnym interfejsie i usuwa historyczny limit 200 stacji z mobilnego katalogu Radia. Zmiany zostały zaprojektowane tak, aby zachować wcześniejszą poprawkę Shorts, nie naruszyć istniejącego playbacku oraz nie zwiększać ryzyka dla stabilnej integracji Android Auto.

## 1. Wspólny koordynator continuation

Dodano `LegacyGroupPaginator` — wspólny, thread-safe koordynator continuation używany przez warstwę Browse/Search/Channel.

SmartTube MediaService nie zwraca jednej płaskiej listy. Wynik jest zbiorem `MediaGroup`, czyli logicznych półek, a każda półka może posiadać własny `nextPageKey`. Z tego powodu nie wprowadzono w tym etapie zależności AndroidX Paging 3 i nie spłaszczono danych do jednego `PagingData`. Takie spłaszczenie zgubiłoby właściciela tokenu continuation i zwiększyłoby ryzyko regresji w Home/Shorts/Channel.

Koordynator:

- zachowuje osobny ciąg stron dla każdej półki,
- wybiera kolejną półkę metodą round-robin,
- wykrywa zakończenie continuation,
- przechowuje strony już pobrane,
- udostępnia wspólny stan `hasMore`,
- działa bez zmiany publicznego modelu MediaService.

`LegacyPagedPayloadMapper` scala strony tej samej logicznej półki i deduplikuje elementy po `MobileMediaItem.id`.

## 2. Browse / Home / Shorts

`LegacyBrowseRepository` został przełączony z prywatnej implementacji continuation na wspólny `LegacyGroupPaginator`.

Najważniejsze: zachowano wcześniejszą naprawę Shorts z pierwszego pakietu. Jeśli token Shorts wygasa, duplikuje się albo MediaService zwraca pusty continuation, aplikacja nadal wykonuje odświeżenie głównego feedu Shorts i dokleja tylko nowe identyfikatory zamiast kończyć przewijanie.

Nie dodano osobnego przełącznika dla Browse, ponieważ nieskończone ładowanie Browse/Shorts istniało już wcześniej i obejmuje krytyczną poprawkę początkową. Etap 03 porządkuje jego implementację, ale nie zamienia go w nową eksperymentalną funkcję.

## 3. Wyszukiwanie — prawdziwe load-more

Rozszerzono:

- `MobileSearchRepository`,
- `LegacySearchRepository`,
- `MobileSearchViewModel`,
- `MobileSearchFragment`,
- `MobileSearchPayload`.

`MobileSearchPayload` ma teraz stan `hasMore`, przy zachowaniu starego konstruktora dla kompatybilności.

Po zbliżeniu się do końca RecyclerView:

1. ViewModel sprawdza, czy trwa już load-more.
2. Repozytorium wybiera półkę z continuation.
3. Wywoływane jest `ContentService.continueGroupObserve()`.
4. Nowa strona jest dokładana do odpowiedniej logicznej półki.
5. Elementy są deduplikowane.
6. UI dostaje nowy payload.
7. DeArrow/original-title metadata enhancer nadal może uzupełnić nowo pobraną stronę.

Jeżeli pierwsza strona jest zbyt krótka, aby RecyclerView dało się przewijać, UI automatycznie prosi o kolejną stronę aż do uzyskania przewijalnej zawartości albo końca continuation.

Repozytorium utrzymuje maksymalnie 8 ostatnich sesji wyszukiwania, aby nie gromadzić bez końca continuation i payloadów w pamięci.

## 4. Kanały — dalsze ładowanie długich sekcji

Analogiczny mechanizm dodano do Channel:

- `MobileChannelRepository.loadMoreChannel()`,
- `LegacyChannelRepository`,
- `MobileChannelViewModel`,
- `MobileChannelFragment`,
- `MobileChannelPayload.hasMore`.

Dotyczy to m.in. długich półek uploadów/playlist kanału posiadających continuation.

Tak samo jak Search, Channel utrzymuje maksymalnie 8 aktywnych sesji continuation i wykonuje deduplikację elementów.

## 5. Radio — usunięcie limitu 200 w mobilnym katalogu

`RadioStationRepository` został przebudowany z jednorazowego katalogu na lazy server-side paging.

Nadal używany jest ten sam serwer Radio Browser, który istniał już w projekcie:

`https://de1.api.radio-browser.info`

Bazowe zapytanie:

`/json/stations/search?hidebroken=true&order=clickcount&reverse=true`

Etap 03 dodaje jedynie parametry:

- `limit`,
- `offset`.

Nie dodano nowego zewnętrznego dostawcy ani nowego endpointu do śledzenia użytkownika.

### Parametry

- strona serwera: 250 rekordów,
- stary tryb po wyłączeniu FeatureFlag: 200 rekordów,
- widoczne okno mobilnego RecyclerView: 120 pozycji,
- kolejne okno lokalne: +120,
- następna strona z serwera jest pobierana dopiero, gdy lokalny cache nie ma już czego pokazać.

Dzięki temu telefon nie próbuje jednocześnie tworzyć tysięcy widoków i nie pobiera całego światowego katalogu podczas pierwszego wejścia.

## 6. Cache pełnego katalogu Radia

Katalog nie jest już przechowywany jako coraz większy JSON w `SharedPreferences`.

Dodano plik aplikacji:

`radio_catalog_v3.ndjson`

Każdy rekord jest osobną linią JSON. Następne strony są dopisywane do pliku, a podczas odtwarzania cache jest deduplikowany po znormalizowanym URL strumienia.

Stary `stations_json` z wcześniejszych paczek jest migrowany automatycznie przy pierwszym uruchomieniu i następnie usuwany z SharedPreferences.

Zapisywany jest również:

- kolejny `offset`,
- informacja, czy osiągnięto koniec katalogu,
- czas ostatniej synchronizacji.

Ulubione pozostają oddzielne i nie są kasowane przez synchronizację katalogu.

## 7. Wyszukiwanie Radia

Wyszukiwarka działa natychmiast na wszystkich stronach katalogu, które są już zapisane lokalnie. Szuka po:

- nazwie,
- kraju,
- kodzie kraju,
- kodeku,
- tagach.

Etap 03 celowo nie pobiera automatycznie całego światowego katalogu tylko dlatego, że użytkownik wpisał rzadkie hasło. Zapobiega to przypadkowemu pobraniu bardzo dużej liczby stron podczas jednego wyszukiwania. Kolejne strony globalnego katalogu są budowane progresywnie podczas przewijania listy.

## 8. Android Auto — świadoma izolacja

Stabilny `SmartTubeAutoMusicService` nie został przebudowany na ogromną listę wszystkich stacji.

Android Auto korzysta z `RadioStationRepository.getStationsForAutomotive()` i otrzymuje maksymalnie 200 najpopularniejszych pozycji z aktualnie zsynchronizowanego cache.

To jest świadome ograniczenie bezpieczeństwa: przesłanie tysięcy `MediaBrowserCompat.MediaItem` jednorazowo może prowadzić do dużych transakcji Binder i pogorszyć stabilną integrację AA. Pełne foldery/paginacja Radia w AA należą do planowanego Etapu 05 „Radio 2.0”.

Ulubione i odtwarzanie stacji w AA nadal korzystają ze wspólnego repozytorium.

## 9. FeatureFlags — wszystko można wyłączyć

W `Ustawienia -> Diagnostyka -> Stage 3: infinite lists` dodano trzy przełączniki, domyślnie włączone:

- paginacja wyszukiwania,
- paginacja kanałów,
- pełny paginowany katalog Radia.

Odpowiadają im flagi:

- `paging_search`,
- `paging_channel`,
- `paging_radio_catalog`.

Po wyłączeniu paginacji Radia repozytorium wraca do historycznego limitu pierwszych 200 stacji.

## 10. Diagnostyka

Stage 01 został rozszerzony o:

- liczbę żądań continuation,
- liczbę błędów continuation,
- ostatnią powierzchnię paginacji (`search`, `channel`, `radio`),
- licznik elementów przed/po stronie,
- aktualny `hasMore`,
- ostatni błąd paginacji,
- liczbę pobranych stron Radia,
- liczbę stacji w cache,
- następny offset Radio Browser,
- stan końca katalogu.

Dzięki temu błędy „lista przestała się ładować” można diagnozować z raportu użytkownika bez zgadywania.

## 11. Zachowanie wcześniejszych zmian

Stage 03 jest paczką kumulatywną. Nadal zawiera m.in.:

- naprawę infinite Shorts/dead continuation,
- poprawkę seekbara przy zmianach jakości,
- zoom + tap/touchSlop,
- globalne Radio,
- poprawione Trending / Nowe rekomendacje,
- recovery przejściowych 403,
- ustawienia Playera,
- preferowane audio/napisy,
- Radio Search/Favorites/DVR,
- eksperymentalne AA Video,
- SponsorBlock/DeArrow dla nowego mobile UI,
- Stage 01 Diagnostykę/FeatureFlags,
- Stage 02 Instant Play.

## 12. Checklista testów na urządzeniu

### Browse / Shorts

- [ ] Home przewija się dalej tak jak przed Stage 03.
- [ ] Shorts przewijają się przez wiele stron.
- [ ] Po dłuższym czasie / powrocie do Shorts wygasły continuation nie blokuje dalszego feedu.
- [ ] Nie pojawiają się duplikaty tych samych ID podczas kolejnych stron.

### Search

- [ ] Wyszukaj popularne hasło z dużą liczbą wyników.
- [ ] Przewiń do końca pierwszej strony.
- [ ] Kolejne wyniki pojawiają się bez ponownego wpisywania zapytania.
- [ ] DeArrow/original titles działają także dla doładowanych wyników.
- [ ] Szybkie przewijanie nie uruchamia wielu równoległych continuation.
- [ ] Wyłączenie `paging_search` zatrzymuje dalsze strony bez crasha.

### Channel

- [ ] Otwórz kanał z dużą liczbą filmów.
- [ ] Przewijanie do dołu doładowuje kolejne dane.
- [ ] Nie ma powielonych kart.
- [ ] Wyłączenie `paging_channel` pozostawia pierwszą stronę działającą.

### Radio

- [ ] Po synchronizacji pojawia się pierwsza strona katalogu.
- [ ] Przewijanie do końca pobiera kolejne strony.
- [ ] Liczba `w cache` rośnie ponad 200.
- [ ] Zamknięcie i ponowne uruchomienie aplikacji zachowuje pobrane strony.
- [ ] Ulubione pozostają po synchronizacji i restarcie.
- [ ] Wyszukiwanie działa na już zapisanym katalogu.
- [ ] Wyłączenie pełnego katalogu ogranicza nową synchronizację do 200.
- [ ] Ponowne włączenie pozwala kontynuować od istniejącego offsetu.
- [ ] Android Auto nadal pokazuje stabilną, ograniczoną listę i odtwarza Radio.

### Diagnostyka

- [ ] Raport pokazuje `Paging` i `Radio catalog`.
- [ ] Po przewijaniu Search/Channel/Radio rosną liczniki continuation/pages.
- [ ] Błąd sieciowy strony Radia pojawia się w `last paging error`.

## 13. Ważna uwaga o Paging 3

Pierwotny plan używał określenia „Paging 3” jako kierunku. Po analizie rzeczywistego projektu Stage 03 realizuje cele paginacji przez istniejący model continuation SmartTube zamiast dodawać AndroidX Paging 3.

Powód jest praktyczny: obecny ContentService zwraca wielopółkowe `MediaGroup` z tokenami przypisanymi do konkretnych grup. Wprowadzenie AndroidX Paging 3 w tym miejscu wymagałoby dodatkowej warstwy spłaszczającej i większej migracji adapterów/ViewModeli. To nie dawałoby użytkownikowi lepszego efektu na tym etapie, a zwiększałoby ryzyko cofnięcia wcześniejszej poprawki Shorts.

AndroidX Paging 3 można rozważyć później przy szerszej modernizacji architektury, np. razem z Media3, jeśli adaptery zostaną przebudowane w kontrolowany sposób.
