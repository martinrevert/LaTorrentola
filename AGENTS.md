# AGENTS.md — Quick Onboarding & Engineering Rules

This document provides mandatory guidance, architecture standards, and development workflows for AI agents and developers working on **LaTorrentola**.

---

## 🛑 Critical Mandatory Rules

### 1. UI, Color & Contrast
* ❌ **NO HARDCODED COLORS OR STYLES**: Never write static colors (`Color.Black`, `Color.White`, `Color(0xFF...)`) or manual text color overrides in composables. Always consume semantic tokens from `MaterialTheme.colorScheme` and `MaterialTheme.typography`.
* ❌ **NO DARK TEXT ON DARK SURFACES**: In Dark Mode (static and Android 12+ dynamic), `onSurface` and `onSurfaceVariant` must be light grey/white (`#F4EFF4`, `#E6E1E5`). Chips (`AdaptiveChip`) must wrap text in `CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface)`.
* ❌ **OPAQUE MODALS & BOTTOM SHEETS**: All `ModalBottomSheet` and `Dialog` containers containing text MUST use solid, 100% opaque surface container colors (`MaterialTheme.colorScheme.surfaceContainerHigh` or `surface`, alpha >= 0.95f). Never use semi-transparent backgrounds that let underlying screen text bleed through.

### 2. Android TV & D-Pad Navigation
* ❌ **SINGLE VERTICAL LAYOUT TREE**: On Android TV (`isTv == true`), `TopAppBar`, filter chips (`AdaptiveChip`), and `MovieList` MUST be direct siblings in a single vertical `Column`. Never put chips in a floating overlay `Column`, as overlays break Compose 2D D-pad focus search.
* ❌ **NATIVE TV COMPONENTS**: Never mix phone M3 touch chips (`FilterChip`) with TV components. Use `TvChip` (`androidx.tv.material3.Surface`) on TV so focus search works natively.
* ❌ **CARD FOCUS HIGHLIGHTS**: Movie cards on TV use 1.1x zoom scale (`focusedScale = 1.1f`). Do NOT draw an extra `.border()` focus outline on movie cards on TV.
* ❌ **DETERMINISTIC FOCUS LINKS**: Action icons in `TopAppBar` MUST define explicit `focusProperties { down = nextFocusRequester }` to point directly to chips/lists below and prevent focus search dead zones.
* ❌ **YOUTUBE PLAYER FOCUS ISOLATION**: `YouTubePlayerView` WebViews trap D-pad focus. Always set `isFocusable = false`, `isFocusableInTouchMode = false`, and `descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS`, and overlay a native `androidx.tv.material3.IconButton` for play/pause control.

### 3. Glass Blur Header (Haze 2.0)
* **Header Scope**: Apply `Modifier.hazeGlass(input = HazeInput.Sources(hazeState))` to the ENTIRE top header container (`TopAppBar` + filter chips), not `TopAppBar` alone.
* **Full-Screen Blur Source**: Scrollable content (`MovieList`) MUST use `fillMaxSize()` with `hazeSource(state = hazeState)` and top/bottom `contentPadding` (top = status bar + header + 16.dp gap, bottom = nav bar + 16.dp) so content scrolls behind the top bar and system navigation bar.
* **Animated Ease-In/Out**: Animate `hazeAlpha` using `derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 10 }` and `animateFloatAsState`.
* **Pre-Android 12 & Preview Fallbacks**: Real-time blur requires API 31+. Pre-Android 12 devices and non-inspection Compose Previews MUST fall back to `MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)` container color instead of `Color.Transparent`.

