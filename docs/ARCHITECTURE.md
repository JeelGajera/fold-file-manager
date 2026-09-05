# Architecture

## The decision everything else hangs off

Every read and write in FOLD passes through one interface:

```kotlin
interface FileSystemProvider {
    suspend fun list(path: FsPath, options: ListOptions): Result<List<FsEntry>>
    suspend fun read(path: FsPath): Result<InputStream>
    suspend fun write(path: FsPath, append: Boolean): Result<OutputStream>
    suspend fun move(from: FsPath, to: FsPath): Result<Unit>
    suspend fun delete(path: FsPath): Result<Unit>
    fun observe(path: FsPath): Flow<FsChange>
    val capabilities: FsCapabilities
}
```

Two implementations:

- **`RawFileProvider`** — the `java.io.File` tree. Needs
  `MANAGE_EXTERNAL_STORAGE`. The primary path.
- **`SafDocumentProvider`** — Storage Access Framework tree grants. The fallback.

### Why this is not ceremony

Play Store review for All Files Access can be refused, and the policy can
tighten after a release. An app hard-wired to raw file access dies with that
decision. Built this way, a refusal *degrades* the app instead of ending it.

`FsCapabilities` is what the UI reads — not a permission flag — so the
reduced-permission screens are a real mode with real screens rather than an
error state bolted on later. `FileSystemProviderFactory.refresh()` runs on every
resume, so a permission revoked in system settings while the app was backgrounded
swaps the provider mid-session instead of throwing `SecurityException` out of a
screen that assumed raw access.

### What the contract requires

Implementations must:

1. canonicalise every incoming path and refuse anything resolving outside an
   allowed root, symlinks included;
2. refuse every path inside the vault, unconditionally;
3. never hide an entry for having an unrecognised type;
4. return `Result.failure` for expected errors and throw only for bugs.

---

## Security boundaries

### `PathGuard` — one check, one place

Path traversal is the top risk, because the LAN server takes paths from
strangers on the network. The check therefore lives in the **provider layer**,
not in a route handler:

```
HTTP route ─┐
Browser UI ─┼─→ FileSystemProvider ─→ PathGuard ─→ filesystem
Indexer   ──┘
```

A new endpoint added later, or a bug in an existing one, cannot route around it.
`FoldServer`'s handlers are deliberately dull: they convert a string to an
`FsPath` and hand it over.

The guard runs three checks in order:

1. **Canonicalise.** `File.getCanonicalFile()` resolves `.`, `..` and every
   symlink against the real filesystem. Textual normalisation alone is not
   enough — a path through a symlinked directory normalises to something
   harmless while resolving somewhere else.
2. **Denied roots win**, before any allowlist is consulted. The vault's blob
   directory and FOLD's own app-private data are denied unconditionally.
3. **Allowed roots**, compared segment-wise on canonical paths so a sibling
   directory whose name merely starts with an allowed root's name is not treated
   as being inside it.

Refusals never say where the path resolved. That message can reach an HTTP
response body, and confirming a resolution maps the device for whoever is
probing. Every refusal from the server answers `404` with the same body, so
"exists but forbidden" and "does not exist" are indistinguishable from outside.

### The vault's exclusions

The vault is excluded from the index, search, thumbnails, widgets and the LAN
server. None of that is enforced by those five components remembering to check.
It is enforced once, in `VaultLocations.deniedRoots`, which `PathGuard` consults
on every resolution and the indexer consults on every directory.

`VaultLocations` lives in `:core:storage` rather than `:core:crypto` on purpose:
the storage layer must know which directory to refuse before the crypto layer
exists in the dependency graph, because the refusal cannot be conditional on the
vault feature being wired up.

### Key hierarchy

```
Android Keystore
  └── KEK  (AES-256, setUserAuthenticationRequired, per-generation alias)
        └── wraps DEK  (AES-256, fresh per file)
              └── encrypts payload  (AES-256-GCM)
```

Two levels because: unlocking is one Keystore operation rather than one per
file; a single file can be re-keyed or removed independently; and rotation
re-wraps a few hundred small keys instead of re-encrypting gigabytes.

Each blob records the alias that wrapped it, so rotation does not have to be
atomic. An interrupted rotation leaves a mixed-generation vault that still
opens, and the old key is destroyed only once nothing references it.

`BiometricPrompt` receives the `Cipher` and hands it back authenticated. That
binds the authentication to the operation; authenticating and then using the key
separately — the shape most tutorials show — is satisfied by anything that can
fake the success callback.

---

## Data flow

```
                      ┌──────────────┐
   FileObserver ─────▶│              │
   WorkManager  ─────▶│  FileIndexer │──▶ Room (metadata only)
   (reconcile)        │              │        │
                      └──────────────┘        ▼
                                        name search (LIKE)
                                              │
   ContentSearcher ◀──── live file reads ─────┘
   (never indexed, size-capped, cancellable)
```

The index holds names, sizes, dates and types. **File contents are never
indexed.** A content index would be a durable second copy of everything the user
has written, outliving the files themselves. Contents are read live, only for
text-like types, only under a size ceiling, and only while a contents search is
running. The cost is latency, and the search screen reports the real number
rather than a decorative one.

---

## Deliberate departures from the design handoff

Each of these is a place the implementation does something other than what the
mock shows, with the reason.

| Handoff | Implementation | Why |
|---|---|---|
| 4-digit server PIN | 6 digits | 10 000 guesses is reachable against a long-running server even through a backoff. Two extra characters cost the user about a second. |
| Proto DataStore | Typed DataStore with a kotlinx.serialization JSON codec | The typed API's guarantees (atomic writes, single writer, a Flow of current value) are what matter. Protobuf would add `protoc` to the build for a settings object of thirty scalars. One class to swap if the wire format is wanted. |
| Room FTS for names | Indexed `LIKE` over a lower-cased name column | An external-content FTS table requires the index's primary key to be a rowid alias, which means carrying a synthetic id alongside the path and keeping the two in step through every move and delete. A `LIKE` scan over a few hundred thousand rows lands in the low tens of milliseconds. |
| Glass surfaces with backdrop blur | Translucent fill and inner-lit border, no blur pass | Compose has no backdrop blur. `Modifier.blur` blurs a composable's own content, not what is behind it. The token file's opaque `$fallback` is used where transparency is unavailable. |
| `.ts` as a video transport stream | TypeScript | On a phone the extension is overwhelmingly source code. Transport streams still resolve correctly through content sniffing. |
| Storage meter bands sum to used space | Bands are drawn against volume-reported used bytes; the remainder stays unfilled | `StatFs` includes the OS and other apps' private data, which FOLD cannot see. Attributing the difference to an "Other" slice would be an invented figure. |
| Nav graph implied by the screen count | A single enum | Ten destinations, no deep links between them, no arguments to serialise. |

---

## Module graph

```
:app
 ├── :feature:browser ──┐
 ├── :feature:search  ──┤
 ├── :feature:transfer ─┼── :core:design
 ├── :feature:vault  ───┤
 ├── :feature:glyph  ───┤
 ├── :widget ───────────┴── :core:storage
 └──                        :core:crypto ── :core:storage
```

`:widget` deliberately does not depend on `:feature:transfer`. It would pull in
Ktor and the whole transfer graph to render two lines of text; instead the
transfer layer publishes two values the widget reads back.
