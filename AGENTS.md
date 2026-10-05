# AGENTS.md — Quick Onboarding & Engineering Rules

This document provides mandatory guidance, architecture standards, and development workflows for AI agents and developers working on **LaTorrentola**.

---

## 🛑 Critical Mandatory Rules

### 1. UI, Color & Contrast
* ❌ **NO HARDCODED COLORS OR STYLES**: Never write static colors (`Color.Black`, `Color.White`, `Color(0xFF...)`) or manual text color overrides in composables. Always consume semantic tokens from `MaterialTheme.colorScheme` and `MaterialTheme.typography`.
* ❌ **NO DARK TEXT ON DARK SURFACES**: In Dark Mode (static and Android 12+ dynamic), `onSurface` and `onSurfaceVariant` must be light grey/white (`#F4EFF4`, `#E6E1E5`). Chips (`AdaptiveChip`) must wrap text in `CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface)`.
* ❌ **OPAQUE MODALS & BOTTOM SHEETS**: All `ModalBottomSheet` and `Dialog` containers containing text MUST use solid, 100% opaque surface container colors (`MaterialTheme.colorScheme.surfaceContainerHigh` or `surface`, alpha >= 0.95f). Never use semi-transparent backgrounds that let underlying screen text bleed through.
* ❌ **STRICT VISUAL & THEME HOMOGENEITY**: All sections (Movies, TV Series, Search, Details, Settings, etc.) across both Handheld and TV MUST strictly respect the same app theme tokens, component patterns, card designs (`surfaceVariant` text container, rating star format "⭐ 8.5", download badges), glass blur effects (`hazeGlass` on handheld), and skeleton placeholders.
* ❌ **ONE APP THEME SOURCE OF TRUTH**: `LaTorrentolaTheme` and `TvLaTorrentolaTheme` provide the app's theme; composables must consume it through `MaterialTheme` semantic color and typography tokens. Do not introduce screen-local palettes, hard-coded styles, or rely on platform/component defaults to create a different look. `androidx.tv.material3` components may have different defaults from Material 3, so explicitly configure their colors, shapes, typography, and focused states to match the app's established design.
* ❌ **MATCHED COMPONENT STYLES ACROSS SCREENS**: Equivalent UI in Movies and TV Series (including Home, genre results, details, cast, episode details, search, dialogs, and sheets) MUST share the same design decisions for layout, sizing, imagery/placeholders, typography, spacing, shape, semantic colors, and loading/empty states. Before implementing or changing a component, find its closest established counterpart and reuse the same shared component or extract one when appropriate. Do not create a TV-only or screen-local variation without a documented functional need.
* ❌ **MATCHED D-PAD FOCUS STYLES ACROSS ALL SCREENS**: Equivalent controls in Movies and every TV Series surface MUST use the same focus indicator shape, color, scale, and behavior. This includes app-bar back/share/favorite actions, chips, cast members, and cards. Follow the focus treatment of the corresponding established component; do not add a border to card types whose reference has none, or omit one where the reference uses it. Prefer shared adaptive components and focus modifiers rather than reimplementing their styles per screen. Do not let `androidx.tv.material3` defaults introduce a focused container background where the corresponding Movies control uses a `focusHighlight` border. Configure TV component colors explicitly or use the same Material 3 control where appropriate, while retaining native TV surfaces when needed for D-pad navigation and deterministic focus links.
* ❌ **PARITY BEFORE COMPLETION**: A UI change is incomplete until its equivalent handheld and TV experiences have been compared in both focused and unfocused states, and relevant wide-screen layouts have been considered. Before finishing, search for all analogous implementations across the app, confirm they use the same shared styling and behavior, and update them together when the change is intended to be app-wide. Do not validate parity by checking only the edited file.
* ❌ **DIALOGS AND SHEETS MUST ADAPT BY FORM FACTOR**: Do not assume a dialog's platform-default width or compact handheld dimensions are appropriate for TV/Chromecast. For TV and wide layouts, explicitly size content to make effective use of available width and height, and scale poster/card dimensions and typography for viewing distance while preserving semantic theme tokens. On handhelds, retain touch-appropriate sizing and avoid applying TV dimensions indiscriminately. Validate proportional layouts at phone, tablet, and TV sizes.
* ❌ **FOCUSED POSTER TREATMENT**: Selectable poster items in actor filmography and catalog grids must use the same 1.1x zoom focus behavior as the corresponding movie/TV posters and must not gain a circular focus indicator. Keep their focused container transparent when the matching poster pattern is transparent; do not add a focus border or default TV focused fill unless the established corresponding poster style uses it. Apply this across handheld, tablet, TV, and Chromecast variants while preserving usable D-pad focus and click behavior.
* ❌ **MANDATORY SCREEN FORMAT SCOPE**: When adding or updating any feature, UI component, screen, or behavior, ALWAYS implement it homogeneously for BOTH Handhelds (phones/tablets) and Television/Chromecast (`isTv`) form factors. If there is ever any ambiguity regarding screen format scope, ALWAYS ASK THE USER for clarification.

