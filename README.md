# FOLD

A file manager for everything on your device.

Android's file pickers read a media index. A media index does not know what a
`.md` note is, or a `.log`, or an `.epub`, or a firmware image — so a file you
can plainly see in Downloads becomes a file you cannot attach, cannot share, and
in some apps cannot even see. FOLD reads the filesystem instead.

That is the whole premise, and everything below follows from it.

---

## What it does

**Reads the filesystem, not MediaStore.** With All Files Access, FOLD walks the
real tree. MediaStore is never consulted anywhere in the codebase.

**Resolves its own MIME types.** An extension table that knows the types Android
misses, magic-byte sniffing behind it, and `application/octet-stream` as a
last resort — which is a *type*, not a reason to hide a file. A file FOLD cannot
name gets a `?` badge and stays in the list.

**Shares over your Wi-Fi.** A small HTTP server on the phone, PIN-protected,
discoverable over mDNS. Open the address in a browser on any device on the same
network. Files stay on the phone; there is no account and no relay.

**Has a vault.** AES-256-GCM per file, keys wrapped by an Android Keystore key
that the hardware refuses to use until you authenticate. Vault contents are
excluded from the index, search, widgets and the LAN server — enforced in the
storage layer, not by remembering to check.

**Works without All Files Access.** If the permission is refused or revoked,
FOLD keeps working inside folders you grant through the system picker. This is a
supported mode with its own screens, not an error state.

---

## Status

FOLD is under active development and not yet released.

CI assembles the app and runs the unit test suites on every push. The storage
layer compiles and its suites pass — 63 tests covering path traversal, MIME
resolution and the LAN server's authentication and rate limiting. The Compose
and Hilt modules are still being brought up, and no instrumented tests exist
yet.

[`docs/STATUS.md`](docs/STATUS.md) tracks what is implemented, what CI verifies,
and what remains before a release.

---

## Build

```bash
./gradlew :app:assembleDebug
```

Requires JDK 17+ and the Android SDK (compileSdk 35). `minSdk` is 30, because
`MANAGE_EXTERNAL_STORAGE` does not exist below it and the app's premise
depends on it.

Run the unit tests:

```bash
./gradlew test
```

---

## Layout

```
:app              shell, navigation, permissions flow, manifest
:core:design      tokens -> Compose theme, components, icons
:core:storage     FileSystemProvider + both impls, MIME, index, settings
:core:crypto      vault key hierarchy and blob format
:feature:browser  home, browse, hidden files, limited access, onboarding, settings
:feature:search   name search over the index, live contents search
:feature:transfer LAN server, discovery, foreground service, share sheet
:feature:vault    vault screens
:feature:glyph    Nothing Glyph controllers and sequences
:widget           Glance home-screen widgets
```

The one architectural decision that matters is
[`FileSystemProvider`](core/storage/src/main/kotlin/com/jeelgajera/fold/core/storage/provider/FileSystemProvider.kt).
Read that file first; [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) explains
why it is shaped that way.

---

## Privacy

FOLD collects nothing and transmits nothing off-device. There is no analytics
SDK, no crash reporter, no advertising id, and no network call to anything but
the phone's own LAN server. [`docs/PRIVACY.md`](docs/PRIVACY.md) states this in
the form the Play Data Safety form asks for, and — more usefully — says how to
verify it rather than asking you to take it on faith.

---

## Licence

MIT. See [`LICENSE`](LICENSE).

Archivo and Doto are bundled under the SIL Open Font License 1.1. The licence
text ships inside the app at
[`core/design/src/main/assets/fonts/OFL.txt`](core/design/src/main/assets/fonts/OFL.txt) —
it lives under `assets/` rather than beside the fonts because Android's `res/font`
directory accepts only `.ttf`, `.ttc`, `.otf` and `.xml`.
