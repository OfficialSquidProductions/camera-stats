# Desktop updates: implementation and release gates

Shot Reporter retains bundle/application ID `org.squidproductions.camerastats`, Windows MSI UpgradeCode `c3774fda-7962-4264-a9bc-c428176d12b4`, existing report storage and separate desktop media journals. No application/source footprint moves reports into a new branding directory.

The update implementation does **not** enable live updates by itself. Desktop releases require the platform checks below; publication does not activate an unconfigured feed. No Shot Reporter update signing credentials, Windows publisher certificate or live feeds have been created. Current Windows downloads are unsigned. Development builds explain this state and cannot automatically install them.

## Existing suite behavior inspected

Read-only references were Scenic `windows/main.cjs`, `windows/updates.cjs`, `macos/Updates.swift`, and Production Desk `desktop/windows/main.cjs`, `desktop/Updates.swift`. Those apps use electron-updater on Windows and Sparkle on macOS. Their UX includes automatic launch checks, manual checks, available-version notices, release details, an explicit download/install decision, failed-save refusal, and relaunch. No reference application, update feed or QA helper was modified/launched.

Shot Reporter adopts those interactions with a desktop Updates control, automatic checks after the initial window task on Mac, after 15 seconds on Windows, and daily thereafter, Stable/Alpha channels, release notes, Download Update, Keep Working/Cancel and Save, Install and Restart. Windows displays release notes as plain Compose text; supplied HTML or script text cannot execute. macOS uses native Sparkle UI with signed, self-contained release notes.

## macOS

The native app uses pinned Sparkle 2.10. `tools/build_apple.py --distribution development|direct|testflight|appstore` distinguishes distribution; development is the default. Store/TestFlight variants omit Sparkle binaries entirely and direct the user to the appropriate distribution surface. These builds must never replace themselves from a direct-download feed.

Direct distribution checks the current stable bundle identity and Apple Team `FZ298DXLS3`; unsigned hosts are disallowed. Signed archive verification occurs before extraction. `SURequireSignedFeed` is enabled: appcast and release notes must also be signed with the dedicated Shot Reporter Ed25519 key through Sparkle's official tooling. Numeric monotonic `CFBundleVersion` orders updates; semantic display versions are separate. Stable excludes alpha; Alpha accepts alpha and stable.

The tentative feed address is `https://raw.githubusercontent.com/OfficialSquidProductions/shot-reporter/main/updates/macos/appcast.xml`. It has not been published by this work, and no trusted public key is configured. The updater therefore fails closed. The direct-distribution release process must package and sign the Sparkle components and notarize the host correctly before activation. Creating a persistent dedicated signing key needs separate user consent.

## Windows trust protocol

The desktop-only `:updates` module has no mobile dependency. The installed app may bundle `/windows-update-policy.json` with:

- `feedURL`: HTTPS signed feed URL.
- `publicKey`: base64 DER SubjectPublicKeyInfo Ed25519 public key (not a secret).
- `allowedHosts`: exact lowercase HTTPS hosts, including every approved artifact redirect host.
- `signerSHA256`: SHA-256 of the dedicated trusted Authenticode leaf certificate's DER bytes.
- `currentVersion`, `currentBuild`, `currentMSIVersion`, and `defaultChannel` (`alpha` or `stable`).

No development environment override or feed-controlled key/publisher rotation is supported. Missing/malformed configuration disables automatic installation. The feed cannot select another app, arbitrary host, publisher, MSI identity or schema. A publisher/key rotation requires a reviewed app release and renewed upgrade validation.

A feed is a UTF-8 JSON envelope with exactly `payload` and `signature`, both base64. Signature is Ed25519 over the **exact decoded payload bytes**, not reparsed/canonicalized JSON. The signed payload has:

```json
{
  "schema": 1,
  "appID": "org.squidproductions.camerastats",
  "generatedAt": "2026-10-19T12:00:00Z",
  "expiresAt": "2026-10-20T12:00:00Z",
  "releases": [{
    "version": "0.2.0-alpha.1",
    "build": 4,
    "channel": "alpha",
    "releaseNotes": "Plain text release notes",
    "windowsX64": {
      "url": "https://APPROVED-HOST/Shot-Reporter.msi",
      "size": 123,
      "sha256": "64_HEXADECIMAL_CHARACTERS",
      "upgradeCode": "c3774fda-7962-4264-a9bc-c428176d12b4",
      "signerSHA256": "64_HEXADECIMAL_CHARACTERS",
      "msiVersion": "0.2.0"
    }
  }]
}
```

This example is documentation, not a usable or signed feed. Maximum envelope is 1 MiB, payload 512 KiB, 100 releases, release-note text 100,000 characters and installer 4 GiB. Dates expire within 31 days and reject materially future timestamps. Checks enforce semantic version ordering and increasing numeric build; the highest authenticated offered build is durably retained locally to block replay of an older still-valid feed. Stable releases cannot contain prerelease identifiers; Alpha accepts both channels. MSI ProductVersion must also increase, including successive alpha builds: Windows Installer considers the first three fields, so changing a display suffix alone is insufficient. It must remain within the Windows MSI numeric bounds.

