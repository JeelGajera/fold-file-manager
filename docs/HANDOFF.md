# Development handoff

Everything needed to pick FOLD up on a local machine: how the code is
organised, which decisions are load-bearing and why, what is verified, what is
not, and what to do next. Read this alongside
[ARCHITECTURE.md](ARCHITECTURE.md) (the reasoning behind the structure),
[STATUS.md](STATUS.md) (the live state) and [PRIVACY.md](PRIVACY.md) (the Play
Data Safety position).

---

## 1. What FOLD is

An Android file manager whose premise is that Android's own pickers read a
media index, and a media index does not know what a `.md`, `.log`, `.epub` or a
firmware image is. FOLD reads the filesystem instead. MediaStore is not
consulted anywhere in the codebase.

Around that premise sit three features that follow from it: a LAN file server
(share what you can see, over Wi-Fi, with no account and no relay), an
encrypted vault, and Nothing Glyph feedback for long operations.

- Package / applicationId: `com.jeelgajera.fold` (debug builds get
  `.debug` appended, so a debug and a release build coexist on one device)
- Version: `versionCode 1`, `versionName 0.4.1`
- `minSdk 30` — `MANAGE_EXTERNAL_STORAGE` does not exist below it
- `compileSdk 35`, `targetSdk 35`, Java/Kotlin target 17
- Not released; no Play listing yet

---

## 2. Getting the project running locally

```bash
git clone https://github.com/JeelGajera/fold-file-manager.git
cd fold-file-manager
./gradlew :app:assembleDebug        # ~3 min cold on CI hardware
./gradlew test                      # unit suites, ~1 min
./gradlew :app:installDebug         # to a connected device or emulator
```

Requirements: JDK 17+ and the Android SDK with platform 35. Gradle 8.11.1 comes
from the wrapper — do not install Gradle separately.

Everything is pinned in one place, `gradle/libs.versions.toml`. The combination
below is known to resolve and build together; bump one at a time.

| | |
|---|---|
| AGP | 8.7.3 |
| Kotlin / KSP | 2.0.21 / 2.0.21-1.0.28 |
| Compose BOM | 2024.12.01 |
| Hilt | 2.52 (+ androidx hilt 1.2.0) |
| Room | 2.6.1 |
| WorkManager | 2.10.0 |
| Glance | 1.1.1 |
| Ktor | 2.3.12 |
| DataStore | 1.1.1 |
| Biometric | 1.1.0 |

### First run on a device

1. Onboarding asks for All Files Access. Granting it opens a system settings
   screen that returns no result, so the grant is re-read in `onResume`.
2. Declining is a supported path, not an error: the app falls to
   `SafDocumentProvider` and shows the limited-access screens. Test both.
3. The vault requires an enrolled biometric or device credential.
4. The LAN server needs the device and the browser on the same network; the
   PIN is shown in the app.

---

## 3. Module layout

```
:app              shell, navigation, permission flow, manifest, Hilt entry point
:core:design      design tokens -> Compose theme, component set, icons
:core:storage     FileSystemProvider + both impls, MIME, index, settings, stats
:core:crypto      vault key hierarchy, blob format, biometric gate
:feature:browser  home, browse, hidden files, limited access, onboarding, settings, about
:feature:search   name search over the index, live contents search
:feature:transfer LAN server, discovery, foreground service, share sheet
:feature:vault    vault screens
:feature:glyph    Glyph controllers, sequences, detection
:widget           Glance home-screen widgets
```

Dependency direction is one-way: `:feature:*` and `:widget` depend on
`:core:*`; no core module depends on a feature; features do not depend on each
other. `:app` is the only module that knows about all of them.

### The files that matter most

| File | Why |
|---|---|
| `core/storage/.../provider/FileSystemProvider.kt` | The seam the whole app is built on. Read first. |
| `core/storage/.../provider/PathGuard.kt` | The security boundary. Every path passes through it. |
| `core/storage/.../provider/VaultLocations.kt` | One definition of what is excluded from index, search, widgets and server. |
| `core/storage/.../mime/MimeResolver.kt` | The reason the app exists — the type table and sniffing. |
| `core/crypto/.../VaultCrypto.kt` | Key hierarchy and blob format. |
| `feature/transfer/.../server/FoldServer.kt` | Every HTTP route, and the one file to touch on a Ktor major bump. |
| `core/design/.../theme/FoldTheme.kt` | Token layer; Material3 is a skeleton, not the design. |

---

## 4. Architecture: the decisions that are load-bearing

### 4.1 `FileSystemProvider` — non-negotiable

Every read and write goes through one interface. Two implementations sit behind
it:

- `RawFileProvider` — `java.io.File`, requires `MANAGE_EXTERNAL_STORAGE`
- `SafDocumentProvider` — Storage Access Framework tree grants

