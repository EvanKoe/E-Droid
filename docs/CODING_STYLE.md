# Coding style — E-Droid

This document describes the conventions used throughout the project. They are intended to
be boring, predictable, and enforceable by tooling. When in doubt, prefer the choice that
keeps a file readable top-to-bottom without cross-referencing.

## 1. Language & tooling

- **Kotlin**, targeting JVM 17, with the official Kotlin code style
  (`kotlin.code.style=official` in `gradle.properties`).
- **Jetpack Compose** for all UI, with the Compose compiler applied via the
  `org.jetbrains.kotlin.plugin.compose` Gradle plugin.
- **Material 3 Expressive** (`androidx.compose.material3`) is the only design system; colours
  come from Material You dynamic colour on Android 12+ and a fixed fallback palette below
  that (`ui/theme/Color.kt`). `minSdk` is 26, `targetSdk` is 36.
- **kotlinx.serialization** for every JSON read/write (repo index, GitHub API, persisted
  lists), **OkHttp** for all network I/O, **Coil 3** for images, **WorkManager** for
  background checks. Don't add a second library for any of these jobs.
- Dependencies are declared once in the **version catalog** (`gradle/libs.versions.toml`)
  and referenced as `libs.*`. No hardcoded versions in `build.gradle.kts`.

Formatting is expected to match the default IntelliJ/Android Studio Kotlin formatter
(4-space indent, trailing commas on multi-line argument lists, imports not wildcarded).

## 2. Project layout

Source is grouped by **layer, then feature**:

```
app.edroid
├── EDroidApp.kt     Application: builds the AppContainer, schedules background work
├── AppContainer.kt  Hand-rolled dependency container: the single place that wires
│                    services, stores and the process-lifetime scope
├── MainActivity.kt  The single activity, hosting MainScreen
├── data/            No UI. Persistence and I/O
│   ├── *Store.kt        One SharedPreferences-backed store per settings area
│   │                    (RepoStore, SettingsStore, RecentSearchesStore)
│   ├── fdroid/          F-Droid repositories: RepoHub, FDroidRepository
│   │   ├── model/       Index and UI-facing models (IndexV2, App, AppDetails, Repository)
│   │   ├── net/         FDroidRepoService, SignedEntryReader (signature verification)
│   │   └── parser/      Streaming index-v2 parser
│   ├── github/          GitHub release tracking: service, checker, worker, store
│   └── install/         DownloadService, ApkInstaller, InstallResultReceiver,
│                        installed-package helpers
└── ui/              Everything the user sees
    ├── MainScreen.kt    The one Scaffold, the nav host and the bottom nav bar
    ├── components/      Reusable widgets (ExpressiveNavBar, AppCard, InstallActions…)
    ├── theme/           Colour scheme, typography, shapes, layering, app theme
    └── apps/ details/ git/ options/ search/ updates/
                         One package per screen: `*Screen` + `*ViewModel` (+ small
                         screen-only helpers)
```

The Material 3 expressive bottom nav bar is a hand-rolled composable
(`ui/components/ExpressiveNavBar`) rather than a library component, so the app can follow
the expressive motion guidelines while staying on the stable Material 3 release.
`MainScreen` owns the single `Scaffold` and the nav bar; screens receive their padding from
it and draw their own titles (`PageTitle`), so there is deliberately no `TopAppBar`.

Rationale: opening `data/install/` shows you *everything* about how an install works, and
opening `ui/git/` shows you the whole Git tab, rather than hunting across `services/`,
`views/`, `models/` for the pieces.

**This tree is part of the style guide.** A PR that adds, removes, or renames a package
updates it in the same commit.

## 3. Naming

- **Classes / objects**: `PascalCase`, named after their single responsibility
  (`FDroidRepoService`, `ApkInstaller`, `GitHubReleaseChecker`).
- **Functions / properties**: `camelCase`, verbs for actions (`fetchEntry`, `refresh`),
  nouns/adjectives for values (`hapticsEnabled`, `installedVersionCode`).
- **Compose composables**: `PascalCase` nouns describing what they render (`AppCard`,
  `SectionTitle`, `DetailBackBar`). Private helper composables live in the same file as
  their only caller.
