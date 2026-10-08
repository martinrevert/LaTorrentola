# AGENTS.md — Quick Onboarding & Engineering Rules

This document provides mandatory guidance, architecture standards, and development workflows for AI agents and developers working on **LaTorrentola**.

---

## 🔒 ABSOLUTE MANDATES (Zero Tolerance Violations)
*These rules admit NO exceptions. Violations will cause immediate PR rejection.*

### 🎨 Color & Typography
* **🔒 NO HARDCODED COLORS**: Never use `Color.Black`, `Color.White`, `Color(0xFF...)`, or explicit hex values in composables/Android Views. **Always** derive colors from `MaterialTheme.colorScheme` or `MaterialTheme.typography`.
* **🔒 THEME PROPAGATION**: Every `setContent`, `ComposeView`, dialog, sheet, or preview **MUST** explicitly pass `themeMode = preferenceManager.getTheme()` to `LaTorrentolaTheme(themeMode = themeMode)`. Note that `LaTorrentolaTheme` automatically delegates to `TvLaTorrentolaTheme` when `context.isTvDevice()` is true. Omitting `themeMode` breaks dark/light theme resolution.
* **🔒 PURE COMPOSE DIALOGS**: Never use `AlertDialog.Builder`. All dialogs/modals **MUST** be Compose overlays wrapped in proper theming:
  - Handheld: `LaTorrentolaTheme(themeMode = preferenceManager.getTheme())`
  - TV: `TvLaTorrentolaTheme` (provides both Material3 & TV Material3 scopes)
* **🔒 OPAQUE CONTAINERS**: Dialogs/BottomSheets containing text **MUST** use `surfaceContainerHigh` (alpha ≥ 0.95f). No semi-transparent backgrounds allowing text bleed-through.

### 📺 TV Navigation & Focus
* **🔒 SINGLE VERTICAL LAYOUT (TV)**: On `isTv == true`, `TopAppBar`, filter chips, and content lists **MUST** be direct siblings in a single `Column`. No floating overlays (breaks D-pad focus).
* **🔒 NATIVE TV COMPONENTS**: Use `androidx.tv.material3` components (`TvChip`, `TvSurface`, etc.) **exclusively** for TV UI. Never use phone M3 touch components (`FilterChip`, etc.) on TV.
* **🔒 DETERMINISTIC FOCUS LINKS**: All action icons in `TopAppBar` **MUST** define `focusProperties { down = nextFocusRequester }` to prevent focus dead zones.
* **🔒 BIDIRECTIONAL LIST FOCUS**: Navigation **up** and **down** between lists/grids and companion action buttons (such as bottom-right "Cancel" buttons) **MUST** go to and return from the immediate adjacent element (e.g., navigating **up** from a Cancel button returns to the **last item** of the list, not the first item), maintaining sequential adjacency. Exceptions apply only when explicitly requested (such as YouTube trailer isolation).
* **🔒 FOCUS TRAPPING / BOUNDARY PREVENTION**: D-pad navigation **MUST NEVER** lose focus or escape container bounds when reaching the first (`up`) or last (`down`/`right`/`left`) item in a list, grid, or dialog. Focus edges must either wrap or explicitly intercept and constrain navigation to valid elements within the component.
* **🔒 YOUTUBE ISOLATION**: `YouTubePlayerView` **MUST** have `isFocusable = false`, `isFocusableInTouchMode = false`, and `descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS`. Overlay with native `IconButton` for controls.

### 🖼️ Visual Consistency
* **🔒 FOCUSED POSTER TREATMENT**: Selectable posters **MUST** use 1.1x zoom (`focusedScale = 1.1f`) with **no** circular indicators or extra borders unless the established reference uses them.
* **🔒 PREVIEW COVERAGE**: Every UI component/screen **MUST** have `@Preview` for:
  - Light/Dark themes
  - Handheld (360x640), Tablet/Wide (600x800), TV/Chromecast (960x540)
  - Wrapped in correct theme (`LaTorrentolaTheme` for handheld/shared, `TvLaTorrentolaTheme` for TV-specific)

