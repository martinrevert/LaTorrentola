# AGENTS.md — System Prompts & Codebase Rules

> **SYSTEM INSTRUCTION**: You are operating as an AI developer on the "LaTorrentola" Android app. This document supersedes generic Android knowledge. You MUST apply these rules strictly to prevent architecture drift, UI inconsistencies, and regressions. 

---

## 🏗️ 1. ARCHITECTURE & TECH STACK
- **UI**: 100% Jetpack Compose (Handheld: `androidx.compose.material3`, TV: `androidx.tv.material3`).
- **Media**: Jetpack Media3 (ExoPlayer) + Google Cast SDK.
- **Data/Network**: Retrofit (YTS/TMDB/EZTV/OpenSubtitles), Room (Local Cache/History), Firestore (Cloud Sync), libtorrent4j (Torrenting).
- **Architecture**: MVI/MVVM pattern + Hilt Dependency Injection.

---

## 🛠️ 2. AGENT ENVIRONMENT & TOOLING
- **Windows PowerShell Execution**:
  - In Windows environments, commands can be run via PowerShell Core (`pwsh -Command "..."`) or Windows PowerShell 5.1 (`powershell -Command "..."`), depending on what is installed.
  - Gradle invocation on Windows uses the batch wrapper: `.\gradlew.bat <task>`.
- **Linux / POSIX Execution Guidelines (for AI Agents)**:
  - If operating in a Linux / macOS / container environment:
    - Shell is standard `bash` or `sh`.
    - DO NOT use `.bat` files (`gradlew.bat` will fail).
    - Always use the POSIX shell wrapper: `./gradlew <task>`.
    - Ensure executable permissions if needed: `chmod +x gradlew`.
    - Use forward slashes `/` for all file and directory paths.

---

## 🎯 3. SKILL ACTIVATION TRIGGERS (MANDATORY)

