# AGENTS.md — Quick onboarding for AI coding agents

Checklist for this agent run:
- [ ] Understand app architecture and DI boundaries
- [ ] Note project-specific serialization / DB / networking patterns
- [ ] List dev workflows (build/install) and gotchas
- [ ] Point to concrete files to inspect for changes

Short summary
- This is a Jetpack Compose + Hilt Android app (Kotlin). Core patterns: Retrofit (Gson), Room, kotlinx.serialization on models, coroutines + Flow, and androidx.navigation3 runtime for navigation keys.

Essential places to read first
- App entry and navigation: `app/src/main/java/com/martinrevert/latorrentola/MainActivity.kt` and `ui/navigation/AppNavigation.kt` (deep-link via Intent extra "PELI"; navigation transfers Movie as JSON using kotlinx.serialization)
- Network & DI: `di/NetworkModule.kt`, `network/YtsService.kt`, `network/YtsRepository.kt` (Retrofit service + repository that mixes remote + Room)
- Models & persistence: `model/YTS/*` (e.g. `Movie.kt`) and `database/Converters.kt`, `database/MovieDao.kt` (Room entities are also annotated with `@Serializable` and use Gson converters)
- Build and dependency versions: `gradle/libs.versions.toml` and `app/build.gradle` (KSP, Hilt, Google services plugins; git-based versionCode)
- Firebase / Google services: `google-services.json` (project and app-level copies) and `app/keys/release.keystore` (release signing asset)

Project-specific patterns and gotchas (do not assume defaults)
- Mixed serialization: Models have both `kotlinx.serialization` (`@Serializable`) and Gson `@SerializedName`. Retrofit is configured with `GsonConverterFactory` and Room TypeConverters use Gson. When adding fields, add both annotations and make converters handle new nested types.
  - Example: `Movie` (app/src/.../model/YTS/Movie.kt) uses `@Serializable` and `@SerializedName`. DB converters (`database/Converters.kt`) use Gson to persist `List<Torrent>` and `List<Cast>`.
- Navigation transfers entire Movie objects as JSON strings via `Json.encodeToString(Movie.serializer(), movie)` and `Json.decodeFromString(...)` in `AppNavigation.kt`. Keep serializers in sync with model changes.
- Coroutines-first codebase: network and DAO methods are `suspend` or return `Flow` (e.g. `YtsService` uses `suspend`, `MovieDao.getAll()` returns `Flow<List<Movie>>`). Do not introduce RxJava; README mentions RxJava historically but code uses coroutines + Flow.
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

- Build release (signed) APK / AAB (ensure `app/keys/release.keystore` is present and signing config is set in Gradle):

```powershell
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
```

Important files to update when changing behavior
- Networking: `network/YtsService.kt` + `di/NetworkModule.kt` (Retrofit client and logging interceptor)
- Data layer: `network/YtsRepository.kt` (combines remote + local flows) and `database/*` (DAO/Converters)
- UI routing: `ui/navigation/AppNavigation.kt` (how Movie JSON is passed) and top-level Composables under `ui/*`

Integration points & external dependencies
- YTS API: base URL defined in `constants/Constants.kt` (Constants.YTS_BASE_URL)
- Firebase: Crashlytics / Messaging — google-services plugin is applied; `google-services.json` must be valid for messaging/crashlytics to work
- ML Kit Translate: used for on-device translations (dependency in gradle BOM)
- YouTube player library for trailers (dependency present in libs)

Testing / CI notes
- There are no real unit tests in the repo (test folders are empty). If adding tests, prefer coroutine test utilities (kotlinx-coroutines-test) and Hilt testing patterns.
- CI must have `git` available (versionCode uses commit count) and Android SDK + buildtools matching AGP settings (`gradle/libs.versions.toml`).

If you edit models:
- Update `@Serializable` Kotlin serializers and add `@SerializedName` for any field used by Retrofit/Room converters.
- Update `database/Converters.kt` if new nested/collection types are persisted.

Quick pointers for PR reviewers (what to check)
- Serialization symmetry: ensure new model fields are present in both kotlinx and Gson annotations
- DI scopes: prefer `@Singleton` in `NetworkModule`-style modules unless intentionally scoped narrower
- Navigation payload size: passing full Movie JSON is convenient but can grow; consider passing ID and fetching details if payload becomes large

End of agent guide — keep this file in root as the single-source quick reference for code-modifying agents.

