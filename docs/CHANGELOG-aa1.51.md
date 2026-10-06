# aa1.51 (versionCode 2435) — czekanie SABR nie ubija utworu

Po teście na telefonie aa1.51 jest publicznym wydaniem. Music i Video EXP mają osobne pliki na stronie wydań oraz osobne manifesty `updates/music.json` i `updates/video-exp.json`. Wydanie aa1.50 zostaje w historii.

## Co się psuło

Na aa1.50 playlista przy zgaszonym ekranie już szła dalej. Inna usterka zatrzymywała pojedynczy utwór na starcie, zanim poleciał pierwszy dźwięk.

YouTube czasem odsyła samo „poczekaj” (SABR, około 4 sekund) i nie daje jeszcze bajtów. Czwarta taka prośba była traktowana jak twardy błąd odtwarzacza. Zanim te oczekiwania się skończyły, aplikacja po 7 sekundach przechodziła na zapasowy strumień progresywny. Timeout albo kod 403 przed startem był brany za zły adres, więc klient YouTube był zmieniany w kółko. Pozycja zostawała na zerze. Sieć i usługa muzyki w tym czasie działały. To nie była usterka zgaszonego ekranu z aa1.50.

## Co zmieniamy i po co

- Prośba serwera o czekanie resetuje licznik błędów odtwarzacza, więc nie urywa utworu przy czwartej próbie.
- Przez pierwsze takie oczekiwania aplikacja nie ucieka na strumień progresywny i nie zmienia klienta.
- Jeśli progresywny strumień padnie, zanim utwór ruszy, wracamy do SABR. Zmiana klienta jest dopiero po trzeciej takiej porażce tego samego filmu.
- Gdy błąd czekania i tak dotrze do odtwarzacza, utwór jest wznawiany na SABR, bez komunikatu i bez rotacji klienta.
- Zostaje trzymanie usługi, blokady procesora i Wi-Fi przy zgaszonym ekranie z aa1.50.
- Numer wersji muzyki to `32.04-mobile-p13-aa1.51`, versionCode `2435`, żeby instalacja weszła na aa1.50 / 2434 bez odinstalowania. Ten sam klucz podpisu i pakiet `app.smarttube.mobile`. Video EXP to `32.04-mobile-p13-aa1.51-carvideo`, pakiet `app.smarttube.mobile.carvideo`.

Jeśli YouTube w ogóle nie wyśle strumienia, dźwięk nadal może nie wstać. Ta paczka zamyka śmierć na czwartym oczekiwaniu i pętlę klientów.

## Czego ta paczka nie robi

- Nie łączy tego forka z nowszym oficjalnym SmartTube.
- Publiczna strona wydań to aa1.51. Poprzednie aa1.50 zostaje w historii i nie jest nadpisywane.
- Music i Video EXP są dwiema osobnymi paczkami. Każda sprawdza własny manifest.
- Android Auto zostaje dodatkiem. Video EXP nie jest potrzebne do słuchania.

## Jak sprawdzić

W Music nacisnąć „Sprawdź aktualizacje” i wziąć aa1.51. To samo osobno w Video EXP, bo to druga paczka. Potem puścić utwór, który wcześniej stawał na starcie, oraz playlistę przy zgaszonym ekranie. Oczekiwane: po krótkim czekaniu utwór rusza, bez pętli klientów, a kolejny utwór wstaje sam.

## English

A YouTube SABR policy-only backoff no longer becomes a fatal player error on the fourth wait, and the app no longer switches to progressive or rotates clients while that wait is still in its window. Pre-play progressive failures return to SABR; the client changes only after three such failures of the same video. Screen-off playback from aa1.50 stays. versionCode 2435 installs over 2434. Music and Video EXP each have their own manifest and APK on the new aa1.51 GitHub release. The aa1.50 release is left intact. Android Auto remains optional. If YouTube never sends media bytes, playback can still fail later.