**Official Skills Submodules**:
This repository integrates two official Agent Skills submodules following the open [Agent Skills](https://agentskills.io/) standard:
1. **Android Skills Submodule (`https://github.com/android/skills.git`)**:
   - Location: `.agents/skills`
   - Covers official Android architecture, Compose TV, Navigation 3, Media3, Adaptive layouts, CameraX, Wear OS, LiteRT, R8 optimization, and Android Intent/Permissions security.
2. **Firebase Agent Skills Submodule (`https://github.com/firebase/agent-skills.git`)**:
   - Location: `.agents/firebase-skills`
   - Covers Firebase Authentication, Cloud Firestore data modeling & queries, Firestore security rules authoring & auditing, Firebase Crashlytics, Analytics, Remote Config, Firebase AI Logic (Gemini API), and CLI setup.

**IDE & Agent Skills Integration (`.agents/skills` and `.android-studio/skills`)**:
- Skills are managed via Git Submodules at `.agents/skills` and `.agents/firebase-skills`.
- To ensure full compatibility across all environments (Android Studio Quail/Agent Mode, Antigravity IDE, Claude Code, Cursor, Codex, and Gemini CLI):
  - `.android-studio/skills` links to `.agents/skills` via junction (Windows) or symlink (Linux/macOS).
  - `.agents/skills/firebase` links to `.agents/firebase-skills/skills` via junction (Windows) or symlink (Linux/macOS).
  - This guarantees that both Android platform skills AND Firebase skills are recursively discovered and immediately visible in both `.android-studio/skills/**/SKILL.md` and `.agents/skills/**/SKILL.md`.

**New Development Machine Setup**:
- **Windows (PowerShell / Command Prompt)**:
  ```powershell
  git submodule update --init --recursive
  if (!(Test-Path .android-studio\skills)) { cmd /c "mklink /J .android-studio\skills .agents\skills" }
  if (!(Test-Path .agents\skills\firebase)) { cmd /c "mklink /J .agents\skills\firebase .agents\firebase-skills\skills" }
  ```
- **Linux / macOS (POSIX Bash)**:
  ```bash
  git submodule update --init --recursive
  mkdir -p .android-studio
  [ -e .android-studio/skills ] || ln -s ../.agents/skills .android-studio/skills
  [ -e .agents/skills/firebase ] || ln -s ../firebase-skills/skills .agents/skills/firebase
  ```
- **Pulling Upstream Skill Updates**:
  ```bash
  git submodule update --remote --merge .agents/skills
  git submodule update --remote --merge .agents/firebase-skills
  ```

**MANDATORY DOMAIN TRIGGER MATRIX**:
**IF** your task involves any of the following domains, **THEN** you MUST load the corresponding Android Studio / Antigravity Skill via the `read_file` tool on the skill's path before writing code:

- `isTvDevice() == true`, `androidx.tv`, or D-pad Focus -> **LOAD:** `leanback-to-compose-tv-migration`
- `LocalPlayerActivity`, `ExoPlayer`, Chromecast -> **LOAD:** `media3-cast-integration`
- `AppNavigation.kt`, Deep Links, TypeSafe Routes -> **LOAD:** `navigation-3`
- Responsive UI (Phone/Tablet/Foldable), System Bars -> **LOAD:** `adaptive` AND `edge-to-edge`
- `AuthRepository`, Google Sign-In, Firebase Auth -> **LOAD:** `firebase-auth-basics` AND `firebase-basics`
- Firestore database, Cloud Sync, Room-Firestore bridge -> **LOAD:** `firebase-firestore` AND `firebase-basics`
- Firestore security rules, validation -> **LOAD:** `firestore-rules-creation` AND `firebase-security-rules-auditor`
- Crashlytics, Crash Reporting -> **LOAD:** `firebase-crashlytics`
- Analytics -> **LOAD:** `firebase-analytics`
- Remote Config -> **LOAD:** `firebase-remote-config-basics`
- Firebase AI Logic / Gemini on-device / Cloud -> **LOAD:** `firebase-ai-logic-basics`
- Magnet links (`Intent.ACTION_VIEW`) -> **LOAD:** `android-intent-security`
- ProGuard, R8, Shrinking, Build Optimization, Keep Rules -> **LOAD:** `performance/r8-analyzer`
- Testing setup, harnesses -> **LOAD:** `testing-setup`
- Camera / CameraX -> **LOAD:** `camerax`
- Wear OS -> **LOAD:** `wear-compose-m3`, `wear-widgets`
- LiteRT / On-device ML -> **LOAD:** `litert-runtime`, `litert-lm`
- ML Kit GenAI Agent / Prompt API -> **LOAD:** `ml-kit-genai-prompt-api`, `mlkit-agent-api`
- Haze, Glassy Blur, hazeSource, hazeGlass, Frosted Glass, dev.chrisbanes.haze -> **LOAD:** `.agents/haze-skills/SKILL.md` AND `jetpack-compose-performance`

---

## 🧪 4. UNIT TESTING & VALIDATION MANDATE (ZERO EXCEPTIONS)
**RULE**: You MUST write or update Unit Tests concurrently with any functional change.
1. **No APK Building Required**: AI agents **DO NOT** need to assemble or build release/debug APKs (`assembleRelease`, `assembleDebug`). Running unit tests is sufficient validation after finishing changes or executing a plan:
   - **Windows**: `pwsh -Command ".\gradlew.bat testDebugUnitTest"` or `powershell -Command ".\gradlew.bat testDebugUnitTest"`
   - **Linux / macOS**: `./gradlew testDebugUnitTest`
2. **Scope**: Create tests for ViewModels, Repositories, UseCases, and Utility classes. UI tests are required for complex Compose logic.
3. **Frameworks**: Use JUnit4, MockK, Coroutines `runTest`, and Turbine (for Flows).
4. **Location**: Place tests in `app/src/test/...` mirroring the package structure of the target class.
5. **Execution**: If you are in planning/execution mode, do not call `set_plan_state(COMPLETED)` until unit tests are written and verified.

---

## 📚 5. ON-THE-FLY LIVING DOCUMENTATION & AUTONOMOUS DIRECTIVE HARDENING (STRICT MANDATE)

### A. Mandatory On-The-Fly Documentation Synchronization
Every architectural, feature, UI, or configuration change MUST be reflected across all project documentation files **on the fly** in the very same turn:
- **`README.md`** (Global User Guide - English): Keep updated with human-friendly descriptions of all user-facing features, settings, and step-by-step instructions.
- **`README.es.md`** (Spanish User Guide - Español): Keep in exact structural and conceptual parity with `README.md`.
- **`ARCHITECTURE.md`** (Technical Manual - English): Keep updated with all deep technical details, system designs, APIs, audio/streaming pipelines, data flows, and Mermaid diagrams (ensuring 100% valid syntax without HTML tag parsing errors).
- **`AGENTS.md`** (System Rules & Guidelines): Keep updated whenever a new architectural rule, pattern, or constraint is established.
- **PROHIBITION**: AI agents are **STRICTLY FORBIDDEN** from deferring documentation to future tasks or finishing functional code changes without updating the corresponding documentation files. Documentation synchronization is a non-negotiable part of the Definition of Done.

### B. Autonomous Directive Hardening on Regressions & Failure Modes
Whenever a regression, bug, edge-case failure, race condition, build error, or architectural drift is encountered, triaged, or resolved:
- The AI agent **MUST AUTOMATICALLY and IMMEDIATELY** record a new mandatory directive or rule in `AGENTS.md`.
- Each new directive must explicitly define:
  1. **The Root Cause / Failure Trap**: What went wrong and why it caused an issue.
  2. **The Mandatory Architectural Contract**: The exact pattern, code structure, or invariant to uphold.
  3. **Explicit Prohibitions**: A clear `PROHIBITION: NEVER ...` clause to forbid the problematic behavior.
  4. **Checklist Integration**: A corresponding verification item added to the Execution Checklist in Section 8.
- **PURPOSE**: Ensure continuous, institutional learning so that future AI agents and developers never repeat past regressions.

---

## 🚫 6. ZERO TOLERANCE CONSTRAINTS (DO NOT VIOLATE)

### A. Material Design 3 (M3) & Material 3 Expressive (M3E) UI Mandate
- **ABSOLUTE ZERO-TOLERANCE FOR MATERIAL 2 (M2)** 
 - ❌ **STRICTLY FORBIDDEN IMPORTS**: Never import `androidx.compose.material.*` (Legacy Compose M2). 
 - ❌ **BANNED M2 COMPONENTS/TOKENS**: `BottomNavigation`, `BottomNavigationItem`, `FlatButton`, `ModalDrawerLayout`, `MaterialTheme.colors`, `ContentAlpha`, `LocalContentAlpha`. 
 - ✅ **MANDATORY M3 IMPORTS**: Always use `androidx.compose.material3.*` for Handheld UI and `androidx.tv.material3.*` for TV UI.
- **THEMING & COLOR SCHEME**
 - ALWAYS extract colors from `MaterialTheme.colorScheme` (Mobile) or `TvMaterialTheme.colorScheme` (TV). 
 - **EXCEPCIÓN EXPLÍCITA DE COLOR (STREAMING RED)**: Las barras/líneas de progreso de historia de reproducción y scrubbers/sliders de línea de tiempo DEBEN ser rojas: `Color(0xFFE50914)`. Esta es la única excepción formal autorizada a los tokens de color dinámicos de M3. 
 - NEVER hardcode strings. ALWAYS use the `UiText` wrapper or `strings.xml`. 
- **PROACTIVE MATERIAL 3 EXPRESSIVE (M3E) RECOMMENDATIONS (HANDHELD ONLY)**:
 When refactoring or creating Handheld Compose UI (`!context.isTvDevice()`), proactively suggest and apply M3 Expressive tactics: 
 - **Hero Moments & High-Impact Actions**: Suggest `SplitButton`, `FABMenu`, or `FloatingToolbar` for quick actions.
 - **Dynamic Shapes & Morphing**: Suggest M3E expanded shapes (35 new geometric shapes) and shape-morphing transitions for selected/active states. 
 - **Spring Physics Motion**: Prefer spring-based motion (`spring()`) over fixed linear durations for fluid gesture-driven UI. 
 - **Containment & High-Contrast Hierarchy**: Group related content inside explicit rounded containers (`surfaceContainer`) to eliminate click uncertainty and improve scanning speed. 
 - **PROHIBITION FOR TV**: DO NOT apply touch-centric M3 Expressive floating toolbars or gesture springs to Android TV layouts (`isTvDevice() == true`), as they break D-pad focus routing.

### B. TV & UI Branching and Theme Mode Propagation (STRICT MANDATE)
- **MANDATORY THEME MODE PROPAGATION**: Every Compose entry point (`setContent`), `Dialog`, `BottomSheet`, `@Preview`, or isolated Window MUST explicitly resolve and pass `themeMode` from `PreferenceManager` to BOTH theme wrappers:
  ```kotlin
  val themeMode = remember(context) { PreferenceManager(context).getTheme() }
  if (context.isTvDevice()) { 
      TvLaTorrentolaTheme(themeMode = themeMode) { /* TV UI */ } 
  } else { 
      LaTorrentolaTheme(themeMode = themeMode) { /* Mobile UI */ } 
  }
  ```
- **CRITICAL DIALOG/WINDOW THEME TRAP (DO NOT IGNORE)**: Compose `Dialog`s and `BottomSheet`s open in separate Android Window instances. Calling `TvLaTorrentolaTheme` without passing `themeMode = ...` WILL default to `THEME_SYSTEM` and silently break the user's selected Dark Mode preference on Android TV!
- **COMPONENT-LEVEL RULE**: Every reusable `@Composable` `Dialog`, `BottomSheet`, or standalone screen component MUST accept an optional `themeMode: Int? = null` parameter and compute:
  ```kotlin
  val effectiveThemeMode = remember(context, themeMode) {
      themeMode ?: PreferenceManager(context).getTheme()
  }
  ```
  Pass `effectiveThemeMode` into both `TvLaTorrentolaTheme(themeMode = effectiveThemeMode)` and `LaTorrentolaTheme(themeMode = effectiveThemeMode)`.
- **STRICT TV COMPONENTS**: If `isTvDevice() == true`, use `androidx.tv.material3.*` components exclusively. DO NOT use mobile interactive components on TV layouts (they break D-pad focus).

### C. Focus & Overlays (Android TV)
- **MODAL OPACITY**: Any Dialog/BottomSheet containing text MUST have an opaque background (Alpha ≥ `0.95f`, e.g., `surfaceContainerHigh`) to prevent text bleed-through.
- **DIALOG FOCUS TRAP**: Focus MUST NEVER escape a Dialog. Use the Compose `androidx.compose.ui.window.Dialog` composable (not a full-screen `Box`) to ensure the D-pad engine traps focus properly over Android Views.
- **YOUTUBE ISOLATION**: `YouTubePlayerView` MUST be blocked from D-pad focus (`isFocusable = false`, `descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS`). Overlay it with a transparent, focusable native `IconButton`.
- **NO CIRCLE/BORDER FOCUS SHAPES ON CHIPS**: No se deberá agregar en Android TV circle shape ni bordes/foco extra a los chips. El foco se indica únicamente mediante el escalado de `Surface` y el contraste de color del fondo (`focusedContainerColor`). Cuando algún objeto de navegación requiera un shape específico para indicar foco en TV, el desarrollador lo solicitará explícitamente en el prompt.

### D. Data Modeling & Secrets
- **DUAL SERIALIZATION**: Data classes shared between Retrofit/Firestore AND Compose Navigation MUST include both `@Serializable` (kotlinx) and `@SerializedName("...")` (Gson). Omitting one causes obfuscation crashes.
- **NO HARDCODED SECRETS**: API Keys/Credentials MUST be read from `BuildConfig` (via `local.properties`). Subtitle credentials MUST use the `OpenSubtitlesCredentialStore` (Android Keystore).

---

## 🚦 7. DOMAIN-SPECIFIC LOGIC RULES

### Torrent Routing (`TorrentHandlingMode`)
When triggering a download/play action, read `TorrentHandlingMode` and execute:
- `EXTERNAL_CLIENT`: Fire `Intent.ACTION_VIEW` with the Magnet URI + `CATEGORY_BROWSABLE` (No app chooser).
- `LOCAL_PLAYBACK` or `CHROMECAST`: Start `TorrentDownloadService` -> Launch `LocalPlayerActivity`.
- *Note:* If playing a partially downloaded torrent, you MUST use `VerifiedTorrentHttpServer`.

### Data Sync Flow & Firestore Cost Minimization (Room as SSOT)
- **Room as the Primary Single Source of Truth (SSOT)**:
  - All user library state (`favorites_movies`, `favorites_tv`, `downloaded_movies`, `downloaded_episodes`, `watch_history`) lives primarily in local Room tables.
  - The UI and ViewModels MUST observe Room exclusively via reactive Kotlin Flows (`favoriteDao`, `downloadRecordDao`, `watchHistoryDao`).
  - **PROHIBITION: NEVER attach real-time `addSnapshotListener` listeners to Firestore collections in ViewModels or UI screens during normal navigation.** Attaching continuous listeners burns through the Google Cloud Free Tier (50,000 reads/day) within hours.
  - Firestore is strictly an asynchronous backup and cross-device sync layer (`syncFromCloud` bootstrap/delta sync and on-demand outbound writes).
  - Firestore SDK MUST configure `PersistentCacheSettings` instead of in-memory caching to reuse disk-cached documents across cold boots.
- **Multi-Account & Shared Device Data Isolation (OTT Pro Pattern)**:
  - To support shared family devices (Android TV, Chromecast with Google TV, shared tablets), Room partitions library and watch history entries by `userId` (composite primary keys `[userId, id]` / `[userId, mediaId]`).
  - The `saved_accounts` table remembers each authenticated Google account (`uid`, `displayName`, `email`, `photoUrl`, `lastActiveTimestamp`).
  - **PROHIBITION: NEVER wipe or delete Room tables on logout.** When a user logs out or switches accounts, local data for all accounts MUST be preserved so returning to a profile displays its history and favorites instantly with zero network roundtrips.
  - **PROHIBITION: NEVER run unpartitioned Room queries for user data.** All queries in DAOs observing or querying watch history and downloads (`observeAll`, `getAll`) MUST filter strictly by `userId`. Guest mode MUST query exclusively `userId = ''` to prevent cross-account history exposure.
  - **Legacy Claiming Invariant**: When a user logs in from an unauthenticated/guest state, unassigned local records (`userId = ''`) MUST be claimed atomically (`claimUnassignedItems(userId)`) to prevent local pre-migration watch history from vanishing.
  - **Android TV Dialog Focus Trap Mandate**: Confirmation and selection overlays on Android TV MUST ALWAYS be wrapped in `androidx.compose.ui.window.Dialog(onDismissRequest = ...)` instead of uncontained full-screen `Box` overlays so Compose's 2D D-pad focus dispatcher is captured and back navigation functions properly.
- **Watch History**: Write to Room DAO FIRST (Immediate UI update) -> Sync to Firestore in the background.
- **Cast Player History Sync**: Cast Player MUST follow the exact same history saving patterns as ExoPlayer: save every 10 seconds while playing, save immediately on pause, and save immediately upon session disconnect/stop/destroy using the remote position. When handoff back to ExoPlayer occurs, ExoPlayer MUST seek to this synchronized position so watch history is pure, uninterrupted, and all UI progress indicators match reality.

### 🎬 Canonical Watch History, Room & Firestore Pure IMDb ID Standard (USER MANDATE - ZERO REGRESSION)
To guarantee vendor independence (preventing vendor lock-in to TMDB or YTS) and seamless interoperability between Torrents, Network Storage (NAS / SMB / NFS / USB), and Local Playback, the storage format in Room and Firestore adheres purely and exclusively to global industry-standard IMDb identifiers. All previous migration and retrocompatibility routines have been fully disassembled as all users in Room and Firestore are 100% migrated:
1. **Canonical ID Definitions (MANDATORY)**:
   - **Movies**: `mediaId` (and document ID in Firestore) MUST ALWAYS be the canonical IMDb ID (e.g. `"tt0111161"` for The Shawshank Redemption). Only canonical IMDb IDs are permitted (pure IMDb format, no `tmdb_` or numeric fallbacks).
     - **Relational Integrity**: Retain the numeric `movieId` internally across entities and models for relational consistency with existing catalog APIs.
     - **PROHIBITION**: NEVER save raw 40-character torrent infoHashes, raw file names, or fallback TMDB IDs as movie `mediaId`.
   - **TV Series Episodes**: `mediaId` MUST ALWAYS adhere to the canonical mnemonic:
     `"${imdbId}_s${seasonNumber}_e${episodeNumber}"` (e.g. `"tt0944947_s1_e2"` for regular episodes, `"tt0944947_s0_e1"` for specials / Season 0), where `imdbId` is the series IMDb ID, and `seasonNumber >= 0` and `episodeNumber > 0`. Only canonical IMDb IDs are permitted (pure IMDb format, no `tmdb_` or legacy `media_` fallbacks).
     - **Season 0 Specials Canonical Integrity**: Season 0 episodes MUST be resolved and stored as `${imdbId}_s0_e${episodeNumber}` instead of saving raw title strings.
     - **Season 0 Series Progress Exclusion**: Season 0 episodes are valid individual episodes with their own chips in detail views (which can be marked partial, completed, or unwatched), but MUST NOT count towards whole-series completion percentages or poster progress bars. Series-level completion and progress bars strictly evaluate regular seasons (`seasonNumber >= 1`).
     - **Relational Integrity**: Retain `seriesId` (TMDB series ID) internally across all models, DTOs, and entity columns for relational navigation.
2. **Pure Single-Key Lookups & Zero Data Loss**:
   - `AppDatabase` v15 and Firestore collections (`users/{uid}/watch_history`, `downloads`, `favorites_movies`, `favorites_tv`) operate strictly with canonical IMDb keys.
   - All multi-key candidate lookups and defensive deletions have been cleanly dismantled: queries directly reference `(userId, mediaId)`.
3. **Mandatory Inverse Resolution for External Media (NAS / USB / Local Files)**:
   - When media is played from network shares (SMB/NFS) or external storage, or when metadata IDs are absent from a torrent:
     - The filename and stream title MUST be parsed via `MediaMetadataResolver` using regex (`extractSeasonAndEpisode`) and release tag cleaning (`extractCleanSearchTitles`).
     - **If Episode**: Asynchronously resolve `seriesId` and `imdbId` via `tmdbRepository.searchTvSeries(cleanTitle)` before finalizing `mediaId`.
     - **If Movie**: Asynchronously resolve `movieId` and `imdbCode` via `ytsRepository.searchMovies(cleanTitle, 1)` (fallback `tmdbRepository.searchMovies`) before finalizing `mediaId`.
     - This guarantees that watching a video on NAS saves progress to the exact same document as playing it via Torrent, enabling uninterrupted cross-source resume.
4. **Decoupled Architecture & UI Components**:
   - All watch progress bars in catalog and thumbnails MUST use the shared `@Composable WatchProgressBar(progressPercent = ...)`.
   - All player timeline scrubbers MUST use `@Composable WatchProgressSlider(value = ...)`.
   - Resolution logic MUST reside in `MediaMetadataResolver`, keeping Activities and UI screens completely decoupled.
5. **Pure Canonical Operation & Dismantled Migration Scaffolding**:
   - Since all active users have completed migration to IMDb IDs, one-time migration routines (`cleanupAndMigrate*`, `migrateSingleItem`, `canonical_migration_v1`) and legacy fallbacks have been eliminated, ensuring lean and high-performance O(1) lookups.
6. **STRICT PROHIBITION FOR FUTURE AI AGENTS**:
   - Future AI agents are **STRICTLY FORBIDDEN** from modifying, altering, or re-introducing non-canonical keys (`tmdb_`, `media_`, numeric IDs, infoHashes) or persisting non-canonical sentinel keys (`"unknown_movie"`, `"unknown_episode"`, or blank) into Room, Firestore, `MediaIdHelper`, `MediaMetadataResolver`, `LocalPlayerActivity`, `TorrentDownloadService`, `PlaybackService`, and `UserLibraryRepository` under any circumstances unless explicitly requested and approved by the user.

### 🔖 Manual Watch Progress & Download Marking System (STRICT MANDATE)
When users trigger manual progress or completion marks via long-press gestures:
1. **Synchronized Progress & Download State**:
   - **Mark Completed** (`markMovieAsCompleted`, `markEpisodeAsCompleted`, `markSeasonAsCompleted`): MUST set 100% watch progress (`positionMs = 1000L`, `durationMs = 1000L`) in Room & Firestore AND save download records (`DownloadedMovie` / `DownloadedEpisode`) to Firestore (`users/{uid}/downloads` or `tv_downloads`), applying download badges in the UI.
   - **Mark Uncompleted** (`markMovieAsUncompleted`, `markEpisodeAsUncompleted`, `markSeasonAsUncompleted`): MUST delete watch progress from Room & Firestore AND remove download records from Firestore (`downloads` / `tv_downloads`) and Room (`torrent_downloads`), clearing download badges/indicators.
2. **Dual Touch & D-Pad Long-Press (`adaptiveLongClickable`)**:
   - **MANDATORY MODIFIER**: Always use `Modifier.adaptiveLongClickable(onClick, onLongClick)` for components supporting long press (season chips, episode cards, movie posters).
   - **NO CONFLICTING INTERNAL CLICKABLES**: DO NOT pass internal `onClick` lambdas to `Card(onClick = ...)` or `FilterChip(onClick = ...)` when attaching `adaptiveLongClickable`. Internal `Surface` clickables consume touch/key events and prevent long-press detection.
   - **D-PAD KEY REPEAT TRAPPING**: `adaptiveLongClickable` intercepts `Key.DirectionCenter`, `Key.Enter`, and `Key.NumPadEnter` holding down (`repeatCount > 0` or `isLongPress`) on Android TV, consuming key release so `onClick` is not fired after long-press.
3. **Favorites Tab Exclusion**:
   - Long press on the Favorites tab in `SearchScreen` is strictly reserved for multi-selecting items for bulk deletion. Do NOT override long press on the Favorites tab with the watch progress dialog.
4. **Dialog Theme & Focus Trapping**:
   - `WatchProgressOptionDialog` MUST explicitly pass `themeMode` resolved from `PreferenceManager` to `TvLaTorrentolaTheme` and `LaTorrentolaTheme` to prevent Dark Mode regressions.
   - On Android TV, dialogs MUST use `Dialog(properties = DialogProperties(usePlatformDefaultWidth = false))` with an opaque container surface ($\ge 0.95f$ alpha) and initial `FocusRequester` on option 1.

### 📺 Google Cast Subtitle Architecture & Stability Mandate (STRICT - DO NOT MODIFY)
When streaming media and subtitles to Google Cast receivers (Chromecast / Nest Hub / Google TV / Android TV):
- **STRICT ARCHITECTURE FREEZE**: AI agents are **STRICTLY PROHIBITED** from modifying, refactoring, or simplifying the Google Cast native `MediaInfo`/`MediaTrack` construction in `PlaybackService.kt`, or changing CORS headers in `VerifiedTorrentHttpServer.kt`, unless Google or Media3 updates introduce an explicit breaking API change that prevents compilation.

1. **Native `MediaInfo` & `MediaTrack` Construction**:
   - In [`PlaybackService.kt`](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/PlaybackService.kt), `toMediaQueueItem` MUST construct Google Cast SDK's native `MediaInfo` and `MediaQueueItem` objects directly using `MediaInfo.Builder` and `MediaTrack.Builder`.
   - **PROHIBITION**: DO NOT replace native `MediaInfo` / `MediaTrack` construction with generic `defaultConverter.toMediaQueueItem(castMediaItem)` calls. Generic conversion strips or misformats native `MediaTrack` payloads required by Chromecast CAF Web Receivers.
   - **Track Builder Pattern**: Text tracks MUST be built with `MediaTrack.Builder(index + 1L, MediaTrack.TYPE_TEXT)`, `.setSubtype(MediaTrack.SUBTYPE_SUBTITLES)`, `.setContentId(lanSubUrl)`, `.setContentType("text/vtt")`, `.setLanguage(lang)`, and `.setName(label)`.
   - **Active Track Flags**: `queueItemBuilder.setActiveTrackIds(longArrayOf(1L))` MUST be set on the `MediaQueueItem.Builder` so Chromecast automatically loads and renders track 1 upon playback start.

2. **HTTP LAN Subtitle Server & CORS (`VerifiedTorrentHttpServer`)**:
   - [`VerifiedTorrentHttpServer.kt`](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/VerifiedTorrentHttpServer.kt) MUST serve WebVTT subtitles with HTTP `200 OK`, `Content-Type: text/vtt`, and complete CORS headers:
     - `Access-Control-Allow-Origin: *`
     - `Access-Control-Allow-Methods: GET, OPTIONS, HEAD`
     - `Access-Control-Allow-Headers: *`
     - `Access-Control-Allow-Private-Network: true`
   - Preflight `Method.OPTIONS` requests MUST return `200 OK` with these exact CORS headers.
   - **NO DUPLICATE MP4 GENERATION**: DO NOT generate duplicate `.mp4` files or perform on-the-fly container remuxing in `TorrentDownloadService` or `VerifiedTorrentHttpServer`. Stream `.mkv`, `.webm`, or `.mp4` video files directly over HTTP with the matching MIME type (`video/x-matroska`, `video/webm`, or `video/mp4`).

3. **OpenSubtitles File Storage & Co-located Cleanup**:
   - Subtitles downloaded from OpenSubtitles MUST be saved inside `filesDir/torrent_downloads/<infoHash>/opensubtitles/<fileId>.vtt`.
   - **PROHIBITION**: DO NOT save torrent subtitles in `cacheDir` when `infoHash` is available or extractable. When deleting a torrent, deleting `filesDir/torrent_downloads/<infoHash>` MUST delete all associated video files and subtitle files in one single operation.
   - In [`LocalPlayerActivity.kt`](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/player/LocalPlayerActivity.kt), if `EXTRA_INFO_HASH` is blank, `torrentInfoHash` MUST be extracted automatically from the local file path or stream URL via regex (`[a-fA-F0-9]{40}`).

4. **Startup Subtitle Preloading**:
   - In [`LocalPlayerActivity.kt`](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/player/LocalPlayerActivity.kt), before calling `controller.setMediaItem(itemToPlay)`, check `openSubtitlesRepository.findLatestDownloaded(torrentInfoHash)`. If a cached subtitle exists, attach its `SubtitleConfiguration` to `itemToPlay` BEFORE starting playback so that the initial Cast `LOAD` request carries both the video and the subtitle track together.

### 🌐 Remote Media Storage (SMB / NFS) & Streaming Performance Mandate (STRICT)
When implementing, touching, or refactoring remote network storage (SMB / NFS) and streaming in `RemoteStorageManager`, `SmbStorageClient`, `NfsStorageClient`, `RemoteMediaDataSource`, `SmbPrefetchInputStream`, or `RemoteMediaHttpServer`:

1. **Persistent Session & Share Caching (MANDATORY)**:
   - SMB (`DiskShare` / `SmbSession`) and NFS (`NfsSession`) connections MUST be cached across playback calls in client session caches.
   - **PROHIBITION**: Closing an `InputStream` or seeking in a video stream MUST ONLY close the underlying `File` or file handle. It MUST **NEVER** close or disconnect the `DiskShare`, `SmbSession`, `Connection`, or `NfsSession`. Establishing SMB/NFS authentication and tree-connect handshakes on every read/seek incurs 5-7 network roundtrips (~150ms) and destroys streaming performance.

2. **Mandatory Asynchronous Prefetching (`SmbPrefetchInputStream`)**:
   - Remote streaming over high-latency or packet-based network protocols (especially SMB via `smbj`) MUST NOT use synchronous stop-and-wait `read()` calls directly on ExoPlayer's decoder thread.
   - All SMB remote streams MUST be wrapped with asynchronous prefetching (`SmbPrefetchInputStream` or equivalent) utilizing a dedicated background worker thread, minimum 16 MB chunk capacity, and recycled buffer pools to eliminate garbage collector pauses.

3. **ExoPlayer LoadControl Buffer Tuning**:
   - When configuring `DefaultLoadControl` in [`PlayerStreamingConfig.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/streaming/PlayerStreamingConfig.kt), `.setPrioritizeTimeOverSizeThresholds(true)` MUST be enabled.
   - Target buffers MUST be sized for high-bitrate 1080p/4K bursts (minimum 96 MB on TV, 64 MB on Mobile, with up to 120s forward buffer) with a dedicated back-buffer ($\ge 10\text{s}$) to absorb network jitter.

4. **Zero Hot-Path Logging**:
   - **PROHIBITION**: NEVER introduce `Log.d`, `Log.v`, or string formatting into hot read paths like `RemoteMediaDataSource.read()` or `SmbPrefetchInputStream.read()`. In Android, frequent logcat IPC calls during 32 KB / 64 KB read loops consume severe CPU time and trigger buffer underruns.

5. **Chromecast URI Proxy Requirement**:
   - Google Cast CAF receivers cannot resolve custom Android URI schemes (e.g. `remote://`).
   - All remote streams directed to Chromecast MUST be served via [`RemoteMediaHttpServer.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/storage/remote/RemoteMediaHttpServer.kt) with HTTP `200/206` range support, WebVTT subtitle support, and full CORS headers:
     - `Access-Control-Allow-Origin: *`
     - `Access-Control-Allow-Methods: GET, OPTIONS, HEAD`
     - `Access-Control-Allow-Headers: *`
     - `Access-Control-Allow-Private-Network: true`

### 🔊 Digital Audio Passthrough, Surround Transcoding & Hardware Pipeline Mandate (STRICT - ZERO REGRESSION)
To ensure digital bitstream passthrough to AVRs / soundbars over HDMI (ARC/eARC) and optical, and eliminate silent downmixes to stereo:
1. **Renderer Priority (NO `EXTENSION_RENDERER_MODE_PREFER`)**:
   - In [`PlayerStreamingConfig.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/streaming/PlayerStreamingConfig.kt), the extension renderer mode MUST be `EXTENSION_RENDERER_MODE_ON`, NEVER `EXTENSION_RENDERER_MODE_PREFER`.
   - **REASON**: `EXTENSION_RENDERER_MODE_PREFER` places `FfmpegAudioRenderer` before `MediaCodecAudioRenderer`. Since FFmpeg natively supports AC3, E-AC3, and DTS, it claims `FORMAT_HANDLED` and decodes the stream to PCM, completely bypassing the receiver's hardware bitstream passthrough.
   - **Correct Precedence**: Passthrough direct bitstream (`MediaCodecAudioRenderer` bypass) > Hardware MediaCodec decoding > FFmpeg software fallback.

2. **Surround Transcoding Guardrails (`Ac3TranscodingAudioSink`)**:
   - Transcoding (AAC 5.1 -> AC3, E-AC3 -> AC3, DTS -> AC3) MUST ONLY occur when:
     - The corresponding user preference (`convertAac51ToAc3`, `convertEac3ToAc3`, `convertDtsToAc3`) is enabled in `PreferenceManager`.
     - For E-AC3 and DTS: The connected audio sink does NOT support direct bitstream playback (`delegate.getFormatSupport(src) != SINK_FORMAT_SUPPORTED_DIRECTLY`).
     - The connected audio sink DOES support direct AC3 bitstream passthrough (`delegate.getFormatSupport(ac3Format) == SINK_FORMAT_SUPPORTED_DIRECTLY`).
   - If the receiver already supports the source codec natively, the stream MUST pass through untouched without invoking the software encoder.
   - Stereo streams (AAC 2.0, PCM 2.0) MUST NEVER be transcoded.

3. **Strict Prohibition on Video Tunneling & Audio Offload During Bitstream Passthrough (ZERO REGRESSION)**:
   - **Tunneling Stall Trap**: `setTunnelingEnabled(true)` forces `MediaCodecVideoRenderer` to synchronize video presentation to hardware audio clocks (`FLAG_HW_AV_SYNC`). However, Android TV audio HALs (MediaTek, Amlogic, Realtek) fail to generate hardware sync timestamps for IEC 61937 High Bit Rate (HBR) streams (especially DTS-HD Master Audio, DTS:X, and TrueHD). Because the audio clock never advances, the hardware video decoder pauses indefinitely, freezing video playback completely. Furthermore, tunneling delays initial video display on Dolby Atmos (E-AC3 JOC) while waiting for the HDMI eARC hardware handshake.
   - **PROHIBITION: NEVER enable global tunneled playback (`setTunnelingEnabled(true)`) when audio bitstream passthrough is supported or active.** Tunneling must remain disabled (`setTunnelingEnabled(false)`). Standard Media3 software AV sync clock tracking (`AudioTrack.getPlaybackHeadPosition()`) MUST be used for smooth, non-blocking video presentation.
   - **Audio Offload Buffer Bloat Trap**: `AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED` is intended for mobile screen-off music playback to let the CPU sleep. In video playback, it forces massive multi-second buffer prerolls, causing severe startup latency on Dolby Atmos (E-AC3 JOC) and risking `AudioFlinger` memory allocation failures (`-12` ENOMEM) on high-bitrate surround bitstreams.
   - **PROHIBITION: NEVER enable Audio Offload (`AUDIO_OFFLOAD_MODE_ENABLED`) for video streaming pipelines.** Audio offload must remain disabled (`AUDIO_OFFLOAD_MODE_DISABLED`).
   - **Fast TV Playback Buffer & Bounded Passthrough Allocations**:
     - In [`PlayerStreamingConfig.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/streaming/PlayerStreamingConfig.kt), `TV_BUFFER_FOR_PLAYBACK_MS` MUST be set to `2_000ms` (2.0s) and `TV_BUFFER_AFTER_REBUFFER_MS` to `3_000ms` for stable startup on Dolby Atmos and high-bitrate surround streams with a protective 3-second cushion on re-buffer to prevent stutter on volatile connections.
     - `DefaultAudioSink` MUST configure `AudioTrackAudioOutputProvider` with `DefaultAudioTrackBufferSizeProvider` setting `.setPassthroughBufferDurationUs(250_000)` (250ms) to ensure passthrough buffer allocations fit safely within Android TV `AudioFlinger` memory bounds without throwing `-12` (ENOMEM).

4. **DTS-HD (Master Audio / High Resolution / DTS:X) Passthrough Trap & User Decision Mandate (ZERO REGRESSION)**:
   - **The False-Positive EDID / Audio HAL Lockup Trap**: When an Android TV / Google TV is connected to an eARC AVR, the AVR advertises DTS-HD MA support in its HDMI EDID. Android OS reads this and includes `AudioFormat.ENCODING_DTS_HD` in `AudioCapabilities`. However, internal Android TV audio HALs (MediaTek, Amlogic, Realtek) often CANNOT transmit IEC 61937 High Bit Rate (HBR) bursts (up to 24.5 Mbps) across the TV's internal bridge from Android OS to the eARC transmitter. Opening `AudioTrack` with `ENCODING_DTS_HD` stalls the audio hardware clock (`AudioTrack.getPlaybackHeadPosition()` remains frozen at 0). Because video frames wait for audio presentation timestamps, the picture freezes permanently at 0s, the network buffer fills up to 96 MB, and playback halts completely.
   - **USER-CONTROLLED RESOLUTION WITH PROACTIVE ERROR RESILIENCE**:
     - Controlled by user preference `decode_dts_hd_to_pcm` in `PreferenceManager.kt` (`decodeDtsHdToPcm`, **defaults to `false`** so direct bitstream passthrough is attempted first).
     - When `decodeDtsHdToPcm == true`:
       - [`Ac3TranscodingAudioSink.getFormatSupport`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/streaming/Ac3TranscodingAudioSink.kt) returns `AudioSink.SINK_FORMAT_UNSUPPORTED` for DTS-HD formats.
       - [`PlayerStreamingConfig.buildRenderersFactory`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/streaming/PlayerStreamingConfig.kt) rejects DTS-HD in `MediaCodecAudioRenderer.supportsFormat` (`C.FORMAT_UNSUPPORTED_SUBTYPE`) and `shouldUseBypass`.
       - ExoPlayer automatically routes DTS-HD to [`FfmpegAudioRenderer`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/androidx/media3/decoder/ffmpeg/FfmpegAudioRenderer.java) (`dca` decoder), decoding the lossless bitstream into uncompressed multichannel LPCM (5.1/7.1) over eARC without hardware clock stalls, OR transcodes to AC3 5.1 if `convertDtsToAc3` is enabled in settings.
     - When `decodeDtsHdToPcm == false`:
       - Direct bitstream passthrough is attempted natively.
       - If bitstream passthrough fails or stalls (`startDtsHdStallWatchdog` in `LocalPlayerActivity.kt` detects playback head halted at 0 while buffer is filled >= 2s for 4s, or `onPlayerError` receives `ERROR_CODE_AUDIO_TRACK_*` / decoder failure), [`PlaybackErrorDialog`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/components/PlaybackErrorDialog.kt) alerts the user with an actionable 1-tap button to enable LPCM decoding immediately and seamlessly resume playback.
     - Standard DTS core (`audio/vnd.dts` / `MimeTypes.AUDIO_DTS` at 1.5 Mbps) is non-HBR IEC 61937 and remains fully supported for native direct bitstream passthrough.

5. **Dolby Developer Guidelines & Theatrical Audio Attributes Mandate (ZERO REGRESSION)**:
   - **Official References**:
     - Android TV: [Enabling Dolby Atmos and Dolby Audio in Android TV media apps](https://professionalsupport.dolby.com/s/article/Enabling-Dolby-Atmos-and-Dolby-Audio-in-Android-TV-media-apps?language=en_US)
     - Android Mobile: [Enabling Dolby Atmos in Android Mobile media apps](https://professionalsupport.dolby.com/s/article/Enabling-Dolby-Atmos-in-Android-Mobile-media-apps?language=en_US)
   - **Mandatory Movie Audio Attributes**:
     - `exoPlayer` in [`PlaybackService.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/PlaybackService.kt) MUST be initialized with `.setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).setUsage(C.USAGE_MEDIA).build(), true)`.
     - **WHY**:
       - On Android TV: Informs HDMI eARC/ARC sinks and TV audio processors that the stream is a theatrical movie, triggering proper multichannel routing and Dolby Atmos decoding on soundbars/AVRs.
       - On Android Mobile: OEM Dolby Atmos audio engines (Samsung, Xiaomi, Motorola, Sony, etc.) and Android 13+ `Spatializer` explicitly require `CONTENT_TYPE_MOVIE` to activate the theatrical Dolby Atmos profile and binaural spatializer for headphones/speakers.
       - Automatic Audio Focus: Passing `handleAudioFocus = true` ensures proper focus preemption and resumption when phone calls or system alerts occur.
   - **Dolby Atmos Streaming Format Distinctions in Audits**:
     - Streaming releases tagged with "Atmos" are predominantly **Dolby Digital Plus with JOC** (`audio/eac3-joc` / `AudioFormat.ENCODING_E_AC3_JOC`), NOT TrueHD.
     - In [`DevicePlaybackCapabilities.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/utils/DevicePlaybackCapabilities.kt), releases with "Atmos" (without "TrueHD") MUST check for `ENCODING_E_AC3_JOC`, `ENCODING_E_AC3`, `ENCODING_AC4`, or OEM platform decoders, preventing false-positive incompatibilities on mobile and TV devices.

### ⏯️ Player Transport Controls, Play/Pause State Authority & Race Condition Prevention Mandate (STRICT)
To eliminate state desynchronization, inverted buttons, and jarring UX across ExoPlayer and Google Cast:
1. **Zero Speculative State Toggling (STRICT PROHIBITION)**:
   - **PROHIBITION**: NEVER use blind speculative state flipping (`isPlaying = !isPlaying`).
   - When handling play/pause clicks:
     - Check the current state explicitly (`val shouldPause = isPlaying`).
     - Set the exact target boolean (`isPlaying = false` or `isPlaying = true`) for immediate, responsive visual feedback.
     - Dispatch the target command (`pause()` or `play()`).
2. **Google Cast Direct State Authority (`RemoteMediaClient`)**:
   - In Google Cast mode, `RemoteMediaClient` is the single source of truth for receiver state.
   - `RemoteCastCanvas` MUST register a `RemoteMediaClient.Callback` to listen directly to `onStatusUpdated()` (`MediaStatus.PLAYER_STATE_PLAYING` vs `PLAYER_STATE_PAUSED`).
   - Transport controls in Cast mode MUST command BOTH `rmc.pause()` / `rmc.play()` AND `controller.pause()` / `controller.play()`.
   - The 250ms periodic position ticker MUST poll `rmc.mediaStatus.playerState` to ensure automatic self-healing within $\le 250\text{ms}$ if IPC events lag.
3. **Clean Cast Transition UX (Zero BottomSheet Flashing)**:
   - When a user taps a Cast device in `CastBottomSheet`, `onDismissRequest()` MUST be called immediately.
   - **PROHIBITION**: NEVER use artificial delays (`delay(600.milliseconds)`) before closing device selection sheets. Artificial delays cause the sheet to recompose into the "Reproduciendo..." connected layout for a split second before vanishing ("fogonazo").
   - `LocalPlayerActivity` MUST automatically dismiss `showCastBottomSheet = false` via `LaunchedEffect(isCasting)` whenever `isCasting` activates, guaranteeing a clean, immediate handoff to `RemoteCastCanvas`.

### ⚡ R8, ProGuard & App Shrinking Mandate (STRICT - ZERO REGRESSION)
To preserve maximum runtime performance, fast startup time, minimal APK/AAB size, and high optimization scores:
1. **PROHIBITION: No Blanket / Package-Wide Wildcard Keeps**:
   - **STRICT PROHIBITION**: NEVER introduce broad wildcard keeps on app packages (e.g., `-keep class com.martinrevert.latorrentola.model.** { *; }`, `utils.**`, `ui.navigation.**`, or `database.**`).
   - Broad keeps freeze all fields, constructors, and methods, completely blocking R8 from dead-code stripping, member inlining, and identifier obfuscation.
2. **PROHIBITION: No Redundant Third-Party Library Keeps**:
   - Modern libraries (Coil 3, ML Kit, YouTube Player, Navigation 3, Room, Hilt, Retrofit, OkHttp) already package their own embedded consumer keep rules in their AAR/JAR dependencies.
   - **STRICT PROHIBITION**: NEVER add manual blanket keeps for libraries (e.g., `-keep class coil3.** { *; }`, `-keep class com.google.mlkit.** { *; }`, or `-keep class androidx.navigation3.** { *; }`). Only use targeted `-dontwarn` suppressions for optional desktop classes (e.g., SMB / BouncyCastle).
3. **Surgical Serialization & Firestore Rules**:
   - Model retention MUST remain strictly annotation-driven:
     - `@SerializedName` fields: `-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }`.
     - Firestore documents: Retain only models annotated with `@IgnoreExtraProperties` (`public <init>(); <fields>; <methods>;`).
     - Kotlinx Serialization: Always use `-keep,allowoptimization @kotlinx.serialization.Serializable class * { *; }`.
4. **Zero Global Optimization Disables**:
   - **STRICT PROHIBITION**: NEVER add `-dontoptimize`, `-dontshrink`, or `-dontobfuscate` to `proguard-rules.pro`.
5. **Reflection & Manifest Entry Points**:
   - Classes instantiated reflectively by the system (e.g., `AppCastOptionsProvider`) must ONLY keep their public constructor (`public <init>();`), NEVER `{ *; }`.
   - Native JNI entry points (`libtorrent_jni`, `LibNfsNative`) must be explicitly preserved for C/C++ interop.
6. **Java Toolchain Pinning in `gradle.properties`**:
   - Preserve `org.gradle.java.installations.auto-detect=false` and `paths` pointing to Android Studio JBR to prevent AGP `JdkImageTransform` from picking up broken external JDKs during system module assembly.

### 📼 Watch Progress, Media Completion Authority, Firestore Cost Optimization & Next Episode Navigation Mandate (STRICT)
To eliminate runaway cloud billing, prevent broken season completion counters, and ensure seamless next-episode progression:
1. **Firestore Cost Optimization & Room Single Source of Truth (SSOT)**:
   - **The 10-Second Write Storm Trap**: Writing to Firestore every 10 seconds during playback produces ~720 to 1,440 write operations per movie and triggers continuous Firestore snapshot listener read echoes on all active devices, generating catastrophic cloud costs.
   - **PROHIBITION: NEVER write to Firestore on periodic 10-second playback ticks.**
   - Periodic playback progress tickers in [`LocalPlayerActivity.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/player/LocalPlayerActivity.kt) and [`PlaybackService.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/PlaybackService.kt) MUST write solely to the local Room database (`userLibraryRepository.savePlaybackProgress(progress, syncRemote = false)`).
   - Syncing snapshots to Firestore (`syncRemote = true`) is STRICTLY RESTRICTED to definitive state transitions:
     - Player paused (`onPlayWhenReadyChanged(playWhenReady = false)`).
     - Player playback finished/ended (`onPlaybackStateChanged(STATE_ENDED)`).
     - Activity/Service lifecycle teardown (`onPause()`, `onStop()`, `onDestroy()`).
     - Google Cast session paused, ending, or ended.
     - Definitive completion marks (>95% threshold or manual toggle).
2. **Media Completion Contracts (External Client vs Internal Playback)**:
   - **External Client Contract (`TorrentHandlingMode.EXTERNAL_CLIENT`)**: Handing off a magnet to an external torrent client cannot be tracked during playback. Therefore, in [`TorrentLaunchHelper.launch`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/utils/TorrentLaunchHelper.kt), opening the external client MUST immediately record a completed download (`DownloadedEpisode` / `DownloadedMovie`) and mark the item as 100% completed in both Room and Firestore.
   - **Internal Playback Contract (ExoPlayer & CastPlayer)**: Surpassing 95% playback (`positionMs >= (durationMs * 0.95f).toLong()`) in [`LocalPlayerActivity.kt`](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/player/LocalPlayerActivity.kt) or [`PlaybackService.kt`](file:///D:/AndroidProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/PlaybackService.kt) MUST save completed `PlaybackProgress` (1000L/1000L) AND record `DownloadedEpisode` / `DownloadedMovie` via `markEpisodeAsCompleted` / `markMovieAsCompleted`.
   - **Premature Download Completion Prohibition**: In internal playback mode, starting a torrent buffer or launching an episode MUST NEVER prematurely record `DownloadedEpisode` or mark the episode/season as completed. Completion is strictly reserved for >= 95% playback or manual toggle.
   - **Season Completion Badges (`TvDetailScreen.kt`)**: Season completed/partial status chips MUST evaluate both `seriesDownloads` AND `watchHistoryMap` entries (`progress.isCompleted || progress.progressPercent >= 95`) using `MediaIdHelper.parseCanonicalEpisode(mediaId)`.
4. **TV Series Poster Overall Progress Bar Contract**:
   - TV series poster progress bars displayed in catalog grids (Home, Favorites, Search) MUST reflect the true overall weighted consumption percentage of the entire series across all seasons and episodes, calculated via `TvSeriesProgressCalculator`.
   - **PROHIBITION: NEVER use a single episode's playback percentage as the series poster progress bar.**
   - **Season 0 Exclusion**: Season 0 specials (`sNum <= 0`) are strictly excluded from series progress percentage and series-level completion calculations.
   - **Upcoming & Unreleased Seasons Availability Rule**: Seasons without available torrents, seasons with empty/zero episode counts, unreleased upcoming seasons (`airDate > todayIso` or `seasonNumber > lastAiredSeason`), and announced seasons without air dates (`airDate.isNullOrBlank()` for season > 1 when earlier seasons have aired), MUST NOT drag a series down into "Watching". When all available regular seasons ($\ge 1$) have been completed, the series status remains "Watched" until episodes of the new season air and become available.
   - **Unreleased Upcoming Series**: Upcoming series premiering in the future with 0 views MUST remain "Unwatched" and NEVER inherit progress from unrelated movies sharing name prefixes. All title-matching fallback logic MUST require `isEpisodeCandidate` and exact or delimited word boundaries (`isEpisodeTitleMatchingSeries`).
   - **Unified SSOT Progress Contract**: Filter statuses (`isSeriesWatched`, `isSeriesInProgress`, `isSeriesUnwatched`) MUST be derived directly from `TvSeriesProgressCalculator.calculateProgressPercent`. Completed series (`pct >= 100`) MUST NEVER appear in "Watching" (`IN_PROGRESS`). Progress must reach 100% when all available episodes are completed (no arbitrary 99% caps).
   - **Asynchronous Room Entity Seasons Enrichment**: Since Room's `FavoriteTvSeriesEntity` does not persist full seasons arrays, `SearchViewModel` MUST asynchronously enrich favorites where `seasons.isNullOrEmpty()` via `tmdbRepository.getTvDetails` to ensure season air dates and episode counts are available for progress calculation.
5. **Next Episode Transition, Player Surface Rebind & Dialog Buffering Contract**:
   - **Dialog Buffering Lifetime**: When an episode release is chosen or auto-play triggered, `showNextEpisodeDialog` MUST remain open and visible while the selected `TorrentReleaseShimmerCard` buffers up to 100% (`STATE_READY`). The dialog is ONLY dismissed when `onNewIntent` receives the ready playback intent.
   - **Pinned Close Affordance**: `QualityChoiceDialog` MUST pin a top-right Close button in the header row outside the scrolling `LazyColumn` so the user can easily dismiss the dialog without scrolling to the bottom.
   - **Clean Hardware Decoder & Surface Rebind**: To eliminate video stalls, black screens, or frozen frames on Android when reusing `LocalPlayerActivity` (`singleTop`), previous media and surfaces MUST be cleared immediately (`mediaController?.stop()`, `mediaController?.clearMediaItems()`, `mediaController?.clearVideoSurface()`, `SubtitleServerRegistry.stopAll()`), and `surfaceRebindKey` incremented at Activity scope to rebuild `PlayerSurface` with a fresh `SurfaceView`.
   - **Listener Leak Prevention**: Registered `Player.Listener` instances on `mediaController` MUST be tracked and cleanly unregistered before attaching new instances and during `onDestroy()`.
6. **Favorites UI Performance & Focus Decoupling Contract (Compose / Android TV)**:
   - **Focus Decoupling in Favorites**: In `SearchScreen` when viewing the Favorites tab (`isShowingFavorites == true`), grid item focus handlers MUST be decoupled (`onMovieFocus = null`, `onSeriesFocus = null`). The ViewModel MUST NOT fire background metadata fetches or update banner states on item focus in Favorites, eliminating sluggish navigation and UI jank.
   - **Precomputed Progress Maps in Compose Grids**: In `FavoriteItemsGrid`, watch progress and completion mappings (`movieProgressMap`, `seriesProgressPercentMap`) MUST be precalculated at the grid level inside `remember(movies/series, watchHistoryMap)` before rendering grid cells, eliminating per-item recomposition overhead and repeated regex parsing.
   - **Static Precompiled Regex & Off-Main-Thread Flow Offloading Mandate**:
     - **The Dynamic Regex JNI Lock Contention Trap**: Instantiating `Regex(...)` dynamically inside loop bodies or frequently called helper functions (such as `MediaIdHelper.normalizeImdbId`, `TvSeriesProgressCalculator.calculateProgressPercent`, or `MediaMetadataResolver`) forces JNI `PatternNative.compileImpl` native regex compilation on every invocation. When executed during list filtering or progress calculation on the main UI thread, JNI lock contention in Android ART (`art::ConditionVariable::WaitHoldingLocks`) blocks the main thread, causing severe UI freezes, frame drops, or crashes on Android TV (TCL Smart TV Pro).
     - **Mandatory Architectural Contract**: All `Regex` instances MUST be instantiated ONCE as precompiled `private val` constants at the object, file, or companion object level. Heavy reactive flow transformations and progress calculations (such as `SearchViewModel.showFavorites` `combine`) MUST be offloaded from the UI thread using `.flowOn(Dispatchers.Default)`.
     - **PROHIBITION: NEVER instantiate `Regex(...)` inside function bodies, loops, or hot data processing paths.**

7. **Text-To-Speech (TTS) Binder IPC Offloading & Main Thread Hang Prevention Mandate (STRICT)**:
   - **The Synchronous Binder IPC Main-Thread Hang Trap**: `TextToSpeech` calls (`setLanguage`, `getVoices`, `setVoice`, `speak`, `stop`) execute synchronous IPC transactions over Binder to the platform TTS engine process (`ITextToSpeechService` -> `ioctl`). When invoked on the main UI thread (e.g. inside `onInit`), sluggish or missing TTS engines on Android TV devices (such as TCL Smart TV Pro) block the main thread in `__ioctl`, causing ANR crashes and freezing the UI.
   - **Mandatory Architectural Contract**: All `TextToSpeech` Binder IPC operations (`setLanguage`, `voices`, `speak`, `stop`) MUST be offloaded to `Dispatchers.IO` using a background `CoroutineScope` inside `VoiceManager`.
   - **PROHIBITION: NEVER invoke `tts.setLanguage()`, `tts.voices`, `tts.speak()`, or `tts.stop()` directly on the Main (UI) thread.** Wrap all TTS calls in `try-catch` blocks to gracefully catch platform/binder exceptions (`DeadObjectException`, `RuntimeException`).

8. **Lifecycle-Aware Flow Collection Mandate (Compose / Android TV)**:
   - **The Lifecycle-Unaware `collectAsState()` GapComposer Corruption Trap**: Standard `collectAsState()` is lifecycle-unaware and continues collecting `StateFlow`/`Flow` emissions even when an Activity or composable screen is paused, stopped, or undergoing lifecycle teardown. On Android TV devices (such as TCL Smart TV Pro running Android 11), background updates from Room or network flows during screen transitions mutate snapshot state while `GapComposer` is disposing/clearing composable groups. This corrupts `GapComposer`'s internal slot table, throwing `GapComposer.getInserting` / `GapComposer.nextSlot` / `GapComposer.changedInstance` crashes on the main thread.
   - **Mandatory Architectural Contract**: All UI flow collections in Compose screens MUST use `collectAsStateWithLifecycle()` from `androidx.lifecycle.compose.collectAsStateWithLifecycle` instead of standard `collectAsState()`.
   - **PROHIBITION: NEVER use lifecycle-unaware `collectAsState()` for ViewModel `StateFlow`s or repository reactive flows in top-level Compose screen composables.**

9. **Libtorrent Native `havePiece` IPC Blocking & Bitfield SIGSEGV Fault Prevention Mandate (STRICT)**:
   - **The Synchronous Native JNI Condition Variable Deadlock Trap**: `torrent_handle.have_piece(index)` in `libtorrent4j` makes a synchronous JNI query into native C++ libtorrent (`Java_org_libtorrent4j_swig_libtorrent_1jni_torrent_1handle_1have_1piece`). The C++ layer queues a command to libtorrent's internal thread and blocks the calling thread via `std::__ndk1::condition_variable::wait` (`pthread_cond_wait`). Invoking `havePiece(p)` in a loop across thousands of pieces inside a `synchronized(sessionLock)` block blocks the dispatcher thread in `__futex_wait_ex` / `pthread_cond_wait`, holds `sessionLock` continuously, and causes deadlocks or ANRs on Android TV devices.
   - **The Unaugmented Status & Out-of-Bounds `PieceIndexBitfield` SIGSEGV Trap**: Invoking `handle.status()` without flags calls `h.status(STATUS_FLAGS_ZERO)`, which omits piece data. In this state, `TorrentStatus.pieces()` returns an empty bitfield (`isEmpty == true`, `m_bytes == nullptr`). Calling `piecesBitfield.getBit(p)` on an empty bitfield or with an index `p >= size()` performs unmapped pointer dereferencing in native C++ (`m_bytes[p / 8]`), crashing the process instantly with `SIGSEGV (SEGV_MAPERR)` at `fault addr 0x4` which cannot be caught by JVM `try-catch`.
   - **Mandatory Architectural Contract**: Piece availability checks MUST retrieve the bitfield using `handle.status(TorrentHandle.QUERY_PIECES)?.pieces()`. Furthermore, callers MUST validate `!isEmpty && p in 0 until size()` before calling `getBit(p)` (e.g. via `piecesBitfield.hasPiece(p)`).
   - **PROHIBITION: NEVER invoke `handle.havePiece(p)` inside loops or within `synchronized(sessionLock)` blocks.** Always query piece availability via `handle.status(TorrentHandle.QUERY_PIECES)?.pieces()` and guard bit accesses with `!isEmpty && p in 0 until size()`.

10. **Media Deletion Teardown, SingleTop `onNewIntent` Preservation & Clean Playback End Mandate (STRICT)**:
   - **The Latched Deletion Flag & `onNewIntent` Drop Trap**: When media deletion is confirmed in `LocalPlayerActivity`, setting a transient deletion flag (e.g. `isDeletingMedia`) without resetting it in a `finally` block permanently latches the activity in a deleting state. Because `singleTop` `onNewIntent` checks this flag, subsequent next-episode transitions or new playback intents are dropped immediately, leaving the player on an idle black screen. Furthermore, ignoring player errors based on `nextEpisodeTriggered` suppresses legitimate startup exceptions on subsequent media.
   - **Mandatory Architectural Contract**: Transient deletion flags MUST be scoped via `try-finally` blocks to the active deletion I/O operation and reset to `false`. `onNewIntent` MUST NOT drop valid incoming playback intents due to previous deletion operations. Furthermore, `proceedAfterPlaybackEnded` MUST cleanly `finish()` the activity when `autoPlayMode` is `DO_NOTHING` and unregister player listeners.
   - **PROHIBITION: NEVER leave `isDeletingMedia` latched to true across coroutine completion, NEVER drop valid `onNewIntent` playback deliveries due to previous deletion operations, and NEVER use `nextEpisodeTriggered` to swallow legitimate player initialization errors.**

11. **Navigation 3 Deep-Link & Push Notification Intent Preservation Across Authentication Mandate (STRICT)**:
   - **The Blind `resetTo(Home)` Clobber Trap**: When an unauthenticated user opens the app from an external deep-link or FCM push notification (`movieJson`, `movieId`), the navigation graph initializes at `Route.Login`. If `onLoginSuccess` blindly resets the back stack with `NavBackStackHandler.resetTo(backStack, Route.Home)`, the pending destination intent is permanently discarded, forcing the user onto the home catalog instead of the clicked movie.
   - **Mandatory Architectural Contract**: External intent destinations MUST be parsed into a pending route (`pendingMovieRoute`) and preserved in memory while at `Route.Login`. Upon successful authentication, `onLoginSuccess` MUST use `NavBackStackHandler.resetToWithTop(backStack, Route.Home, pendingMovieRoute)` (which guarantees `backStack.size >= 1` in `Snapshot.withMutableSnapshot` to prevent `NavDisplay` empty stack crashes) and consume the intent via `onInitialDataHandled()`. Pressing "Back" from the detail screen must cleanly pop back to `Route.Home`.
   - **PROHIBITION: NEVER execute blind `resetTo(backStack, Route.Home)` on login completion when an external deep-link or push notification target is pending, and NEVER reintroduce unauthenticated guest access.**

12. **Local Loopback Stream Server Liveness & Crashlytics Error Dampening Mandate (STRICT)**:
   - **The Premature Crashlytics Exception & Stale Port Trap**: When streaming a torrent, `TorrentDownloadService` hosts a local HTTP server (`VerifiedTorrentHttpServer`) on `127.0.0.1`. On Android TV devices with strict RAM/LMK constraints or when recovering from standby/sleep or torrent completion, background services may recycle or sockets close. If `openReadyPlayer` holds a stale, closed server instance in `streamServers` without checking `server.isAlive()`, it re-dispatches intents targeting dead ports. Furthermore, when ExoPlayer encounters a transient loopback connection failure (`ERROR_CODE_IO_NETWORK_CONNECTION_FAILED` on `127.0.0.1`), calling `AppLogger.e(...)` before auto-recovery records false-positive non-fatal exception incidents in Firebase Crashlytics for self-healing transient events.
   - **Mandatory Architectural Contract**:
     1. `streamServers[infoHash]` vital signs MUST be verified via `server.isAlive()`. Stale instances where `!server.isAlive()` must be purged and resurrected with fresh `VerifiedTorrentHttpServer` instances and listening ports.
     2. In `LocalPlayerActivity`, transient local loopback stream connection failures (`isLocalStream && (ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT || ERROR_CODE_IO_UNSPECIFIED)`) MUST be logged as warnings (`AppLogger.w`) during initial recovery attempts (up to 3 retries). Only if recovery fails repeatedly (> 3 attempts) may it be reported as an error (`AppLogger.e`). Recovery counters MUST reset to 0 upon reaching `STATE_READY` or active playback (`isPlaying == true`).
   - **PROHIBITION: NEVER log transient local loopback connection failures directly to `AppLogger.e` prior to auto-recovery retries, and NEVER reuse cached `streamServers` without verifying `server.isAlive()`.**

13. **Foldable Front/Cover Screen & Constrained Viewport Adaptive Layout Contract (STRICT)**:
   - **The Constrained Viewport Squishing & Single-Letter Vertical Badge Trap**:
     - *Root Cause / Failure Trap*: On compact foldable front/cover screens (e.g. Galaxy Z Fold, Pixel Fold in vertical cover screen mode with widths ~320dp–340dp and 21:9–23:9 aspect ratios) or compact handheld viewports (< 360dp), fixed horizontal layouts (`Row`) for metadata badges, rating items, action buttons, or download release cards exceed the available horizontal space. Inside a column next to a poster (~115dp) with standard screen margins (16dp x 2) and item spacing (16dp), less than 160dp remains. When badges or buttons in a rigid `Row` run out of space without `softWrap = false` or a wrapping container, individual text words are squished into single-character columns (`[ C c u l r e r t a i l ]`), or action buttons inside the poster column squeeze and force ratings/action buttons into awkward vertical offsets. Similarly, modal dialogs with fixed `widthIn(min = 320.dp)` and rigid 24dp margins clip off-screen.
     - *Mandatory Architectural Contract*:
       1. **Zero Data or Functionality Loss**: 100% of information (badges, tags, ratings, actions, providers, options) and interactive capabilities MUST be preserved on constrained screens. Never hide or truncate content to fix layout constraints.
       2. **FlowRow for Multi-Item Metadata & Actions**: Metadata badges, studio/director tags, and action buttons MUST use `FlowRow` (`horizontalArrangement = Arrangement.spacedBy(...)`, `verticalArrangement = Arrangement.spacedBy(...)`) rather than rigid `Row`s. Badge labels MUST specify `maxLines = 1` and `softWrap = false` to guarantee words never break letter-by-letter.
       3. **Decoupled Action Buttons on Phone Detail Layouts**: In phone layouts (< 600dp), action buttons ("Ver Trailer", "Añadir a favoritos", "Compartir") MUST NOT be placed inside the narrow column next to the poster. They MUST be placed full-width underneath the poster row, giving them the complete screen width to render or flow naturally.
       4. **Adaptive Width & Padding Thresholds**: Screen and dialog containers MUST inspect `isConstrainedWidth` (`LocalConfiguration.current.screenWidthDp < 360`) to adaptively scale padding (e.g., `12.dp` vs `16.dp` on screens, `10.dp–12.dp` vs `24.dp` on dialogs, poster width `100.dp` vs `115.dp`). Dialog surfaces MUST NEVER specify rigid minimum widths exceeding `screenWidthDp - 24.dp` (avoid `widthIn(min = 320.dp)`).
       5. **Preview Coverage**: All detail screens and dialogs MUST include `@Preview` configurations with narrow foldable dimensions (`widthDp = 320`, `widthDp = 340`).
   - **PROHIBITION: NEVER use rigid `Row`s without wrapping for dynamic metadata chips or multi-button action bars on handheld layouts, NEVER trap action button groups inside narrow columns beside posters on phones, and NEVER specify rigid `widthIn(min = ...)` constraints on dialogs that exceed constrained foldable screen widths.**

14. **Handheld Portrait Player Controls Contract (YouTube-Style, STRICT)**:
   - **The Detached Controls & Vanishing Title Trap**:
     - *Root Cause / Failure Trap*: In portrait, the letterboxed video occupies only ~150–230dp in the vertical center. An overlay pinned to `fillMaxSize()` pushes the top bar under the status bar and the scrubber over the navigation bar, far from the picture; and 6 fixed-width icon buttons in the same `Row` as `mediaTitle` (`weight(1f)`) squeeze the title to 0dp so the file name disappears.
     - *Mandatory Architectural Contract*:
       1. In `PlayerControlsOverlay`, when `isPortrait` (`!isTv && orientation == PORTRAIT`), the visible controls MUST be anchored to a frame centered on the video whose height comes from `PlayerPortraitLayout.controlsFrameHeightDp(...)` (video aspect ratio from `presentationState.videoSizeDp`). Gestures MUST remain on the full-screen root.
       2. The portrait top bar MUST show Back + `mediaTitle` (≥ `weight(1f)`, up to 2 lines) + at most 3 essential actions (Subtitles, Cast, More). Secondary actions (Audio tracks, OpenSubtitles, Subtitle style, Stats for nerds) MUST remain reachable via the More `ModalBottomSheet` using the same callbacks (100% feature parity).
       3. Timestamps, the handheld Fullscreen toggle and `WatchProgressSlider` MUST sit on the video's bottom edge.
       4. Android TV and landscape MUST keep the original full-screen layout, all 6 top buttons, and D-pad focus behavior.
       5. Fullscreen toggling MUST use `requestedOrientation` and release the lock (`SCREEN_ORIENTATION_UNSPECIFIED`) via `PlayerPortraitLayout.shouldReleaseOrientationLock` only when auto-rotate is enabled.
       6. In handheld landscape (`!isTv && !isPortrait`), system bars MUST be hidden via `WindowInsetsControllerCompat` (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) and the controls container MUST apply `Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))` so transient 3-button navigation bars or camera cutouts never overlap the Cast icon, fullscreen button, or scrubber. In portrait, system bars hide when `controlsVisible == false` and reappear when `controlsVisible == true` (`PlayerPortraitLayout.shouldShowSystemBars`).
   - **PROHIBITION: NEVER pin handheld portrait player controls to the screen edges, NEVER put more than 3 icon actions next to `mediaTitle` in the portrait top bar, NEVER omit `systemBars.union(displayCutout)` insets on handheld landscape player controls, and NEVER remove a player action instead of relocating it to the overflow sheet.**

15. **OTT Profile Selector Cold-Start Gate, Zero Welcome-Screen Flash & Biometric Unlock Contract (STRICT)**:
   - **The Asynchronous Room `emptyList()` Welcome-Screen Flash ("Fogonazo") & Skipped Biometric Gate Trap**:
     - *Root Cause / Failure Trap*: Exposing `savedAccounts` from Room (`savedAccountDao.observeAll()`) via `.stateIn(..., initialValue = emptyList())` causes `savedAccounts` to evaluate as empty during the first composition frame (~15–50ms) while Room queries SQLite on `Dispatchers.IO`. Consequently, `LoginScreenContent` momentarily renders `WelcomeLoginBody` (app banner logo + Google Sign-In button) before jumping to `ProfileSelectorBody` ("¿Quién anda ahí?"). Additionally, initializing `AppNavigation` with `if (currentUser != null) Route.Home else Route.Login` bypasses the OTT profile selector on subsequent launches, while gating `BiometricAuthHelper.authenticate` behind `viewModel.isCurrentAccount(account.uid)` skips biometric verification whenever `currentUser` is `null` after sign-out.
     - *Mandatory Architectural Contract*:
       1. `AuthViewModel.savedAccounts` MUST use `initialValue = null` (`StateFlow<List<SavedAccountEntity>?>`) and `AuthRepository.savedAccountsFlow` MUST combine `savedAccountDao.observeAll()` with `_currentUserFlow` so an active Firebase user is always represented.
       2. In `LoginScreenContent`, when `savedAccounts == null`, ONLY the themed background surface (`TvSurface` / `Surface`) may be rendered—neither `WelcomeLoginBody` nor `ProfileSelectorBody`—until the initial account list is emitted.
       3. `AppNavigation` MUST initialize `initialRoute` at `Route.Login` on cold start so returning users always pass through the OTT profile selector ("¿Quién anda ahí?").
       4. On handheld devices (`!isTv`), tapping any saved profile in `LoginScreen` when `BiometricAuthHelper.isBiometricAvailable(context)` is `true` MUST always trigger `BiometricAuthHelper.authenticate` prior to invoking `viewModel.selectAccount(account, context)`.
   - **PROHIBITION: NEVER use `initialValue = emptyList()` for `AuthViewModel.savedAccounts` (which causes `WelcomeLoginBody` to flash before `ProfileSelectorBody`), NEVER skip `Route.Login` on cold start in `AppNavigation`, and NEVER gate `BiometricAuthHelper.authenticate` on `isCurrentAccount(account.uid)`.**

16. **Libtorrent `SessionManager.start()` Alert Mask Override, Staggered Piece Deadlines & Hybrid Sliding-Window Sequential Streaming Contract (STRICT)**:
   - **The `SessionManager.start()` `all_categories` Alert Flood, Flat-Deadline Starvation & Global Sequential Swarm Choking Trap**:
     - *Root Cause / Failure Trap*:
       1. `libtorrent4j`'s `SessionManager.start(SessionParams)` internally overwrites `alert_mask` on `params.getSettings()` to `alert.all_categories` right before constructing the native session. If `TorrentSessionConfig.applyRuntimeOptimizations(manager, ...)` is not called immediately after `manager.start(sessionParams)`, `block_progress_notification` remains enabled, flooding JNI and Android's Scudo allocator with alerts for every 16 KB block. Conversely, omitting `alert.piece_progress_notification` from `createOptimizedAlertMask()` suppresses `PieceFinishedAlert` events after `applyRuntimeOptimizations` runs.
       2. Assigning an identical flat `150ms` deadline and `Priority.TOP_PRIORITY` to 15–30 startup and tail pieces simultaneously causes `libtorrent`'s piece picker to treat Piece 15 as equally urgent as Piece 0, starving the immediate playhead.
       3. Keeping `TorrentFlags.SEQUENTIAL_DOWNLOAD` enabled globally or setting `setSequentialRange(resumePiece, target.lastPiece)` across the entire file prevents peers who only hold pieces outside the immediate playhead from contributing via rarest-first, reducing aggregate swarm throughput and unchoke reciprocity.
     - *Mandatory Architectural Contract*:
       1. `TorrentDownloadService` MUST invoke `TorrentSessionConfig.applyRuntimeOptimizations(manager, ...)` immediately after `manager.start(sessionParams)`, and `TorrentSessionConfig.createOptimizedAlertMask()` MUST include `alert.piece_progress_notification` alongside `status_notification`, `error_notification`, and `storage_notification` while strictly excluding `block_progress_notification`.
       2. Once torrent metadata resolves in `TorrentDownloadService`, global `TorrentFlags.SEQUENTIAL_DOWNLOAD` MUST be unset (`handle.unsetFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)`) and replaced with a bounded sliding sequential window (`setSequentialRange(windowStart, windowEnd)`, bounded by `SEQUENTIAL_WINDOW_PIECES = 48`) so pieces beyond the window use `libtorrent`'s native rarest-first picker.
       3. `StreamingPiecePlanner` MUST assign staggered staircase deadlines (`150ms` stepping up to `3000ms`) and tiered priorities (`Priority.TOP_PRIORITY` for the first 4 critical head pieces, `Priority.SIX` for lookahead), and gate initial player readiness on `initialTailBytes` (`1 MB`) rather than the full `8 MB` tail range.
       4. `TorrentSessionConfig` MUST enable parallel tracker announces (`announce_to_all_tiers = true`, `announce_to_all_trackers = true`), TCP preference in mixed-mode congestion control (`bandwidth_mixed_algo_t.prefer_tcp`), fast stalled-block recovery (`request_timeout = 10`, `piece_timeout = 10`, `prioritize_partial_pieces = true`), and warm DHT session state persistence (`saveSessionState` / `resolveSessionStateFile`).
   - **PROHIBITION: NEVER start `SessionManager` without immediately calling `TorrentSessionConfig.applyRuntimeOptimizations(manager, ...)`, NEVER omit `alert.piece_progress_notification` from `createOptimizedAlertMask()`, NEVER assign flat identical deadlines across the entire startup buffer, and NEVER lock `setSequentialRange` to `target.lastPiece` across the whole file.**

17. **In-App PiP `TextureView` Surface Handoff & Partial Torrent Loopback HTTP URI Preservation Contract (STRICT)**:
   - **The Delayed `clearVideoSurface()` IPC Wipeout & `TextureView` Single-Listener Overwrite Trap**:
     - *Root Cause / Failure Trap*:
       1. In Media3 (`MediaControllerImplBase`), calling `controller.setVideoTextureView(textureView)` registers the controller's internal `SurfaceCallback` via `textureView.setSurfaceTextureListener(surfaceCallback)`. Because Android's `TextureView` supports only a single `SurfaceTextureListener`, attaching a custom `surfaceTextureListener` overwrites Media3's listener or gets overwritten by `setVideoTextureView`. Furthermore, `controller.clearVideoSurface()` in `MediaControllerImplBase` unconditionally sends a `setVideoSurfaceWithSize(null, 0, 0)` IPC to `PlaybackService` even if the controller's local surface reference is already `null`. If `LocalPlayerActivity` calls `clearVideoSurface()` in `finish()` (where `isPlayerForeground` becomes `false` and `FloatingVideoPipOverlay` immediately attaches its `TextureView`), and then calls `clearVideoSurface()` again ~300ms later in `onStop()` and `onDestroy()`, the late IPC detaches `FloatingVideoPipOverlay`'s `TextureView` surface in `PlaybackService`, leaving the PiP video frozen on a single frame while audio continues.
       2. During torrent streaming, `libtorrent` pre-allocates a sparse file on disk where only the startup head (`16–32 MB`) and MKV `Cues` tail (`1 MB`) are initially downloaded and verified; un-downloaded middle pieces on disk contain zero bytes (`0x00`). If `PlayerIntentUtils.buildMediaItem`, `LocalPlayerActivity.refreshMediaItem` (when async IMDb metadata resolves), `FloatingVideoPipOverlay.expandToFullScreen`, or `TorrentCardActionHandler` replaces the `VerifiedTorrentHttpServer` loopback URI (`http://127.0.0.1:<port>/...`) with a direct `file://` URI—or starts an unverified `VerifiedTorrentHttpServer` with `{ _, _ -> true }` because `file.exists()` is true—linear playback at the beginning works, but seeking forward into an un-downloaded middle offset causes `MatroskaExtractor` to read raw `0x00` bytes from disk without waiting for piece verification, crashing ExoPlayer with `java.lang.IllegalStateException: No valid varint length mask found` in `VarintReader.readUnsignedVarint`.
     - *Mandatory Architectural Contract*:
       1. `FloatingVideoPipOverlay` MUST NOT set a custom `TextureView.SurfaceTextureListener` that competes with `MediaController.setVideoTextureView(view)`. When expanding from PiP to fullscreen (`isExpanding || LocalPlayerActivity.isPlayerForeground`), `onRelease` MUST nullify `view.surfaceTextureListener = null` before disposal so `SurfaceCallback.onSurfaceTextureDestroyed` does not send a `null` surface IPC during activity startup.
       2. `LocalPlayerActivity` MUST guard exit surface release with a single-shot flag (`hasClearedVideoSurfaceForExit` via `releaseVideoSurfaceForPipHandoff()`) and increment `pipSurfaceHandoffTick` so `mediaController?.clearVideoSurface()` is invoked at most ONCE when leaving fullscreen (`finish()` or `onStop()`), and never repeated in subsequent `onStop()`/`onDestroy()` lifecycle callbacks.
       3. Whenever a torrent stream is partial (`EXTRA_PARTIAL_TORRENT_STREAM == true`, download state `!= STATE_COMPLETED`, or `streamUrl` is a loopback `http://127.0.0.1`/`localhost` URI), `PlayerIntentUtils.buildMediaItem`, `LocalPlayerActivity` (`handleIntent`, `buildPendingMediaItem`, `setupControllerMedia`, `refreshMediaItem`), `FloatingVideoPipOverlay`, and `TorrentCardActionHandler` MUST strictly preserve the HTTP loopback URI (`http://127.0.0.1:<port>/...`) and `EXTRA_PARTIAL_TORRENT_STREAM = true` in `Intent` and `MediaMetadata.extras`. `LocalPlayerActivity` MUST NEVER start a fallback `{ _, _ -> true }` `VerifiedTorrentHttpServer` on a partial torrent file, and `MainActivity.readyStreamEvents` MUST NOT call `controller.setMediaItem` while `LocalPlayerActivity.isPlayerActivityForeground()` is `true`.
   - **PROHIBITION: NEVER call `mediaController?.clearVideoSurface()` multiple times across `finish()`, `onStop()`, and `onDestroy()` when handing off to `FloatingVideoPipOverlay`, NEVER overwrite `TextureView.surfaceTextureListener` after calling `controller.setVideoTextureView(view)`, and NEVER substitute a `file://` URI or an unverified `{ _, _ -> true }` HTTP server for an active `http://127.0.0.1` `VerifiedTorrentHttpServer` stream on a partially downloaded torrent.**

