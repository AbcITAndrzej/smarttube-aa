# SmartTube AA Music i SmartTube AA Video EXP

Projekt zawiera jeden wspolny kod zrodlowy, ale tworzy dwie osobno instalowane aplikacje.

## SmartTube AA Music

- Pakiet: `app.smarttube.mobile`
- Wariant Gradle: `StmobileDebug`
- Przeznaczenie: muzyka YouTube, playlisty, radio internetowe i biblioteka offline.
- Android Auto tworzy bezpieczny interfejs na podstawie `SmartTubeAutoMusicService`.
- Jest to podstawowa i zalecana wersja projektu.

## SmartTube AA Video EXP

- Pakiet: `app.smarttube.mobile.carvideo`
- Wariant Gradle: `StmobileCarvideo`
- Przeznaczenie: eksperymentalny ekran aplikacji na wyswietlaczu Android Auto podczas postoju.
- Nie zawiera `SmartTubeAutoMusicService`; muzyka i radio pozostaja w aplikacji Music.
- Jest instalowana obok Music i ma osobne dane aplikacji.
- Funkcja zalezy od wersji Androida, Android Auto i polityki danego urzadzenia.

Video EXP nie jest przeznaczone do ogladania podczas jazdy. System samochodu moze je zamknac
lub zablokowac po rozpoczeciu jazdy.

## Ktory APK pobrac

- Zwykly uzytkownik: `SmartTube-AA-Music_*_universal.apk`.
- Telefon arm64, mniejszy plik: `SmartTube-AA-Music_*_arm64-v8a.apk`.
- Dodatkowy test obrazu: odpowiedni `SmartTube-AA-Video-EXP_*`.

Wersje Music i Video sa budowane z tego samego commita oraz publikowane w jednym GitHub
Release. Dzieki temu maja zgodna baze kodu i numer wydania.
