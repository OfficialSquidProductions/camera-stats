# 0.1.0 owner-test field alpha

Validated on 2026-10-19 with Xcode 26.6 / Swift 6.3.3.

- 23 XCTest cases passed: report snapshots, numbering, archive schema/CRC/SHA-256 validation, malformed packages, conservative merges, PDF/CSV/HTML, transactional SQLite recovery, failed saves and stale writers.
- Separate native subprocess committed the demo, received SIGKILL without closing SQLite, and reopened all three completed takes successfully.
- iPhone 17e / iOS 26.5 Simulator: opened the shared demo; previewed and saved its PDF through Files; saved a `.camreport`; imported the actual saved file; verified its integrity preview and opened a separate editable report. The saved archive contained three 3A takes and circle take 2.
- iPad mini / iPadOS 26.5 Simulator: inspected portrait layout, the shared circled take 2 and editable report fields; added take 4 with inherited settings; terminated and relaunched the app; verified take 4 remained.
- Canonical demo script SHA-256: `84b4bea90b72a1d3d09be8925b07cdf696e9bc579386ed21c3255bb305336f87`. Eight scenes and 16 planned shots match Scenic and Production Desk; completed sample takes are 3A/1–3, circle 2. Existing reports are never reset by the demo button.
- Suite icon comparison verified the rounded white Mac tile and green mark geometry against Scenic and Production Desk. The original supplied brand asset remains unchanged.
- Signed iPhone/iPad and universal arm64/x86_64 Mac archives uploaded to App Store Connect. Both 0.1.0 (1) builds processed successfully and are marked Testing in the manual Owner Testing internal group, with one owner tester. This is not a public App Store submission.
- Direct Mac artifact: Developer ID signature, hardened runtime, notarization, stapled ticket and Gatekeeper assessment verified.

## Remaining validation

Physical iPhone/iPad use and full interactive Mac document workflows need owner testing. Android and Windows apps remain unimplemented. Camera connection, media ingestion, slate OCR, exact-frame footage browsing, physical RAW renaming and certified NLE adapters are outside this field-alpha milestone. No camera or NLE hardware support is claimed.

The first Mac TestFlight upload warned that debug symbols were missing. Upload succeeded, but native Mac crash symbolication is limited in build 1; future archives must include dSYMs.