18. **Firebase Installations Service (FIS) & Push Token Resilience Contract (STRICT)**:
   - **The Mismatched Package ID & Unhandled FIS Token ExecutionException Trap**:
     - *Root Cause / Failure Trap*:
       1. The debug build type uses `applicationIdSuffix ".debug"` (`com.martinrevert.latorrentola.debug`). When `FirebaseMessaging` initiates topic subscriptions or retrieves tokens, `GmsRpc` requests an installation auth token from Firebase Installations Service (FIS). If `app/google-services.json` lacks the client configuration for `com.martinrevert.latorrentola.debug` (or if the package is unregistered/restricted in Google Cloud Console), FIS backend returns 403 / authentication error, throwing `ExecutionException: com.google.firebase.installations.FirebaseInstallationsException`.
       2. Calling `syncWithBackend(true)` immediately after a failed `subscribeToTopic` call triggers a duplicate failing FIS token request. Furthermore, unhandled static calls to `FirebaseMessaging.getInstance()` or `FirebaseInstallations.getInstance()` throw `IllegalStateException` when `FirebaseApp` is uninitialized.
     - *Mandatory Architectural Contract*:
       1. `app/google-services.json` MUST define client configurations for BOTH `com.martinrevert.latorrentola` (Release) AND `com.martinrevert.latorrentola.debug` (Debug), and both apps MUST be registered in the Firebase Console with Firebase Installations API enabled in Google Cloud Console.
       2. `FirebaseMessagingInitializer` MUST intercept `FirebaseInstallationsException` and FIS auth errors via `handleFirebaseInstallationsError`, attempt `FirebaseInstallations.getInstance().delete()` to purge stale installation tokens, and log diagnostic guidance (`AppLogger.w`) while preventing duplicate cascading token requests.
       3. All static `FirebaseMessaging.getInstance()` calls MUST be wrapped in defensive `try-catch` blocks to prevent unhandled crashes.
   - **PROHIBITION: NEVER omit `com.martinrevert.latorrentola.debug` configuration from `app/google-services.json`, NEVER trigger redundant token fetches after an FIS authentication failure, and NEVER invoke static `FirebaseMessaging.getInstance()` without defensive error handling.**

