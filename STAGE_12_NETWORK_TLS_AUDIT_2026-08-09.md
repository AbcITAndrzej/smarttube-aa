# Stage 12 — audyt wspólnej warstwy sieci / TLS

Data: 2026-08-09

Ten dokument opisuje zastany kod sieciowy projektu. Stage 12 nie zmienia zachowania TLS w produkcji, ponieważ wspólna warstwa jest używana także przez inne flavor/modules i wymaga testów kompatybilności na rzeczywistych starych urządzeniach.

## 1. OkHttp — parametry wspólne

Plik:

`SharedModules/sharedutils/src/main/java/com/liskovsoft/sharedutils/okhttp/OkHttpCommons.java`

Zastane ustawienia:

- connect timeout: 20 s,
- read timeout: 20 s,
- write timeout: 20 s,
- connection pool: 20 połączeń / 5 min,
- wymuszenie HTTP/1.1 przez `fixStreamResetError()`,
- własna lista cipher suites / TLS compatibility,
- dodatkowy `UnzippingInterceptor`,
- profiler i BODY logger tylko gdy `BuildConfig.DEBUG`.

Stage 12 nie zmienia tych parametrów globalnie, ponieważ wpływają na YouTube, API, aktualizacje, SponsorBlock/DeArrow i inne istniejące usługi.

## 2. Krytyczne historyczne obejście certyfikatów API <=24

`OkHttpCommons.configureToIgnoreCertificate()` jest wywoływane przez wspólny `setupBuilder()`.

Dla `SDK_INT <= 24` funkcja tworzy `X509TrustManager`, którego `checkServerTrusted()` i `checkClientTrusted()` są puste, a następnie ustawia zbudowany z niego `SSLSocketFactory`.

Hostname verifier nie jest w tej metodzie globalnie ustawiony na `true`, ale **walidacja łańcucha certyfikatów jest osłabiona** na Androidzie 7.0 i starszym.

To jest ryzyko bezpieczeństwa i techniczny dług odziedziczony po projekcie, nie funkcja dodana w naszej roadmapie.

### Dlaczego Stage 12 nie usuwa tego automatycznie

- `stmobile` nadal ma `minSdk 21`,
- kod jest wspólny dla innych flavorów/urządzeń TV,
- komentarze wskazują, że obejście powstało z powodu realnych problemów certyfikatów/aktualizacji na starych urządzeniach,
- usunięcie bez macierzy testów mogłoby odciąć sieć części użytkowników.

### Rekomendowany osobny etap security-hardening

1. dodać przełączalną ścieżkę z systemowym/default `X509TrustManager`,
2. przetestować Android 5.0/5.1/6.0/7.0/7.1,
3. sprawdzić YouTube, GitHub update, SponsorBlock, DeArrow, Radio Browser i wszystkie API,
4. jeżeli potrzebna jest kompatybilność TLS — zastosować Conscrypt/system trust, a nie trust-all,
5. dopiero wtedy usunąć `configureToIgnoreCertificate()`.

## 3. DoH URLConnection

Plik:

`SharedModules/sharedutils/src/main/java/com/liskovsoft/sharedutils/helpers/NetworkHelpers.kt`

`getDohURLConnection()` może rozwiązać host przez Google DNS i połączyć się przez adres IP. Dla tej ścieżki ustawia:

`conn.hostnameVerifier = HostnameVerifier { _, _ -> true }`

Komentarz w kodzie wskazuje, że jest to obejście `SSLPeerUnverifiedException`, ponieważ URL zawiera IP zamiast oryginalnego hostname.

To również powinno zostać objęte osobnym security-hardeningiem. Stage 12 nie zmienia go bez potwierdzenia, które istniejące wywołania nadal polegają na tej ścieżce.

## 4. HTTP/1.1

`fixStreamResetError()` wymusza wyłącznie `Protocol.HTTP_1_1` dla wspólnego klienta. Może to ograniczać korzyści multiplexingu HTTP/2, ale komentarze projektu wskazują wcześniejsze problemy `StreamResetException` przy częstym przerywaniu/tworzeniu streamów.

W Stage 12 nie przełączono globalnie HTTP/2. Taki eksperyment powinien być mierzony osobnym benchmarkiem i testowany pod kątem stream start/retry/403, a nie wdrażany razem z Baseline Profile.

## 5. Debug logging

`OkHttpProfilerInterceptor` i `HttpLoggingInterceptor.Level.BODY` są dodawane tylko pod `BuildConfig.DEBUG`. To może istotnie obciążać lokalne debug buildy i zniekształcać pomiary sieci.

Do porównywania wydajności należy używać build type `benchmark`/release-like, nie zwykłego debug z profilerem HTTP.

## 6. SponsorBlock i DeArrow po Stage 12

Stage 12 nie zmienia hostów. Dodaje tylko cache i single-flight, więc przy wielokrotnych żądaniach tych samych danych liczba requestów może spaść.

Diagnostyka pokazuje hit/miss/in-flight/join dla obu usług, dzięki czemu efekt można sprawdzić na urządzeniu.

## 7. Wniosek

Najważniejszą rzeczą do przyszłej poprawy bezpieczeństwa jest eliminacja trust-all dla API <=24 i walidacja DoH/IP-hostname. Nie połączyliśmy tego z Stage 12, ponieważ byłaby to zmiana o wysokim ryzyku regresji sieciowej w całym projekcie, a nie zwykła optymalizacja mobilnego UI.
