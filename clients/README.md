# Camera Stats — Android / Windows field clients

Development clients. **No new platform is advertised as available until its recorded build and runtime checks pass.** Existing Apple releases remain separate and unchanged.

Use JDK 21 and the checksum-pinned wrapper. For core/desktop development on a machine without Android SDK:

```sh
./gradlew -PskipAndroid=true :core:test :jdbc:test :desktop:test
./gradlew -PskipAndroid=true :desktop:run
```

For Android, use the SDK 35 platform/build tools already installed under an Android SDK whose terms the owner has accepted. This project never accepts SDK terms automatically:

```sh
./gradlew :android:assembleDebug :android:connectedDebugAndroidTest
```

Run `:desktop:packageDistributionForCurrentOS` on a Windows x64 machine to build its bundled-runtime installer. Windows code signing and production Android signing are not configured. Debug test signing is temporary and is not an owner release identity.

The UI supports offline project/crew and per-take fields, inherited camera defaults, flags, review, separate demo reports, `.camreport` import/export with conflict preview, native PDF and generic CSV/HTML. The desktop does not run a web server; Android declares no network permission. Local SQLite is WAL/FULL, with prior snapshots and stale-writer protection. Deliberately export an independent backup.

See [architecture and acceptance gates](../docs/CLIENT_ARCHITECTURE.md). The full Revision 6 product is not complete; these clients do not implement camera control, footage ingestion, OCR or physical RAW renaming.
