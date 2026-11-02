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
**Official Skills Submodule (`https://github.com/android/skills.git`)**:
- Skills are managed via Git Submodule at `.agents/skills` (compatible with Antigravity IDE and Android Studio) and linked via junction at `.android-studio/skills`.
- **On a new development machine**: Run `git submodule update --init --recursive` (and on Windows `mklink /J .android-studio\skills .agents\skills` if `.android-studio/skills` junction is missing).
- **To pull upstream skill updates**: Run `git submodule update --remote --merge .agents/skills`.

**IF** your task involves any of the following domains, **THEN** you MUST load the corresponding Android Studio / Antigravity Skill via the `read_file` tool on the skill's path before writing code:

- `isTvDevice() == true`, `androidx.tv`, or D-pad Focus -> **LOAD:** `leanback-to-compose-tv-migration`
- `LocalPlayerActivity`, `ExoPlayer`, Chromecast -> **LOAD:** `media3-cast-integration`
- `AppNavigation.kt`, Deep Links, TypeSafe Routes -> **LOAD:** `navigation-3`
- Responsive UI (Phone/Tablet/Foldable), System Bars -> **LOAD:** `adaptive` AND `edge-to-edge`
- `AuthRepository`, Firestore, FCM -> **LOAD:** `firebase-basics` AND `firebase-auth-basics`
- Magnet links (`Intent.ACTION_VIEW`) -> **LOAD:** `android-intent-security`
- ProGuard, R8, Shrinking, Build Optimization, Keep Rules -> **LOAD:** `performance/r8-analyzer`

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

### A. Theming & Hardcoded Values
- **NEVER** use explicit colors (e.g., `Color.Black`, `Color(0xFF...)`).
- **ALWAYS** extract colors from `MaterialTheme.colorScheme` (Mobile) or `TvMaterialTheme.colorScheme` (TV).
- **EXCEPCIÓN EXPLÍCITA DE COLOR (BARRAS Y SCRUBBERS DE PROGRESO DE HISTORIAL)**: Las barras y líneas de progreso de la historia de reproducción (`watch progress` en pósters de películas, fichas de episodios, miniaturas de catálogo, indicadores de salto rápido `quickSeek`), y los scrubbers/sliders de línea de tiempo de los reproductores (`WatchProgressSlider`, `Slider` en `PlayerControlsOverlay` y `RemoteCastCanvas`) DEBEN ser rojas (estándar de la industria / rojo streaming: `Color(0xFFE50914)`). Esta es una excepción formal y mandatoria para respetar el estándar visual de plataformas de streaming.
- **NEVER** hardcode strings. **ALWAYS** use the `UiText` wrapper or `strings.xml`.

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

### Data Sync Flow
- **Watch History**: Write to Room DAO FIRST (Immediate UI update) -> Sync to Firestore in the background. Room is the Single Source of Truth.
- **Cast Player History Sync**: Cast Player MUST follow the exact same history saving patterns as ExoPlayer: save every 10 seconds while playing, save immediately on pause, and save immediately upon session disconnect/stop/destroy using the remote position. When handoff back to ExoPlayer occurs, ExoPlayer MUST seek to this synchronized position so watch history is pure, uninterrupted, and all UI progress indicators match reality.

### 🎬 Canonical Watch History Media ID & Cross-Source Parity Mandate (STRICT - ZERO REGRESSION)
To ensure seamless interoperability between Torrents, Network Storage (NAS / SMB / NFS / USB), and Local Playback, the storage format of watch history in Room and Firestore MUST adhere strictly to the canonical definitions:
1. **Canonical ID Definitions (MANDATORY)**:
   - **Movies**: `mediaId` MUST ALWAYS be the canonical numeric string ID from TMDB/YTS catalog (e.g. `"12230"`).
     - **PROHIBITION**: NEVER save 40-character torrent infoHashes (`092ea1d9309572ad7caf0868c3a94217dad15769`), raw file names (e.g. `Inception.2010.1080p.mkv`), or arbitrary raw strings as movie `mediaId`.
   - **TV Series Episodes**: `mediaId` MUST ALWAYS adhere to the canonical mnemonic:
     `"media_${seriesId}_s${seasonNumber}_e${episodeNumber}"` (e.g. `"media_111803_s1_e1"`), where `seriesId` is the canonical TMDB series ID, and `seasonNumber` and `episodeNumber` are positive integers.
     - **PROHIBITION**: NEVER vary prefixes, use capital letters (`media_S1_E1`), or save raw episode file names.
