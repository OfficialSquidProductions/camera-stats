# Shot Reporter

**A Squid Productions Software**

Previously Camera Stats. Existing reports, app identities, and signing keys are preserved; see the [rebrand compatibility notes](docs/REBRAND.md).

Early, local camera-reporting apps for iPhone, iPad, Mac, Android phones/tablets, and Windows. The complete product follows the 29-page Camera Reporting Product Plan, Revision 6 (October 19, 2026). This repository begins the field alpha; it is **not the plan's complete first release**.

## Try the owner-test alpha

Download the signed, notarized universal Mac alpha from the [GitHub release](https://github.com/OfficialSquidProductions/shot-reporter/releases/tag/v0.1.1-alpha.3). The download/status page is [shotreporter.squidproductions.org](https://shotreporter.squidproductions.org). Shot Reporter 0.1.1 (2) is available to the invited account owner in TestFlight on iPhone, iPad, and Mac. See [validation evidence](docs/VALIDATION.md).

Verified [Windows x64 installers and signed Android APK](https://github.com/OfficialSquidProductions/shot-reporter/releases/tag/v0.1.1-alpha.3) are also available. Windows installers are unsigned; Android testing covered phone/tablet emulators.

To build the report app from source, open `apple/CameraStats.xcodeproj`, choose `CameraStats`, and build for macOS 14+, iPhone, or iPad on iOS/iPadOS 17+. The report app uses system frameworks and local data. Configure your own signing team for device distribution. The desktop development branch adds a separate media worker; use its [local build and testing instructions](docs/DESKTOP_MILESTONE.md) to enable footage review. The published reporting alpha does not include that development workflow.

The additive Android/Windows clients use a shared Kotlin/JVM report engine and Compose screens, native SQLite adapters, system file pickers and platform PDF renderers. Their [build instructions](clients/README.md) require JDK 21 and the pinned Gradle wrapper; Android builds also require an SDK whose terms the owner has accepted. Windows installers bundle their JVM. The Apple and Kotlin clients retain the same compatible report model. See the [architecture and interoperability gates](docs/CLIENT_ARCHITECTURE.md).

Tap **Open The Last Light demo** on Today. It opens a separate fictional report with three completed takes for shot 3A and the shared 16-shot plan across eight scenes from Production Desk's sample script, including Maya, Eli, Jonah, and the lighthouse. Read the full script from Today, inspect the shared circled take 2, preview a landscape PDF, or save/import a `.camreport`. Creating another demo never overwrites an existing report. The demo follows the shared The Last Light test story; this task does not modify the other applications.

The app supports full project/crew, camera/media, recording, lens/measurement, and take fields; quick/standard/full take views; profile and position settings; take progression per camera; inherited settings snapshots; flags; separate camera/DP/continuity/editorial notes; search; self-review and missing-field checks; local history and recoverable deleted takes; report-only handoffs; landscape PDF preview/export; generic CSV/HTML exports; and declarative naming previews. The supplied SP mark is kept unchanged under `assets/brand` and used to generate platform assets. The exact family credit is visible in the app.

## Local saves and imports

Every accepted field edit commits a validated report snapshot to a local SQLite database with WAL journaling and synchronous FULL durability, retaining the preceding snapshot in the same transaction. A failed or stale save keeps the previously accepted in-memory report. The active document selection commits with its report. Previous snapshot files are migrated idempotently and kept unchanged for recovery. Export an independent backup regularly. Report files are never a footage backup.

`.camreport` schema 1 is a documented ZIP archive containing `manifest.json` and `report.json`, with CRCs and a SHA-256 report checksum. This alpha supports complete reports only, without attachments, encryption, or scoped handoffs. Imports show the project/revision/device, additions, and conflicts. Independent additions merge by stable ID. Repeated package imports are idempotent. Omitted records never mean deletion. Competing edits require opening the incoming report separately; a full three-way field conflict resolver remains unfinished. Unsupported schemas/fields and unsafe/corrupt archives are rejected without changing the local report.

## Build and test

```sh
python3 tools/build_apple.py
swift tools/make_icons.swift "$PWD"
swift test --package-path app
python3 tools/verify_crash.py
xcodebuild -project apple/CameraStats.xcodeproj -scheme CameraStats -configuration Debug -destination 'platform=macOS' CODE_SIGNING_ALLOWED=NO build
xcodebuild -project apple/CameraStats.xcodeproj -scheme CameraStats -configuration Debug -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

The project generator does not select a developer team or perform signing/account operations. Generated platform icon canvases preserve the mark's proportions and source green. No commercial font files, vendor decoders, proprietary SDKs, external OCR models, camera footage, or private production data are bundled. The app uses platform typography.

## Current boundaries

Android and Windows field-reporting clients are implemented. Android phone/tablet instrumentation, signed-release installation/reopen/reinstall checks, and Windows bundled/installed-runtime tests use isolated CI machines; physical-device validation remains outstanding. Windows x64 installers are unsigned. The Android release identity stays local, outside the repository; CI receives signed APK bytes only. The portable format remains independent of the Apple interface.

The [desktop development milestone](docs/DESKTOP_MILESTONE.md) implements read-only working-folder registration, exact presentation-frame review, short silent forward/reverse review, explicit manual slate confirmation and report association, saved versioned naming presets, and verified renamed copies with interruption journals, resume and non-destructive undo. Synthetic H.264 MOV/MP4 and MPEG-4 Part 2 MP4 fixtures exercise this profile. Source originals stay unchanged. This work is separate from the published alpha: production packaging, RAW adapters, slate detection/OCR, real-time audio/full timeline shuttle, portable media-reference exchange, and named-NLE relink/conform certification remain unfinished. No camera or hardware compatibility is inferred from synthetic fixtures.

See [requirements and acceptance](docs/REQUIREMENTS.md), [format contract](docs/CAMREPORT.md), [validation](docs/VALIDATION.md), and [release checklist](docs/RELEASE.md). The 12-hour airplane-mode day, physical low-storage/power-loss tests, real-device handoffs, media/SDK licensing, and real RAW/NLE certification are outstanding. Source is MIT licensed; third-party components retain their own licenses. The download page links only verified releases.
