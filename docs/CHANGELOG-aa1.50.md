# aa1.50 (versionCode 2434) — odtwarzanie przy zgaszonym ekranie

Po potwierdzeniu na telefonie aa1.50 jest publicznym wydaniem. Music i Video EXP mają osobne pliki na stronie wydań oraz osobne manifesty `updates/music.json` i `updates/video-exp.json`.

## Co się psuło

Na telefonie, przy zgaszonym ekranie i playliście, muzyka miała dwie usterki. W Android Auto ich nie było.

1. Po około dwóch minutach utworu dźwięk urywał się na dłużej. Odtwarzacz na chwilę przechodził w stan bezczynności (brak przygotowanego utworu). Usługa muzyki była wtedy gaszona. Android 16 nie pozwala uruchomić takiej usługi ponownie z tła, więc proces tracił pierwszeństwo i radio Wi-Fi zasypiało.
2. Kolejny utwór z playlisty był wybierany, ale strumień nie wstawał, dopóki ekran nie został włączony i nie wciśnięto play. W tym samym oknie aplikacja co sekundę ponawiała pobieranie informacji o formacie i pokazywała komunikat o braku sieci.

Sam brak sieci nie był pierwszą przyczyną. Najpierw znikała usługa, potem radio.

## Co zmieniamy i po co

- Usługa muzyki zostaje na czas grania, krótkiej przerwy sieciowej i zmiany utworu. Gaśnie przy pauzie, zatrzymaniu albo końcu kolejki. Dzięki temu nie trzeba jej odpalać drugi raz przy zgaszonym ekranie.
- Na czas tej usługi trzymana jest blokada procesora (do 4 godzin, jako bezpiecznik) oraz blokada Wi-Fi w trybie wysokiej wydajności. Sama blokada procesora nie utrzymuje radia. Obie są zwalniane razem z usługą.
- Jeśli Android i tak odrzuci start usługi z tła, kolejne próby są co 5 sekund, a nie kilkaset razy na minutę.
- Gdy naprawdę nie ma sieci (nie da się rozwiązać nazwy, timeout albo brak trasy), informacja o formacie jest ponawiana co 5 sekund i bez wyskakującego komunikatu. Inne błędy formatu działają jak wcześniej.
- W powiadomieniu w trakcie takiej przerwy zostaje przycisk pauzy, a powiadomienia nie da się przypadkiem zrzucić.
- Numer wersji muzyki to `32.04-mobile-p13-aa1.50`, versionCode `2434`, żeby instalacja weszła na obecną aa1.49 / 2433 bez odinstalowania. Ten sam klucz podpisu i pakiet `app.smarttube.mobile`.

Android Auto nie dostaje drugiej sesji multimediów. Ta poprawka dotyczy odtwarzacza w telefonie.

## Czego ta paczka nie robi

- Nie łączy tego forka z nowszym oficjalnym SmartTube.
- Publiczna strona wydań to aa1.50. Poprzednie aa1.49 zostaje w historii.
- Music i Video EXP są dwiema osobnymi paczkami. Każda sprawdza własny manifest.

## Jak sprawdzić

W Music nacisnąć „Sprawdź aktualizacje” i wziąć aa1.50. To samo osobno w Video EXP, bo to druga paczka. Potem puścić tę samą playlistę przy zgaszonym ekranie i nie włączać telefonu przez kilka utworów. Oczekiwane: brak wielominutowej ciszy i samodzielne przejście do kolejnego utworu. Krótki przystanek przy twardym błędzie strumienia nadal może się zdarzyć. Jeśli usterka wróci, zebrać log tak jak poprzednio.

## English

Phone playback dropped its foreground service on ExoPlayer IDLE (network timeout or the gap before the next playlist item). Android 16 then refused to start that service again with the screen off, the Wi-Fi radio slept, and format info was reloaded every second. aa1.50 keeps the service, a partial wake lock and a Wi-Fi lock until pause or the end of the queue, and slows real network retries to 5 seconds without a toast. versionCode 2434 installs over 2433. Music and Video EXP each have their own manifest and APK on the aa1.50 GitHub release. Android Auto remains optional.
