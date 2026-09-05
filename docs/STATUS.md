# Project status

FOLD is under active development. This document records what is implemented,
what is covered by automated verification, and what remains before a release.

## Implemented

| Area | Module | State |
|---|---|---|
| `FileSystemProvider` abstraction, raw and SAF implementations | `:core:storage` | Complete |
| Path canonicalisation and access control (`PathGuard`) | `:core:storage` | Complete |
| MIME resolution: extension table, content sniffing, fallback | `:core:storage` | Complete |
| Room metadata index, `FileObserver` watching, WorkManager reconciliation | `:core:storage` | Complete |
| Typed DataStore preferences | `:core:storage` | Complete |
| Vault key hierarchy, blob format, biometric gate | `:core:crypto` | Complete |
| Design tokens, theme and component set | `:core:design` | Complete |
| Browser, search, vault, transfer and glyph screens | `:feature:*` | Complete |
| LAN server, PIN auth, mDNS discovery, foreground service | `:feature:transfer` | Complete |
| Share-sheet integration | `:feature:transfer` | Complete |
| Glance home-screen widgets | `:widget` | Complete |
| Glyph sequences and controllers | `:feature:glyph` | Wired; hardware integration pending |

## Verified

CI (`.github/workflows/build.yml`) assembles the debug variant and runs the unit
test suites on every push and pull request.

| Check | State |
|---|---|
| Dependency resolution and configuration | Passing |
| Android resource compilation | Passing |
| Compilation of every module, including Room, KSP and Hilt | Passing |
| `:app:assembleDebug` | Passing; the debug APK is uploaded as a build artifact |
| Unit test suites across `:core:storage`, `:core:crypto` and `:feature:transfer` | 74 tests, 0 failures |
| Instrumented tests | Not yet written |

The unit suites cover the security-critical logic: path canonicalisation and
containment (`PathGuardTest`, 19), MIME resolution (`MimeResolverTest`, 21),
path modelling (`FsPathTest`, 9), the vault blob header and key-alias
versioning (`VaultBlobHeaderTest`, 11), and the LAN server's authentication and
rate limiting (`ServerAuthTest`, 14).

## Known risk areas

Ranked by likelihood of needing attention.

1. **Ktor API surface.** Pinned to 2.3.12.
   `PipelineContext<Unit, ApplicationCall>` is the 2.x route-handler receiver;
   Ktor 3.x renames it to `RoutingContext`. `FoldServer.kt` is the file to
   update on a major-version bump.

2. **Glance layout primitives.** `:widget` targets Glance 1.1.1, whose layout
   API has changed between releases. `FoldWidgets.kt` is self-contained.

3. **Room schema export.** `FoldDatabase` exports to `core/storage/schemas`,
   created on the first successful build. Commit it so migrations are
   reviewable as diffs.

## Remaining work

- [ ] Instrumented tests: permission grant, denial and revocation mid-session;
      vault encrypt/decrypt round-trip on device; key rotation.
- [ ] Ktor `testApplication` suite for the server routes. The dependency is
      declared; the traversal cases mirror `PathGuardTest`.
- [ ] Glyph hardware integration. Nothing's Glyph Developer Kit and Glyph Matrix
      SDK require developer registration and an API key declared in the
      manifest. Until that is done `GlyphDetection.SDK_INTEGRATION_PENDING`
      reports true and every device behaves as though it has no glyph hardware,
      which is the intended fallback rather than a defect.
- [ ] Device matrix: Phone (2a) for the strip glyph, a non-Nothing device,
      Android 11 and current, a low-storage device, and a large transfer over a
      slow network.
- [ ] Play Console submission: All Files Access declaration with demo video and
      written justification citing the MIME-type gap; `dataSync` foreground
      service justification; Data Safety form (see [PRIVACY.md](PRIVACY.md)).
