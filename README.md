# La Torrentola 🎬

**La Torrentola** is a modern Android application for exploring high-definition movie and TV series catalogs, discovering new releases and episodes, watching movie trailers, and managing a personal media library with cloud synchronization through your Google account.

Built for phones, tablets, foldables, and **Android TV / Chromecast**, the app features a *Frosted Glass* UI aesthetic (powered by Haze 2.0), native D-pad remote navigation, and direct integration with torrent managers on mobile or remote NAS home servers (such as Transdrone, Synology DS get, or local downloaders).

---

## 🌟 Key Features

### 📱 For Users
* **Catalog & Quality Filters**: Browse movies sorted by video quality (**4K 2160p**, **1080p x265**, **1080p**, **720p**) and genres (Action, Sci-Fi, Comedy, Drama, New Releases, and Previously Watched).
* **TV Series Catalog**: Switch Home between Movies and TV to browse TMDB series by airing status, popularity, rating, or genre. Open seasons and episodes, find releases through EZTV, and send episode magnets directly to a compatible torrent client.
* **Google Cloud Library Sync**: Sign in with your Google account to back up and automatically synchronize your favorite movies and watch history across all your devices.
* **TV Episode Download History**: TV episode torrent selections are recorded separately from movie downloads and synced to the signed-in user's cloud library.
* **Integrated Trailer Player**: Watch official YouTube HD trailers directly inside the app without ads or pop-ups.
* **On-Device Spanish Translation**: Instant, automatic summary translation powered by local on-device AI—no additional mobile data or cloud costs.
* **Language Exclusion Filter**: Automatically hide movies in languages you prefer not to watch.
* **100% Android TV & Remote Ready**: Smooth navigation engineered for TV remotes with responsive card scaling, high-contrast focus indicators, and D-pad shortcuts.
* **One-Tap Magnet Link Launching**: Tap any download quality option to generate a standard *magnet link* that opens in your preferred torrent client (phone or NAS).

---

## 💡 Quick User Guide

1. **Explore the Catalog**: Scroll through the main home screen to discover popular movies and new releases. Use the top chips to filter by genre or resolution.
2. **Browse TV Series**: Select **TV** on Home, choose a feed or genre, then open a series to select a season and episode.
3. **Find an Episode Release**: Open an episode to see matching EZTV releases and available quality/seed information.
4. **Start Downloads**: Select a movie quality or TV episode release to send its magnet link to your torrent client or NAS.
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
   Add the Firebase Web Client ID and TMDB API key to `local.properties` in the project root:
   ```properties
   FIREBASE_WEB_CLIENT_ID=your_web_client_id_here
   TMDB_API_KEY=your_tmdb_api_key_here
   ```
   The build exposes these through `BuildConfig`; do not commit credentials or hardcode them in source.
   A TMDB key is required for the TV-series catalog and detail screens.

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
