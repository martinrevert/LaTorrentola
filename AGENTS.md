# AGENTS.md — Quick onboarding for AI coding agents

Checklist for this agent run:
- [x] Understand app architecture and DI boundaries
- [ ] Note project-specific serialization / DB / networking patterns
- [ ] List dev workflows (build/install) and gotchas
- [x] Point to concrete files to inspect for changes
- [x] Implement unit and UI tests for core components

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
- Multi-Selection Pattern: The `SearchScreen` (favorites view) implements a selection mode for D-pad compatibility. Short-press navigates to details, long-press enters selection mode. Once in selection mode, short-press toggles selection.
- Credential Safety: Never hardcode API keys or Web Client IDs. Use `local.properties` with a corresponding `buildConfigField` in `app/build.gradle`. Reference them via `BuildConfig`.
- DI scope: Hilt is used for singletons (see `di/NetworkModule.kt`). When adding bindings, follow the `@Module @InstallIn(SingletonComponent::class)` pattern.
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

