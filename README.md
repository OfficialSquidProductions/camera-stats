# Camera Stats

**A Squid Productions Software**

An early, local-only camera-reporting app for iPhone, iPad, and Mac. The complete product follows the 29-page Camera Reporting Product Plan, Revision 6 (October 19, 2026). This repository begins the field alpha; it is **not the plan's complete first release**.

## Try the owner-test alpha

Open `apple/CameraStats.xcodeproj`, choose `CameraStats`, and build for macOS 14+, iPhone, or iPad on iOS/iPadOS 17+. No package downloads or runtime network access are required. Signing is deliberately unconfigured until the existing Apple developer workflow is coordinated. Nothing has been submitted to TestFlight yet. The MIT source is published at https://github.com/OfficialSquidProductions/camera-stats; the download/status page is https://camera-stats-download.vastbow4.chatgpt.site while the requested custom domain is awaiting DNS verification.

Tap **Open The Last Light demo** on Today. It opens a separate fictional report with 24 takes over the eight scenes from Production Desk's sample script, including Maya, Eli, Jonah, and the lighthouse. Read the full script from Today, inspect circled/MOS/pickup examples, preview a landscape PDF, or save/import a `.camreport`. Creating another demo never overwrites an existing report. The demo follows the shared The Last Light test story; this task does not modify the other applications.

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

Android and Windows are required by the plan, but have no implemented clients yet. The portable format remains independent of the Apple interface. Desktop footage playback/skimming, exact-frame seeking, slate detection/OCR, RAW decoding, media reconciliation, certified group-aware RED R3D/BRAW physical rename/copy/recovery, and tested NLE adapters are not implemented. Naming previews never touch camera files. No hardware, camera, codec, or NLE compatibility is claimed.

See [requirements and acceptance](docs/REQUIREMENTS.md), [format contract](docs/CAMREPORT.md), [validation](docs/VALIDATION.md), and [release checklist](docs/RELEASE.md). The 12-hour airplane-mode day, low-storage/power-loss testing, real-device tests, cross-platform exchange, media/SDK licensing, and real RAW/NLE certification are outstanding. Source is MIT licensed; third-party components retain their own licenses. The local website draft has no fabricated download or TestFlight links.
