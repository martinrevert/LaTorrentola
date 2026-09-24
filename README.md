# La Torrentola 🎬

**La Torrentola** is a modern Android application designed for exploring high-definition movie catalogs, discovering new releases, watching trailers, and managing a personal movie library with seamless cloud synchronization across all your devices using your Google account.

Built for phones, tablets, foldables, and **Android TV / Chromecast**, the app features a *Frosted Glass* UI aesthetic (powered by Haze 2.0), native D-pad remote navigation, and direct integration with torrent managers on mobile or remote NAS home servers (such as Transdrone, Synology DS get, or local downloaders).

---

## 🌟 Key Features

### 📱 For Users
* **Catalog & Quality Filters**: Browse movies sorted by video quality (**4K 2160p**, **1080p x265**, **1080p**, **720p**) and genres (Action, Sci-Fi, Comedy, Drama, New Releases, and Previously Watched).
* **Google Cloud Library Sync**: Sign in with your Google account to back up and automatically synchronize your favorite movies and watch history across all your devices.
* **Integrated Trailer Player**: Watch official YouTube HD trailers directly inside the app without ads or pop-ups.
* **On-Device Spanish Translation**: Instant, automatic summary translation powered by local on-device AI—no additional mobile data or cloud costs.
* **Language Exclusion Filter**: Automatically hide movies in languages you prefer not to watch.
* **100% Android TV & Remote Ready**: Smooth navigation engineered for TV remotes with responsive card scaling, high-contrast focus indicators, and D-pad shortcuts.
* **One-Tap Magnet Link Launching**: Tap any download quality option to generate a standard *magnet link* that opens in your preferred torrent client (phone or NAS).

---

## 💡 Quick User Guide

1. **Explore the Catalog**: Scroll through the main home screen to discover popular movies and new releases. Use the top chips to filter by genre or resolution.
2. **Search Titles or Cast**: Tap the search icon (or use voice search on mobile) to find any movie by name.
3. **View Details & Trailers**: Tap a movie poster to view Spanish translated summaries, full cast lists, IMDb ratings, and play the trailer.
4. **Sync Favorites**: Tap the heart icon to save movies to your favorites. They sync instantly across all your Google-connected devices.
5. **Start Downloads**: Select your desired quality in the torrent list (e.g., 1080p x265) to send the download task directly to your torrent client or NAS.

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
* **On-Device AI Translation**: [Google ML Kit Translate](https://developers.google.com/ml-kit/language/translation) for local summary translation.

> [!NOTE]
> For a full breakdown of the software architecture, modular UI components, and sequence diagrams, refer to the **[Architecture Guide (ARCHITECTURE.md)](ARCHITECTURE.md)**.

---

## 🛠️ Setup & Build Instructions

### Prerequisites

1. **Credentials in `local.properties`**:
   Add your Firebase Web Client ID to `local.properties` in the project root:
   ```properties
   FIREBASE_WEB_CLIENT_ID=your_web_client_id_here
   ```

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