### ⚡ Architecture Integrity
* **🔒 DUAL SERIALIZATION**: All models shared with Navigation/Firestore **MUST** have `@Serializable` (kotlinx.serialization) and `@SerializedName` (Gson).
* **🔒 ROOM CONVERTERS**: Update `database/Converters.kt` when adding nested collections to Room entities.
* **🔒 CREDENTIAL SAFETY**: Never hardcode API keys/Web Client IDs. Use `local.properties` → `buildConfigField` → `BuildConfig`.

---

## ⚠️ STRONG REQUIREMENTS (Consult if Deviating)
*These require adherence unless a documented functional need exists. Deviations require human consultation.*

### 🧩 Component Implementation
* **⚠️ MATCHED COMPONENT STYLES**: Equivalent UI across Movies/TV/Handheld **MUST** share:
  - Layout structure
  - Typography/sizing
  - Semantic colors (from theme tokens)
  - Shape/spacing
  - Loading/empty/error states
  - **Exception**: Only deviate if function requires it (e.g., TV needs larger touch targets). **Consult before implementing.**
* **⚠️ TV COMPONENT VISUAL MATCH**: When using TV-native components:
  - Configure them to **visually match** their Movies counterparts using theme tokens
  - Example: `TvChip` should use same focus indicator scale/color as `FilterChip` in Movies
  - **Do not** assume TV defaults match Movies – explicitly set colors/shapes from `MaterialTheme`
* **⚠️ GLASS BLUR (HAZE 2.0)**:
  - Apply `Modifier.hazeGlass(input = HazeInput.Sources(hazeState))` to **entire header** (TopAppBar + chips)
  - Scrollable content **MUST** use `fillMaxSize()` with `hazeSource(state = hazeState)` and proper `contentPadding`
  - Animate `hazeAlpha` via `derivedStateOf { gridState.firstVisibleItemIndex > 0 || offset > 10 }`
  - Pre-API 12: Fallback to `surface.copy(alpha = 0.9f)` (not `Color.Transparent`)
* **⚠️ LANGUAGE FILTERING**: All movie catalog listings **MUST** apply `MovieFilter.filterMovies(...)` before display.
* **⚠️ PLACEHOLDER ALIGNMENT**: Skeleton states **MUST** use identical `contentPadding` as loaded content.

### 📦 Data & Persistence
* **⚠️ DOWNLOAD RECORDS**: `DownloadedMovie` **MUST** populate: `movieId`, `movieTitle` (or ""), `quality`, `hash`, `timestamp`, `movie`.
* **⚠️ TORRENT MODE ROUTING**: Quality selection **MUST** branch on `TorrentHandlingMode`:
  - `EXTERNAL_CLIENT`: Direct magnet `Intent.ACTION_VIEW` + `CATEGORY_BROWSABLE` (no chooser)
  - `LOCAL_PLAYBACK`/`CHROMECAST`: Start `TorrentDownloadService` → `LocalPlayerActivity`
* **⚠️ SUBTITLE CREDENTIALS**: OpenSubtitles credentials **MUST** be encrypted via `Android Keystore` (`OpenSubtitlesCredentialStore`). Never plaintext.
* **⚠️ WATCH HISTORY**: Playback progress **MUST** persist locally first (Room) → sync to Firestore background.

---

## 💡 BEST PRACTICES (Consult for Major Changes)
*These represent current app patterns. Consult when considering alternatives.*

### 📱 Adaptive Layouts
* Use `WindowSizeClasses` or `Material3` breakpoints for adaptive UIs
* For multi-pane: Prefer `Navigation3 Scenes` with `PaneScaffold`
* Adjust target sizes for input devices (touch vs. mouse/keyboard)
* **Consult before**: Changing breakpoint values or pane navigation patterns

### 🎯 Focus & Interaction
* On TV: Use `focusHighlight()` modifier for interactive elements
  - Circular elements: 2.dp border with `colorScheme.primary` when focused
* Share `scrollState` between headers/lists for synchronized scroll
* Add `focusRestorer()` to lists/grids to maintain focus after data changes
* **Consult before**: Modifying focus behavior beyond established patterns

