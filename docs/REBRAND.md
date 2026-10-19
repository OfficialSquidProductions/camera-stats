# Shot Reporter rebrand

Shot Reporter was previously named Camera Stats. Version 0.1.1 (build 2; alpha.3 downloads) changes visible branding and export names while retaining the reporting field alpha scope and the same fictional The Last Light demo script.

The following are compatibility identities, not visible product names. Preserve them when building updates:

- Apple bundle ID and Android application ID: `org.squidproductions.camerastats`.
- Apple App Store Connect app: `6824581046`; existing signing team/identities and owner-only beta group.
- Report format/UTI: `org.squidproductions.camreport`, schema 1, extension `.camreport`.
- Windows installer UpgradeCode: `c3774fda-7962-4264-a9bc-c428176d12b4`.
- Windows reports: `%LOCALAPPDATA%/Squid Productions/Camera Stats`; Apple application support suffix `org.squidproductions.camerastats`; Android's existing private sandbox.
- Existing signing key, package/source module names, database schemas and preference keys.

No report-content migration or automatic title/history rewriting occurs. Existing user documents retain their own values. The suite's original green Squid Productions mark and icon geometry remain unchanged. New PDF/HTML/export headers use Shot Reporter.

The GitHub repository retains its history, releases and assets under `OfficialSquidProductions/shot-reporter`. Historical Camera-Stats-named release files remain available for compatibility; do not recreate the old repository slug. The old website must continue routing visitors after the new domain is verified.

An MSI/EXE or APK download is still manually installed. This rebrand does not introduce an updater, telemetry, media browsing or footage renaming.

## Validation

- Apple: 24 Swift regression tests, including a SQLite database and report exported by the original `fe51b582742d036bb1c4bae09d2df3497777675c` core, reopen through the current application Store with all values, IDs, history, revision and previous snapshot preserved. This fixture is synthetic. SQLite crash recovery also passed.
- iPhone/iPad: actual in-place simulator updates from 0.1.0 (1) to 0.1.1 (2); every current/previous report snapshot hash and active selection matched before/after (two phone reports and one tablet report). The installed display name and resulting layouts were inspected.
- Windows: [native CI](https://github.com/OfficialSquidProductions/shot-reporter/actions/runs/39319984010) ran the previous public 0.1.0 MSI/runtime, seeded a synthetic report in the normal legacy directory, upgraded using the 0.1.1 MSI, and checked complete report equality, history, active document, previous snapshot, preferences, export/import, old-product removal, and packaged/installed runtime behavior. The installer log records the same UpgradeCode and actual removal of the prior product. Twenty JVM tests also passed.
- Android: three native instrumented cases on each API 35 phone/tablet emulator passed; the exact signed release uses the existing certificate and increments versionCode to 2. [Exact signed release verification](https://github.com/OfficialSquidProductions/shot-reporter/actions/runs/39321937709) passed on both phone and tablet: install the previous signed public APK, create the canonical demo, force-stop/reopen it, install the new APK with the same signer, verify the retained report and Shot Reporter header, and inspect landscape. The first phone preparation attempt missed a cold-launch touch before upgrade; its test-only input wait was repaired and both sizes passed with the unchanged APK.
- Mac: universal x86_64/arm64 archives include dSYMs; direct download passed strict signature, Apple notarization, stapler and Gatekeeper checks. Owner TestFlight uses the original app record. The Mac persistence check uses the original-core fixture rather than changing the owner's local reports; interactive owner Mac validation remains useful.
- Website: desktop and 390px phone layouts, generated Worker validation and old-host 308 path/query preservation checks passed before deployment.

All released app implementation, resources and project settings were built at `302067f0b7fd3232594fe358a9209ca746f731ff`. The follow-up compatibility fixture/test and cold-emulator input handling change no released app implementation or resources. No footage workflows or updater behavior were added.