19. **In-Progress "Watching" Feeds, Metadata Enrichment & Zero-Flicker State Lifecycle Contract (STRICT)**:
   - **The Asynchronous Resolution Hang, Empty-State Flash & Dropped Canonical IMDb ID Trap**:
     - *Root Cause / Failure Trap*:
       1. In `SearchViewModel.showWatchingMovies()`, `updateState()` checked `missingMovieImdbIds.isEmpty()` where `missingMovieImdbIds` was an unmutated captured list from `combine`. If an in-progress movie could not be resolved on YTS, `_uiState.value` remained permanently stuck on `SearchUiState.Loading` instead of transitioning to `SearchUiState.Empty`. Furthermore, omitting in-memory caching caused redundant YTS network calls on every flow emission.
       2. In `SearchViewModel.showWatchingTvSeries()`, `combine` emitted `inProgressSeries = emptyList()` on its first pass while asynchronous TMDB lookups were in-flight, immediately setting `_tvUiState.value = SearchTvUiState.Empty` and creating a jarring empty-state flash ("fogonazo") before switching to `Success`.
       3. Resolving a series via `tmdbRepository.findTvSeriesByImdbId(imdbId)` returns summary metadata omitting external IDs. Storing `fullDetails` directly into cache without copying `imdbId = fullDetails.effectiveImdbId ?: imdbId` dropped the canonical IMDb ID, breaking recency sorting and title heuristic matching in `TvSeriesProgressCalculator`.
       4. Combining `getDownloadedEpisodes()` without populating `seriesMap` and failing to short-circuit when `episodeProgress.isEmpty()` triggered useless TMDB network calls for missing seasons of favorited series when no episodes had ever been watched. Mixing IMDb hash codes with TMDB series integer IDs in `attemptedSeriesDetailIds` risked key collision and failed to clear on force refresh.
     - *Mandatory Architectural Contract*:
       1. `showWatchingMovies()` MUST track resolution completion via `isComplete: Boolean` on `updateState` and transition cleanly to `SearchUiState.Empty` when no movies are resolved or match filters. Resolved movies MUST be cached in memory (`watchingMoviesCache`) to prevent duplicate YTS network calls.
       2. `showWatchingTvSeries()` MUST track active background lookups (`hasPendingFetches = missingImdbIds.isNotEmpty() || missingSeasons.isNotEmpty() || activeFetches > 0`) and preserve `SearchTvUiState.Loading` while fetches are in flight, eliminating empty-state flashes.
       3. Series resolved by IMDb ID MUST preserve `imdbId` via `fullDetails.copy(imdbId = fullDetails.effectiveImdbId ?: found.effectiveImdbId ?: imdbId)` when stored in `seriesDetailsCacheFlow`.
       4. `showWatchingTvSeries()` MUST short-circuit to `SearchTvUiState.Empty` immediately when `episodeProgress.isEmpty()`, without querying TMDB for seasons of favorites. Tracking sets for TMDB series IDs (`attemptedSeriesDetailIds: Set<Int>`) and external IMDb lookups (`attemptedImdbSeriesLookupIds: Set<String>`) MUST be decoupled and cleared when `force = true`.
   - **PROHIBITION: NEVER leave `SearchUiState.Loading` permanently active when external movie lookups fail, NEVER emit `SearchTvUiState.Empty` while background TMDB fetches are in-flight, NEVER drop canonical `imdbId` when caching TMDB series summaries, and NEVER trigger series season enrichment when `episodeProgress` is empty.**