- **Suffixes carry the role**: `*Screen` (a full-screen composable), `*ViewModel`,
  `*Store` (persistence), `*Service` (network client or framework service),
  `*Repository` / `*Hub` (data aggregation), `*Worker`, `*Receiver`.
- **Constants**: `UPPER_SNAKE_CASE`, using the Android style guide's definition — a `val`
  with no custom `get`, holding deeply immutable data. Scalars and strings also take
  `const`. Anything that isn't deeply immutable stays `camelCase` even when it's a `val`
  (an `LruCache`, a `Semaphore`, a non-empty array). A `CompositionLocal` follows the
  Compose convention (`LocalFoo`).
- **Where constants live**: either top-level in the file that uses them, or in a
  `companion object` when they belong to one type (`TAG`, `MAX_ATTEMPTS`, `PREFS_NAME`).
  What is *not* allowed is an unnamed literal buried in an expression — every tuning value
  (timeouts, retry counts, cache sizes, notification ids) gets a name, wherever it lives.
- **Constant visibility**: the narrowest that works. `private` for a value only its own
  file or class reads (`TAG`, `USER_AGENT`); public for anything another file legitimately
  needs (`ENTRY_JAR`, `DEFAULT_PER_PAGE`), because making it private would force the caller
  to hardcode the value.
- **Framework components** keep their suffix so their role is obvious in the manifest
  (`DownloadService`, `InstallResultReceiver`).

## 4. Visibility & structure

