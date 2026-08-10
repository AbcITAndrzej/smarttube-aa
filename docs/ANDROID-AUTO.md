# Android Auto w SmartTube AA

## Pierwsze uruchomienie

1. Zainstaluj APK SmartTube AA na telefonie.
2. Otwórz w aplikacji `Ustawienia → Android Auto`.
3. Wybierz `Otwórz ustawienia Android Auto`.
4. Naciśnij 10 razy pozycję `Wersja i informacje o uprawnieniach`, aby włączyć tryb programisty Android Auto.
5. Z menu Android Auto otwórz `Ustawienia programisty` i włącz `Nieznane źródła`.
6. Wróć do SmartTube, potwierdź wykonanie obu chronionych kroków i wybierz `Dodaj / sprawdź`.
7. Połącz ponownie telefon z samochodem albo uruchom ponownie Android Auto.

Android nie pozwala aplikacji samodzielnie włączyć trybu programisty ani opcji
`Nieznane źródła`. Ekran SmartTube może otworzyć właściwe ustawienia, sprawdzić
obecność Android Auto i własnej usługi multimedialnej oraz przeprowadzić użytkownika
przez pozostałe kroki.

## Układ playlist

W sekcji `Układ playlist Android Auto` można:

- zmieniać kolejność playlist przyciskami `Przenieś wyżej` i `Przenieś niżej`;
- ukrywać wybrane playlisty w Android Auto;
- odświeżyć listę z konta YouTube;
- przywrócić domyślny układ.

Te ustawienia są zapisywane wyłącznie lokalnie na telefonie. Nie usuwają, nie
przenoszą i nie modyfikują playlist na koncie YouTube. Nowe playlisty znalezione
później są automatycznie dodawane na końcu lokalnego układu.

## Radio

Sekcja `Radio` korzysta z katalogu Radio Browser zsynchronizowanego w aplikacji
mobilnej. Ulubione stacje są zapisywane lokalnie na telefonie i pojawiają się również
w Android Auto. Przy błędzie synchronizacji HTTP 503 spróbuj ponownie albo użyj
innego połączenia internetowego.

## Offline

Po włączeniu `Pokaż bibliotekę Offline w Android Auto` dostępne są sekcje
`Ostatnio zapisane`, `Playlisty offline` i `Ulubione offline`. Android Auto odtwarza
tylko ukończone lokalne pliki audio; samo otwarcie sekcji nie uruchamia pobierania.

Sposoby przygotowania plików opisuje [krótka instrukcja użytkownika](INSTRUKCJA-UZYTKOWNIKA.md#jak-działa-offline).

## Wyłączenie dostępu

Wyłączenie przełącznika `Włącz SmartTube w Android Auto` dezaktywuje wyłącznie
usługę multimedialną SmartTube AA. Odtwarzanie w telefonie i dane konta YouTube
pozostają bez zmian. Po ponownym włączeniu może być konieczne ponowne połączenie
telefonu z Android Auto.

## Dokumentacja Androida

- [Testowanie aplikacji na Android Auto](https://developer.android.com/training/cars/testing)
- [Obsługa Android Auto w aplikacji multimedialnej](https://developer.android.com/training/cars/media/auto)
- [Usługa MediaBrowserService](https://developer.android.com/training/cars/media/create-media-browser)