### 4. Magnet Links & Torrent Downloads
* **Direct Magnet Launch**: Magnet link intents MUST use `Intent(Intent.ACTION_VIEW)` with `Intent.CATEGORY_BROWSABLE` and formatted URI `"magnet:?xt=urn:btih:$hash&dn=$encodedTitle&tr=..."`.
* **No Intent Chooser**: Launch magnet intents directly via `context.startActivity(intent)` without wrapping in `Intent.createChooser`, allowing TV and mobile OS to pass torrents directly to default torrent clients (Flud, ZetaTorrent, LibreTorrent, etc.).
* **Firestore Download Records**: When recording downloads via `DownloadedMovie`, MUST map `movieId = movie.id`, `movieTitle = movie.title ?: ""`, `quality = quality`, `hash = torrentHash`, `timestamp = System.currentTimeMillis()`, and `movie = movie`. Never omit `movieTitle` or `movie`.
* **No Rating Filters on User Collections**: User collections ("Ya vistas" / Downloaded Movies and Favorites in `SearchViewModel`) MUST NOT filter out movies using `preferenceManager.getMinimumRating()`.

### 5. TV Series Catalog & Episode Downloads
* **Separate Catalogs**: Home has distinct Movies and TV modes. Movies use YTS; series, genres, seasons, and episodes use TMDB. Do not route TV data through movie models, YTS filters, or movie detail destinations.
* **TV Data Pipeline**: `TvHomeViewModel`/`TvGenreResultsViewModel` own TV feed and discovery state; `TmdbRepository` calls `TmdbService`; TV endpoints require the configured `TMDB_API_KEY`. Keep the key in `local.properties`/`buildConfigField`, never in source.
* **TV Home and D-pad**: Keep the TV Home app bar, mode controls, feed/genre chips, and active grid as direct siblings in one vertical `Column`. Mode controls must remain reachable from app-bar actions, and all TV controls use `AdaptiveChip`/native TV Material components with deterministic focus where needed.
* **Genre Usage**: TV genre visit counts are independent of movie genre counts. Persist via `TvGenreDao`/Room `tv_genre_stats`; the TV genre catalog sorts by visit count and then name. Update database migrations if the persistence schema changes.
* **Episode Torrent Matching**: EZTV lookup uses the TV series IMDb ID from TMDB external IDs with the `tt` prefix removed, then matches the exact season and episode numbers. Preserve this identity chain; TMDB series IDs are not IMDb IDs.
* **Episode Downloads**: TV episode history is a `DownloadedEpisode` in the signed-in user's Firestore `tv_downloads` subcollection, keyed by torrent hash. Do not save episode records to the movie `downloads` collection or model them as `DownloadedMovie`.
* **TV Magnet Launch**: Episode release buttons launch the supplied magnet URI (or a hash-derived magnet URI) directly with `ACTION_VIEW` and `CATEGORY_BROWSABLE`, without an intent chooser.

### 6. Layout & Content Standards
* **Placeholder Alignment**: `MovieListPlaceholder` and `MovieDetailPlaceholder` skeleton states MUST use identical `contentPadding` values as loaded content states to prevent vertical layout jumps.
* **Mandatory Language Filtering**: All movie catalog listings (Home, Search, "New" releases) MUST apply the user's language filter (`MovieFilter.filterMovies(...)`) before displaying results.
* **Credential Safety**: Never hardcode API keys or Web Client IDs. Inject via `local.properties` + `buildConfigField` in `app/build.gradle` and reference via `BuildConfig`.

---

## ⚡ Agent Workflow & Quality Standards

1. **Regression Prevention via Git Inspection**:
   Before modifying code, run git diff (e.g. `git --no-pager diff HEAD~1`) to verify working layout structures and prevent regressions in D-pad navigation, edge-to-edge padding, or Haze 2.0 glass effects.
2. **Up-to-Date Kotlin KDoc**:
   Every new or modified Kotlin class, function, property, composable, and model field MUST have accurate KDoc. Include `@property` tags for data class fields.
3. **Mandatory Skill Usage**:
   Consult workspace skills before modifying specialized subsystems:
   - `leanback-to-compose-tv-migration` (TV layouts & D-pad focus)
   - `firebase-basics` & `firebase-auth-basics` (Auth & Firestore sync)
   - `edge-to-edge`, `styles`, `adaptive` (Layouts & system bar styling)
