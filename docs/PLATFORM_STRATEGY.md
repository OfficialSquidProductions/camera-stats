# Platform strategy

The Apple field alpha uses native SwiftUI with a UI-independent Swift model, stored-ZIP exchange, conservative merges and transactional SQLite persistence. Android and Windows now have additive Kotlin/JVM and Compose clients under `clients/`; they use the same schema-1 interchange contract and exact shared demo.

The earlier Swift C ABI/JNI feasibility proposal was superseded for this reporting milestone by the [recorded client architecture decision](CLIENT_ARCHITECTURE.md). It would have required a separate matching Swift Android SDK, NDK and ABI deployment layer beyond this host’s Apple toolchain. The selected implementation keeps one shared engine across Android/Windows and leaves the released Apple engine unchanged. This introduces a second implementation of report rules, so deterministic Swift-generated fixtures and JVM-to-unchanged-Swift reader checks are release gates.

Android uses app-private system SQLite, Storage Access Framework file pickers and native PDF. Windows uses native file dialogs, JDBC SQLite and Java2D/PDFBox on a bundled JVM. Neither host is a WebView or runs a local web server. Android requests no Internet, camera, microphone or broad-storage permission and disables automatic OS backup. Export independent report backups deliberately.

The first Windows target is x64. Android declares minimum API 26 and is tested on API 35 phone/tablet emulators; lower API levels and physical devices remain validation work. Windows arm64, a 12-hour physical-device day, exact school PDF parity, real media/OCR/RAW/NLE processing and vendor SDK redistribution remain separate acceptance gates. No camera/media certification is inferred from report interoperability.
