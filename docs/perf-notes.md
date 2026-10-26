# Performance notes — round 3 (2026-10-22, app-wide pass)

Measured on molab release builds (same commit, before/after).

| | Before | After |
|---|---|---|
| Release APK | 8,922,361 B | 8,570,118 B (-352 KB, -3.9%) |
| resources.arsc | 346,516 B | 30,016 B |

## Changed
- **Code editor typing.** `SimpleLanguage` re-tokenized the whole document on the UI thread on every
  keystroke. It now runs on Sora's `AsyncIncrementalAnalyzeManager`: background thread, only the edited
  lines (plus following lines while their start state differs), spans kept aligned in between. The
  tokenizer is pure (`SimpleTokenizer`, `SimpleTokenizerTest`), same colours, and block comments now
  carry across lines.
- **Idle brand clocks.** `rememberAnimationClock(maxFps)` sleeps between ticks below 60 fps instead of
  waking on every vsync. `OrobiIdle` ticks at 30 (the Home bar shows it as long as Home is open), so the
  UI thread and GPU idle between frames. Low-RAM and Lite stay capped at 30 for every clock.
- **Startup.** Poppins is decoded on a background thread while the splash is up, through the same calls
  Compose makes (`Resources.getFont` on 26+, `ResourcesCompat` below), so the first frame hits the cache.
- **Baseline Profile.** Now also covers `data/ai/**` (agent loop, parsers, providers, tools),
  `data/preview/**`, sessions and the browser agent (`HPL`: hot, post-startup).
- **Editor file tree.** Project-row emissions (one per file write, via `updatedAt`) used to rebuild the
  tree each time, on top of the refresh after every tool: two walks per agent edit. First load still
  loads at once; later emissions coalesce into one refresh after 600 ms of quiet.
- **Preview.** `LOAD_DEFAULT` instead of `LOAD_NO_CACHE`: the dev server sends `no-store` for every
  project file, so edits still show, while CDN libraries stop being re-downloaded on each reload.
- **Network.** Coil and `GitHubActionsService` share `AiHttp.client`'s pool and dispatcher.
- **Home.** A project's live link is read in the background before its menu opens (was a disk read in
  composition).
- **APK.** English-only resources (`localeFilters`), no `kotlin/**` builtins, no `META-INF/*.version`,
  two unused Compose dependencies removed.

## Measured but not changed (risky without a device)
- **JGit is kept whole** (`-keep class org.eclipse.jgit.** { *; }`): 1.30 MB of 7.72 MB raw dex. A
  narrower rule (`-keep,allowshrinking` + keeping `TranslationBundle` field names and enums used through
  `getEnumConstants`) could save a few hundred KB, but a mistake breaks clone/push only in release
  builds. Needs an on-device smoke test of clone, commit, push, pull before trying.
- **ViewModels kept whole** (`ui.screens.**ViewModel*` and their `$` classes, 257 KB raw): constructors
  are already kept by `-keep class * extends ViewModel { <init>(...); }`, so the member keeps could go
  for a small gain once a release build can be exercised on a phone.
- **ToolCard computes a diff in composition** for every edit row with old/new content
  (`app/.../components/agent/ToolCard.kt`): main-thread work proportional to file size per row in long
  chats. Belongs with the chat-performance work (compute it off the main thread or carry the counts).
- **Code editor copies the whole document per keystroke** (`editor.text.toString()` in
  `SoraCodeEditorView`) to push it to the view model. Debouncing needs the push tied to its file and a
  flush before save; small files make it cheap, so it was left.

---

# Performance notes (wip/perf)

Findings from the low-end-device performance pass. Part 1 lists what changed on
this branch. Part 2 lists problems found in files this branch was not allowed
to touch (other agents own them), so they can be fixed once those branches
land.

## 1. What changed on this branch

### Build
- `compose-stability.conf` (repo root), wired into `:app` and
  `:core:designsystem` via `composeCompiler.stabilityConfigurationFiles`. It
  declares the core:data models (Project, ChatMessage, FileTreeNode, entities,
  AiModel, Git/CI models), `java.time.*`, `java.io.File` and Kotlin
  `List/Map/Set` as stable. core:data is compiled without the Compose compiler,
  so before this change every composable that took one of those types was
  non-skippable. **Rule:** only add deeply immutable types to that file.