### 🎬 Media Playback
* Native file streaming: Use `Uri.fromFile(file)` for 100% responsive seeking
* Partial streams: Use `VerifiedTorrentHttpServer` with `setShowRewindButton(true)`/`setShowFastForwardButton(true)`
* Cast handoff: `LocalPlayerActivity` switches `ExoPlayer`/`CastPlayer` based on session availability
* **Consult before**: Altering torrent playback architecture or HTTP server logic

### 🔔 Notifications & Sync
* FCM token sync with backend via `FcmRepository`
* Deep-link routing from push notifications
* **Consult before**: Changing notification payload structure or sync logic

---

## ❓ AREAS REQUIRING HUMAN JUDGMENT
*Pause and consult when encountering these situations:*

1. **Theme Conflicts**: When `LaTorrentolaTheme` and `TvLaTorrentolaTheme` tokens appear to contradict for a specific UI state
2. **Component Divergence**: When implementing a UI element with no clear counterpart in the other form factor (Movies vs. TV)
3. **Navigation Exceptions**: When considering non-standard navigation patterns (e.g., conditional deep links outside `AppNavigation`)
4. **Performance Trade-offs**: When optimizing might violate a mandate (e.g., skipping a theme lookup for frame rate)
5. **Ambiguous Requirements**: When user request lacks specificity about form factor scope, theme behavior, or interaction details

*When in doubt, use:*  
`ask_user(question = "Clarify [specific ambiguity]", answer_type = "single_choice", options = ["Option A", "Option B", "Need more context"])`

---

## 📐 ARCHITECTURE REFERENCE
*(Unchanged from original - preserved for context)*

### Essential File Locations
| Category | File Path | Description |
| :--- | :--- | :--- |
| **Entry & Navigation** | [MainActivity.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/MainActivity.kt)<br>[AppNavigation.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/navigation/AppNavigation.kt) | App entry, Intent handling ("PELI" extra), auth-gated routing. |
| **Auth & Cloud Sync** | [AuthRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/AuthRepository.kt)<br>[UserLibraryRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/UserLibraryRepository.kt)<br>[LoginScreen.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/auth/LoginScreen.kt) | Google credential sign-in and Firestore library persistence. |
| **Networking & DI** | [YtsService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/YtsService.kt)<br>[YtsRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/YtsRepository.kt)<br>[NetworkModule.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/di/NetworkModule.kt) | YTS REST API repository and Hilt singleton bindings. |
| **TV Catalog & Data** | [TvHomeViewModel.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/home/TvHomeViewModel.kt)<br>[TvGenreResultsViewModel.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/home/TvGenreResultsViewModel.kt)<br>[TmdbRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/TmdbRepository.kt)<br>[TmdbService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/TmdbService.kt) | TMDB TV feeds, genre discovery, seasons, episodes, and linked IMDb identifiers. |
| **TV Screens & Downloads** | [TvCatalogComponents.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/TvCatalogComponents.kt)<br>[TvDetailScreen.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/detail/TvDetailScreen.kt)<br>[TvEpisodeDetailScreen.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/detail/TvEpisodeDetailScreen.kt)<br>[EztvRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/EztvRepository.kt) | Responsive TV catalog/detail UI, episode torrent search, and direct magnet launch. |
| **Shared UI** | [Chips.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/Chips.kt)<br>[MovieItem.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/MovieItem.kt)<br>[MovieList.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/MovieList.kt)<br>[Placeholders.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/Placeholders.kt)<br>[QualityChoiceDialog.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/QualityChoiceDialog.kt) | Reusable design system components and skeleton screens. |
| **In-App Torrent & Playback** | [TorrentDownloadService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/TorrentDownloadService.kt)<br>[VerifiedTorrentHttpServer.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/VerifiedTorrentHttpServer.kt)<br>[LocalPlayerActivity.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/player/LocalPlayerActivity.kt)<br>[TvPlayerViewModel.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/player/TvPlayerViewModel.kt)<br>[TorrentDownloadsScreen.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/downloads/TorrentDownloadsScreen.kt)<br>[TorrentHandlingMode.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/model/torrent/TorrentHandlingMode.kt)<br>[TorrentDownloadDao.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/database/TorrentDownloadDao.kt) | libtorrent4j foreground download service, verified HTTP streaming, ExoPlayer+Cast activity, in-app download manager, mode enum, and Room DAO. |
| **Subtitles** | [OpenSubtitlesRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/OpenSubtitlesRepository.kt)<br>[OpenSubtitlesService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/OpenSubtitlesService.kt) | Subtitle search and download via OpenSubtitles API; credentials stored in Android Keystore. |
| **Push Notifications** | [FcmRepository.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/FcmRepository.kt)<br>[FcmService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/FcmService.kt)<br>[MyFirebaseMessagingService.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/MyFirebaseMessagingService.kt)<br>[FirebaseMessagingInitializer.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/FirebaseMessagingInitializer.kt) | FCM token sync with backend; push notification handling and deep-link routing. |
| **Utilities** | [UiText.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/utils/UiText.kt)<br>[DeviceUtils.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/utils/DeviceUtils.kt)<br>[MovieFilter.kt](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/utils/MovieFilter.kt) | `GenreTranslation` mapper, `Context.isTvDevice()`, language/rating filters. |