`FileSystemProviderFactory` picks one and can swap it at runtime. **The UI reads
`FsCapabilities`, never a permission flag.** That single rule is what makes
limited access a supported mode with its own screens rather than an error
state, and it is what makes a permission revoked from system settings while the
app is backgrounded a provider swap on the next resume instead of a
`SecurityException` thrown out of a screen that assumed raw access.

The practical consequence: if Play review refuses the All Files Access
declaration, or policy tightens later, the app degrades rather than dies. Do
not add a code path that branches on the permission directly — add a capability.

### 4.2 `PathGuard` — the security boundary

It lives in the provider layer, not in route handlers, so no endpoint added
later can bypass it. Three properties, in this order:

1. **Canonicalise against the real filesystem**, resolving symlinks — not
   string normalisation.
2. **Deny first.** The vault and other denied roots are checked *before* any
   allowlist is consulted, so an allowlist entry can never re-admit a denied
   path.
3. **Compare segment-wise.** `/storage/emulated/0/Docs` must not match
   `/storage/emulated/0/Docs-private`; a string `startsWith` gets this wrong.

Refusals never disclose where a path resolved — the message is a flat
`"Path is not accessible"`. The LAN server answers every refusal with an
identical `404` body, so "exists but forbidden" and "does not exist" are
indistinguishable from outside.

Covered by 19 adversarial tests: dot-dot chains, prefix siblings, encoded
segments, NUL bytes, and symlinks escaping both into and out of the vault.

### 4.3 MIME resolution

Three stages, in order: an extension table covering what the platform map
misses → magic-byte sniffing → `application/octet-stream`. The last is treated
as a **resolved type**, not a reason to hide a file; an unidentifiable file gets
a `?` badge and stays in the list. 21 tests.

### 4.4 Vault key hierarchy

Per-file DEK, wrapped by an Android Keystore KEK created with
`setUserAuthenticationRequired` and `setInvalidatedByBiometricEnrollment`.
`BiometricPrompt` is handed the `Cipher` and returns it authenticated, which
binds the authentication to the operation rather than to a callback that could
be raced.

KEK aliases are versioned (`fold.vault.kek.N`) and the alias is recorded in the
blob header (format v2), so key rotation does not orphan existing files.

The vault locks on `onStop` — losing the foreground, not a timer and not a back
press. `FLAG_SECURE` is applied only while a vault screen is showing, because
applying it app-wide would break legitimate screenshots and train people to
work around it.

### 4.5 Index and search

Room metadata index, `FileObserver` for live changes, WorkManager reconciliation
every 12 hours under idle-and-charging constraints (`IndexWorker.runNow` fires
immediately when the permission is first granted, so search works straight
away).

**Only metadata is indexed. File contents are searched live and never stored.**
Vault paths are excluded at the storage layer via `VaultLocations.deniedRoots`,
not by each consumer remembering to filter.

### 4.6 LAN server

Ktor 2.3.12 CIO. Six-digit PIN, compared in constant time with
`MessageDigest.isEqual`, with per-address exponential backoff. Range requests
supported via PartialContent. mDNS advertisement through Android NSD. Runs
under a foreground service so the state is never invisible; deletes require an
explicit confirmation header and answer `428` without it.

---

## 5. Deliberate departures from the original design handoff

Each was a judgement call, and each is reversible. Fuller reasoning is in
[ARCHITECTURE.md](ARCHITECTURE.md).

| Handoff said | Implementation | Reason |
|---|---|---|
| 4-digit PIN | 6 digits | 10 000 guesses is reachable against a long-running server even through a backoff |
| Proto DataStore | Typed DataStore with a JSON codec | Avoids adding `protoc` to the build for a settings object of ~30 scalars; one class to swap if that changes |
| Room FTS | Indexed `LIKE` | External-content FTS requires a rowid primary key, forcing a synthetic id alongside the path |
| Backdrop blur | Translucent fill and border | Compose has no backdrop blur; the token file's opaque fallback is used |
| `.ts` as video | `.ts` as TypeScript | On a phone the extension is overwhelmingly source; transport streams still resolve via magic-byte sniffing |
| — | Enum-based navigation | Type-safe routes over string parsing |

---

## 6. What is verified, and what is not

CI (`.github/workflows/build.yml`) runs on every push and pull request:
`:app:assembleDebug` (uploading the APK as an artifact) and `./gradlew test`
(uploading the HTML reports).

**Passing:** dependency resolution, Android resource compilation, compilation of
every module including Room, KSP and Hilt, and 74 unit tests:

| Suite | Tests | Covers |
|---|---|---|
| `PathGuardTest` | 19 | canonicalisation, containment, traversal, symlink escape |
| `MimeResolverTest` | 21 | extension table, sniffing, fallback |
| `FsPathTest` | 9 | path modelling |
| `VaultBlobHeaderTest` | 11 | blob format, key-alias versioning |
| `ServerAuthTest` | 14 | PIN auth, constant-time compare, rate limiting |