### 2. Android TV & D-Pad Navigation
* ❌ **SINGLE VERTICAL LAYOUT TREE**: On Android TV (`isTv == true`), `TopAppBar`, filter chips (`AdaptiveChip`), and `MovieList` MUST be direct siblings in a single vertical `Column`. Never put chips in a floating overlay `Column`, as overlays break Compose 2D D-pad focus search.
* ❌ **NATIVE TV COMPONENTS**: Never mix phone M3 touch chips (`FilterChip`) with TV components. Use `TvChip` (`androidx.tv.material3.Surface`) on TV so focus search works natively.
* ❌ **CARD FOCUS HIGHLIGHTS**: Preserve each card type's established focus treatment. Movie poster cards on TV use 1.1x zoom scale (`focusedScale = 1.1f`) and do NOT draw an extra `.border()` outline. Other card-like controls (for example cast items) must match their own movie counterpart's focus treatment; do not generalize the poster-card rule to every TV surface.
* ❌ **DETERMINISTIC FOCUS LINKS**: Action icons in `TopAppBar` MUST define explicit `focusProperties { down = nextFocusRequester }` to point directly to chips/lists below and prevent focus search dead zones.
* ❌ **YOUTUBE PLAYER FOCUS ISOLATION**: `YouTubePlayerView` WebViews trap D-pad focus. Always set `isFocusable = false`, `isFocusableInTouchMode = false`, and `descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS`, and overlay a native `androidx.tv.material3.IconButton` for play/pause control.
* ✅ **FOCUS HIGHLIGHTERS**: Use `focusHighlight()` modifier for all interactive elements in TV mode. For circular elements (cast member portraits): Apply 2.dp border with `MaterialTheme.colorScheme.primary` when focused. Maintain homogeneous focus indicator shapes, colors, scale, and behavior across all screens. Do NOT use hardcoded colors/styles - always use semantic tokens from `MaterialTheme`.
* ✅ **SCROLL BEHAVIOR ON FOCUS**: When top bar gains focus, smoothly scroll to top (`scrollState.animateScrollTo(0)`) to show full hero image. Share `scrollState` between components when synchronized scrolling is needed. Add `focusRestorer()` to lists/grids to maintain focus position when items change.

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
* ✅ **TMDB DATA INTEGRATION FOR MOVIES**: 
  - **Data Sourcing Priority**: Prefer TMDB for cast/crew details, character names, high-quality profile images; use YTS as primary movie source with TMDB as enrichment via IMDb ID.
  - **Implementation Pattern**: When movie loads, check if `imdbCode` is available; call TMDB `find/{external_id}` with IMDb ID to get TMDB movie ID; call TMDB `/movie/{movie_id}/credits` to get full cast/crew; map TMDB responses to app's `Cast` model (name → cast member name, characterName → character portrayed, urlSmallImage → `https://image.tmdb.org/t/p/w185{profile_path}`).
  - **Critical Handling**: Always handle API failures/network issues - never crash on missing TMDB data; show appropriate loading states during TMDB data fetching; preserve existing data model structure when enhancing with TMDB data; use TMDB's standard image URL patterns (w185 for cast profiles).

### 6. Layout & Content Standards
* **Placeholder Alignment**: `MovieListPlaceholder` and `MovieDetailPlaceholder` skeleton states MUST use identical `contentPadding` values as loaded content states to prevent vertical layout jumps.
* **Mandatory Language Filtering**: All movie catalog listings (Home, Search, "New" releases) MUST apply the user's language filter (`MovieFilter.filterMovies(...)`) before displaying results.
* **Credential Safety**: Never hardcode API keys or Web Client IDs. Inject via `local.properties` + `buildConfigField` in `app/build.gradle` and reference via `BuildConfig`.

