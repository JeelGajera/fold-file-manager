# Status: what is verified and what is not

This document exists because the honest answer to "does it work?" is "it has
never been compiled", and burying that would waste the next person's afternoon.

## The constraint

This codebase was written in an environment with:

- JDK 21 and Gradle available;
- **no Android SDK** — `dl.google.com` is blocked by the environment's network
  policy, so the SDK and build-tools could not be installed;
- **no Google Maven** — `maven.google.com` / `dl.google.com/dl/android/maven2`
  is blocked, so AGP, AndroidX, Compose, Room, Hilt and Glance could not be
  resolved.

Maven Central *is* reachable, which is why the non-Android dependencies
(Ktor, kotlinx) are pinned with more confidence than the AndroidX ones.

## What that means concretely

| | Status |
|---|---|
| Compiles | **Not verified.** No compiler has seen this code. |
| Unit tests pass | **Not verified.** No test has been executed. |
| Runs on a device | **Not verified.** |
| API usage correct | Written carefully against known-stable APIs; unconfirmed. |
| Architecture and security reasoning | Sound on inspection; that is what was actually delivered. |

Treat the first build as a bring-up exercise, not a regression.

## Where to look first

Ranked by how likely they are to be wrong, most likely first.

1. **Dependency versions** (`gradle/libs.versions.toml`). Every version is a
   real published one, but the *combination* has not been resolved together.
   AGP 8.7.3 / Kotlin 2.0.21 / Compose BOM 2024.12.01 / Hilt 2.52 is a
   conventional set; if something conflicts, this file is the single place to
   fix it.

2. **`androidx.compose.ui.text.googlefonts.R.array.com_google_android_gms_fonts_certs`**
   in `FoldTypography.kt`. The certificate array ships inside
   `ui-text-google-fonts`, and with non-transitive R classes it has to be
   addressed through that library's own `R`. If it does not resolve, either add
   the certs array to `:core:design`'s own resources or drop the downloadable
   fonts and ship the TTFs. The app degrades to system fonts either way, and no
   layout depends on a glyph advance.

3. **Ktor API surface.** Pinned to 2.3.12. `PipelineContext<Unit, ApplicationCall>`
   as the route-handler receiver is a 2.x shape; Ktor 3.x renamed it to
   `RoutingContext`. If you upgrade Ktor, `FoldServer.kt` is the file to edit.

4. **Glance layout primitives.** `:widget` uses Glance 1.1.1. Glance's layout
   API is small and has moved between releases; `FoldWidgets.kt` is short and
   self-contained if it needs adjusting.

5. **Room + KSP.** `FoldDatabase` exports its schema to `core/storage/schemas`;
   the directory is created on the first successful build.

## What is genuinely worth trusting

The parts that carry the risk were written to be reviewable without a compiler,
and they are the parts to read rather than to re-derive:

- **`PathGuard`** and its test suite. The traversal cases (dot-dot chains,
  prefix siblings, encoded segments, NUL truncation, symlink escapes in and out
  of the vault) are enumerated deliberately. The logic is ordinary `File`
  canonicalisation and string comparison — no Android API in sight — so it is
  checkable by reading it.
- **`MimeResolver`** and its tests. Pure functions over bytes.
- **`ServerAuth`** and its tests. Pure logic over an injected clock.
- **`VaultCrypto`**'s blob format and its tests. Pure byte handling; the
  Keystore half needs a device.

## Before the first release

- [ ] Build and fix whatever the compiler finds.
- [ ] Run `./gradlew test` and fix whatever the tests find.
- [ ] Write the instrumented tests: permission grant, denial, and revocation
      mid-session; vault round-trip on real hardware; key rotation.
- [ ] Add the Ktor `testApplication` suite for the server routes — the
      dependency is already declared, the traversal cases mirror `PathGuardTest`.
- [ ] Register for Nothing's Glyph SDK, add the AAR and the manifest API key.
      Until then `GlyphDetection.SDK_INTEGRATION_PENDING` is true and every
      device behaves as if it had no glyph, which is correct rather than broken.
- [ ] Manual matrix from the handoff: Phone (2a), a non-Nothing device,
      Android 11 and current, a low-storage device, and a large transfer over a
      slow network.
- [ ] Play Console: All Files Access declaration with the demo video and the
      written justification citing the MIME-type gap; the `dataSync` foreground
      service justification; the Data Safety form.