---

## ✅ UPDATED PR REVIEW CHECKLIST
*Verify these before marking PR ready:*

- [ ] **🔒 Color/Theme**: Zero hardcoded colors; all colors from `MaterialTheme`/`TvMaterial3` tokens; `themeMode` properly propagated
- [ ] **🔒 TV Navigation**: Single vertical column on TV; native TV components used; deterministic focus links set
- [ ] **🔒 Dialogs/Sheets**: Pure Compose implementations; opaque containers (`surfaceContainerHigh` ≥ 0.95f alpha)
- [ ] **⚠️ Component Matching**: Equivalent UI across form factors shares layout/typography/spacing/state handling (deviations documented & consulted)
- [ ] **⚠️ TV Visual Match**: TV-native components configured to visually match Movies counterparts via theme tokens
- [ ] **🔒 Focused Posters**: 1.1x zoom only; no circular indicators/borders unless reference uses them
- [ ] **⚠️ Glass Blur**: Applied to full header; animated ease-in/out; pre-API two fallback correct
- [ ] **🔒 Magnet Links**: Direct `Intent.ACTION_VIEW` + `CATEGORY_BROWSABLE`; no chooser
- [ ] **⚠️ Download Records**: `DownloadedMovie`/`DownloadedEpisode` fully populated per spec
- [ ] **⚠️ Torrent Mode**: Quality selection correctly branches on `TorrentHandlingMode`; no path crossing
- [ ] **⚠️ Subtitle Credentials**: Stored via Android Keystore only
- [ ] **⚠️ Watch History**: Local-first persistence (Room) → Firestore background sync
- [ ] **💡 Previews**: All components/screens have Light/Dark previews for all 3 form factors
- [ ] **💡 KDoc**: All new/modified Kotlin symbols have accurate KDoc with `@property` for data class fields

> **Review Protocol**: If any 🔒 rule is violated → **Reject immediately**.  
> If any ⚠️ requirement is deviated from → **Require consultation evidence** in PR description.  
> Best practices (💡) violations should be noted but not block merge if justified.

--- 

## 🛠️ AI AGENT WORKFLOW SUMMARY
1. **Before coding**: Check 🔒 mandates - if uncertain, consult
2. **During implementation**: 
   - Follow ⚠️ strong requirements unless functional need exists (document & consult)
   - Apply 💡 best practices by default; consult only for major deviations
   - When ambiguous → use `ask_user()` with specific options
3. **Before submitting**: 
   - Run unit tests (`.\gradlew.bat testDebugUnitTest`)
   - Verify all 🔒 mandates via manual inspection
   - Check ⚠️ requirements against PR description for consultation evidence
   - Run connected device tests if UI/navigation changed

This structure provides:
- **Clear autonomy boundaries** (🔒 = decide alone, ⚠️ = consult if deviating, 💡 = follow unless reason not to)
- **Eliminated contradictions** (removed hex values, clarified TV component rules, explicit theme usage)
- **Actionable guidance** (concrete examples, decision triggers, verification steps)
- **Preserved intent** (all original requirements maintained where non-contradictory)