- Strong skipping is already on by default (Kotlin 2.0.20+), so no flag is needed.
- `includeSourceInformation` is back to the plugin default (debug builds only).
  Compose reports: `-PcomposeReports=true` writes them to
  `app/build/compose_compiler`.
- `app/src/main/baseline-prof.txt`: a hand-written Baseline Profile covering
  startup, home, onboarding, editor/chat/markdown/diff and the Sora/Markwon hot
  paths. `profileinstaller` was already a dependency. There is no
  macrobenchmark module, so CI needs no device.
- Unused `sora-editor language-textmate` dependency removed. No code
  referenced it.
- R8: the blanket `-keep` on `ui.screens.git.**` is gone (it stopped R8 from
  optimizing about 3k lines of composables). Added keeps for
  `@JavascriptInterface` methods. `Log.v`/`Log.d` are stripped in release.
- Already in place before this branch: minify + shrinkResources, the
  configuration cache, parallel builds, non-transitive R.

### Lite mode
- `LiteMode { AUTO, ON, OFF }` in `PreferencesRepository` (`lite_mode` key);
  the selector lives under Settings > Performance.
- AUTO resolves through `DevicePerformance.isLowEndDevice()`
  (designsystem/theme). It returns true when any of these holds:
  `isLowRamDevice`, `memoryClass <= 192 MB`, or `totalMem <= 3 GB`. The result
  is cached per process.
- `OroTheme(liteMode = ...)` provides `LocalLiteMode` / `OroTheme.liteMode` and
  switches to the standard (non-springy) motion scheme. Both MainActivity and
  PreviewActivity feed it.
- Effects of Lite mode:
  - no Home backdrop blur;
  - no idle loops on the empty state, agent badge or CI badge;
  - the brand mascot clocks run at 30fps;
  - no `animateContentSize` on streaming AI messages;
  - no file-tree row animations;
  - no editor cursor animation.

### Hot-path fixes
- AiMessageContent:
  - The text parser was **quadratic**: it called `find()` for two regexes at
    every character. It now caches the next match.
  - Regexes are compiled once.
  - Streaming re-parses run on `Dispatchers.Default` with `conflate()`. The
    first parse stays synchronous so heights do not jump.
  - ToolCallGroup entries are now memoised.
- MarkdownText: one shared Markwon instance (it was rebuilt per text block of
  every message). The TextView only re-sets text/colour/typeface when they
  change. The regex is hoisted.
- DiffUtils:
  - The common prefix and suffix are trimmed before the LCS. A full table of a
    3k-line file was ~36 MB on the main thread.
  - The table is capped at 2M cells, falling back to a block replacement
    beyond that.
  - The quadratic `add(0, ...)` is gone.
  - Context filtering uses a flag array.
- File tree: flattened visible rows, so each row is its own lazy item with
  key/contentType. Before, a whole expanded subtree was composed inside one
  item. `ProjectRepository.buildFileTree` no longer stats inside the sort
  comparator and no longer walks `.git` contents.
- HomeScreen:
  - The "Has Git" filter's `File.exists()` calls moved off the main thread.
  - The blur radius is read in `graphicsLayer {}`, so the Scaffold no longer
    recomposes on every frame. It uses BlurEffect, API 31+ only.
- PreviewActivity:
  - Framework detection moved off the main thread. It could walk the whole
    tree, node_modules included, three times.
  - Console log capped at 500 entries.
  - The WebView is paused/resumed with the activity.
  - Flows use `collectAsStateWithLifecycle`.
- Sora editor wrapper:
  - Language/colour scheme are no longer re-applied right after creation, and
    settings are only applied when they change.
  - Selected text is extracted on tap, not on every selection change.
  - The listener reads the latest `onContentChange` callback.
- Git:
  - `GitUiState` derived lists are lazy, one filter per state instead of one
    per read.
  - Changed-file rows are capped at 200 per section.
  - The CI badge only animates when a run is in progress, via graphicsLayer.
  - `collectAsStateWithLifecycle` everywhere.