**Not verified.** Be honest about this when planning:

- **The app has never been run.** Not on a device, not on an emulator. It
  compiles and its logic suites pass; nothing has exercised a single screen,
  the permission flow, the server end to end, or a vault round trip.
- No instrumented tests exist.
- No Compose UI tests exist.
- The Glyph path is inert by construction (see below).

---

## 7. Remaining work

Ordered as a sensible local sequence.

### 7.1 First session: run it

Install the debug build on a device or emulator and walk every flow. Expect
runtime problems that compilation cannot catch — Hilt graph errors at first
injection, missing manifest entries, navigation argument mismatches, a
`FileObserver` on an unreadable path, Compose recomposition loops. This is the
single highest-value next step and everything below is easier after it.

### 7.2 Instrumented tests

- Permission grant, denial, and revocation mid-session (the provider swap).
- Vault encrypt/decrypt round trip on real Keystore hardware.
- Key rotation across `fold.vault.kek.N` aliases.
- Index reconciliation after files change while the app is stopped.

### 7.3 Ktor `testApplication` suite for the server routes

The dependency is already declared. The traversal cases should mirror
`PathGuardTest` so the boundary is proven at the HTTP layer too, plus: PIN
lockout, Range requests, the `428` delete confirmation, and the identical-404
property.

### 7.4 Glyph hardware integration

`GlyphDetection.SDK_INTEGRATION_PENDING` is currently `true`, so every device
reports no glyph hardware and sequences are previewable but inert. That is the
intended fallback, not a defect. To finish it: register for Nothing's Glyph
Developer Kit / Glyph Matrix SDK, declare the API key in the manifest, wire
`GlyphBackend`, and flip the flag. Test on a Phone (2a) for the strip.

### 7.5 Device matrix

Phone (2a) for glyph, a non-Nothing device, Android 11 and current, a
low-storage device, and a large transfer over a slow network.

### 7.6 Release engineering

- Commit `core/storage/schemas` once generated, so Room migrations are
  reviewable as diffs.
- Verify the R8 release build; `isMinifyEnabled` and `isShrinkResources` are on
  and have never been exercised.
- Signing config and a release keystore (not in the repo).

### 7.7 Play Console submission

- All Files Access declaration: demo video plus written justification citing
  the MIME-type gap — this is the review that can sink the app, and §4.1 is the
  argument for why the app degrades rather than breaks if refused.
- `dataSync` foreground service justification for the LAN server.
- Data Safety form — [PRIVACY.md](PRIVACY.md) is written in the shape the form
  asks for.

---

## 8. Known risk areas

1. **Ktor API surface.** Pinned to 2.3.12. `PipelineContext<Unit,
   ApplicationCall>` is the 2.x route-handler receiver; Ktor 3.x renames it to
   `RoutingContext`. `FoldServer.kt` is the file to update on a major bump.
2. **Glance layout primitives.** `:widget` targets Glance 1.1.1, whose layout
   API has moved between releases. `FoldWidgets.kt` is self-contained, so the
   blast radius is one file. Note that `defaultWeight()` is a `RowScope` /
   `ColumnScope` member — it cannot be imported as a free function.
3. **Room schema export.** `FoldDatabase` exports to `core/storage/schemas`;
   commit it.
4. **Compose experimental APIs.** `combinedClickable` requires
   `@OptIn(ExperimentalFoundationApi::class)`; expect more of these as Compose
   moves.
5. **Bundled fonts.** Archivo and Doto ship as variable TTFs under
   `core/design/src/main/res/font/`. The OFL licence text lives in
   `core/design/src/main/assets/fonts/OFL.txt` — `res/font` accepts only
   `.ttf`, `.ttc`, `.otf` and `.xml`. Downloadable fonts were deliberately
   dropped so the app makes no network request at all, which is what
   PRIVACY.md now claims.

---

## 9. Repository conventions

- **Conventional commits.** No session information, no AI co-author trailers,
  no tool attribution in commit messages.
- Commit author: `Jeel Gajera <jeelgajera200@gmail.com>`.
- Branch names describe the change with a conventional prefix
  (`fix/`, `feat/`, `docs/`); no fixed prefix.
- One version catalog: add dependencies to `gradle/libs.versions.toml`, never
  inline in a module's `build.gradle.kts`.
- Comments explain *why*, not *what*. The codebase is public; write for a
  reader who has never seen this conversation.
- The app ships no analytics, no crash reporter, and no advertising id. Issue
  reporting is manual: the About screen shows the commit the build came from,
  links to it, and opens a pre-filled GitHub issue carrying version, commit,
  Android release and device model, which the user reviews before sending.