### 7. In-App Torrent, Playback & Subtitles
* **Torrent Mode Routing**: The selected `TorrentHandlingMode` (read from `PreferenceManager`) determines how a quality selection is processed. `EXTERNAL_CLIENT` launches a magnet intent directly. `LOCAL_PLAYBACK` and `CHROMECAST` start `TorrentDownloadService` and open `LocalPlayerActivity`; never cross these paths.
* **libtorrent4j Piece Safety**: `VerifiedTorrentHttpServer` MUST only serve byte ranges whose covering torrent pieces have completed their SHA-1 hash check. Never serve unverified data, even when the download is nearly complete.
* **Cast Handoff**: `LocalPlayerActivity` switches between `ExoPlayer` and `CastPlayer` based on Cast session availability. Both players consume the same local HTTP URL from `VerifiedTorrentHttpServer`; the phone/TV and the Cast receiver must be on the same LAN.
* **TorrentDownloadDao Consistency**: All status transitions (`QUEUED` → `DOWNLOADING` → `COMPLETE` / `ERROR`) MUST be written to `TorrentDownloadDao` so `TorrentDownloadsScreen` reflects accurate state.
* **Subtitle Credential Storage**: OpenSubtitles username and password are encrypted with Android Keystore via `OpenSubtitlesCredentialStore`. Never store them in `SharedPreferences` in plaintext or in source code.
* **Subtitle Track Loading**: After downloading a subtitle file, pass its local path as an `ExternalSubtitleConfiguration` to `ExoPlayer`. For Cast sessions, encode the subtitle as a `MediaTrack` in the `MediaInfo` before loading.

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
- **Persistence**: Firebase Firestore (cloud sync for Favorites & Downloads by Google UID), Room (`GenreStats`, `TvGenreStats`, `DateLastVisit`, `TorrentDownload`).
- **Auth & Services**: Firebase Auth + Google Sign-In (Credential Manager), Firebase Cloud Messaging, ML Kit Translate.
- **In-App Torrent & Playback**: libtorrent4j foreground service (`TorrentDownloadService`), `VerifiedTorrentHttpServer` (verified HTTP byte-range streaming), Media3 ExoPlayer + Cast SDK (`LocalPlayerActivity`). Raises `minSdk` to API 28.
- **Subtitles**: OpenSubtitles API via Retrofit (`OpenSubtitlesRepository`); user credentials encrypted with Android Keystore; API key via `BuildConfig`.

### Essential File Locations
| Category | File Path | Description |
| :--- | :--- | :--- |
| **Entry & Navigation** | [MainActivity.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/MainActivity.kt)<br>[AppNavigation.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/navigation/AppNavigation.kt) | App entry, Intent handling ("PELI" extra), auth-gated routing. |
| **Auth & Cloud Sync** | [AuthRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/AuthRepository.kt)<br>[UserLibraryRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/UserLibraryRepository.kt)<br>[LoginScreen.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/auth/LoginScreen.kt) | Google credential sign-in and Firestore library persistence. |
| **Networking & DI** | [YtsService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/YtsService.kt)<br>[YtsRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/YtsRepository.kt)<br>[NetworkModule.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/di/NetworkModule.kt) | YTS REST API repository and Hilt singleton bindings. |
| **TV Catalog & Data** | [TvHomeViewModel.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/home/TvHomeViewModel.kt)<br>[TvGenreResultsViewModel.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/home/TvGenreResultsViewModel.kt)<br>[TmdbRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/TmdbRepository.kt)<br>[TmdbService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/TmdbService.kt) | TMDB TV feeds, genre discovery, seasons, episodes, and linked IMDb identifiers. |
| **TV Screens & Downloads** | [TvCatalogComponents.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/TvCatalogComponents.kt)<br>[TvDetailScreen.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/detail/TvDetailScreen.kt)<br>[TvEpisodeDetailScreen.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/detail/TvEpisodeDetailScreen.kt)<br>[EztvRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/EztvRepository.kt) | Responsive TV catalog/detail UI, episode torrent search, and direct magnet launch. |
| **TV Persistence & Models** | [TmdbTv.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/model/TMDB/TmdbTv.kt)<br>[DownloadedEpisode.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/model/user/DownloadedEpisode.kt)<br>[TvGenreDao.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/database/TvGenreDao.kt) | Dual-serialized TMDB data, Firestore episode download records, local TV genre usage. |
| **Shared UI** | [Chips.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/Chips.kt)<br>[MovieItem.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/MovieItem.kt)<br>[MovieList.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/MovieList.kt)<br>[Placeholders.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/Placeholders.kt)<br>[QualityChoiceDialog.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/QualityChoiceDialog.kt) | Reusable design system components and skeleton screens. |
| **In-App Torrent & Playback** | [TorrentDownloadService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/TorrentDownloadService.kt)<br>[VerifiedTorrentHttpServer.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/VerifiedTorrentHttpServer.kt)<br>[LocalPlayerActivity.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/player/LocalPlayerActivity.kt)<br>[TvPlayerViewModel.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/player/TvPlayerViewModel.kt)<br>[TorrentDownloadsScreen.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/downloads/TorrentDownloadsScreen.kt)<br>[TorrentHandlingMode.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/model/torrent/TorrentHandlingMode.kt)<br>[TorrentDownloadDao.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/database/TorrentDownloadDao.kt) | libtorrent4j foreground download service, verified HTTP streaming, ExoPlayer+Cast activity, in-app download manager, mode enum, and Room DAO. |
| **Subtitles** | [OpenSubtitlesRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/OpenSubtitlesRepository.kt)<br>[OpenSubtitlesService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/OpenSubtitlesService.kt) | Subtitle search and download via OpenSubtitles API; credentials stored in Android Keystore. |
| **Push Notifications** | [FcmRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/FcmRepository.kt)<br>[FcmService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/FcmService.kt)<br>[MyFirebaseMessagingService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/MyFirebaseMessagingService.kt)<br>[FirebaseMessagingInitializer.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/FirebaseMessagingInitializer.kt) | FCM token sync with backend; push notification handling and deep-link routing. |
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

