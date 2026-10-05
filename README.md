# SmartTube AA

SmartTube AA to nieoficjalny fork [SmartTube](https://github.com/yuliskov/SmartTube) na telefon. Służy do muzyki, playlist, radia i filmów z YouTube. Działa samodzielnie. Android Auto jest dodatkiem: kto nie chce samochodu, w ogóle go nie włącza.

This is an unofficial phone fork of SmartTube for YouTube music, playlists, radio and video. Android Auto is optional.

Projekt nie jest aplikacją Google ani YouTube.

## Aktualna wersja: aa1.50

Wersja stabilna **aa1.50**, versionCode **2434**, architektura **arm64-v8a**. To ta wersja, którą widać na stronie wydań.

Są dwie osobne aplikacje, bo tak wymaga Android: muzyka w samochodzie i eksperymentalny obraz nie mogą być tym samym pakietem. Można zainstalować jedną albo obie. Mają osobne ikony, osobne dane i osobny przycisk „Sprawdź aktualizacje”.

| | Music | Video EXP |
| --- | --- | --- |
| Do czego | Telefon oraz, opcjonalnie, muzyka w Android Auto | Tylko wtedy, gdy chcesz eksperymentalny obraz na ekranie samochodu podczas postoju |
| Pakiet | `app.smarttube.mobile` | `app.smarttube.mobile.carvideo` |
| Plik | [SmartTube AA Music aa1.50](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/aa1.50/SmartTube-AA-Music-aa1.50-arm64-v8a.apk) | [SmartTube AA Video EXP aa1.50](https://github.com/AbcITAndrzej/smarttube-aa/releases/download/aa1.50/SmartTube-AA-Video-EXP-aa1.50-arm64-v8a.apk) |

Większości osób wystarczy **Music**. Video EXP nie jest potrzebne do słuchania.

Oba pliki są podpisane tym samym certyfikatem co wcześniejsze wydania SmartTube AA, więc wchodzą na poprzednią wersję tego samego wariantu bez odinstalowania. Strona wydania: [aa1.50](https://github.com/AbcITAndrzej/smarttube-aa/releases/tag/aa1.50).

## Na telefonie, bez samochodu

Po instalacji Music otwiera się jak zwykła aplikacja. Można się zalogować do YouTube, słuchać playlist i radia, oglądać filmy na telefonie i sterować odtwarzaniem z ekranu blokady. Zgaszony ekran nie powinien przerywać playlisty.

Android Auto można pominąć. Nic w telefonie od tego nie zależy.

## Android Auto, jeśli chcesz

Ta część jest opcjonalna. Dotyczy wariantu **Music**, czyli dźwięku: playlisty, radio i biblioteka offline. Obraz w samochodzie to osobna, nieobowiązkowa aplikacja Video EXP i tylko na postoju. Samochód może ten obraz ograniczyć albo wyłączyć. Nie służy do oglądania w trakcie jazdy.

1. Zainstaluj Music. Video EXP dołóż tylko wtedy, gdy naprawdę chcesz obraz na postoju.
2. W aplikacji otwórz `Ustawienia → Android Auto`.
3. Wybierz `Otwórz ustawienia Android Auto`.
4. W Android Auto stuknij 10 razy `Wersja i informacje o uprawnieniach`, aż włączy się tryb programisty.
5. Wejdź w `Ustawienia programisty` i włącz `Nieznane źródła`.
6. Wróć do SmartTube, potwierdź oba kroki i wybierz `Dodaj / sprawdź`.
7. Rozłącz i połącz telefon z samochodem jeszcze raz.

Android nie pozwala aplikacji włączyć tych dwóch przełączników za użytkownika. Ekran w SmartTube tylko otwiera właściwe ustawienia i sprawdza, czy usługa muzyki jest widoczna.

Przełącznik `Włącz SmartTube w Android Auto` wyłącza wyłącznie samochód. Odtwarzanie w telefonie i konto YouTube zostają. Szerszy opis playlist, radia i trybu offline jest w [instrukcji Android Auto](docs/ANDROID-AUTO.md).

## Co jest w aa1.50

- Playlista gra dalej przy zgaszonym ekranie, także przy przejściu do kolejnego utworu.
- Krótka przerwa sieci nie gasi odtwarzania i nie zasypuje komunikatami.
- Zostają konto YouTube, radio, ekran blokady, napisy i wybór ścieżki audio, jeśli YouTube ją udostępnia.
- Music i Video EXP aktualizują się osobno przyciskiem „Sprawdź aktualizacje”.

## Pochodzenie

Projekt bazuje na otwartym kodzie SmartTube i zachowuje informacje o licencji oraz autorach.