20. **Network Payload Compression (Brotli/Gzip), HTTP Disk Cache & Connection Sharing Mandate (STRICT)**:
   - **The Missing Brotli Negotiation & Uncached Redundant Fetch Trap**:
     - *Root Cause / Failure Trap*: OkHttp's built-in `BridgeInterceptor` only requests `Accept-Encoding: gzip`. CDNs and backends (Cloudflare on TMDB, Torrentio, OpenSubtitles, GitHub) support modern Brotli (`br`) compression, which produces 15–25% smaller payloads than gzip on text/JSON. Without `BrotliInterceptor`, larger payloads increase network transit latency and mobile data consumption. Furthermore, instantiating separate `OkHttpClient.Builder()` instances for specialized services (e.g. YTS) isolates connection pools and dispatchers, multiplying TLS handshakes, while omitting an on-disk `okhttp3.Cache` forces full network refetches for unexpired cached responses or `304 Not Modified` validations. Additionally, placing `BrotliInterceptor` before `HttpLoggingInterceptor` causes logging to receive compressed `br` payloads and print `(encoded body omitted)`, appending fallback interceptors downstream silences fallback logs, and sharing the 30 MB API cache with Coil triggers dual caching and rapid eviction of API responses.
     - *Mandatory Architectural Contract*:
       1. `NetworkModule` MUST register `loggingInterceptor` BEFORE `BrotliInterceptor` on the shared `OkHttpClient` so incoming responses are decompressed before `HttpLoggingInterceptor` logs them in debug builds. `OpenSubtitlesHttpClient` MUST also register `BrotliInterceptor`.
       2. An on-disk `okhttp3.Cache` (30 MB) MUST be configured in `NetworkModule.provideHttpCache` and attached to `OkHttpClient` for API metadata responses.
       3. Specialized clients (such as YTS with fallback interceptors) MUST be constructed via `okHttpClient.newBuilder().apply { interceptors().add(0, fallbackInterceptor) }` so fallback requests and errors are captured by `HttpLoggingInterceptor`.
       4. Coil 3 `ImageLoader` in `LaTorrentolaApp` MUST register `OkHttpNetworkFetcherFactory(callFactory = { okHttpClient.get().newBuilder().cache(null).build() })` to share the connection pool, TLS reuse, and Brotli decompression while isolating image disk storage to Coil's dedicated `DiskCache` without polluting or evicting the 30 MB API response cache.
   - **PROHIBITION: NEVER register `BrotliInterceptor` before `HttpLoggingInterceptor` in `OkHttpClient`, NEVER append `fallbackInterceptor` downstream of logging in YTS client, NEVER share the 30 MB API `okhttp3.Cache` directly with Coil 3 `ImageLoader`, and NEVER instantiate isolated `OkHttpClient.Builder()` from scratch for Retrofit services when the shared `okHttpClient` can be extended via `newBuilder()`.**

21. **Zero-Flicker Authentication Exit & Favorites Placeholder Loading Contract (STRICT)**:
   - **The Premature `resetAuthState()` Login Profile Flash & Unenriched Favorites Poster Shrink Trap**:
     - *Root Cause / Failure Trap*:
       1. In `AppNavigation.kt:entry<Route.Login>`, calling `authViewModel.resetAuthState()` inside `onLoginSuccess` immediately resets `_authState.value = AuthState.Idle` while `Route.Login` is still executing its exit animation or popping from the backstack. Because `savedAccounts` is already populated in Room from the successful login, `LoginScreenContent` transitions out of the loading/success state and renders `ProfileSelectorBody` ("¿Quién anda ahí?") for a fraction of a second before `Route.Home` is drawn, creating a jarring profile picker screen flash.
       2. In `SearchViewModel.showFavorites()`, `FavoriteTvSeriesEntity` from Room lacks `seasons`. When entering Favorites with a progress-dependent filter (`FavoritesFilter.IN_PROGRESS`, `WATCHED`, or `UNWATCHED`), `TvSeriesProgressCalculator` fell back to estimated episode counts (10 eps), temporarily treating completed series as in-progress. If `SearchScreen` displayed content immediately or fell back to raw `favoriteTvSeries` via `uiState.favoriteSeries.ifEmpty { favoriteTvSeries }`, posters rendered immediately and then abruptly vanished or shrank when asynchronous TMDB enrichment arrived and recomputed the true watch status.
       3. Calling `calculateProgressPercent` repeatedly across large lists inside `FavoriteItemsGrid` caused redundant regex parsing and unindexed watch history scans.
     - *Mandatory Architectural Contract*:
       1. `AppNavigation.kt` MUST NOT reset auth state inside `onLoginSuccess`. `LoginScreenContent` MUST guard both `ProfileSelectorBody` and `WelcomeLoginBody` with `savedAccounts != null && authState !is AuthState.Success`, rendering an empty themed surface during `AuthState.Success` exit animations. `LoginScreen` MUST reset auth state exclusively via `DisposableEffect(Unit) { onDispose { viewModel.resetAuthState() } }`.
       2. In `SearchViewModel.showFavorites()`, when a progress-dependent filter is active (`FavoritesFilter.IN_PROGRESS`, `WATCHED`, `UNWATCHED`) and any series lacks `seasons` and is not yet cached, `_uiState.value` MUST remain `SearchUiState.Loading` (which displays `MovieListPlaceholder`) until TMDB enrichment completes. Premature content MUST NEVER be displayed before the definitive count is computed.
       3. In `SearchScreen.kt`, `FavoriteItemsGrid` MUST receive `uiState.favoriteSeries` directly (NEVER falling back to unfiltered `favoriteTvSeries`), and `seriesProgressPercentMap` MUST index watch history once via `TvSeriesProgressCalculator.buildEpisodeProgressIndex(watchHistoryMap.values)`.
   - **PROHIBITION: NEVER call `authViewModel.resetAuthState()` inside `onLoginSuccess` in navigation entries, NEVER render `ProfileSelectorBody` when `authState is AuthState.Success`, NEVER display partial/premature posters in Favorites before asynchronous TMDB season enrichment finishes when progress-dependent filters are active, and NEVER use `.ifEmpty { favoriteTvSeries }` to bypass filtered `uiState.favoriteSeries`.**