### UI Homogeneity Workflow
For every UI change, including changes requested for only one screen:
1. Find the matching component or interaction in Movies and the corresponding handheld/TV implementations. Treat the existing app pattern as the reference unless the request explicitly changes that pattern.
2. Reuse the existing shared component where possible. If equivalent components are independently implemented, consider extracting a shared component rather than allowing their styles to drift.
3. Compare the full visual and interaction states: default, focused (especially with a D-pad), pressed/selected, disabled, loading, empty, and error where applicable. Check semantic colors, typography, dimensions, shape, spacing, image clipping and fallback, labels, and focus scale/indicator.
4. Check the affected screen on handheld, tablet/wide, and TV/Chromecast. Validate that dialogs and sheets use the available viewport proportionally, and that text and poster sizes are readable at the expected viewing distance without wasting screen space. Preserve native TV focus behavior while explicitly matching the app's visual treatment.
5. Search for all analogous usages and update every surface covered by the requested behavior. Record any intentional difference in the code or documentation with its reason; do not leave unexplained one-off styling.

---

## 🛠️ Development & Testing Workflows

### PowerShell Commands
#### Gradle and Java in this Windows environment
- Run Gradle from the repository root (`D:\AndroidProjects\LaTorrentola`) using the checked-in wrapper: `.\gradlew.bat`. Do not rely on a separately installed Gradle.
- Gradle must use Android Studio's bundled JBR at `C:\Program Files\Android\Android Studio\jbr`. The `java` found on the default system path is Oracle GraalVM 25; it fails Android's `androidJdkImage`/`jlink` transform for this project's Android SDK. Set `JAVA_HOME` and put its `bin` first on `Path` in the same PowerShell process before invoking the wrapper:
  ```powershell
  $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
  $env:Path = "$env:JAVA_HOME\bin;$env:Path"
  .\gradlew.bat :app:testDebugUnitTest --tests "com.martinrevert.latorrentola.ui.detail.TvEpisodeDetailViewModelTest" --no-configuration-cache
  ```
- Each agent PowerShell command starts in a fresh process, so set these environment variables again for every Gradle invocation. Keep the `--no-configuration-cache` flag for test runs in this environment; it avoids the wrapper's configuration-cache serialization failure.
- For any other Gradle task below, apply the same JDK setup in that command before running `.\gradlew.bat`; add `--no-configuration-cache` when running tests.

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
- [ ] **UI Homogeneity**: Compared the change with its Movies counterpart and all analogous TV/handheld surfaces; focus and unfocused states use the established shared treatment, with any intentional differences justified.
- [ ] **Adaptive Dialogs & Posters**: Sheets/dialogs make appropriate use of phone/tablet/TV space; selectable posters use the established zoom focus without unintended focused fills or circular indicators.
- [ ] **Magnet Links**: Direct `Intent.ACTION_VIEW` intent with `CATEGORY_BROWSABLE` and no chooser.
- [ ] **Downloads Sync**: `DownloadedMovie` instantiated with `movieId`, `movieTitle`, `quality`, `hash`, `timestamp`, `movie`.
- [ ] **Torrent Mode**: Quality selection correctly branches on `TorrentHandlingMode`; `LOCAL_PLAYBACK`/`CHROMECAST` paths start `TorrentDownloadService` and `LocalPlayerActivity`; `EXTERNAL_CLIENT` path only fires the magnet intent.
- [ ] **Piece Safety**: `VerifiedTorrentHttpServer` serves only hash-verified byte ranges; unverified data is never exposed to ExoPlayer or CastPlayer.
- [ ] **TorrentDownloadDao**: All status transitions written to Room so `TorrentDownloadsScreen` reflects accurate progress.
- [ ] **Subtitle Credentials**: OpenSubtitles username/password stored via `OpenSubtitlesCredentialStore` (Android Keystore), never in plaintext.
- [ ] **Serialization**: Dual `@Serializable` + `@SerializedName` annotations present on data models.
- [ ] **KDoc**: Up-to-date KDoc on all new or modified functions, classes, and properties.