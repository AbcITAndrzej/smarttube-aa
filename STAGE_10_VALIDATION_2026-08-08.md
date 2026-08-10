# Stage 10 — raport walidacji

Data: 2026-08-08
Baza porównawcza: Stage 09 Offline Android Auto

## Wykonane kontrole

- parsowanie wszystkich XML-i wariantu `stmobile`,
- kontrola duplikatów nazw zasobów EN/PL,
- kontrola nowych referencji Stage 10 do zasobów,
- kontrola równowagi nawiasów/komentarzy/łańcuchów w zmienionych plikach Java,
- kontrola manifestu i deklaracji foreground `dataSync`,
- kontrola braku zahardkodowanych nowych `http://`/`https://` w klasach Trip Reserve,
- kontrola, że baza Stage 10 nie zapisuje signed/stream URL,
- kontrola izolacji syntetycznych ID `trip:*` od ręcznych ID playlist,
- kontrola współdzielenia Stage 08 downloadera zamiast tworzenia nowego transportu bajtów,
- kontrola, że Android Auto nadal korzysta ze stabilnej `SmartTubeAutoMusicService`,
- próba uruchomienia Gradle wrappera,
- test zastosowania patcha na czystym Stage 09 oraz porównanie 1:1,
- test integralności końcowego ZIP-a.

## Wyniki

- XML `stmobile`: **75/75** poprawnie sparsowanych.
- Duplikaty nazw w `values/mobile_native_strings.xml`: **0**.
- Duplikaty nazw w `values-pl/mobile_native_strings.xml`: **0**.
- Różnica EN/PL jest wyłącznie wcześniejszym `aa_app_name` dostępnym po angielsku; wszystkie nowe stringi Stage 10 istnieją w obu wersjach.
- Zmienione/dodane pliki względem Stage 09: **25**.
- Patch round-trip: `patch -p1` na czystym Stage 09 zakończył się kodem **0**, a `diff -qr` z drzewem Stage 10 nie wykazał różnic.
- Kontrola whitespace `git diff --no-index --check`: brak komunikatów o whitespace errors (kod 1 wynika wyłącznie z oczekiwanej różnicy drzew).
- Smoke test syntetycznych ID Trip Reserve: **STAGE10_ID_SMOKE_OK**.
- Smoke test nowych preferencji/defaultów/clampingu: **STAGE10_PREFS_SMOKE_OK**.
- W klasach `OfflineTripReserve*.java` brak nowych zahardkodowanych `http://`/`https://`.
- W bazie historii Stage 10 brak pola signed URL/stream URL.
- Gradle wrapper został uruchomiony, ale nie doszedł do fazy kompilacji z powodu braku lokalnej dystrybucji Gradle 7.5 i `UnknownHostException: services.gradle.org`.
- Końcowy ZIP i SHA-256 są weryfikowane po utworzeniu paczki; wynik znajduje się również w odpowiedzi wydania.

## Ograniczenie środowiska

Pełne `:smarttubetv:testStmobileDebugUnitTest` / `assembleStmobileDebug` nie może wystartować w tym środowisku, ponieważ wrapper nie ma lokalnej dystrybucji Gradle 7.5 i próbuje pobrać `https://services.gradle.org/distributions/gradle-7.5-bin.zip`; host jest niedostępny (`UnknownHostException`). Nie jest to błąd kodu Stage 10, ale oznacza, że finalną kompilację Android należy wykonać w środowisku użytkownika z Android SDK i Gradle 7.5.
