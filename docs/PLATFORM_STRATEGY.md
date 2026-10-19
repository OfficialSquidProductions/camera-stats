# Shared core and Android / Windows feasibility

The first owner-test milestone uses native SwiftUI on iPhone/iPad/Mac with a UI-independent Swift report model, ZIP exchange, merge rules and SQLite persistence. Android and Windows remain required targets, not optional replacements. Their clients are not implemented or advertised as supported in this release.

## Practical reuse path

Keep the report/package contract independent of any UI. Reuse `Model.swift`, `Demo.swift`, `Repository.swift`, the stored-ZIP reader/writer and naming validation. Separate the Apple PDF renderer and SHA-256 provider from this portable layer. Expose explicit JSON request/response operations through a C ABI so Kotlin/JNI on Android and a native Windows UI can call the same compiled core without inventing a second merge policy. Keep file pickers, SQLite binary packaging, SHA provider, PDF rendering, lifecycle and filesystem permissions in platform adapters. A Swift-only UI is not a Windows or Android client.

Swift's official platform documentation lists Windows development/deployment and Android deployment, but this host's Xcode toolchain targets Apple platforms. A Windows build requires a Windows host/toolchain and SDK; Android requires an Android-capable Swift toolchain/SDK plus Android SDK/NDK and JDK. This Mac currently has none of JDK, Android SDK, Gradle, Rust, .NET, or a Windows SDK installed. `/usr/bin/java` is an unconfigured launcher. This is a measured toolchain gap, not evidence that Android/Windows cannot be built.

The current `CryptoKit` import and CoreText/CoreGraphics PDF implementation are concrete Apple dependencies that prevent simply relabeling the current package cross-platform. Apple's open-source swift-crypto is a potential SHA provider for non-Apple targets; its current README specifically names Linux and ARM64 Windows, so x64 Windows and Android combinations must be proved rather than inferred. It is Apache-2.0 with bundled crypto dependencies; include its notices if adopted. No such dependency is presently bundled.

## Next bounded spike and exit gates

1. Extract platform PDF/hash interfaces and export deterministic, non-private golden .camreport fixtures covering student fields, multiple flags, deletions, additions/conflicts, unknown schemas, Unicode and corrupt ZIPs.
2. Build the same non-UI core under the current Windows Swift SDK, and prove create/edit/reopen/package round-trips against Apple fixtures. Validate SQLite WAL, durable commit, path semantics and generic CSV/HTML on x64 and arm64 separately.
3. Build a native Android Kotlin screen calling the shared core through JNI, with app-private transactional storage, Storage Access Framework import/export and native PDF/share adapters. Validate phone/tablet rotation, lifecycle, no network permission and the same full student report offline.
4. Add native Windows table/inspector UI and a separate desktop media-worker process. Do not put RAW support in a WebView or claim camera compatibility from report parsing.
5. Complete cross-device exchange, 12-hour offline/lifecycle and low-storage tests before listing platform downloads.

If the Swift/JNI spike fails on the needed Android baseline, evaluate a Rust/UniFFI core as a deliberate migration with identical fixtures, rather than adding an untested JavaScript wrapper or maintaining divergent merge implementations. Flutter/Kotlin Multiplatform are other UI candidates; a framework choice does not remove native media/SDK work.

Primary references reviewed October 19, 2026: [Swift platform support](https://www.swift.org/platform-support/), [Windows Swift installation](https://www.swift.org/install/windows/), [Android SDK getting started](https://swift.org/documentation/articles/swift-sdk-for-android-getting-started.html), [Apple swift-crypto](https://github.com/apple/swift-crypto). These describe toolchain possibilities, not tested Camera Stats platform support.