- Default to the **narrowest visibility**. Public API is deliberate; helpers are `private`.
- **One top-level type per file**, named after that type. Small, tightly-coupled types
  (a model and its `Localized` helper, a sealed `UiState` and its screen's ViewModel) may
  share a file when they are always used together.
- Keep classes small and single-purpose. "Impure" work that touches the framework
  (`PackageManager`, `PackageInstaller`, `NotificationManager`) is concentrated in one place
  (`ApkInstaller`, `InstalledPackages.kt`) so the rest of the code stays testable and
  declarative.
- **Dependencies are wired in `AppContainer`**, by hand, through constructors. No service
  locators, no singletons reached through `object`, no DI framework until the graph
  genuinely outgrows the container. A ViewModel receives what it needs from the container.
- **One source of truth per piece of state.** A store, repository or hub owns each value
  and exposes it as a `Flow`; everything else observes it. Don't copy the same state into
  a second holder.
- Lifecycle is explicit and symmetric: anything owned by a service, activity or composable
  has a teardown for its setup (cancel the scope, unregister the receiver, close the
  stream).
- **A scope or registration with no teardown must say why, in a comment.** Not tearing
  something down is a real decision, and the next reader can't tell a deliberate one from a
  forgotten one. `AppContainer.appScope` is the pattern: its KDoc states that it outlives
  every screen on purpose. Without that sentence, a missing teardown is treated as a bug in
  review.

## 5. Concurrency

- Coroutines only; no raw threads. Each long-lived owner creates its own scope
  (`CoroutineScope(SupervisorJob() + dispatcher)`) and cancels it when it dies; ViewModels
  use `viewModelScope`.
- Pick the dispatcher at the I/O boundary, not at the call site: services and stores wrap
  their own blocking work in `withContext(Dispatchers.IO)` (or `Default` for CPU-bound
  parsing), so callers can invoke them from any dispatcher.
- Work that must finish even if the UI goes away (downloads, installs) runs on
  `AppContainer.appScope` or in `DownloadService`, never in a screen's scope. Periodic work
  goes through WorkManager.
- ViewModels expose state as `StateFlow` via `stateIn(viewModelScope, WhileSubscribed(5_000), …)`
  and composables collect with `collectAsStateWithLifecycle()`.
- `CancellationException` is never swallowed: don't wrap suspend calls in a catch that
  eats it.

## 6. Compose conventions

- Composables are **stateless where possible**: they take data in and hand events out via
  lambdas (`onInstall`, `onBack`, `onSelect`). State is hoisted to the caller or the
  ViewModel.
- Screen state is modelled as a single immutable `UiState` per ViewModel, with sealed types
  for load phases (`SyncState.Loading` / `Error`), never a pile of loose booleans.
- Side-effecting reads (permission status, installed packages) use lifecycle-aware effects
  (`DisposableEffect` + `LifecycleEventObserver`) so the UI refreshes on resume.
- **No hardcoded colours in UI components — the whole app is Material You compliant.**
  Every colour a screen paints comes from `MaterialTheme.colorScheme`, so it follows the
  wallpaper on Android 12+ and flips with light/dark. If a colour needs to contrast against
  something, derive it (`contentColorFor`, `onSurface`) — never assume which way round the
  theme is. The single exception is the **fallback palette in `ui/theme/Color.kt`**, which
  exists precisely to feed the colour scheme on pre-Android-12 devices.
- Shapes, type and elevation come from the theme (`ui/theme/Shape.kt`, `Type.kt`,
  `Layering.kt`); don't build ad-hoc `RoundedCornerShape(…)` or `TextStyle(…)` inline when
  a theme value exists.
- Dimensions are always `.dp` / `.sp`; spacing uses `Arrangement.spacedBy` rather than
  manual padding between siblings.
- Every `items` / `itemsIndexed` block in a `LazyColumn`, `LazyRow`, or lazy grid **must**
  pass a stable `key` (`app.packageName`, not the index). Without it Compose falls back to
  positional keys, so an insertion, removal or reorder throws away composition state; with
  it, only the affected items move and `Modifier.animateItem()` can track them. Standalone
  `item` blocks take a literal key (`item(key = "featured-title")`) so they stay stable
  when the keyed items around them change.
- **Navigation haptics**: every navigation action (tab, back, category, app) gives a light
  `HapticFeedbackType.VirtualKey` tick, gated on the user's haptics setting.

## 7. Android specifics

- **Permissions**: request nothing silently, and declare nothing the app can't justify.
  Each non-obvious permission in the manifest carries a one-line comment saying why
  (`QUERY_ALL_PACKAGES`, `FOREGROUND_SERVICE_DATA_SYNC`). The UI surfaces the live status
  of anything the user must grant (install-unknown-apps, notifications) and links to the
  right settings screen.
- **Network**: all traffic goes through the one shared `OkHttpClient` from `AppContainer`
  and sends the project `User-Agent`. Remote indexes are untrusted input: F-Droid repo
  indexes are verified against their signed `entry.jar` before use (`SignedEntryReader`),
  and a verification failure surfaces as `RepoVerificationException`, never as a silent
  fallback to unverified data.
- **Installs** go through `PackageInstaller` sessions (`ApkInstaller`). Partial downloads
  and abandoned sessions are always cleaned up, on success, failure and cancellation.
- **Persistence** is small and local: one `SharedPreferences`-backed store per area,
  holding JSON produced by kotlinx.serialization, with a tolerant `load()` that falls back
  to the empty value when the stored data is unreadable. Large caches (parsed indexes) live
  as files in `cacheDir`, versioned so a format change invalidates them.
- **Privacy by default**: no analytics, no accounts, no data leaves the device except the
  requests needed to fetch repo indexes, APKs and GitHub release metadata.
- `runCatching` guards genuinely fallible framework or I/O calls (missing package, revoked
  session, unreadable cache) and always logs or falls back — it is never used to swallow
  programming errors. Log with `Log.w` / `Log.e` and a per-class `TAG`.

## 8. Comments

- **Every method and function has a `/** */` KDoc block on top**, whatever its visibility,
  composables included. It says exactly what the function does in one or two sentences, so
  a reader never has to open the body to know. Add `@param` / `@return` only when a name
  and type don't already make them obvious. Overrides of framework methods
  (`onCreate`, `onReceive`) are the one exception.
- **Every non-obvious class has a KDoc block** stating its single responsibility and any
  surprising constraint (e.g. why `AppContainer` is hand-rolled, why `appScope` outlives
  every screen).
- **In a composable body, a short `//` comment sits right before each distinct UI part**,
  naming it in a few words: `// Uninstall button`, `// App icon`, `// Version row`. One per
  part (a button, a row, a card, a list section), not one per line, and a part that only
  wraps others (`Column`, `Box`) is named when it is itself a meaningful section.
- Outside those two rules, inline comments explain **why**, not **what**: a constraint, a
  workaround, a non-obvious ordering. Don't narrate plain statements.
- No commented-out code, no TODOs left in `main`.