- Memories: the search filter is memoised. `collectAsStateWithLifecycle`.
- Settings: the font-size slider writes DataStore once, on release, instead of
  on every drag event.
- Startup: the `:crash_handler` process no longer seeds models or opens the DB.
  `themeMode`/`liteMode` flows use `distinctUntilChanged`.
- Misc:
  - Shared DateTimeFormatters in `Project` and `ChatMessage`.
  - Coil ImageRequests are remembered in the asset grid, the asset list and the
    image detail view.
  - The file-mention popup flattens once per tree and stops filtering at 8 hits.

## 2. Issues in files this branch did not touch (for the orchestrator)

### EditorViewModel.kt
- **One giant `EditorUiState`** combines file tree, open files, file content
  and messages. Every streamed chunk re-emits the whole state, which
  recomposes `EditorScreen` and re-runs every child's parameter comparison.
  Expose separate StateFlows (chat, editor, tree) or at least `messages` on
  its own.
- `isFileModified = values[7] as String != _originalFileContent.value` runs an
  O(file size) string compare on **every** emission, including every chat
  token. Compute it only when `_fileContent` changes (for example a separate
  `combine(_fileContent, _originalFileContent)` with `distinctUntilChanged`).
- `updateFileContent()` copies `_openFiles` and stores the full content in the
  tab on every keystroke. Consider storing content only on tab switch or save.
- `_messages.value = _messages.value + msg` and `toMutableList()` copies on
  every streaming update (every 80 ms). Fine for short chats, but O(n) per
  chunk on long ones. Consider keeping the streaming message in its own
  StateFlow and merging it only in the UI.
- Make sure file reads/writes and `getFileTree` stay on `Dispatchers.IO`.
  They currently do via ProjectRepository. Large files should keep going
  through `LargeFileHandler`.

### ChatTab.kt
- `isStreaming = isLoading && message == messages.lastOrNull()` calls
  `ChatMessage.equals` (a full content compare) for every item on every
  recomposition. Use `message.id == lastMessageId`, with `lastMessageId`
  computed once outside `items {}`.
- Add `contentType` to `items(messages)` (`if (it.isUser) "user" else "ai"`) and
  to the `agentSessions` items so the lazy list reuses compositions properly.
- `Modifier.animateItem()` on every message: fine, but skip it when
  `OroTheme.liteMode` is on.
- The infinite transition in this file: gate it on visibility, read it in
  `graphicsLayer {}`, and skip it in Lite mode.
- The auto-scroll `LaunchedEffect(messages.size, ...)` uses
  `animateScrollToItem`. On low-end devices prefer `scrollToItem` when
  `liteMode`, or when the jump is more than a screen.

### RunTranscript.kt
- `toHistoryText` builds `Regex("\n{3,}")` on every call. Hoist it next to
  THINK/TOOL_BLOCK/DIFF.

### ToolCard.kt / ToolCallGroup.kt
- Both import `rememberInfiniteTransition`. Any running-state spinner/pulse
  should read its animated value in `graphicsLayer {}` / `drawBehind {}` rather
  than in composition, and should not run in Lite mode
  (`OroTheme.liteMode`).
- ToolCard uses `verticalScroll` for tool output. Very long outputs (file
  reads) should be truncated or rendered lazily.

### AiModelsScreen.kt
- `val modelsByProvider = models.groupBy { it.provider }` runs in composition
  on every recomposition. Wrap it in `remember(models)`.
- `items(..., key = { it.id })`: the same model id under two providers would
  crash with a duplicate key. Use `"${it.provider.name}/${it.id}"` as
  ModelSelectorSheet already does.

### ProvidersScreen.kt / ModelSelectorSheet.kt
- Nothing significant found. Both already use remember'd grouping and stable
  keys.

### OroDatabase / ai/** (core:data)
- Not reviewed in depth. When adding new Room entities, keep them all-`val` so
  the `com.oro.app.data.local.entity.**` entry in `compose-stability.conf`
  stays true.