4. **Reference Documentation**:
   - [Android Developer Docs](https://developer.android.com/doc)
   - [Compose for TV Guide](https://developer.android.com/tv/compose)
   - [Firebase Android Setup](https://firebase.google.com/docs/android/setup)
   - [Chris Banes Haze Blur](https://github.com/chrisbanes/haze)
   - [Material 3 Design System](https://developer.android.com/develop/ui/compose/designsystems/material3)

---

## 🏗️ Architecture & Component Map

### Tech Stack Overview
- **UI & Navigation**: Jetpack Compose, Material 3, `androidx.tv.material3`, `androidx.navigation3` runtime (passes serialized `Movie` JSON strings).
- **DI & Network**: Hilt (`@Singleton` in `SingletonComponent`), Retrofit (`GsonConverterFactory`), OkHttp.
- **Persistence**: Firebase Firestore (cloud sync for Favorites & Downloads by Google UID), Room (local session metadata: `GenreStats`, `DateLastVisit`).
- **Auth & Services**: Firebase Auth + Google Sign-In (Credential Manager), Firebase Messaging, ML Kit Translate.

### Essential File Locations
| Category | File Path | Description |
| :--- | :--- | :--- |
| **Entry & Navigation** | [MainActivity.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/MainActivity.kt)<br>[AppNavigation.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/navigation/AppNavigation.kt) | App entry, Intent handling ("PELI" extra), auth-gated routing. |
| **Auth & Cloud Sync** | [AuthRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/AuthRepository.kt)<br>[UserLibraryRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/UserLibraryRepository.kt)<br>[LoginScreen.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/auth/LoginScreen.kt) | Google credential sign-in and Firestore library persistence. |
| **Networking & DI** | [YtsService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/YtsService.kt)<br>[YtsRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/YtsRepository.kt)<br>[NetworkModule.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/di/NetworkModule.kt) | YTS REST API repository and Hilt singleton bindings. |
| **TV Catalog & Data** | [TvHomeViewModel.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/home/TvHomeViewModel.kt)<br>[TvGenreResultsViewModel.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/home/TvGenreResultsViewModel.kt)<br>[TmdbRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/TmdbRepository.kt)<br>[TmdbService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/TmdbService.kt) | TMDB TV feeds, genre discovery, seasons, episodes, and linked IMDb identifiers. |
| **TV Screens & Downloads** | [TvCatalogComponents.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/TvCatalogComponents.kt)<br>[TvDetailScreen.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/detail/TvDetailScreen.kt)<br>[TvEpisodeDetailScreen.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/detail/TvEpisodeDetailScreen.kt)<br>[EztvRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/EztvRepository.kt) | Responsive TV catalog/detail UI, episode torrent search, and direct magnet launch. |
| **TV Persistence & Models** | [TmdbTv.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/model/TMDB/TmdbTv.kt)<br>[DownloadedEpisode.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/model/user/DownloadedEpisode.kt)<br>[TvGenreDao.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/database/TvGenreDao.kt) | Dual-serialized TMDB data, Firestore episode download records, local TV genre usage. |
| **Shared UI** | [Chips.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/Chips.kt)<br>[MovieItem.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/MovieItem.kt)<br>[MovieList.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/MovieList.kt)<br>[Placeholders.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/Placeholders.kt) | Reusable design system components and skeleton screens. |
| **Utilities** | [UiText.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/utils/UiText.kt)<br>[DeviceUtils.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/utils/DeviceUtils.kt)<br>[MovieFilter.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/utils/MovieFilter.kt) | `GenreTranslation` mapper, `Context.isTvDevice()`, language/rating filters. |

### TV Series Integration Map

The Home Movies/TV selector switches independent catalogs without changing the app's navigation root:

1. **Browse:** `HomeScreen` presents TMDB feeds (On the Air, Airing Today, Popular, Top Rated), a locally usage-ranked TV genre row, and a responsive TV poster grid. `TvHomeViewModel` handles paging, refresh, deduplication, and focus restoration. `TvGenreResultsViewModel` handles genre discovery and sort direction/field.
2. **Open series:** Navigation passes the TMDB series ID in `Route.TvDetail`. `TvDetailViewModel` loads the series, defaults to the first non-special season when possible, fetches the selected season's episodes, and observes the current user's episode downloads.
3. **Open episode:** Navigation carries the series ID, season number, episode number, series name, and serialized episode metadata in `Route.TvEpisodeDetail`. The episode screen can use that metadata directly; it resolves it from TMDB by season if not supplied.
4. **Find release:** `TvEpisodeDetailViewModel` asks TMDB for the series' IMDb external ID, then `EztvRepository` requests EZTV releases and filters exact season/episode matches, ordered by seed count.
5. **Download:** Selecting a release records `DownloadedEpisode` in Firestore under `users/{uid}/tv_downloads/{hash}`, then directly launches its magnet link for the OS torrent handler.

When changing this subsystem, trace the full route and identity chain across `AppNavigation`, both TV detail ViewModels, `TmdbRepository`, `EztvRepository`, and `UserLibraryRepository`. TV genre usage is local Room data; episode download history is user-scoped Firestore data. These stores and models are intentionally separate from movie genres and movie downloads.

### Data Model Rules
- **Dual Serialization Symmetry**: Models must include both `@Serializable` (kotlinx.serialization for navigation/Firestore) and `@SerializedName` (Gson for Retrofit/Room).
- **Room Converters**: Update `database/Converters.kt` whenever new nested model collections are added to Room.

---

## 🛠️ Development & Testing Workflows

### PowerShell Commands
```powershell
# Clean & build debug APK
.\gradlew.bat clean; .\gradlew.bat assembleDebug

# Install debug APK to connected device and launch
.\gradlew.bat installDebug
adb shell am start -n com.martinrevert.latorrentola/.MainActivity

# Run unit tests
.\gradlew.bat testDebugUnitTest

# Run instrumented UI tests (requires connected device/emulator)
.\gradlew.bat connectedDebugAndroidTest

# Generate JaCoCo coverage report
.\gradlew.bat testDebugUnitTest jacocoTestReport

# Build release APK & AAB (requires release.keystore)
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
```

### Testing Infrastructure
- **Unit Tests**: Located in `app/src/test/java`. Built with MockK, Turbine, and Truth. Use `MainDispatcherRule` to mock `Dispatchers.Main`.
- **Instrumented Tests**: Located in `app/src/androidTest/java`. Uses `createComposeRule()`, Hilt, and custom test runner `com.martinrevert.latorrentola.HiltTestRunner`.
- **Versioning**: `app/build.gradle` calculates `versionCode` via `git rev-list --count HEAD`. Git must be available in the execution environment.

---

## 🔍 PR Review Checklist

- [ ] **Colors & Theme**: No static colors used; M3/TV semantic color tokens used exclusively.
- [ ] **Contrast**: WCAG AAA contrast verified in both Light and Dark modes.
- [ ] **Modals**: Solid opacity (`surfaceContainerHigh`) on bottom sheets/dialogs with text.
- [ ] **TV D-Pad**: Verified 2D D-pad focus navigation; single layout tree with no floating overlays.
- [ ] **Magnet Links**: Direct `Intent.ACTION_VIEW` intent with `CATEGORY_BROWSABLE` and no chooser.
- [ ] **Downloads Sync**: `DownloadedMovie` instantiated with `movieId`, `movieTitle`, `quality`, `hash`, `timestamp`, `movie`.
- [ ] **Serialization**: Dual `@Serializable` + `@SerializedName` annotations present on data models.
- [ ] **KDoc**: Up-to-date KDoc on all new or modified functions, classes, and properties.
