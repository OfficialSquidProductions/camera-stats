# Validation evidence

October 19, 2026, local development host, Xcode 26.6 (17F113), Apple Silicon.

Core Swift tests currently cover:

- Snapshot defaults and independent per-camera take progression.
- Stored-ZIP .camreport round-trip, multivalued flags, checksum/CRC corruption, truncation, oversized packages and unsafe entry paths.
- Unsupported schema and future-field rejection without altering the local report.
- Stable-ID additions, omitted-row preservation, repeat import idempotence, conservative conflicting-edit rejection.
- Hidden full-report requirements, role changes without data loss, generic CSV quoting and HTML escaping.
- Landscape PDF continuation with a very long note, end-marker preservation, flags, repeated revision headers and landscape page geometry.
- Portable naming rules and missing token behavior.
- A 1,000-entry shooting day archive round-trip.
- Separate native process commit, forced SIGKILL without SQLite cleanup, and recovery of all 24 committed demo entries in a new process (`python3 tools/verify_crash.py`).
- The Last Light demo's eight scene IDs, independent project identity, and continuity references to the brass key.

Code/asset builds succeeded for Mac arm64 Debug, iOS Simulator Debug, and unsigned iOS device Release. After SQLite persistence changes, a universal arm64+x86_64 Mac Release build and unsigned iPhone/iPad archive also succeeded. The final release rebuild and archive include the PDF divider correction and database-full fault hook. The expanded suite ran 23 tests with zero failures, including native Store migration/restart, SQLite WAL reopen/previous snapshot, rollback before commit, stale-save rejection across two connections, simulated SQLITE_FULL rollback, malicious ZIP inputs, and native FileDocument-wrapper file exports. The icon was verified against PNGs extracted from the installed Scenic and Production Desk App.icns: opaque tile bounds [40, 40, 983, 983], colored mark bounds [331, 139, 692, 884], exact match to Scenic and within one antialiasing pixel of Production Desk. The iOS icon is 1024px opaque RGB. Original SP_GREEN.png SHA-256 remains b7888877f83bbbef01fd23e11040a171dc88c4a6d82ffe2110706aca1ab234af.

Initial iPhone 17e / iOS 26.5 UI checks loaded a fictional report, added scene 12B take 4, displayed inherited settings and local save confirmation. An app restart was performed, but persistence after restart was not visually rechecked before app-control testing stopped. The new The Last Light demo and PDF preview compile and have core tests; their complete mobile UI flows remain unverified. iPad mini was booted and app launched, but its layout was not inspected. Mac app-control returned the native sidebar/form accessibility tree; no complete desktop workflow was tested.

After the user reported repeated Camera Stats app-control permission prompts, the app-control JavaScript session was reset successfully (tool result: “js kernel reset”). No app-control call remained pending. The task-owned Mac Camera Stats process and Camera Stats in both test simulators were stopped; data was preserved. No OS privacy/security setting was changed and the app-control route was not retried. Exact cause is unverified. This is a reset of the automation session, not a claim that the entire chat/application session restarted.

The sanitized source allowlist was scanned across 44 files with no credential-pattern hits. It contains no bundled commercial fonts, vendor decoder SDKs or camera footage. This scan is a limited pattern/allowlist check, not a comprehensive security audit.

No 12-hour offline day, low-storage/power-loss test, physical-device test, Windows/Android build, RAW decoding/rename, or NLE conform has been completed. Public MIT source and the hosted status page have been published. No signing, TestFlight submission, verified app download, or custom-domain activation is claimed.
