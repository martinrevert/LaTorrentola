# La Torrentola 🎬

**La Torrentola** is a modern Android application for exploring high-definition movie and TV series catalogs, discovering new releases and episodes, watching movie trailers, and managing a personal media library with cloud synchronization through your Google account.

Built for phones, tablets, foldables, and **Android TV / Chromecast**, the app features a *Frosted Glass* UI aesthetic (powered by Haze 2.0), native D-pad remote navigation, and direct integration with torrent managers on mobile or remote NAS home servers (such as Transdrone, Synology DS get, or local downloaders).

---

## 🌟 Key Features

### 📱 For Users
* **Catalog & Quality Filters**: Browse movies sorted by video quality (**4K 2160p**, **1080p x265**, **1080p**, **720p**) and genres (Action, Sci-Fi, Comedy, Drama, New Releases, and Previously Watched).
* **TV Series Catalog**: Switch Home between Movies and TV to browse TMDB series by airing status, popularity, rating, or genre. Open seasons and episodes, find releases through EZTV, and use the configured torrent mode.
* **Google Cloud Library Sync**: Sign in with your Google account to back up and automatically synchronize your favorite movies and watch history across all your devices.
* **TV Episode Download History**: TV episode torrent selections are recorded separately from movie downloads and synced to the signed-in user's cloud library.
* **Integrated Trailer Player**: Watch official YouTube HD trailers directly inside the app without ads or pop-ups.
* **On-Device Spanish Translation**: Instant, automatic summary translation powered by local on-device AI—no additional mobile data or cloud costs.
* **Language Exclusion Filter**: Automatically hide movies in languages you prefer not to watch.
* **100% Android TV & Remote Ready**: Smooth navigation engineered for TV remotes with responsive card scaling, high-contrast focus indicators, and D-pad shortcuts.
* **Torrent handling**: Choose external-client handoff, in-app download with local playback, or in-app download with Chromecast from Settings. In-app playback starts after a contiguous startup buffer has been verified by libtorrent4j; later HTTP byte ranges are served only after every overlapping torrent piece passes its hash check. Completed downloads remain in the app's download manager until deleted.
* **Subtitles**: Local playback and Cast playback can search and load subtitles through OpenSubtitles when an API key is configured and the user has saved their account credentials in Settings.

---

## 💡 Quick User Guide

1. **Explore the Catalog**: Scroll through the main home screen to discover popular movies and new releases. Use the top chips to filter by genre or resolution.
2. **Browse TV Series**: Select **TV** on Home, choose a feed or genre, then open a series to select a season and episode.
3. **Find an Episode Release**: Open an episode to see matching EZTV releases and available quality/seed information.
4. **Start Downloads**: Select a movie quality or TV episode release. Settings choose whether the magnet is sent to an external torrent app, downloaded and streamed locally, or downloaded and offered to a Chromecast-compatible receiver. In-app transfers can be paused, resumed, played, or deleted from **In-app downloads** in Settings.
5. **Search and Details**: Search movie titles or cast, or open a movie poster for summaries, cast, IMDb ratings, and trailers.
6. **Sync Your Library**: Sign in and save favorites or select downloads; movie and TV download histories are stored separately in your account.

---

## 💻 Technical Overview for Developers

This app has been refactored to leverage the latest Jetpack Compose libraries and reactive Android architecture:

* **Language**: [Kotlin 2.1+](https://kotlinlang.org/) with the K2 compiler.
* **UI Framework**: [Jetpack Compose](https://developer.android.com/compose) with **Material 3** for mobile and **TV Material 3** for Android TV.
* **Visual Effects**: [Haze 2.0](https://github.com/chrisbanes/haze) for frosted glass blur on app bars and headers, featuring scroll-driven `EaseInOutCubic` alpha interpolation.
* **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android).
* **Auth & Cloud Persistence**: [Firebase Auth](https://firebase.google.com/docs/auth) via **Google Credential Manager** and [Cloud Firestore](https://firebase.google.com/docs/firestore) for remote synchronization.
* **Navigation**: [AndroidX Navigation 3](https://developer.android.com/jetpack/androidx/releases/navigation) Compose-first runtime.
* **Networking**: [Retrofit 3.0](https://square.github.io/retrofit/) with OkHttp 5 and Coroutines Flow.
* **TV Catalog & Episode Releases**: TMDB provides series/season/episode metadata; EZTV provides episode torrent releases matched through each series' IMDb identifier.
* **On-Device AI Translation**: [Google ML Kit Translate](https://developers.google.com/ml-kit/language/translation) for local summary translation.

> [!NOTE]
> For a full breakdown of the software architecture, modular UI components, and sequence diagrams, refer to the **[Architecture Guide (ARCHITECTURE.md)](ARCHITECTURE.md)**.

---

## 🛠️ Setup & Build Instructions

### Prerequisites

1. **Credentials in `local.properties`**:
   Add the Firebase Web Client ID and TMDB / OpenSubtitles API keys to `local.properties` in the project root:
   ```properties
   FIREBASE_WEB_CLIENT_ID=your_web_client_id_here
   TMDB_API_KEY=your_tmdb_api_key_here
   OPEN_SUBTITLES_API_KEY=your_opensubtitles_api_key_here
   ```
   The build exposes these through `BuildConfig`; do not commit credentials or hardcode them in source.
   A TMDB key is required for the TV-series catalog and detail screens. The OpenSubtitles API key is required for subtitle search; users enter their own OpenSubtitles username and password in Settings, where credentials are encrypted using Android Keystore.
   Build-time API keys can be extracted from a distributed APK; do not treat them as secrets and use appropriately scoped keys and quotas.
   In-app libtorrent support raises `minSdk` to Android 9 (API 28). Cast playback requires the phone/TV and receiver to be reachable on the same local network.

2. **Google Services Config**:
   Place your `google-services.json` inside the `app/` folder. Ensure your development SHA-1 fingerprint is registered in the Firebase Console.

### Build Commands (PowerShell / Bash)

* **Compile Debug APK**:
  ```powershell
  .\gradlew.bat assembleDebug
  ```

* **Install on Connected Device or Emulator**:
  ```powershell
  .\gradlew.bat installDebug
  ```

* **Run Unit Tests**:
  ```powershell
  .\gradlew.bat testDebugUnitTest
  ```

* **Generate JaCoCo Coverage Report**:
  ```powershell
  .\gradlew.bat testDebugUnitTest jacocoTestReport
  ```

---

## 🧪 Testing & Coverage

The project includes a comprehensive test suite using **MockK**, **Turbine**, **Google Truth**, **JaCoCo**, and Compose UI tests with **HiltTestRunner**.

* **Repositories & ViewModels**: Verification of asynchronous flows (`StateFlow`), language filtering, pagination, and error handling.
* **UI Components**: Parallel Compose Previews (`@PreviewLightDark` for mobile and `*TvPreview` for TV).