22. **Haze 2.1 Clear Liquid Glass + Material 3 (`haze-glass-material3`), Transparent Edge-to-Edge TopAppBars, Tier 2 Sheet Backdrop Blur & Android TV Sandboxing Mandate (STRICT - ZERO REGRESSION)**:
   - **The Bare Haze Glass Contract Breach, Solid Scroll-Fade Top Bar, Joined Player Slab & Entire-Composable Alpha Traps**:
     - Using bare `dev.chrisbanes.haze:haze-glass` without `dev.chrisbanes.haze:haze-glass-material3` and `GlassStyle.material3(...)` (falling back to hardcoded `Color.Black`/`Color.White` tints) violates the Material 3 design contract in `.agents/haze-skills/SKILL.md`.
     - Rendering solid `TopAppBar` containers (`TopAppBarDefaults.topAppBarColors()`) or fading `TopAppBar` to opaque `MaterialTheme.colorScheme.surface.copy(alpha = topBarAlpha)` on scroll creates an abrupt solid bar at the top of the viewport that degrades edge-to-edge immersion.
     - Applying Haze frosted glass to `TopAppBar`s or secondary sticky headers adds unnecessary visual noise and `hazeSource` plumbing across screens.
     - Wrapping ExoPlayer controls in joined opaque/frosted slabs (`Top Bar pill`, `Center capsule`, `Bottom card`) on narrow portrait phones obscures most of the active video frame.
     - Applying `.graphicsLayer { alpha = ... }` to a parent glass container dims text/icons along with the glass, and stacking opaque `.background(...)` over `hazeGlass` blocks the underlying live video.
     - Using semi-transparent `ModalBottomSheet` containers over partial `hazeSource` regions causes half-blurred backdrop collisions during enter/dismiss animations.
   - **Centralized Material 3 Glass & Transparent Edge-to-Edge Top Bar Architecture (`ui/theme/HazeModifiers.kt` + `haze`, `haze-glass`, `haze-glass-material3` `2.1.0`)**:
     - All handheld Haze usage MUST go through `HazeModifiers.kt` (never raw `rememberHazeState()` / `GlassStyle` / `hazeGlass(...)` ad-hoc), and Gradle dependencies MUST include `haze`, `haze-glass`, and `haze-glass-material3` (`2.1.0`):
       - **Transparent Edge-to-Edge Top App Bars (NO Haze, NO Solid Scroll Bar)**: Every `TopAppBar` across handheld and TV screens MUST set `TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)` with zero Haze modifiers, zero `hazeSource` wiring, and NO scroll-opaque `topBarAlpha` solid background fade.
       - **Per-Object Clear Liquid Glass + Material 3 on Handheld ExoPlayer (`hazeClearPlayerGlass`)**: Applied individually to each circular button (`ControlButton` via `glassHazeState`), the compact timestamp capsule, and Quick Seek / Double Tap pills in `LocalPlayerActivity` — NEVER as joined bars/cards. Uses `GlassStyle.clear` with shallow `2.dp` blur (`OpticalSizeValue.Fixed(2.dp)`) bound to Material 3 via `dev.chrisbanes.haze.glass.material3.material3(containerColor = ..., tint = ...)` and `MaterialTheme.colorScheme` tokens (`scrim`, `outline`) resolved by `resolveClearPlayerGlassColors(...)`, with NO stacked opaque `.background(...)` (falling back to translucent `scrim` at `42%` + `1.dp` `outline` border on API < 31). Pair with an edge-only vertical gradient scrim (`0.38f -> Transparent -> Transparent -> 0.42f`) and soft dark text shadows on unboxed titles so live video remains unobstructed behind and between controls.
       - **Tier 2 — Modal Bottom Sheets (`hazeSheetBackdropBlur` + Opaque Sheet Surface)**: All modal bottom sheets (`ActorDetailBottomSheet`, `GenreBottomSheet`, `TvGenreBottomSheet`, `CastBottomSheet`, `PlayerMobileBottomSheet`, and player overflow sheet) MUST use opaque `MaterialTheme.colorScheme.surfaceContainerHigh` with NO `hazeGlass` or `hazeState` parameter, while the parent screen root applies `Modifier.hazeSheetBackdropBlur(isSheetVisible = ..., enabled = !isTv)` (`Animatable` 0.dp ↔ 18.dp over 250ms in `graphicsLayer` via `RenderEffect.createBlurEffect` on Android 12+).
   - **ExoPlayer Media3 Live Video Sampling Pipeline & Android TV Sandboxing**:
     - On Handheld (`!isTv`), `PlayerSurface` MUST set `surfaceType = SURFACE_TYPE_TEXTURE_VIEW` with `Modifier.hazeSource(playerHazeState)`.
     - On Android TV (`isTv`), keep `surfaceType = SURFACE_TYPE_SURFACE_VIEW` and bypass all Haze shaders and `RenderEffect` blurs (`rememberHandheldHazeState(true) == null`, `hazeSheetBackdropBlur(..., enabled = false)`) to protect TV GPU budgets and D-pad focus navigation.
   - **PROHIBITION: NEVER use bare `haze-glass` without `haze-glass-material3` and `GlassStyle.material3(...)` bound to `MaterialTheme.colorScheme` tokens, NEVER render solid `TopAppBar` backgrounds or scroll-fading solid `topBarAlpha` bars (keep `containerColor = Color.Transparent` and `scrolledContainerColor = Color.Transparent`), NEVER apply Haze glass to Top App Bars or sticky screen headers, NEVER wrap handheld ExoPlayer controls in joined frosted slabs or stack opaque `.background(...)` on top of `hazeClearPlayerGlass`, NEVER invoke raw `hazeGlass`/`rememberHazeState`/`GlassStyle` outside `HazeModifiers.kt`, NEVER use semi-transparent `ModalBottomSheet` containers (Tier 2 requires opaque `surfaceContainerHigh` + root `hazeSheetBackdropBlur`), NEVER enable Haze or `RenderEffect` blur when `isTvDevice() == true`, and NEVER use `SURFACE_TYPE_SURFACE_VIEW` on handheld when sampling ExoPlayer video with `hazeSource`.**

23. **Android 17 (API 37) `ACCESS_LOCAL_NETWORK` & `MediaRouter2` Discovery Gating Mandate (STRICT)**:
   - **The Premature `MediaRouter.addCallback` & Infinite Cast Spinner Trap (`Issue #581018323`)**:
     - *Root Cause / Failure Trap*: Starting with Android 17 (API 37, `CINNAMON_BUN`), apps targeting SDK 37+ are blocked from LAN mDNS/SSDP/Cast and SMB/NFS traffic unless `android.permission.ACCESS_LOCAL_NETWORK` is declared in `AndroidManifest.xml` and granted at runtime. Furthermore, when `MediaTransferReceiver` is declared in the manifest, `androidx.mediarouter` delegates discovery to `MediaRoute2Provider` (`MediaRouter2`). If any screen or overlay (`CastBottomSheet`, `FloatingVideoPipOverlay`, `TorrentDownloadsScreen`) calls `MediaRouter.addCallback` while `ACCESS_LOCAL_NETWORK` is still denied, `MediaRouter2` caches an empty route state (`Issue #581018323`) and fails to push newly discovered Cast receivers even after the user grants the permission at runtime—leaving `CastBottomSheet` spinning infinitely on `"Searching for devices…"`.
     - *Mandatory Architectural Contract*:
       1. `AndroidManifest.xml` MUST declare `<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />`.
       2. All API 37+ local network permission detection, `ActivityCompat.shouldShowRequestPermissionRationale` / App Settings fallback routing, and `MediaRouter` callback flag selection MUST go through `LocalNetworkPermissionHelper`.
       3. Every `MediaRouter.addCallback` registration (`CastBottomSheet`, `FloatingVideoPipOverlay`, `TorrentDownloadsScreen`) MUST be gated on `hasLocalNetworkPermission == true` (refreshed via `Lifecycle.Event.ON_RESUME` and `ActivityResultContracts.RequestPermission`).
       4. `CastBottomSheet` MUST display a localized Material 3 / TV Material 3 permission card when `ACCESS_LOCAL_NETWORK` is denied, perform a brief callback unregistration/re-registration gap (`isRestartingDiscovery`) with `CALLBACK_FLAG_REQUEST_DISCOVERY or CALLBACK_FLAG_PERFORM_ACTIVE_SCAN` upon grant or manual rescan, and provide a timed Rescan action if discovery yields no routes.
       5. Do NOT launch `ACCESS_LOCAL_NETWORK` concurrently with `POST_NOTIFICATIONS` on cold start in `MainActivity.onCreate()` (simultaneous system permission dialogs cancel each other); request it contextually in Cast and Network Storage UIs (`CastBottomSheet`, `ExternalMediaBrowserScreen`).
   - **PROHIBITION: NEVER call `MediaRouter.addCallback` before `LocalNetworkPermissionHelper.hasLocalNetworkPermission(context)` is `true`, NEVER leave `CastBottomSheet` showing an infinite spinner when `ACCESS_LOCAL_NETWORK` is missing, and NEVER trigger `ACCESS_LOCAL_NETWORK` simultaneously with `POST_NOTIFICATIONS` on `MainActivity` cold start.**

24. **Zero-Deprecation, Modern API Migration & Compiler Warning Hygiene Mandate (STRICT)**:
   - **The Deprecated Test Shim, OkHttp 5 Nullability & Library API Rot Trap**:
     - *Root Cause / Failure Trap*: Dependency upgrades (such as OkHttp 5 changing `Response.body` from nullable `ResponseBody?` to non-null `ResponseBody`, or `smbj` deprecating `DiskEntry.getFileName()` in favor of `getUncPath()` / `getPath()`) leave behind `Unnecessary safe call` (`?.`) and `Deprecated in Java` warnings in tests and callers. Similarly, keeping `@Deprecated` `@VisibleForTesting` methods alive in production ViewModels solely for legacy unit tests (e.g., `TvEpisodeDetailViewModel.markEpisodeAsDownloaded`) perpetuates prohibited patterns and compiler warnings instead of testing the modern replacement (`markEpisodeAsCompleted` / `markEpisodeAsUncompleted`).
     - *Mandatory Architectural Contract*:
       1. Always fix deprecation and nullability warnings at the root by migrating call sites, mocks, and unit tests to the current non-deprecated APIs (e.g., `res.body.bytes()` / `res.body.string()` in OkHttp 5+, `DiskEntry.getUncPath()` / `DiskEntry.getPath()` in `smbj`).
       2. When a method is superseded or prohibited, delete the deprecated `@VisibleForTesting` wrapper and its dead helper code immediately, and update unit tests to validate the active non-deprecated API (`markEpisodeAsCompleted` / `markEpisodeAsUncompleted`).
       3. Validate `:app:compileDebugUnitTestKotlin` alongside `testDebugUnitTest` so test source sets remain warning-free.
   - **PROHIBITION: NEVER silence compiler deprecation or unnecessary-safe-call warnings with `@Suppress("DEPRECATION")`, `@Suppress("UNNECESSARY_SAFE_CALL")`, or deprecated `@VisibleForTesting` shims; always migrate callers and tests to the non-deprecated modern API.**

25. **Media3 Stuck Buffering Detection, Bounded Load Error Retries & Stream Stall Resilience Mandate (STRICT)**:
   - **The Infinite 500ms Retry Loop & 10-Minute `StuckPlayerException` Crashlytics Flood Trap**:
     - *Root Cause / Failure Trap*:
       1. In `PlayerStreamingConfig.buildLoadErrorHandlingPolicy()`, overriding `DefaultLoadErrorHandlingPolicy.getRetryDelayMsFor()` to unconditionally return `500L` without checking `loadErrorInfo.errorCount > maxRetries` or delegating non-retryable error detection to `super.getRetryDelayMsFor(loadErrorInfo)` created an infinite retry loop on stalled or aborted streams (e.g. 0 seeds, network drop, or piece verification timeout).
       2. Holding ExoPlayer in `STATE_BUFFERING` with `playWhenReady == true` for 10 continuous minutes (600,000 ms) triggered Media3's default `StuckPlayerDetector.StuckBufferingDetector`, throwing an `ExoPlaybackException.createForUnexpected(...)` caused by `StuckPlayerException` (obfuscated as `md2` / `h88`).
       3. In `LocalPlayerActivity.kt`, `onPlayerError` logged the exception with `AppLogger.e`, flooding Firebase Crashlytics with Non-fatal error reports for normal network/torrent stalls.
       4. Because `resolvePlaybackDecodingError` only recognized audio/video decoder issues, the stall error was ignored, leaving the player silently frozen in `STATE_IDLE` on a black screen without user feedback.
     - *Mandatory Architectural Contract*:
       1. `PlayerStreamingConfig.buildLoadErrorHandlingPolicy(maxRetries: Int = 6)` MUST return `C.TIME_UNSET` when `loadErrorInfo.errorCount > maxRetries` and delegate non-retryable errors to `super.getRetryDelayMsFor(loadErrorInfo)`, so fatal exceptions are terminated immediately.
       2. `PlaybackService.kt` MUST configure `.setStuckBufferingDetectionTimeoutMs(120_000)` on `ExoPlayer.Builder` (2 minutes instead of the default 10-minute 600,000ms trap).
       3. `LocalPlayerActivity.kt` MUST inspect player errors via `isStuckPlayerError(error)` and network connection checks: stream/socket stalls and `StuckPlayerException` MUST be logged with `AppLogger.w` (preventing un-damped Crashlytics issue spam). `LocalPlayerActivity` MUST attempt a single automatic recovery before displaying `PlaybackErrorDialog` configured with `isStallError = true`, an actionable **Retry** button that restarts streaming and re-prepares playback, and a **Close** button.
   - **PROHIBITION: NEVER return a constant retry delay from LoadErrorHandlingPolicy without bounding by maxRetries and delegating non-retryable errors to super (which returns C.TIME_UNSET), NEVER allow ExoPlayer stuck buffering detection to default to the 10-minute (600,000ms) trap, NEVER report StuckPlayerException or transient stream socket stalls via AppLogger.e to Crashlytics, and NEVER leave the player in STATE_IDLE on a black screen without presenting a retryable stall error dialog.**

26. **Third-Party Submodule Immutability & JNI-Layer `libnfs` UDP Broadcast Discovery Mandate (STRICT)**:
   - **The Third-Party Submodule Modification Prohibition & Broadcast UDP `rpc->pdu` Double-Free (`Scudo SIGABRT` in `rpc_purge_all_pdus` / `rpc_destroy_context`) Trap**:
     - *Root Cause / Failure Trap*:
       1. External Git submodules (`app/src/main/cpp/libnfs`, `app/src/main/cpp/ffmpeg`, `.agents/skills`, `.agents/firebase-skills`) are read-only upstream dependencies; modifying files inside third-party submodules dirty-states the repository and breaks upstream sync.
       2. In upstream `libnfs`, `nfs_find_local_servers()` initializes a broadcast UDP context (`rpc->is_udp = 1`, `rpc->is_broadcast = 1`) with only 4 default hash buckets (`DEFAULT_HASHES = 4`) and queues `PMAP2_CALLIT` probes into `rpc->waitpdu[hash]` across 3 rounds without purging previous rounds.
       3. When a LAN server replies to a broadcast UDP probe, upstream `rpc_read_from_socket()` assigns `rpc->pdu = rpc_find_pdu(rpc, xid)`, leaves the PDU inside `rpc->waitpdu[hash]`, zeroes `pdu->next = NULL` (severing hash bucket collisions), and never clears `rpc->pdu = NULL` after `rpc_process_pdu()`.
       4. When `nfs_find_local_servers()` later calls `rpc_destroy_context(rpc)`, `rpc_fail_reading_pdu()` frees `rpc->pdu` and `rpc_purge_all_pdus()` immediately frees the exact same PDU from `rpc->waitpdu[hash]` a second time—triggering Android Scudo's `invalid chunk state when deallocating address` `SIGABRT`.
     - *Mandatory Architectural Contract*:
       1. **Third-Party Submodule Immutability**: NEVER modify any file inside `app/src/main/cpp/libnfs`, `app/src/main/cpp/ffmpeg`, or any third-party submodule. All workarounds and integrations MUST be implemented in our own wrapper code (`app/src/main/cpp/libnfs_jni.c` or Kotlin classes).
       2. In `app/src/main/cpp/libnfs_jni.c`, `Java_com_martinrevert_latorrentola_service_storage_remote_LibNfsNative_nativeFindLocalServers` MUST NOT call upstream `nfs_find_local_servers()`. Instead, it MUST use our JNI wrapper implementation (`jni_find_local_servers_safe()`) built on `libnfs` APIs (`rpc_init_udp_context`, `rpc_set_hash_size(rpc, 64)`, `rpc_bind_udp`, `rpc_set_udp_destination`, `rpc_pmap2_callit_task`, `rpc_service`, `rpc_purge_all_pdus`, `rpc_destroy_context`):
          - Call `rpc_set_hash_size(rpc, 64)` before `rpc_bind_udp()` and `rpc_purge_all_pdus(rpc, RPC_STATUS_CANCEL, NULL)` at the start of each probe round so broadcast XIDs never collide in the same `waitpdu[hash]` bucket.
          - Clear `rpc->pdu = NULL` inside `jni_callit_cb()`, after every `rpc_service()` call, and immediately before `rpc_purge_all_pdus()` / `rpc_destroy_context(rpc)` so `rpc_fail_reading_pdu()` never double-frees a broadcast PDU residing in `rpc->waitpdu[hash]`.
          - Clamp `poll(&pfd, 1, mpt)` timeout with `if (mpt < 0) { mpt = 0; }` so negative elapsed time never blocks indefinitely.
       3. On the Kotlin layer, `NetworkStorageScanner.scanNetwork()` MUST serialize concurrent scans via `scanMutex`, and all context-creating `LibNfsNative` discovery/export calls (`findLocalServersSynchronized()`, `getExportsSynchronized()`) MUST synchronize on `LibNfsNative.discoveryLock`.
   - **PROHIBITION: NEVER edit files inside third-party submodules (`app/src/main/cpp/libnfs`, `app/src/main/cpp/ffmpeg`), NEVER call upstream `nfs_find_local_servers()` directly from `libnfs_jni.c`, NEVER call `rpc_destroy_context()` or `rpc_purge_all_pdus()` on a broadcast UDP context without clearing `rpc->pdu = NULL` first, and NEVER invoke `LibNfsNative.nativeFindLocalServers()` or `LibNfsNative.nativeGetExports()` outside `LibNfsNative.discoveryLock`.**