2. **Mandatory Inverse Resolution for External Media (NAS / USB / Local Files)**:
   - When media is played from network shares (SMB/NFS) or external storage, or when metadata IDs are absent from a torrent:
     - The filename and stream title MUST be parsed via `MediaMetadataResolver` using regex (`extractSeasonAndEpisode`) and release tag cleaning (`extractCleanSearchTitles`).
     - **If Episode**: Asynchronously resolve `seriesId` via `tmdbRepository.searchTvSeries(cleanTitle)` before finalizing `mediaId`.
     - **If Movie**: Asynchronously resolve `movieId` via `ytsRepository.searchMovies(cleanTitle, 1)` (fallback `tmdbRepository.searchMovies`) before finalizing `mediaId`.
     - This guarantees that watching a video on NAS saves progress to the exact same document as playing it via Torrent, enabling uninterrupted cross-source resume.
3. **Decoupled Architecture & UI Components**:
   - All watch progress bars in catalog and thumbnails MUST use the shared `@Composable WatchProgressBar(progressPercent = ...)`.
   - All player timeline scrubbers MUST use `@Composable WatchProgressSlider(value = ...)`.
   - Resolution logic MUST reside in `MediaMetadataResolver`, keeping Activities and UI screens completely decoupled.
4. **Automatic Legacy Cleanup & Migration**:
   - `UserLibraryRepository` MUST detect non-canonical records (hashes, titles, malformed strings) in Room and Firestore, resolve their canonical IDs from title/name, migrate progress to the canonical key, and purge the legacy records.
5. **STRICT PROHIBITION FOR FUTURE AI AGENTS**:
   - Future AI agents are **STRICTLY FORBIDDEN** from modifying, altering, or circumventing these storage keys, canonical formats, or reverse lookup mechanisms in Room, Firestore, `MediaIdHelper`, `MediaMetadataResolver`, `LocalPlayerActivity`, `TorrentDownloadService`, `PlaybackService`, and `UserLibraryRepository` under any circumstances unless explicitly requested and approved by the user.

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
   - **Internal Playback Contract (ExoPlayer & CastPlayer)**: Surpassing 95% playback (`positionMs >= (durationMs * 0.95f).toLong()`) in [`LocalPlayerActivity.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/player/LocalPlayerActivity.kt) or [`PlaybackService.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/service/PlaybackService.kt) MUST save completed `PlaybackProgress` (1000L/1000L) AND record `DownloadedEpisode` / `DownloadedMovie` via `markEpisodeAsCompleted` / `markMovieAsCompleted`.
   - **Season Completion Badges (`TvDetailScreen.kt`)**: Season completed/partial status chips MUST evaluate both `seriesDownloads` AND `watchHistoryMap` entries (`progress.isCompleted || progress.progressPercent >= 95`) using `MediaIdHelper.parseCanonicalEpisode(mediaId)`.
3. **Next Episode Progression & Multi-Stream Filtering**:
   - **Torrentio Release Pollution Trap**: Torrentio stream endpoints return all streams mapped to a series IMDb identifier, which may include whole-season packs or releases from previous/other episodes.
   - **PROHIBITION: NEVER serve Torrentio series streams without strict episode verification.**
   - In [`TorrentioRepository.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/network/TorrentioRepository.kt), all candidate streams MUST pass `!TorrentioMetadataParser.isSeasonPack(...)` AND `TorrentioMetadataParser.matchesEpisode(...)`.
   - In [`TorrentCardActionHandler.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/utils/TorrentCardActionHandler.kt), `nextEpisodeInfo` MUST be forwarded in `launchIntent`, `readyIntent`, and `serviceIntent` for all card play actions.
   - In [`TvPlayerViewModel.kt`](file:///c:/Users/marti/StudioProjects/LaTorrentola/app/src/main/java/com/martinrevert/latorrentola/ui/player/TvPlayerViewModel.kt), `_nextEpisodeData.value = null` MUST be reset at the start of `evaluateNextEpisode` to prevent dialogs from rendering stale episode targets.

### Previews
- Every UI Component MUST include `@Preview` functions covering: Light Mode, Dark Mode, Mobile form-factor, and TV form-factor (using appropriate theme wrappers).

---

## 🧠 8. EXECUTION CHECKLIST
Before marking a task complete, verify:
1. [ ] Did I ask the user if this applies to Mobile, TV, or both? (If ambiguous).
2. [ ] Are Unit Tests written, updated, and passing?
3. [ ] Are there ZERO hardcoded colors, strings, or dimensions?
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
14. [ ] Did I update `README.md`, `README.es.md`, and `ARCHITECTURE.md` on the fly to reflect all new features, settings, or architectural components?
15. [ ] If resolving a bug, regression, or edge-case failure, did I automatically document a new mandatory directive in `AGENTS.md` and add its corresponding checklist item to prevent future regressions?