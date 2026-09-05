# Privacy

FOLD collects nothing and transmits nothing off-device.

That sentence is easy to write and worth almost nothing on its own, so this
document says what it means precisely, and — more usefully — how to check it.

---

## What FOLD reads

| | |
|---|---|
| File names, sizes, dates, folder structure | Yes, to list and index them. |
| File contents | Only during a contents search, only for text-like types, only under an 8 MB ceiling, and only for files inside the folders you granted. Never written anywhere. |
| The Wi-Fi network name | Only where the platform allows it without location permission. Where it does not, the UI says "your Wi-Fi network" instead. FOLD does not request location to print a string. |
| Anything else | No. |

## What FOLD stores

| | Where | Contains |
|---|---|---|
| Metadata index | `fold-index.db`, app-private | Names, sizes, dates, MIME types, paths. **No file contents.** Excluded from backup. Clearable in one tap from Settings. |
| Preferences | `fold-settings.json`, app-private | Theme, sort, server port and toggles, glyph events. No identifiers. |
| Vault blobs | `files/vault/`, app-private | AES-256-GCM ciphertext under random names. Keys in the Android Keystore. Excluded from backup **and** from device-to-device transfer. |

## What FOLD transmits

Nothing, to anyone, ever — with exactly one exception, which the user starts by
hand:

**The LAN server.** When you tap "START SHARING", FOLD binds an HTTP server to
the phone's Wi-Fi address. Files then move between your phone and other devices
on your own network. There is no relay, no account, no cloud, and no third party
in the path. It stops when you stop it, when the idle timeout fires, or when the
process ends.

It binds to the Wi-Fi interface's own address rather than `0.0.0.0`, so it does
not appear on cellular, on a VPN tunnel, or on a USB tether. On a phone with no
Wi-Fi connection it refuses to start without an explicit confirmation, because on
some carriers a cellular address is publicly routable.

## What FOLD does not contain

- No analytics SDK.
- No crash reporter.
- No advertising id, install id, or device fingerprint.
- No push service.
- No web font, stylesheet or script loaded from a CDN — including in the page
  the LAN server serves to browsers, which is entirely self-contained.

---

## How to verify this yourself

Do not take the list above on trust. It is checkable, and here is how:

**Read the dependency list.** `gradle/libs.versions.toml` is the complete set of
third-party code in the app. It is one file, and every entry is either AndroidX,
Kotlin, Ktor or a test library.

**Grep for network calls.** The only outbound HTTP code in the project is the
Ktor *server*. There is no HTTP client dependency at all.

**Watch the traffic.** Install the debug build, use the app normally with the LAN
server off, and point a proxy or a network monitor at it. There should be
nothing at all — the fonts are bundled, so not even a font fetch occurs.

**Read the manifest.** `app/src/main/AndroidManifest.xml` lists every permission
with a comment saying why. There is no location, no contacts, no camera, no
`READ_PHONE_STATE`.

---

## Play Data Safety form

For whoever fills this in at submission time:

- **Data collected:** none.
- **Data shared:** none.
- **Data types:** none. FOLD reads files on the device to do its job; it does not
  collect them, because nothing leaves the device and nothing is transmitted to
  the developer or a third party.
- **Encryption in transit:** the LAN server runs plain HTTP on the local network
  by default, protected by a per-session PIN. This is a deliberate trade — see
  the note in `FoldServer.kt` — and it is user-to-own-device traffic on the
  user's own network, not data sent to a developer.
- **Data deletion:** every byte FOLD stores is app-private and removed by
  uninstalling. The index is separately clearable from Settings.

Keeping all of this true is a genuine differentiator against the alternatives.
It stops being true the moment a crash reporter is added, so if crash reporting
is ever wanted, the honest options are an in-app "report an issue" flow that
opens a pre-filled GitHub issue with the version and stack trace visible to the
user before they send it — never a background uploader.
