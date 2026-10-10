# WaveAA — YouTube playlists, music & internet radio for Android Auto

[![Website](https://img.shields.io/badge/Website-waveaa.com-10b5c9?style=flat-square)](https://waveaa.com/en/)
[![Latest release](https://img.shields.io/github/v/release/AbcITAndrzej/smarttube-aa?display_name=tag&style=flat-square)](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest)
[![License](https://img.shields.io/github/license/AbcITAndrzej/smarttube-aa?style=flat-square)](LICENSE)

**WaveAA** is a free, open-source, unofficial [SmartTube](https://github.com/yuliskov/SmartTube) fork for Android phones. Use YouTube playlists, music and internet radio on your phone; optionally listen using Android Auto. No subscription is required by WaveAA.

**Official website:** https://waveaa.com/en/ · **Polish:** https://waveaa.com/pl/ · **Latest downloads:** https://github.com/AbcITAndrzej/smarttube-aa/releases/latest

> **Driving safety:** WaveAA Music provides audio features for Android Auto. WaveAA Video EXP is a **separate experimental app for parked use only**. Do not watch video while driving. Car/Android Auto restrictions may prevent Video EXP from appearing.

## Download

The project publishes two separate APK variants for **arm64-v8a** phones:

| App | Purpose | Package | Download |
| --- | --- | --- | --- |
| **WaveAA Music** (recommended) | Phone player and optional Android Auto audio, including YouTube playlists, internet radio and offline library | `app.smarttube.mobile` | [Latest Music APK / release](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest) |
| **WaveAA Video EXP** (optional) | Experimental video on the car screen **while parked**; not needed for audio | `app.smarttube.mobile.carvideo` | [Latest Video EXP APK / release](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest) |

**Always download from this repository's official [Releases](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest).** Check the filename to choose Music or Video EXP. APKs are not distributed via Google Play by this repository.

## Features

- **YouTube playlists & music:** Sign in where supported, organize playlists and play on the phone.
- **Internet Radio 2.0:** Search stations; browse favorites, recently played, countries and genres; stream failover where available.
- **Offline audio:** Download playlists, resume interrupted transfers, and listen to saved audio during connectivity gaps.
- **Android Auto audio (optional):** Access playlists, radio and offline library from a compatible Android Auto media interface.
- **Phone player:** Background playback, screen-off listening, sleep timer and playback controls.
- **Video EXP (separate app):** Experimental parked-only display mode; subject to Android and vehicle restrictions.

Some features depend on the Android version, phone, car and availability of third-party services. WaveAA is not affiliated with or endorsed by Google, YouTube or Android Auto.

## Android Auto setup (Music only)

1. Install the **Music** variant from [Releases](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest).
2. In WaveAA open **Settings → Android Auto → Open Android Auto settings**.
3. Enable Android Auto developer settings (tap **Version and permission info** repeatedly, typically 10 times).
4. In Android Auto developer settings, enable **Unknown sources**. Only install APKs you trust.
5. Return to WaveAA, confirm the protected steps and select **Add / check**.
6. Reconnect the phone to the car, or restart Android Auto.

These settings cannot be enabled automatically by a third-party app. For detailed instructions see [Android Auto documentation](docs/ANDROID-AUTO.md), the [English installation guide](https://waveaa.com/en/install/) or the [Polish guide](https://waveaa.com/pl/install/). The **Music** app also works on the phone without Android Auto.

## Guides

- [Android Auto music & YouTube playlists](https://waveaa.com/en/android-auto-music/) · [PL](https://waveaa.com/pl/android-auto-music/)
- [Internet radio in Android Auto](https://waveaa.com/en/internet-radio/) · [PL](https://waveaa.com/pl/internet-radio/)
- [Offline music and playlists](https://waveaa.com/en/offline-playlists/) · [PL](https://waveaa.com/pl/offline-playlists/)
- [Installation & frequently asked questions](https://waveaa.com/en/install/) · [PL](https://waveaa.com/pl/install/)

## Contribute & report issues

This is a community open-source project. Use [Issues](https://github.com/AbcITAndrzej/smarttube-aa/issues) for reproducible bugs, feature requests and compatibility reports. When reporting a bug, include the **release tag**, Android version, phone model, car/Android Auto version (if relevant), expected behavior and actual behavior. **Never post authentication tokens, private playlists or personal logs** in public issues.

To follow updates, **Star** this repository or **Watch → Releases**. Check the [Releases page](https://github.com/AbcITAndrzej/smarttube-aa/releases) for the authoritative latest version (the README intentionally does not hardcode release numbers).

## License & upstream credits

WaveAA is an unofficial fork based on [SmartTube by yuliskov](https://github.com/yuliskov/SmartTube). It preserves attribution and license notices; see [LICENSE](LICENSE). No affiliation with Google, YouTube or Android Auto is implied.

---

## 🇵🇱 WaveAA — po polsku

**WaveAA** to darmowa aplikacja open source na telefon z Androidem: playlisty YouTube, muzyka, radio internetowe i odtwarzanie offline. **Android Auto jest opcjonalne** — aplikacja działa również bez samochodu.

- **[Pobierz najnowszą wersję (Music / Video EXP)](https://github.com/AbcITAndrzej/smarttube-aa/releases/latest)** — wybierz odpowiedni APK `arm64-v8a`.
- **[Strona projektu po polsku](https://waveaa.com/pl/)** · **[Instrukcja instalacji](https://waveaa.com/pl/install/)**
- **Music** służy do muzyki na telefonie i opcjonalnie do słuchania w Android Auto.
- **Video EXP** to odrębny eksperymentalny wariant do używania **wyłącznie podczas postoju**. Nie należy oglądać filmów w czasie jazdy.
- Nie musisz instalować obu aplikacji. Do zwykłego słuchania wystarczy **Music**.
- Błędy i propozycje rozwoju zgłaszaj przez [GitHub Issues](https://github.com/AbcITAndrzej/smarttube-aa/issues).

WaveAA jest nieoficjalnym forkiem [SmartTube](https://github.com/yuliskov/SmartTube). Projekt nie jest powiązany z Google, YouTube ani Android Auto.