Downloads use system TLS, bounded connect/read timeouts and at most five redirects. Every redirect is revalidated against the bundled exact-host HTTPS allowlist; credentials, nonstandard ports and fragments are rejected. A new owned attempt directory receives a partial file, immutable journal, exact byte count and SHA-256 check, then an atomic staging rename. Failed/cancelled partial files never become installable. Existing files are not reused or overwritten. Cancellation retains diagnostic/recovery files rather than deleting data.

Before installation the app checks the staged hash/length again, verifies every accepted report is durable, holds all existing per-project worker locks and writes consistent SQLite snapshots using `VACUUM INTO`. `quick_check` verifies snapshots. A local manifest maps backup filenames to original relative database paths. No camera footage is accessed by the updater. Save failure, pending import, stale report state, active media work, insufficient backup space or helper failure keeps the app open.

A fresh per-install attempt ticket launches the system Windows PowerShell helper, without execution-policy bypass. The helper verifies the package's trusted Authenticode signature and pinned leaf certificate, MSI UpgradeCode, ProductName, ProductVersion and x64 template **before acknowledging readiness**. It also refuses other running Shot Reporter/Camera Stats instances. A prepared handoff holds the save guard but cannot install. The UI must publish an atomically renamed durable commit marker before requesting exit; disposal before commit publishes cancellation and releases the guard. Interrupted helper readiness waits cancel and terminate the helper. The UI exits only after acknowledgement, and the helper waits for actual process exit. Parent exit without a valid commit marker never starts MSI. It holds a read-only file lock across package verification and installation, repeats package validation, invokes system Windows Installer with basic cancellable UI, suppresses reboot, retains verbose logs and an install result, and relaunches the known installed Shot Reporter path. MSI owns transaction rollback; the updater never passes report/media storage paths to it. A failed install reopens the previous installed path where it still exists and displays retained outcome/log information. No automatic database restore is attempted because that could overwrite newer user work. Pre-update snapshots remain for reviewed manual recovery.

## Validation and unresolved gates

Local JVM tests exercise authentic and tampered signatures, app/publisher/host mismatch, expiration, channels, semantic ordering, persisted replay watermark, exact/truncated/oversize/changed/cancelled downloads, staging tamper, failed save, helper failure and retained save guard. SQLite integration tests reopen unchanged report snapshots and an interrupted-copy journal; active worker locks, pending imports and stale report states block install. Regression tests cover competing commit/dispose operations and missing accepted revision-zero reports. UI-thread state capture keeps save checks consistent across report selection or edits. Compose UI tests verify an unconfigured build cannot offer installation. All fixtures are synthetic and isolated from owner data.

The native Windows PowerShell/COM/MSI elevation, publisher check, actual package rollback, installed-process close and relaunch are **not validated on this Mac**. There is no local Windows VM. The dedicated desktop release workflow now prepares native Windows rejection checks and an isolated manual-MSI upgrade; successful signed automatic installation remains a separate gate. An older-to-newer signed MSI upgrade must run in isolated Windows storage before enabling a live feed, including same identity/UpgradeCode, alpha progression, non-ASCII paths, user cancellation, network failure, modified installer, reboot-required result and disk-full backup failure. Verify every report snapshot, history, preferences, media journal and registered source path after relaunch. Existing reporting-only MSI upgrade evidence does not certify this new helper.

Mac tests cover update policy/save/cancel/media locks, native Sparkle delegate gates and isolated old/new recovery fixtures. Ephemeral fixture Ed25519 signatures and directory swaps are not a real Sparkle installation. A dedicated signed/notarized older-to-newer update and relaunch remains a release gate.

Before activation: obtain user authorization for a persistent dedicated signing identity/key if needed, approve any certificate purchase, configure trusted public metadata, produce signed/notarized platform builds, validate native upgrade/recovery, review release notes and request publication/feed authorization. This engineering work creates no feed, account, key, purchase, TestFlight upload, release or merge.

Primary platform references: [Windows Installer ProductVersion](https://learn.microsoft.com/en-us/windows/win32/msi/productversion), [major upgrades](https://learn.microsoft.com/en-us/windows/win32/msi/major-upgrades), [installer options](https://learn.microsoft.com/en-us/windows/win32/msi/standard-installer-command-line-options), [Authenticode verification](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.security/get-authenticodesignature), [read-only MSI database access](https://learn.microsoft.com/en-us/windows/win32/msi/installer-opendatabase).


## Windows desktop release candidate workflow

`.github/workflows/desktop-release.yml` runs on the explicitly authorized desktop candidate branch or manual dispatch with an exact reviewed source SHA. It has read-only repository permissions, no Android/TestFlight job and no release/feed publication step. It builds a minimal FFmpeg9.0.2/zlib1.3.2 profile from unmodified official hash-pinned source, checks OS-only DLL dependencies and includes notices, build metadata and corresponding-source archives. The JVM is pinned to the exact Temurin release matching its full source archive.

Native Windows tests must reject the actual unsigned installer and same-length tampered bytes before helper readiness or MSI invocation. An isolated profile exercises the previous public0.1.1 MSI, report snapshots/history/preferences, a synthetic prior media-journal schema and the new installed0.2.0 runtime, including bundled exact-frame review/copy/undo. No vendor SDK/runtime or camera media is uploaded. Curated evidence contains XML, logs and synthetic-test PASS markers only. A manual unsigned MSI may be published only after this workflow passes; automatic installation remains disabled without trusted signing/feed configuration. This native rejection coverage does not certify successful signed auto-update or native Windows RAW decoding.
