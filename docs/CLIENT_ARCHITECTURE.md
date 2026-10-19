# Windows / Android field clients

## Decision — 2026-10-19

The released SwiftUI clients and Apple report engine remain unchanged. New Android and Windows field clients share a Kotlin/JVM report engine and Compose screens. This replaces the earlier proposed Swift/C-ABI/JNI spike for this field-alpha milestone: it avoids a separate new Swift SDK/NDK/ABI deployment layer while keeping both new clients on one engine. Swift remains the schema-1 interoperability reference. This is an additive implementation, not a claim that a second engine eliminates compatibility risks.

- `clients/core`: report model, strict stored-ZIP subset, CRC/SHA-256 integrity, conservative merge, naming previews, review rules, generic CSV/HTML and transactional snapshot repository.
- `clients/shared-ui`: Today / Log / Review / Saves, preferences, complete report fields, independent demo, flags, review, recovery and import preview.
- Android: system SQLite, app-private files, Storage Access Framework pickers, native landscape PDF, Android phone/tablet layouts. Minimum API 26. No Internet, camera, microphone or broad storage permission. OS automatic backup is disabled; deliberate report exports are supported.
- Windows: Compose Desktop on a bundled JVM, JDBC SQLite, native file dialogs, durable file replacement and native Java2D/PDFBox landscape PDF. The first build target is Windows x64; arm64 is not advertised until tested. No browser/WebView or local web server is involved.
- Both use the untouched suite mark/icon and the exact shared The Last Light fixture. Built-in demo opens as an independent report; real saved reports are preserved.

The Apple PDF layout is unchanged. New PDF adapters provide complete landscape reports with continuation pages; exact school-form parity and source-frame/media features remain separate acceptance work.

## Contract gates

`tools/generate_cross_platform_fixtures.py` compiles the existing Apple core without editing it. Deterministic fixtures contain no account, device or real production data. They cover Unicode, flags, additions, competing edits, tombstones and a 1,000-take day. The shared JVM engine must read these unchanged. Its own exported and edited packages must then be accepted by the unchanged Swift reader. Unknown/missing report fields, invalid bounds, unsafe paths, unsupported schema/scope, CRC/SHA errors and duplicate IDs are rejected before local mutation.

SQLite stores current/previous full report packages plus active selection in one WAL/FULL transaction. Optimistic fingerprints reject stale writers; failed saves leave the accepted report intact. New clients deliberately use older SQLite-compatible UPDATE/INSERT statements for Android API 26 instead of newer UPSERT syntax. Local DB files are not a cloud-sync format.

## Toolchains and CI

This Mac originally had Xcode/Swift and Node, but no JDK, Gradle, Android SDK, .NET, Rust or Windows SDK. Official checksum-verified Temurin JDK 21 and Gradle 8.13 were extracted to a temporary folder. No global Java/Xcode setting or Apple signing credential was changed.

The public repository's existing Actions service is enabled. Only standard public-repository runners are used; build evidence is retained in an owner-visible draft GitHub release instead of metered Actions artifact storage. The narrowly scoped publishing jobs use only GitHub’s automatically supplied ephemeral job token; no paid/larger runner, persistent credential, new store membership or signing certificate is created. CI uses the official runner image's installed Android SDK. Automatic SDK downloads/license acceptance are disabled in Gradle; emulator image installation receives no automatic license approval. If a new license is requested, that job must stop for owner approval.

Pinned build versions: Kotlin/Compose compiler 2.1.21, Compose Multiplatform 1.8.2, Android Gradle Plugin 8.9.2, Gradle 8.13, JDK 21 compiling Java 17 bytecode, Android compile/target SDK 35. These intentionally form a compatible stable set; a newer framework version is not substituted without rebuild/tests. Android instrumentation packages use ephemeral debug keys. The owner separately approved a dedicated Camera Stats release identity, stored outside source on the owner's Mac with its password in macOS Keychain. Local apksig 8.9.2 signs and verifies release APKs; CI only receives the exact signed candidate bytes for installation/reopen/reinstall tests. Windows artifacts remain unsigned; no new signing certificate was purchased or created.

## Release gates and remaining scope

Builds, core tests, cross-reader compatibility, real native storage tests and rendered phone/tablet/Windows checks must pass before a client is listed as available. CI artifacts may be development builds while validation is incomplete. The release website must state the actual validation level.

Revision 6 still requires richer versioned profiles/requirements, full field conflict resolution, scoped/attached/encrypted handoffs, a 12-hour physical-device day, low-storage/power-loss/latency evidence and exact school-form parity beyond this alpha. Desktop media ingestion, exact-frame browsing, OCR and physical RAW rename/undo require real media, vendor SDK terms and tested NLE adapters. No destructive RAW rename, camera integration or NLE certification is implemented here.

References reviewed 2026-10-19: [Compose support](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), [native desktop distributions](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-native-distribution.html), [Swift Android prerequisites](https://www.swift.org/documentation/articles/swift-sdk-for-android-getting-started.html), [GitHub public runner billing](https://docs.github.com/en/actions/concepts/billing-and-usage), [runner SDK inventory](https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md).
