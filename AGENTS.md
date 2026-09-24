# AGENTS.md — Quick onboarding for AI coding agents

Checklist for this agent run:
- [x] Understand app architecture and DI boundaries
- [ ] Note project-specific serialization / DB / networking patterns
- [ ] List dev workflows (build/install) and gotchas
- [x] Point to concrete files to inspect for changes
- [x] Implement unit and UI tests for core components

Mandatory Agent Workflow & Standards
1. **Regression Prevention via Git Inspection**:
   Before making changes, agents MUST run `git diff` or compare against recent working commits (e.g. `"C:\Program Files\Git\cmd\git.exe" --no-pager diff HEAD~1`) to verify previous working layout structures and prevent regressions in D-pad navigation, edge-to-edge padding, or Haze 2.0 glass effects.
2. **Mandatory Skill Usage for Standards**:
   Agents MUST load and follow workspace skills (`leanback-to-compose-tv-migration`, `firebase-basics`, `firebase-auth-basics`, `edge-to-edge`, `styles`, `adaptive`) before modifying TV layouts, authentication flows, or edge-to-edge styling to ensure official Android and Firebase standards are strictly maintained.
3. **Official Documentation & Reference Links for Agents**:
   - **Android Developer Guidance**: [developer.android.com](https://developer.android.com/doc)
   - **Android TV & Compose for TV Guide**: [developer.android.com/tv/compose](https://developer.android.com/tv/compose)
   - **Firebase Android SDK Setup & Best Practices**: [firebase.google.com/docs/android/setup](https://firebase.google.com/docs/android/setup)
   - **Chris Banes Haze Glass Blur Effect**: [github.com/chrisbanes/haze](https://github.com/chrisbanes/haze)
   - **Jetpack Compose Material 3 Design System**: [developer.android.com/develop/ui/compose/designsystems/material3](https://developer.android.com/develop/ui/compose/designsystems/material3)

Short summary
- This is a Jetpack Compose + Hilt Android app (Kotlin). Core patterns: Retrofit (Gson), Room, kotlinx.serialization on models, coroutines + Flow, and androidx.navigation3 runtime for navigation keys. Authentication is handled via Firebase + Google Sign-in (Credential Manager).

Essential places to read first
- App entry and navigation: `app/src/main/java/com/martinrevert/latorrentola/MainActivity.kt` and `ui/navigation/AppNavigation.kt` (deep-link via Intent extra "PELI"; navigation transfers Movie as JSON using kotlinx.serialization). Navigation also handles auth-gating (Route.Login vs Route.Home).
- Authentication & Cloud Sync: `network/AuthRepository.kt`, `network/UserLibraryRepository.kt` (Firestore), `ui/auth/AuthViewModel.kt`, `ui/auth/LoginScreen.kt`, and `di/AuthModule.kt`. Sensitive credentials like `WEB_CLIENT_ID` are injected via `local.properties`.
- Network & DI: `di/NetworkModule.kt`, `network/YtsService.kt`, `network/YtsRepository.kt` (Retrofit service + repository that mixes remote + Firestore sync).
- Models & persistence: `model/YTS/*` (e.g. `Movie.kt`) and `database/Converters.kt`. Local persistence (Room) is reserved for session data (GenreStats, LastVisit); Favorites and Downloads are synced via Firestore.
- Build and dependency versions: `gradle/libs.versions.toml` and `app/build.gradle` (KSP, Hilt, Google services plugins; git-based versionCode)
- Firebase / Google services: `google-services.json` (project and app-level copies) and `app/keys/release.keystore` (release signing asset)

Project-specific patterns and gotchas (do not assume defaults)
- Mixed serialization: Models have both `kotlinx.serialization` (`@Serializable`) and Gson `@SerializedName`. Retrofit is configured with `GsonConverterFactory`.
- Cloud-First Persistence: Favorites and Download history are stored in Firebase Firestore (keyed by Google UID). Room is only used for local analytics and session metadata. Do not add new entities to Room if they need to persist across devices.
- Navigation transfers entire Movie objects as JSON strings via `Json.encodeToString(Movie.serializer(), movie)` and `Json.decodeFromString(...)` in `AppNavigation.kt`. Keep serializers in sync with model changes.
- Translation & UI Utilities: `utils/UiText.kt` contains the `GenreTranslation` object, which is the single source of truth for mapping API genre strings to localized `UiText` resources. Use this in all screens (Home, Search, Details) to maintain consistency.
- Multi-Selection Pattern: The `SearchScreen` (favorites view) implements a selection mode for D-pad compatibility. Short-press navigates to details, long-press enters selection mode. Once in selection mode, short-press toggles selection.
- Credential Safety: Never hardcode API keys or Web Client IDs. Use `local.properties` with a corresponding `buildConfigField` in `app/build.gradle`. Reference them via `BuildConfig`.
- DI scope: Hilt is used for singletons (see `di/NetworkModule.kt`). When adding bindings, follow the `@Module @InstallIn(SingletonComponent::class)` pattern.
- Theme and Readability: Always respect the app's themes. Ensure all UI changes are compatible with both light and dark modes without losing human readability. Avoid hardcoding colors like `Color.Black` or `Color.White` unless they are specifically meant to be static; instead, use `MaterialTheme.colorScheme` tokens. Be careful with imports to avoid shadowing standard Material3 components with TV-specific ones that might have different default behaviors.
- Translucent Status Bar & Top Header (Haze 2.0) Rules:
    *   **Header Glass Scope**: Apply `Modifier.hazeGlass(input = HazeInput.Sources(hazeState))` to the ENTIRE top header container (`TopAppBar` + chips like `GenreChips` / `QualityChips`), NOT just `TopAppBar` alone, so the translucent glass blur effect covers the top app bar and filter chips cohesively.
    *   **Full-Screen `hazeSource`**: The scrollable content (`MovieList`) MUST fill `fillMaxSize()` with `hazeSource(state = hazeState)` and receive `contentPadding` (top = status bar + `TopAppBar` + chips + 16.dp accessible gap, bottom = navBarHeight + 16.dp) so movie cards scroll behind the top header and bottom system bar.
    *   **Animated Ease-In / Ease-Out**: Animate `hazeAlpha` using `derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 10 }` and `animateFloatAsState(targetValue = if (isScrolled) 0.15f else 1f, animationSpec = tween(600, easing = EaseInOutCubic))`. Apply `graphicsLayer { alpha = hazeAlpha }` to the `hazeGlass` header modifier.
    *   **Pre-Android 12 (API < 31) & Preview Fallback**: Real-time blur requires Android 12+ (API 31+). Check `val isPreAndroid12 = !LocalInspectionMode.current && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)`. Pre-Android 12 devices MUST fall back to `MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)` container color instead of `Color.Transparent`. Always check `!LocalInspectionMode.current` so Compose Previews render with `Color.Transparent` and show Haze glass effects properly.
- Android TV & D-Pad Focus Navigation (Zero Regression Guidelines):
    *   **Conditional TV Layout Tree (`isTv`)**: NEVER place chips (`QualityChips`, `GenreChips`) in a floating overlay `Column` separate from `MovieList` on TV! Overlay columns break Compose 2D D-pad focus search between chips and grid cards. On TV (`isTv == true`), always use a single vertical `Column` where `TopAppBar`, chips, and `MovieList` are direct siblings in the same layout tree.
    *   **Unified TV Components (`androidx.tv.material3.Surface`)**: NEVER mix phone Material 3 touch chips (`FilterChip`, `SuggestionChip`) with TV `Surface` on TV screens. Use `TvChip` (`androidx.tv.material3.Surface`) for all chips and icon buttons on TV so every element speaks the native `tv-material3` focus protocol.
    *   **Zoom Scale vs Border Highlights**: On TV, movie cards (`MovieItem`) use `scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f)`. Do NOT draw an extra `.border()` focus line on movie cards on TV; the 1.1x zoom scale is the official indicator.
    *   **Deterministic Focus Links (`focusProperties`)**: Action icons on the right side of `TopAppBar` MUST specify `focusProperties { down = nextFocusRequester }` pointing directly to chips or list below, preventing focus search dead zones.
    *   **YouTube Player Focus Isolation**: WebViews (`YouTubePlayerView`) steal and trap D-pad focus. Always configure `YouTubePlayerView` with `isFocusable = false`, `isFocusableInTouchMode = false`, and `descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS`, overlaying a native `androidx.tv.material3.IconButton` for play/pause control.
- Placeholder Alignment Rule:
    *   `MovieListPlaceholder` (loading skeleton state) MUST use the EXACT same `contentPadding` values as `MovieList` (loaded state) on every screen (`HomeScreen`, `SearchScreen`, `Favorites`, etc.) to prevent vertical jumps or misalignments when content finishes loading.
- Modular Component Architecture:
    *   Shared UI components MUST be placed in `ui/components/`:
        - `ui/components/TvChip.kt` (TV-optimized chips)
        - `ui/components/Chips.kt` (`GenreChips`, `QualityChips`)
        - `ui/components/MovieItem.kt` (`MovieItem` card)
        - `ui/components/MovieList.kt` (`MovieList` grid)
        - `ui/components/Placeholders.kt` (`MovieListPlaceholder`, `MovieItemPlaceholder`)
    *   Screen composables (`HomeScreen.kt`, `SearchScreen.kt`, `MovieDetailScreen.kt`) should only orchestrate state and import shared components from `com.martinrevert.latorrentola.ui.components.*`.
- Adaptive UI & Multi-Device Support: The app is designed for phones, tablets, foldables, and Android TV. 
    *   **Device Detection**: Use `Context.isTvDevice()` (from `DeviceUtils.kt`) for TV-specific logic. 
    *   **Wide Screens**: Use `LocalConfiguration.current.screenWidthDp >= 600` (often referred to as `isWideScreen`) to detect tablets and foldables in landscape.
    *   **Layout Differences**: TVs and wide screens use side-by-side layouts (e.g., in `MovieDetailScreen` and `SettingsScreen`) and larger grid column counts. 
    *   **Input Handling**: TVs require D-pad focus handling. Use `Modifier.focusHighlight()`, `focusRestorer()`, and avoid touch-only interactions like `PullToRefresh` on TV.
    *   **Previews**: Every screen MUST have multiple `@Preview` functions: `PreviewLightDark` for mobile (light/dark) and `*TvPreview` with `uiMode` variations for TV (light/dark).
- Mandatory Language Filtering: Any screen or logic that retrieves and displays a list of movie cards (e.g., Home, Search, "New" releases, "Already seen", "Favorites") MUST apply the user's language filter (from `PreferenceManager.getFilteredLanguages()`) before showing the results. This ensures consistency across the entire app experience. Use `MovieFilter.filterMovies()` for this purpose. This is a critical requirement for all movie listing logic.
- Git-based versionCode: `app/build.gradle` runs `git rev-list --count HEAD` to set `versionCode`/`versionName`. Ensure git is present in CI or on developer machines when producing builds.

Common tasks & exact commands (Windows PowerShell)
- Clean & build debug APK:

```powershell
.
\gradlew.bat clean; .\gradlew.bat assembleDebug
```

- Install debug APK to a connected device:

```powershell
.\gradlew.bat installDebug
# then start app via adb (package + launcher activity)
adb shell am start -n com.martinrevert.latorrentola/.MainActivity
```

- Run all unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

- Run instrumented (UI) tests:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

- Generate JaCoCo coverage report:

```powershell
.\gradlew.bat testDebugUnitTest jacocoTestReport
```

- Build release (signed) APK / AAB (ensure `app/keys/release.keystore` is present and signing config is set in Gradle):

```powershell
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
```

Important files to update when changing behavior
- Networking: `network/YtsService.kt` + `di/NetworkModule.kt` (Retrofit client and logging interceptor)
- Authentication & Sync: `network/AuthRepository.kt`, `network/UserLibraryRepository.kt` and `ui/auth/*`
- Data layer: `network/YtsRepository.kt` (combines remote + local flows) and `database/*` (DAO/Converters)
- UI routing: `ui/navigation/AppNavigation.kt` (how Movie JSON is passed and auth-gating) and top-level Composables under `ui/*`

Integration points & external dependencies
- YTS API: base URL defined in `constants/Constants.kt` (Constants.YTS_BASE_URL)
- Firebase: Auth (Google Sign-in), Crashlytics, and Messaging. `google-services.json` must be valid and SHA-1 registered in Firebase Console for Google Sign-in.
- ML Kit Translate: used for on-device translations (dependency in gradle BOM)
- YouTube player library for trailers (dependency present in libs)

Testing / CI notes
- Modern unit tests are located in `app/src/test/java`. They use MockK, Turbine, and Truth.
- Instrumented UI tests are located in `app/src/androidTest/java`. They use `createComposeRule()` and Hilt.
- Instrumented tests use a custom runner: `com.martinrevert.latorrentola.HiltTestRunner`.
- MockK Android (`mockk-android`) is included for instrumented test mocking.
- When testing ViewModels, use `MainDispatcherRule` (under `rules/`) to mock `Dispatchers.Main`.
- The project forces a modern version of `byte-buddy` (1.18.15+) and uses `org.gradle.jvmargs` in `gradle.properties` to avoid `sun.misc.Unsafe` warnings across all build tasks (compilation and testing) on modern JDKs.
- JaCoCo is configured (v0.8.15) with broad exclusions for ByteBuddy/MockK proxy classes to prevent instrumentation errors on newer JDKs.
- CI should run `./gradlew testDebugUnitTest` to verify logic and optionally `connectedDebugAndroidTest` for UI.
- If adding new testable components, follow the existing patterns in `YtsRepositoryTest`, `HomeViewModelTest`, or `HomeUiTest`.
- CI must have `git` available (versionCode uses commit count) and Android SDK + buildtools matching AGP settings (`gradle/libs.versions.toml`).

If you edit models:
- Update `@Serializable` Kotlin serializers and add `@SerializedName` for any field used by Retrofit/Room converters.
- Update `database/Converters.kt` if new nested/collection types are persisted.

Quick pointers for PR reviewers (what to check)
- Serialization symmetry: ensure new model fields are present in both kotlinx and Gson annotations
- DI scopes: prefer `@Singleton` in `NetworkModule`-style modules unless intentionally scoped narrower
- Navigation payload size: passing full Movie JSON is convenient but can grow; consider passing ID and fetching details if payload becomes large

End of agent guide — keep this file in root as the single-source quick reference for code-modifying agents.
