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