27. **Offline-First TV Progress Cache, Progressive Home Startup & Lightweight Watch History Delta Sync Mandate (STRICT)**:
   - **The Cold Favorites TMDB Storm, Blocking Multi-Page Home Startup & 15-Minute Cross-Device Sync Lag Trap**:
     - *Root Cause / Failure Trap*:
       1. Fetching full `TmdbService.getTvDetails` payloads (`append_to_response=external_ids,credits,videos,images`, 500 KB–2 MB each) sequentially for every favorited TV series on cold start—even for unwatched series—caused multi-second stalls on Favorites "Viendo" because `FavoriteTvSeriesEntity` does not store `seasons`.
       2. Waiting on `HomeViewModel` to sequentially fetch up to 4 YTS pages before emitting the first `HomeUiState.Success`, combined with slow 15s/30s timeouts on unreachable YTS mirrors and zero prefetching during `Route.Login`, significantly delayed Home first paint.
       3. Throttling all cloud sync behind `UserLibraryRepository.syncFromCloud()`'s 15-minute library cooldown left cross-device watch history (e.g., finishing an episode on Android TV and opening the phone) stale for up to 15 minutes unless the user manually signed out or restarted.
       4. Running Login-to-Home prefetch inside `AuthViewModel.viewModelScope` gets canceled immediately when `Route.Login` is popped, aborting the in-flight request that `HomeViewModel` coalesced onto.
       5. Invoking `syncFromCloud()` while holding `watchHistoryDeltaMutex` creates a non-reentrant deadlock if `syncFromCloud()` delegates back to `syncWatchHistoryDelta()`.
     - *Mandatory Architectural Contract*:
       1. **Decoupled Offline-First Room Cache (`tv_progress_cache`, `AppDatabase` v15)**:
          - Persist lightweight TMDB season/air-schedule metadata (`seasonsJson`, `lastAirDate`, `nextAirDate`, `numberOfSeasons`, `numberOfEpisodes`, `status`, `inProduction`, `imdbId`, `fetchedAtMs`) in a standalone device-shared `tv_progress_cache` Room table (`TvProgressCacheEntity` / `TvProgressCacheDao`), completely decoupled from `favorites_tv` and Firestore sync so cloud sync never clobbers season schedules.
          - Enforce adaptive TTL via `TvProgressCacheEntity.isStale()`: **6 hours** for ongoing/in-production series (`Returning Series`, `In Production`, `Planned`, `Pilot`, or blank status) and **7 days** for ended/canceled series (`Ended`, `Canceled`). Always fall back to stale persisted data when offline.
       2. **Lightweight Progress Endpoint, Unwatched Skip & Bounded Concurrency (`SearchViewModel`)**:
          - Favorites and Watching TV progress enrichment MUST call `TmdbRepository.getTvProgressSummary` (`TmdbService.getTvProgressSummary`, `append_to_response="external_ids"` only—never full `getTvDetails`).
          - Skip TMDB enrichment via `shouldEnrichSeriesForProgress()` whenever watch history has no episodes or a series with a known canonical IMDb ID has zero watched episodes.
          - Hydrate from Room (`getPersistedTvProgressSummaries`) BEFORE touching the network. Refresh stale entries silently in the background (`scheduleSilentStaleRefresh`) without resetting `_uiState` to `Loading`, preserving Directives #19 and #21 (still holding `Loading` placeholders for true cache misses under progress-dependent filters).
          - Fetch true cache misses concurrently using bounded parallelism (`Semaphore(6)` + `async`/`awaitAll`).
       3. **Speculative Home Prefetch, Progressive Page 1 & Fast YTS Failover**:
          - `YtsRepository` MUST keep a short-TTL (3 min) in-memory page cache with in-flight request coalescing (`CompletableDeferred`), bypassing cache when `forceRefresh = true`.
          - `YtsRepository.prefetchHomeFirstPage(...)` MUST run on `YtsRepository`'s singleton `prefetchScope` (`SupervisorJob() + Dispatchers.IO`), NEVER on `AuthViewModel.viewModelScope`, and be triggered from `LoginScreen` via `AuthViewModel.prefetchHomeCatalog()`.
          - `HomeViewModel.loadMovies()` and `refresh()` MUST emit `HomeUiState.Success` immediately once Page 1 produces visible movies while continuing to fetch subsequent pages up to `TARGET_INITIAL_BATCH`, and MUST trigger a low-priority one-shot background pre-warm (`Semaphore(4)` on `Dispatchers.IO`) of favorited TV series that have episode progress.
          - `NetworkModule` `@YtsRetrofit` derived `OkHttpClient` MUST enforce fast failover timeouts (`5s` connect, `8s` read) while keeping `fallbackInterceptor` prepended at index 0 (Directive #20).
       4. **Lightweight Cross-Device Watch History Delta Sync & Unchanged 10-Second Player Contract**:
          - `UserLibraryRepository.syncWatchHistoryDelta(force = false)` MUST query ONLY `users/{uid}/watch_history` with a single-field index query: `whereGreaterThan("timestamp", lastSync - 5_000L).orderBy("timestamp", DESCENDING).limit(50)` (0 composite Firestore indexes required), throttled by a **30-second cooldown** (bypassed only when `force = true`), serialized by `watchHistoryDeltaMutex`, and tracked under its own metadata key (`COLLECTION_KEY_WATCH_HISTORY_DELTA`).
          - When `effectiveLastSync == 0L`, delegate to `syncFromCloud(forceFull = false)` **OUTSIDE** `watchHistoryDeltaMutex.withLock` to guarantee zero re-entrant deadlock.
          - Trigger `syncWatchHistoryDelta(force = false)` on `MainActivity.onResume()`, `SearchViewModel.showFavorites()`, `showWatchingMovies()`, `showWatchingTvSeries()`, and when `syncFromCloud()` hits its 15-minute library cooldown; trigger `syncWatchHistoryDelta(force = true)` on Home pull-to-refresh (`HomeViewModel.refresh(showIndicator = true)`).
          - **STRICT PLAYER WRITE CONTRACT PRESERVATION**: The 10-second playback ticker MUST continue writing **EXCLUSIVELY to Room (`syncRemote = false`)**. Remote Firestore writes (`syncRemote = true`) remain strictly limited to pause, stop, player exit, Cast handoff, and completion.
   - **PROHIBITION: NEVER store TMDB season progress cache inside `favorites_tv` or sync it to Firestore, NEVER call full `getTvDetails()` for Favorites/Watching progress enrichment, NEVER run `prefetchHomeFirstPage` inside `AuthViewModel.viewModelScope`, NEVER call `syncFromCloud()` while holding `watchHistoryDeltaMutex`, NEVER add a Firestore composite index requirement to `syncWatchHistoryDelta()`, NEVER attach real-time `addSnapshotListener` listeners for watch history, and NEVER change the player's 10-second Room-only ticker (`syncRemote = false`) to write to Firestore.**

28. **Zero-Footprint Private SFTP Release Deployment & Mandatory SHA-256 In-App Auto-Update Contract (STRICT)**:
   - **The Public `build.gradle` Credential Exposure, Stale Manifest Cache, Unverified APK Installation & Debug-Build Lockout Traps**:
     - *Root Cause / Failure Trap*:
       1. Defining private SFTP deployment logic, internal server IPs, or SSH credentials directly inside `app/build.gradle` leaks infrastructure and secrets to the public Git repository. Furthermore, accessing `Task.project` inside `doLast` breaks Gradle when `org.gradle.configuration-cache=true` is enabled in `gradle.properties`.
       2. Uploading `manifest.json` before the `.apk` file finishes uploading exposes a race window where clients fetch the new manifest and receive 404 or truncated APK files, while leaving old `.apk` and `.sha256` files indefinitely fills server storage.
       3. Querying `manifest.json` through an `OkHttpClient` with a 30 MB disk cache without `CacheControl.FORCE_NETWORK` (`no-cache, no-store`) serves stale cached manifests, delaying mandatory update detection.
       4. Launching the package installer on a downloaded APK without verifying its cryptographic `SHA-256` digest against `manifest.sha256` risks installing corrupted or tampered binaries.
       5. Running mandatory update checks when `BuildConfig.DEBUG == true` (`com.martinrevert.latorrentola.debug`) blocks development builds and fails installation due to applicationId/signing mismatches against the release APK.
     - *Mandatory Architectural Contract*:
       1. **Zero-Footprint Gradle Init Script (`~/.gradle/init.d/latorrentola-deploy.gradle` + `local.properties`)**:
          - Keep `app/build.gradle` 100% free of private deployment tasks. The user-level Gradle init script reads `DEPLOY_SFTP_*` and `DEPLOY_PUBLIC_BASE_URL` from git-ignored `local.properties`, registers a configuration-cache-safe `@DisableCachingByDefault` typed task (`publishReleaseToLocalServer`), and hooks `assembleRelease.finalizedBy(publishTaskProvider)`.
          - Upload order MUST be strictly atomic: 1) `<release>.apk`, 2) `<release>.apk.sha256`, 3) `manifest.json`, followed by SFTP cleanup (`rm`) of previous `.apk` and `.sha256` versions in the remote APK folder.
       2. **Release-Only Mandatory Check & SHA-256 Verification (`AppUpdateRepository` & `AppUpdateDialog`)**:
          - `AppUpdateRepository.checkForMandatoryUpdate()` MUST immediately return `null` when `isDebugBuild == true` (`BuildConfig.DEBUG`), and use `CacheControl.FORCE_NETWORK` (`no-cache, no-store`) when querying `Constants.APK_UPDATE_MANIFEST_URL`.
          - `AppUpdateRepository.downloadAndVerifyApk()` MUST compute the `SHA-256` digest of the downloaded file and verify it against `manifest.sha256` before emitting `ApkDownloadState.ReadyToInstall`; on mismatch, it MUST immediately delete the file and emit `AppUpdateErrorType.CHECKSUM_MISMATCH`.
          - `AppUpdateDialog` MUST be non-dismissible (`dismissOnBackPress = false`, `dismissOnClickOutside = false`), support both Handheld M3 and Android TV M3 with explicit `themeMode` propagation and Streaming Red (`Color(0xFFE50914)`) progress bars, and handle `canRequestPackageInstalls()` + `FileProvider` (`${applicationId}.fileprovider`) installation seamlessly on `onResume()`.
   - **PROHIBITION: NEVER commit SFTP deployment tasks, internal server IPs, or credentials into `app/build.gradle`, NEVER enable mandatory APK update blocking when `BuildConfig.DEBUG == true`, NEVER upload `manifest.json` before the `.apk` upload completes, and NEVER launch the Android package installer on a downloaded APK without first verifying its SHA-256 checksum.**

29. **Transient Device-Side Network Failure Crashlytics Dampening Mandate (STRICT)**:
   - **The Offline/DNS Non-Fatal Flood Trap**:
     - *Root Cause / Failure Trap*: Repositories (`TorrentioRepository`, `EztvRepository`, `OmdbRepository`, etc.) catch `Exception` and log with `AppLogger.e(...)`, which records a Crashlytics non-fatal. When a user is offline or their DNS fails, `java.net.UnknownHostException: Unable to resolve host "torrentio.strem.fun"` (or `SocketTimeoutException` / `ConnectException` / `NoRouteToHostException`) is reported as an app defect although it is a client-environment condition that cannot be fixed in code.
     - *Mandatory Architectural Contract*: `CrashlyticsManager.findTransientNetworkCause(throwable)` / `CrashlyticsManager.isTransientNetworkError(throwable)` owns the unidirectional cause-chain check (`AppLogger.isTransientNetworkError` delegates to `CrashlyticsManager` with no cyclic dependency). `AppLogger.e(...)` and `AppLogger.recordException(...)` MUST route transient network throwables to `AppLogger.w` (logcat + breadcrumb only, NO `crashlyticsManager.logError`). A second, independent gate lives in `CrashlyticsManager.recordException(...)` and `CrashlyticsManager.logError(...)`: it drops the same transient network throwables, emits a single `WARN` breadcrumb naming the matched transient cause's simple class name, and never records a Crashlytics non-fatal.
   - **PROHIBITION: NEVER call `crashlyticsManager.logError` / `FirebaseCrashlytics.recordException` directly (bypassing `AppLogger`) for network failures, NEVER remove `isTransientNetworkError` dampening from `AppLogger.e` / `AppLogger.recordException`, NEVER remove the transient-network guard from `CrashlyticsManager.recordException` / `logError`, and NEVER reintroduce a cyclic import from `CrashlyticsManager` back to `AppLogger`.**

30. **Obsolete Video Surface MediaCodec Failure Auto-Recovery Mandate (STRICT)**:
   - **The `rendering to non-initialized(obsolete) surface` Non-Fatal Trap**:
     - *Root Cause / Failure Trap*: `MediaCodecVideoRenderer` throws `ExoPlaybackException` (`ERROR_CODE_DECODING_FAILED`, `format_supported=YES`) caused by `MediaCodec$CodecException: rendering to non-initialized(obsolete) surface` when the output `Surface` is released/replaced mid-render (PiP handoff, rotation, activity stop/start, TextureView recreation). It is a surface-lifecycle race, not a decoder defect, yet `LocalPlayerActivity.handlePlayerError` treated it as a generic error (`AppLogger.e` Crashlytics non-fatal plus a fatal decoder dialog).
     - *Mandatory Architectural Contract*: `isObsoleteSurfaceError(error)` (`ui/components/PlaybackErrorDialog.kt`) MUST inspect `MediaCodecVideoDecoderException.isSurfaceValid` and require `"surface"` message context alongside `"obsolete"`, `"non-initialized"`, `"abandoned"`, or `"released"`. Both `LocalPlayerActivity.handlePlayerError` and `setupControllerMedia` (PiP fast-path and active-item attachment via `handleExistingLocalPlayerError`) MUST route obsolete-surface errors on local playback to `handleObsoleteSurfaceError`: log with `AppLogger.w`, track attempts with monotonic `SystemClock.elapsedRealtime()` via `nextSurfaceRecoveryAttempt` (30 s window, reset on `STATE_READY` / `isPlaying` / stream change), bump `surfaceRebindKey`, cancel and track `surfaceRecoveryJob` across `onNewIntent` / `onStop` / `onDestroy`, preserve prior `playWhenReady`, verify `Lifecycle.State.STARTED` after the 500 ms delay before re-preparing at `resolveRecoveryPlaybackPositionMs(...)` (preferring positive controller position to preserve backward seeks), and after `MAX_SURFACE_RECOVERY_ATTEMPTS` (2) show `resolveExhaustedSurfaceRecoveryError(error)` (falling back to a retryable stall error if `resolvePlaybackDecodingError` returns `null`).
   - **PROHIBITION: NEVER report obsolete-surface MediaCodec failures through `AppLogger.e` / Crashlytics, NEVER show the fatal decoder dialog on the first obsolete-surface failure, NEVER run delayed surface recovery when stopped or without cancelling stale recovery jobs, and NEVER retry without rebinding the video surface (`surfaceRebindKey++`).**

