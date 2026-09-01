V11 - WEB SABR + CONTENT-BOUND POTOKEN

Wydanie:
- SmartTube Mobile 32.04-mobile-p13-aa1.24
- versionCode 2408

Cel:
Usunac przerwanie odtwarzania po poczatkowym buforze. Referencyjny test
ONESIE V8.1 wykazal StreamProtectionStatus 2 -> 3 bez tokenu oraz kompletne
audio/video przy statusie 1 z tokenem powiazanym z videoId.

Diagnoser uzyty do znalezienia przyczyny:
- katalog: D:\APK\_SMARTUBE_\UPDATE\ONESIE-REFERENCE-WINDOWS-V8.1
- uruchomienie: RUN-V8.1-MULTIPATH
- logi: D:\APK\_SMARTUBE_\UPDATE\ONESIE-REFERENCE-WINDOWS-V8.1\logs
- log rozstrzygajacy:
  onesie-reference-EoMSS8rztd0-v81-decipher-multipath-20260901-154757.txt

Jak doszlismy do rozwiazania:
1. Wczesniejsze proby DASH przez ANDROID_VR i iOS odtwarzaly tylko poczatkowy
   bufor, a kolejne zakresy danych konczyly sie HTTP 403.
2. Diagnoser V8.1 potwierdzil poprawne rozwiazywanie parametru n, wiec sam
   decipher nie byl juz brakujacym elementem.
3. Proba SABR bez content PoToken zmieniala StreamProtectionStatus z 2 na 3
   (ATTESTATION_REQUIRED) i zatrzymywala transfer.
4. WEB PoToken wygenerowany dla dokladnego videoId, umieszczony w protobuf
   StreamerContext.poToken, dal status OK (1) i kompletne audio/video.
5. W aplikacji wykrylismy jeszcze dwa ciche fallbacki: niespojny visitorData
   oraz uznawanie prawidlowych, pozbawionych indywidualnych URL-i deskryptorow
   SABR za uszkodzone. Po ich usunieciu aplikacja wybiera WEB/SABR bez
   przechodzenia do ANDROID_VR/DASH.

Zmiany:
- WEB jest pierwszym klientem tylko dla kompletnej sciezki SABR.
- wynik WEB jest przyjmowany, gdy ma adaptiveFormats, serverAbrStreamingUrl,
  videoPlaybackUstreamerConfig i niepusty content PoToken dla videoId.
- gdy token lub transport SABR nie jest dostepny, automatycznym fallbackiem
  pozostaje bezposredni klient iOS/DASH.
- VideoLoader wybiera zweryfikowany SABR przed DASH.
- PoToken jest przenoszony jako bajty w protobuf StreamerContext.poToken.
- sam parametr pot= na zwyklym URL nie jest kryterium sukcesu.
- WEB uzywa wersji 2.20260831.01.00 zweryfikowanej w tescie V8.1.
- naglowek X-Goog-Visitor-Id i kontekst zapytania uzywaja identycznego
  visitorData powiazanego z PoToken; mieszanie go z wartoscia AppService
  powodowalo odpowiedz UNPLAYABLE i cichy fallback do ANDROID_VR/DASH.
- kompletna odpowiedz SABR nie jest oznaczana jako uszkodzona tylko dlatego,
  ze deskryptory adaptiveFormats nie maja osobnych URL; transportem jest wtedy
  wspolny serverAbrStreamingUrl wraz z videoPlaybackUstreamerConfig.

Walidacja docelowa:
- kompilacja stmobileDebug,
- instalacja arm64 na S26 Ultra,
- log V11_SABR_POT selected client=WEB,
- brak StreamProtectionStatus ATTESTATION_REQUIRED podczas odtwarzania.

Walidacja wykonana 2026-09-01 na SM-S948B:
- BUILD SUCCESSFUL (479 zadan), instalacja i start przez opcje 9 helpera,
- V11_SABR_POT selected client=WEB, content PoToken ma 120 znakow,
- film referencyjny EoMSS8rztd0 odtworzony co najmniej do 2:25,
- kolejne odpowiedzi po przekroczeniu minuty: StreamProtectionStatus OK (1),
- brak ATTESTATION_REQUIRED i brak fallbacku do ANDROID_VR/DASH.

Build, instalacja i obsluga telefonu:
- SMARTUBE-GITHUB-HELPER-v1-FIXED-ADB-S26.bat
- opcja 9: build Mobile, instalacja i start na S26 Ultra

Uwaga o logach:
Pelne logi diagnostyczne moga zawierac podpisane adresy i tokeny. Nie nalezy
publikowac ich bez anonimizacji; w repo pozostaje tylko ten opis i bezpieczne
markery/dlugosci tokenow.
