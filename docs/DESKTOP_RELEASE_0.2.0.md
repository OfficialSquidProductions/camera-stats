# Desktop 0.2.0-alpha.1

This desktop milestone adds local footage review to the existing reporting alpha. Mac uses native SwiftUI with a bundled JVM media worker; Windows uses Compose with the same media engine. iPhone/iPad, Android and TestFlight releases remain on 0.1.1. This is an early alpha, not the complete Revision 6 product plan.

## Included workflow

Register a working folder read-only, inspect clip metadata, seek exact presentation frames, and review a short silent sequence forwards or backwards. Confirm slate labels manually, inspect report suggestions and explicitly associate a clip with a take. Save a naming preset, preflight copies, verify source/destination bytes and decoded frame evidence, resume interruption journals, and retain recovery files when undoing copies. RAW package copies preserve internal span names and sidecars inside a named outer folder. Inventory confirmation is explicit and does not certify a camera card is complete or safe to erase.

The Last Light demo remains a separate report, with the same eight-scene script and 16-shot plan shared with Scenic and Production Desk. Opening another demo preserves existing reports.

## Packaging and upgrades

Mac release metadata is 0.2.0 (3), with release label 0.2.0-alpha.1. The media package targets macOS 14+ on Apple silicon; the previous reporting-only universal Mac alpha remains available for Intel Macs. Windows MSI ProductVersion is 0.2.0 and retains the existing UpgradeCode. Windows installers are unsigned manual downloads; a trusted publisher certificate is not configured.

Keep the existing application identifiers and report storage paths. Mac must retain the existing sandbox storage model unless an explicit, separately validated compatibility migration is implemented. Media schema upgrades retain a consistent pre-upgrade SQLite snapshot. Report schema 1 and `.camreport` exchange remain compatible. Export an independent report backup before upgrading. Copy journals are recoverable operation records, not a footage backup.

A dedicated Mac Sparkle public key has been authorized and generated; its private key stays in the owner's Keychain. Automatic update delivery remains disabled in distributed packages until the real signed/notarized older-to-newer installation and relaunch gate passes. Windows also remains disabled without a trusted publisher, dedicated update key/feed and native signed-upgrade acceptance. Existing unit tests and manual installer upgrades do not establish those gates.

## RAW setup and limits

No RED or Blackmagic SDK, vendor runtime, proprietary test footage or decoded frame is distributed. RED decoding uses a separately installed, authorized REDline executable. BRAW decoding requires building the original helper against a locally authorized SDK and keeping the vendor runtime local. Choose tools explicitly in the desktop media workspace. See [RAW setup](RAW_DESKTOP.md) and [local validation profiles](LOCAL_RAW_TESTS.md).

Actual local Mac validation covers an official two-span RED sample, the BRAW SDK's single-frame sample with sidecar, and an installed four-frame BRAW benchmark. Long-shoot/multicard BRAW, native Windows RAW adapters, camera hardware, NTFS/exFAT/network-volume operation and named-NLE relink/conform remain unvalidated. Slate OCR, real-time audio/full timeline shuttle and portable media-reference exchange remain unfinished. Originals are never renamed or overwritten by this workflow.

## Release evidence

Publish only after the clean source build, native packaged-runtime acceptance and platform signing/install gates have passed. The dedicated Windows workflow checks source-built codec tools, SDK-free tests, rejection of unsigned/tampered installers by the update helper, a previous-public-to-new manual MSI upgrade, report/history/preferences/media-journal preservation, and installed review/copy/undo. Its curated evidence excludes SDKs and camera media.

The release assets must include SHA-256 checksums, exact source commit/build metadata, matching complete OpenJDK source, unmodified FFmpeg source and rebuild recipe; Windows also includes zlib source and compiler/notices metadata. See [third-party notices](../THIRD_PARTY_NOTICES.md). Release notes and validation metadata identify any platform that could not complete its gates; a prepared candidate is not a published release.