31. **Android TV Player High-Contrast Focus & Time-Based D-Pad Timeline Scrubbing Mandate (STRICT)**:
   - **The Low-Contrast TV Focus, Light-Mode Scrim Inversion & Abrupt/00:00-Jumping D-Pad Slider Traps**:
     - *Root Cause / Failure Trap*:
       1. Applying a translucent `.background(onSurface.copy(alpha = 0.25f))` wash and hardcoding `Icon(tint = tint)` inside `TvIconButton` produced washed-out focus contrast on Android TV player buttons; rendering TV controls over a 50% black scrim under `THEME_LIGHT` also caused dark `onSurfaceLight` (`#151E11`) icons and focused circles to vanish against the black video scrim.
       2. Leaving Material 3 `Slider` (`WatchProgressSlider`) unhandled on Android TV caused abrupt percentage jumps, while coercing unknown/zero durations to `1L`, omitting `dur > 0L` guards in `PlayerControlsOverlay`/`RemoteCastCanvas`, failing to scope debounce coroutines to `(controller, currentMediaId)`, leaving `!isSliderFocused` in the auto-hide guard, ignoring `repeatCount > 0` on OK/Enter, or letting the 250ms ticker overwrite optimistic seek positions caused accidental seeks to `00:00`, cross-episode seeks, permanently stuck overlays, repeat play/pause toggles, and thumb snap-back.
     - *Mandatory Architectural Contract*:
       1. On Android TV (`isTv`, memoized with `remember(context)`), `PlayerControlsOverlay` MUST scope its dark-scrim video overlay body in `TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK)` (keeping modals/sheets on the user's `themeMode`), use `TvMaterialTheme.colorScheme` tokens exclusively, and render `ControlButton` via `androidx.tv.material3.IconButton` with `TvIconButtonDefaults.colors(containerColor = Color.Transparent, contentColor = resolvedTint, focusedContainerColor = TvMaterialTheme.colorScheme.onSurface, focusedContentColor = TvMaterialTheme.colorScheme.surface, disabledContainerColor = Color.Transparent, disabledContentColor = TvMaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))`, `scale = TvIconButtonDefaults.scale(focusedScale = 1.15f)`, explicit `buttonSize` framing, and `TvIcon` inheriting `LocalContentColor.current`.
       2. On Android TV, `WatchProgressSlider` MUST compute `dur = duration.coerceAtLeast(0L)` (never `1L`), gate scrubbing on `dur > 0L`, intercept `Key.DirectionLeft`, `Key.DirectionRight`, and `Key.DirectionCenter`/`Key.Enter`/`Key.NumPadEnter` (ignoring `repeatCount > 0` on OK/Enter) via `onPreviewKeyEvent`, compute time-based steps via `PlayerTvScrubHelper` (`10s` for repeats `0..4`, `30s` for `5..12`, `60s` for `> 12`, scaled down to `<= totalDurationMs` for short/sub-second clips, with finite-checked `Double` precision in `fractionToPositionMs`), reset scrub state and key the 450ms debounce `LaunchedEffect` on `(controller, currentMediaId, tvScrubCommitTrigger)`, hold optimistic seek positions briefly against the 250ms ticker until `onPositionDiscontinuity`/convergence/expiry, and omit `!isSliderFocused` from the 6-second auto-hide guard so controls hide after debounced scrubs.
       3. Every `WatchProgressSlider` `onValueChange` / `onValueChangeFinished` callback (`PlayerControlsOverlay` and `RemoteCastCanvas`) MUST guard on `isScrubbing` AND `dur > 0L` so stray or pre-metadata events can never seek to `00:00`. Handheld touch scrubbing MUST remain preserved.
   - **PROHIBITION: NEVER hardcode `tint = tint` on `TvIcon` or render TV dark-scrim player controls with `THEME_LIGHT` or mobile `MaterialTheme.colorScheme` tokens, NEVER coerce unknown/zero duration to `1L` in player timelines, NEVER leave TV scrub debounce coroutines unkeyed on `(controller, currentMediaId)`, NEVER block overlay auto-hide on `isSliderFocused`, NEVER allow `repeatCount > 0` OK/Enter events to toggle play/pause on the slider, and NEVER invoke `controller.seekTo(...)` inside `onValueChangeFinished` without verifying `isScrubbing && dur > 0L`.**

32. **OpenSubtitles Strict IMDb-ID-Only Search, All Three Spanish Codes (`ea,es,sp`) & Parallel Language Batching Mandate (STRICT)**:
   - **The Ignored `currentImdbId`, YTS-to-TMDB ID Mismatch, Missing `ea`/`sp` Codes & English Page-1 Crowding Traps**:
     - *Root Cause / Failure Trap*:
       1. `LocalPlayerActivity.searchSubtitles()` ignored `currentImdbId`, `dbRecord?.imdbId`, and canonical `MediaItem.mediaId`, and passed `effectiveMovieId` (a **YTS internal ID**, NOT a TMDB ID) into `tmdbRepository.getMovieImdbId(effectiveMovieId)`. TMDB returned either `null` or a completely unrelated movie's IMDb ID.
       2. `OpenSubtitlesRepository` requested only `languages=en,es` in a **single** `page=1` query sorted by `download_count desc`. On popular movies (e.g. *Twilight*, `tt1099212`), top-50 English subtitles crowded Spanish subtitles completely off page 1, while OpenSubtitles' regional Spanish codes `ea` (Spanish LA) and `sp` (Spanish EU) were neither requested nor accepted by `isEnglishOrSpanish()`, `sortSubtitleResultsForDevice()`, or `getLanguageFlag()`.
       3. Retaining leading zeros on `imdb_id` (`tt0111161` -> `0111161`) and non-alphabetical `@Query` parameters caused `301` redirects on OpenSubtitles' CDN.
       4. Falling back to text-query OpenSubtitles searches (`openSubtitlesRepository.search(query = ...)`) returned noisy/unrelated releases, and retrying TV episodes without `seasonNumber`/`episodeNumber` erroneously sent `type=movie`.
     - *Mandatory Architectural Contract*:
       1. `LocalPlayerActivity.searchSubtitles()` MUST query OpenSubtitles **exclusively by IMDb ID** (`openSubtitlesRepository.searchByImdbId(...)`) with **zero name/text-query fallback** to OpenSubtitles.
       2. IMDb ID resolution MUST first check already-known sources via `resolveKnownImdbId` (`currentImdbId`, `EXTRA_IMDB_ID`, `TorrentDownloadService.EXTRA_IMDB_ID`, `dbRecord?.imdbId`, canonical `mediaItemId`, direct `tt\d{5,10}` tokens), then catalog IDs (`tmdbRepository.getTvImdbId` for TV series, `ytsRepository.getMovieSummary(movieId).data?.movie?.imdbCode` for YTS movies—never passing a YTS `movieId` to TMDB), and finally deduce the IMDb ID for local/USB/NAS files via title + release year matching (`selectBestMovieCandidate` on YTS, falling back to `tmdbRepository.searchMovies` + `MediaMetadataResolver.selectBestTmdbMovieCandidate` + `tmdbRepository.getMovieImdbId` with the genuine TMDB ID when absent from YTS, or `selectBestTvCandidate` on TMDB for TV) before calling `searchByImdbId`.
       3. `OpenSubtitlesRepository` MUST support all three OpenSubtitles API v1 Spanish codes (`es`, `ea`, `sp` via `SPANISH_LANGUAGE_CODES = "ea,es,sp"`) and, when searching without a single-language filter, execute **two parallel queries** (`languages=ea,es,sp` and `languages=en`), deduplicating by `fileId` so English never crowds out Spanish on page 1.
       4. `OpenSubtitlesRepository.normalizeImdbIdForApi` MUST strip both `tt` prefixes and leading zeros (`trimStart('0')`), and `OpenSubtitlesService.searchSubtitles` MUST use `@GET("subtitles")` with alphabetically ordered `@Query` parameters.
       5. `getLanguageFlag` MUST evaluate explicit language codes (`ea` -> `🇦🇷`, `sp` -> `🇪🇸`, `es`, `en`) before substring heuristics on filenames, and `TorrentDownloadsScreen` / `LocalPlayerActivity` MUST propagate `EXTRA_IMDB_ID` and `resolved.imdbId`.
   - **PROHIBITION: NEVER fall back to name/text-query OpenSubtitles searches (`openSubtitlesRepository.search(query = ...)`) in `LocalPlayerActivity.searchSubtitles()`, NEVER pass a YTS `movieId` to `tmdbRepository.getMovieImdbId(...)`, NEVER omit `ea` or `sp` from Spanish OpenSubtitles queries/filters/flags, NEVER combine English and Spanish into a single page-1 `download_count` query, and NEVER send leading zeros in `imdb_id`/`parent_imdb_id` to OpenSubtitles API v1.**

### Previews
- Every UI Component MUST include `@Preview` functions covering: Light Mode, Dark Mode, Mobile form-factor, and TV form-factor (using appropriate theme wrappers).

---

## 🧠 8. EXECUTION CHECKLIST
Before marking a task complete, verify:
1. [ ] Did I ask the user if this applies to Mobile, TV, or both? (If ambiguous).
2. [ ] Are Unit Tests written, updated, and passing?
3. [ ] Are there ZERO hardcoded colors (except `Color(0xFFE50914)` on progress bars/scrubbers), strings, or dimensions?
4. [ ] Is TypeSafe serialization fully intact (both annotations present)?
5. [ ] Is TV logic securely sandboxed behind `isTvDevice()`?
6. [ ] Did I pass `themeMode` from `PreferenceManager` to `TvLaTorrentolaTheme(themeMode = ...)` and `LaTorrentolaTheme(themeMode = ...)` in every Dialog, BottomSheet, and Compose entry point to prevent Dark Mode regressions on Android TV?
7. [ ] Did I use `pwsh` or `powershell` (on Windows) or `bash` with `./gradlew` (on Linux) for terminal commands?
8. [ ] Did I run unit tests (`testDebugUnitTest`) as validation instead of assembling release/debug APKs?
9. [ ] If modifying remote storage/streaming, did I preserve session caching, asynchronous prefetching, and keep hot-path read loops free of Log.d?
10. [ ] If modifying player controls or Cast handoff, did I avoid speculative `!isPlaying` flips, bind to `RemoteMediaClient`, and ensure zero bottom sheet flashing on Cast start?
11. [ ] If modifying audio rendering or player streaming config, did I preserve `EXTENSION_RENDERER_MODE_ON` (never `_PREFER`), keep direct passthrough ahead of FFmpeg, retain surround transcoding guardrails (`getFormatSupport == SINK_FORMAT_SUPPORTED_DIRECTLY`), ensure Tunneling and Audio Offload remain DISABLED to prevent video stalls and Atmos startup delays, keep TV playback buffer at 2,000ms, and maintain rebuffer cushion at 3,000ms?
12. [ ] If modifying proguard-rules.pro, models, or dependencies, did I avoid blanket package wildcards ({ *; }), verify library consumer rules, and ensure R8 optimizations/inlining remain unhindered?
13. [ ] Did I ensure 10-second playback tickers write ONLY to Room (`syncRemote = false`), syncing to Firestore only on pause, stop, player exit, or completion, while ensuring external client downloads mark 100% completion in Room & Firestore immediately and >95% internal playback records completed download entities?
14. [ ] Did I ensure that starting a torrent stream in internal player mode NEVER prematurely marks the episode or season as downloaded/completed, reserving completion exclusively for >= 95% playback?
15. [ ] Did I verify that TV series poster progress bars reflect the true overall weighted series consumption percentage via `TvSeriesProgressCalculator`, rather than a single episode's playback?
16. [ ] Did I ensure that the Next Episode dialog remains open while buffering up to 100% (`STATE_READY`), provides a pinned Close button, and that transitioning episodes in `LocalPlayerActivity` clears previous media/surfaces and rebinds `PlayerSurface` via `surfaceRebindKey`?
17. [ ] Did I update `README.md`, `README.es.md`, and `ARCHITECTURE.md` on the fly to reflect all new features, settings, or architectural components?
18. [ ] If resolving a bug, regression, or edge-case failure, did I automatically document a new mandatory directive in `AGENTS.md` and add its corresponding checklist item to prevent future regressions?
19. [ ] Did I ensure that the UI reads library and progress state exclusively from Room (0 continuous Firestore snapshot listeners), that Firestore uses `PersistentCacheSettings`, and that local Room data is partitioned by `userId` and preserved across logouts for shared devices?
20. [ ] Did I ensure that Season 0 specials are assigned canonical `${imdbId}_s0_e${episodeNumber}` IDs and strictly excluded from whole-series overall progress percentages and completion states?
21. [ ] Did I ensure that in Favorites grids, focus handling is decoupled from background calls, progress maps are precalculated via `remember`, and upcoming unreleased seasons/series do not falsely inherit watched progress or disrupt completed series?
22. [ ] Did I verify that all `Regex` patterns are precompiled as static constants rather than dynamically instantiated inside functions/loops, and that heavy reactive flow operations are offloaded using `.flowOn(Dispatchers.Default)`?
23. [ ] Did I verify that all TextToSpeech Binder IPC operations in `VoiceManager` are offloaded to `Dispatchers.IO` and wrapped in `try-catch` blocks to prevent UI thread hangs and ANRs on Android TV?
24. [ ] Did I verify that all ViewModel `StateFlow` collections in top-level Compose screen composables use `collectAsStateWithLifecycle()` to prevent lifecycle teardown slot table corruption in `GapComposer`?
25. [ ] Did I verify that piece availability queries use `handle.status(TorrentHandle.QUERY_PIECES)?.pieces()` with bounds-safe `hasPiece(p)` checks (`!isEmpty && p in 0 until size()`) instead of calling `handle.havePiece(p)` or raw `getBit(p)` on unaugmented status bitfields?
26. [ ] Did I verify that in `LocalPlayerActivity`, `isDeletingMedia` is reset in a `finally` block, `onNewIntent` processes new playback intents, error suppression does not use `nextEpisodeTriggered`, and `DO_NOTHING` `autoPlayMode` finishes cleanly after media deletion?
27. [ ] Did I verify that deep-link / push notification destinations are preserved across the unauthenticated login gate via `NavBackStackHandler.resetToWithTop(backStack, Route.Home, pendingRoute)` and that unauthenticated guest access remains prohibited?
28. [ ] Did I verify that local loopback stream server instances in `TorrentDownloadService` check `server.isAlive()` before reuse/resurrection, and that `LocalPlayerActivity` logs transient local loopback connection failures as warnings (`AppLogger.w`) up to 3 retries rather than immediately flooding Crashlytics with `AppLogger.e`?
29. [ ] Did I verify that constrained foldable handheld screens (< 360dp) use `FlowRow` for metadata badges and action buttons, decouple action buttons below poster rows, use adaptive padding, enforce `softWrap = false` on badge chips, and maintain 100% data and feature parity without clipping or vertical letter breakage?
30. [ ] If modifying `PlayerControlsOverlay`, did I keep portrait handheld controls anchored to the video frame (top bar with Back + visible title + ≤ 3 actions, overflow sheet for the rest, timestamps/fullscreen/scrubber on the video's bottom edge) while leaving TV and landscape layouts and D-pad focus untouched?
31. [ ] Did I ensure `AuthViewModel.savedAccounts` initializes with `null` so `LoginScreenContent` never flashes `WelcomeLoginBody` before `ProfileSelectorBody`, `AppNavigation` starts at `Route.Login` on cold start, and handheld profile selection always requires `BiometricAuthHelper.authenticate` when biometrics are available?
32. [ ] Did I verify that `TorrentSessionConfig.applyRuntimeOptimizations` is called immediately after `manager.start(sessionParams)`, `piece_progress_notification` is included in `createOptimizedAlertMask()`, `StreamingPiecePlanner` uses staggered staircase deadlines and a bounded sliding sequential window (`SEQUENTIAL_WINDOW_PIECES`), and warm DHT state is persisted across sessions?
33. [ ] Did I verify that `LocalPlayerActivity` clears its video surface at most once on exit (`releaseVideoSurfaceForPipHandoff()`), `FloatingVideoPipOverlay` never overwrites Media3's `TextureView.SurfaceTextureListener`, and partially downloaded torrents strictly retain their `http://127.0.0.1` `VerifiedTorrentHttpServer` URI (`EXTRA_PARTIAL_TORRENT_STREAM = true`) across metadata refreshes, PiP transitions, and card clicks so forward seeking never reads unverified sparse `file://` zero bytes?
34. [ ] Did I verify that NO `androidx.compose.material.*` (M2) imports were introduced?
35. [ ] Are all mobile components using `androidx.compose.material3.*` and TV components using `androidx.tv.material3.*`? 
36. [ ] For Handheld UI, did I evaluate if an M3 Expressive tactic (e.g., spring motion, shape morphing, or expressive containers) elevates the experience?
37. [ ] Did I verify that `app/google-services.json` includes client configurations for both `com.martinrevert.latorrentola` (Release) and `com.martinrevert.latorrentola.debug` (Debug), allowing both variants to build and co-exist on the same device?
38. [ ] Did I verify that `FirebaseMessagingInitializer` handles FIS authentication errors (`FirebaseInstallationsException`), purges stale installation tokens via `FirebaseInstallations.getInstance().delete()`, logs diagnostic instructions, and wraps `FirebaseMessaging` calls in `try-catch` blocks?
39. [ ] Did I verify that Haze 2.1 usage follows `ui/theme/HazeModifiers.kt` and `.agents/haze-skills/SKILL.md` (includes `haze-glass-material3:2.1.0` and binds `GlassStyle.clear` via `.material3(...)` + `MaterialTheme.colorScheme` tokens; transparent edge-to-edge `TopAppBar`s with `containerColor = Color.Transparent` and `scrolledContainerColor = Color.Transparent` and zero Haze or scroll-opaque fade; per-object `hazeClearPlayerGlass` with no stacked opaque background or joined slabs on handheld ExoPlayer; Tier 2 root `hazeSheetBackdropBlur` behind opaque `surfaceContainerHigh` modal bottom sheets; `SURFACE_TYPE_TEXTURE_VIEW` on handheld; and zero Haze/`RenderEffect` blur on Android TV)?
40. [ ] Did I verify that in `SearchViewModel`, `showWatchingMovies` and `showWatchingTvSeries` cleanly transition to `Empty` upon resolution completion without hanging on `Loading`, preserve `Loading` while background lookups are active (zero empty-state flash), cache resolved movies in memory, preserve canonical `imdbId` on TMDB lookups, and short-circuit when `episodeProgress` is empty?
41. [ ] Did I verify that `HttpLoggingInterceptor` precedes `BrotliInterceptor` on `OkHttpClient`, that on-disk `okhttp3.Cache` is attached for API metadata, that YTS client prepends `fallbackInterceptor` at index 0 via `okHttpClient.newBuilder().apply { interceptors().add(0, ...) }`, and that Coil 3 uses `OkHttpNetworkFetcherFactory` with `.cache(null)` to preserve image isolation?
42. [ ] Did I verify that `LoginScreen` resets auth state exclusively `onDispose` (never prematurely in `onLoginSuccess`), guards against rendering profiles when `authState is AuthState.Success`, that `SearchViewModel.showFavorites` retains `SearchUiState.Loading` (placeholders) until TMDB seasons enrich for progress-dependent filters, and that `FavoriteItemsGrid` uses `TvSeriesProgressIndex` without bypassing `uiState.favoriteSeries`?
43. [ ] Did I verify that `AndroidManifest.xml` declares `ACCESS_LOCAL_NETWORK`, all `MediaRouter.addCallback` callers (`CastBottomSheet`, `FloatingVideoPipOverlay`, `TorrentDownloadsScreen`) gate registration on `LocalNetworkPermissionHelper.hasLocalNetworkPermission(context)` and refresh on `ON_RESUME`, and `CastBottomSheet` / `ExternalMediaBrowserScreen` surface localized Grant / Open Settings prompts without competing with `POST_NOTIFICATIONS` on cold start?
44. [ ] Did I resolve all Kotlin compiler deprecation and unnecessary-safe-call warnings across main and unit test sources by migrating to non-deprecated modern APIs and removing obsolete `@Deprecated` `@VisibleForTesting` shims (without `@Suppress` workarounds)?
45. [ ] Did I verify that `PlayerStreamingConfig.buildLoadErrorHandlingPolicy` bounds retries to `maxRetries` (returning `C.TIME_UNSET` when exceeded and for non-retryable errors), `PlaybackService` bounds stuck buffering detection to 120,000ms, `LocalPlayerActivity` logs stream stalls and `StuckPlayerException` as warnings (`AppLogger.w`), and `PlaybackErrorDialog` displays a retryable stall card instead of leaving the player frozen in `STATE_IDLE`?
46. [ ] Did I verify that third-party submodules (`app/src/main/cpp/libnfs`, `app/src/main/cpp/ffmpeg`) remain 100% unmodified, that `libnfs_jni.c` uses `jni_find_local_servers_safe()` (clearing `rpc->pdu = NULL` after broadcast responses and before `rpc_purge_all_pdus` / `rpc_destroy_context`, pre-sizing hash buckets via `rpc_set_hash_size(rpc, 64)`, and clamping `poll()` timeouts), and that `NetworkStorageScanner.scanNetwork()` and `LibNfsNative` discovery/export calls are serialized via `scanMutex` and `LibNfsNative.discoveryLock`?
47. [ ] Did I verify that TV progress metadata is cached offline-first in Room v15 `tv_progress_cache` (decoupled from `favorites_tv` & Firestore) using lightweight `getTvProgressSummary` (`append_to_response="external_ids"`) with bounded `Semaphore(6)` parallelism and unwatched-series skipping, that Home uses singleton-scoped Login prefetch (`YtsRepository.prefetchScope`), 3-min cache/coalescing, progressive Page 1 emission, and 5s/8s YTS failover timeouts, and that cross-device watch history uses `syncWatchHistoryDelta` (30s cooldown, single-field `timestamp` query, bootstrap outside mutex) while keeping 10-second player tickers strictly Room-only (`syncRemote = false`)?
48. [ ] Did I verify that the SFTP release deployment task lives outside `app/build.gradle` (`~/.gradle/init.d/latorrentola-deploy.gradle` + `local.properties`), uploads `.apk` -> `.sha256` -> `manifest.json` atomically while cleaning older artifacts, and that `AppUpdateRepository` skips checks on `BuildConfig.DEBUG`, bypasses HTTP cache (`CacheControl.FORCE_NETWORK`), and verifies the downloaded APK's SHA-256 digest before launching `FileProvider` installation?
49. [ ] Did I verify that `CrashlyticsManager.findTransientNetworkCause` / `isTransientNetworkError` owns the unidirectional transient network cause check (with `AppLogger.isTransientNetworkError` delegating to it), `AppLogger.e` / `AppLogger.recordException` downgrade transient network failures to warning breadcrumbs, and `CrashlyticsManager.logError` / `recordException` emit a single warning breadcrumb naming the matched cause without recording Crashlytics non-fatals?
50. [ ] Did I verify that `isObsoleteSurfaceError` checks `MediaCodecVideoDecoderException.isSurfaceValid` and requires surface context, and that `LocalPlayerActivity` routes both live and pre-existing obsolete-surface errors to `handleObsoleteSurfaceError` (warning log only, monotonic `SystemClock.elapsedRealtime()` window reset on playback recovery/stream change, tracked `surfaceRecoveryJob` cancelled on stop/destroy/new intent, foreground `Lifecycle.State.STARTED` check, preserved `playWhenReady`, backward-seek-safe `resolveRecoveryPlaybackPositionMs`, and non-null exhaustion fallback)?
51. [ ] Did I verify that Android TV dark-scrim `PlayerControlsOverlay` is scoped to `TvLaTorrentolaTheme(themeMode = PreferenceManager.THEME_DARK)` with `TvMaterialTheme.colorScheme` tokens and memoized `isTv`, that TV `ControlButton`s use `TvIconButton` with inverted focus colors (`focusedContainerColor = onSurface`, `focusedContentColor = surface`) + `TvIcon` inheriting `LocalContentColor.current`, and that `WatchProgressSlider` uses `dur = duration.coerceAtLeast(0L)` + `dur > 0L` guards across `PlayerControlsOverlay` and `RemoteCastCanvas`, media-item-keyed 450ms debounced `PlayerTvScrubHelper` commits (with `repeatCount > 0` filtered on OK/Enter, pending-seek settling protection against the 250ms ticker, and auto-hide unblocked by `isSliderFocused`)?
52. [ ] Did I verify that OpenSubtitles searches run strictly by canonical IMDb ID (with zero name/text-query fallback), check known IMDb IDs before catalog lookups (using `ytsRepository.getMovieSummary` for YTS `movieId`s and never passing a YTS ID to TMDB), deduce IMDb IDs via title+year for local/USB/NAS files (YTS with TMDB `search/movie` -> genuine `tmdbMovieId` fallback for movies, TMDB `search/tv` for episodes), query Spanish (`ea,es,sp`) and English (`en`) in parallel with leading-zero-stripped IDs and alphabetical `@Query` params, and map `ea`/`sp`/`es`/`en` accurately in sorting and `getLanguageFlag`?