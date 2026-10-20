# Mac desktop packaging

The desktop `0.2.0-alpha.1` candidate uses Apple version `0.2.0`, numeric build `3`, stable application ID `org.squidproductions.camerastats`, and existing Apple team `FZ298DXLS3`. This profile supports **Apple Silicon, macOS 14 or later**. The retained reporting-only release provides the previous Intel/universal download; do not label this worker bundle universal.

## Prepare without signing or account access

`tools/prepare_macos_desktop_release.py` operates offline against supplied local inputs. It creates a fresh candidate outside the source checkout, runs the JVM worker build, and either builds the native Release app or consumes a supplied unsigned Release app. It never installs, signs, notarizes, uploads, writes feeds, reads Keychain values, or creates credentials.

Required inputs are the reviewed Temurin 21.0.12.1+1 Mac aarch64 JDK, Gradle, and these complete corresponding-source archives:

- `ffmpeg-9.0.2.tar.xz`, SHA-256 `8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e`.
- `OpenJDK21U-jdk-sources_21.0.12.1_1.tar.gz`, SHA-256 `573057d03584ae793fb7ec9a14c76d826d9187a53efeefd99da47403a5308234`.

The FFmpeg archive is extracted into a fresh temporary build and compiled with an explicit local Xcode compiler/SDK and macOS-14 target flags. Network protocols, automatic dependency detection, GPL and nonfree components are disabled. The profile includes ordinary review decoders and PPM/TIFF conversion needed for separately supplied RAW tools. No vendor RAW SDK is linked into FFmpeg.

This rebuild matters: the earlier development-only FFmpeg binary had a macOS-26 minimum and must not enter this release. The packager checks every bundled Mach-O for arm64 support, a minimum OS no newer than 14, and absent absolute dependencies outside macOS system libraries. Universal-object headers are distinguished from actual library references.

```sh
python3 tools/build_apple.py --distribution direct
python3 tools/prepare_macos_desktop_release.py \
  --java-home /local/temurin-21/Contents/Home \
  --ffmpeg-archive /local/ffmpeg-9.0.2.tar.xz \
  --jdk-source-archive /local/OpenJDK21U-jdk-sources_21.0.12.1_1.tar.gz \
  --gradle /local/gradle-8.13/bin/gradle \
  --output /private/new-release-candidate
```

The default requires a clean source checkout. `--allow-dirty` creates a review-only candidate whose metadata forbids public release until a clean rebuild. `--app-input` can consume a matching unsigned Release app; its version and bundle ID are checked. A final public candidate must come from the exact tested clean commit.

Supplying `--public-update-key` stamps the public Ed25519 key and fixed reviewed appcast location. Omitting it removes both feed and key, disabling direct updates. Never pass a private key. Publication permission does not remove the real signed upgrade/relaunch acceptance gate: leave the feed unpublished and ship a manual download if that gate has not passed.

## Bundled code and licenses

`Contents/Resources/DesktopMedia` contains the worker JARs, a jlink runtime, separate FFmpeg/ffprobe executables, and the arm64 SQLite JNI library at `native/libsqlitejdbc.dylib`. The Java launch must explicitly select this native directory. Embedded Mac native copies are omitted from the SQLite JAR while its classes, notices and license resources remain; the separate JNI library is signed with the application. This avoids relying on an extracted ad-hoc Mac library in a temporary directory.

The runtime's original `legal/` tree and original JAR notices remain. The worker's `legal/` directory includes Shot Reporter MIT, dependency notices, Sparkle's license, FFmpeg LGPL-2.1, source-delivery information and the SQLite layout notice. Source assets and the FFmpeg build recipe are staged beside the app in `source-assets/`.

**Publish all corresponding-source assets with the binary in the same GitHub release.** Preserve their checksums and actual download availability. Upstream links alone do not replace this release's source delivery. Keep the original runtime license notices; Shot Reporter's MIT license does not relicense JDK, FFmpeg, Sparkle or other dependencies. The separate FFmpeg programs can be rebuilt/replaced from the accompanying source and recipe; production update signature verification remains enforced.

No REDline executable, RED/BRAW SDK/runtime, camera clip, sidecar, downloaded sample, or BRAW helper binary is bundled. The public original BRAW helper source/build instructions remain separate. Users obtain and authorize vendor tools/runtimes themselves. Do not convert local SDK evaluation consent into redistribution permission.

## Coordinated signing and notarization

Preparation emits `MAC_SIGNING_PLAN.json` and explicit entitlement profiles. `tools/sign_macos_candidate.py --candidate /private/candidate --identity EXISTING_CERTIFICATE_SHA1` performs a separately authorized existing-identity operation. It signs native objects and nested bundles from inside out with hardened runtime and timestamps, then the outer app. It never uses `--deep` to sign. Verification may use `--deep --strict`.

The sandboxed host is the default and retains the existing selected-file entitlement and Sparkle IPC exceptions. Java uses the sandbox inheritance profile plus JIT permission; it does not receive a blanket library-validation exception. Other existing helper entitlements are preserved as needed while debug `get-task-allow` is removed. Signing failures or Keychain authorization prompts must be handled explicitly; do not change Keychain ACLs or weaken update/signature validation to work around them.

`--unsandboxed-host` only prepares an explicitly selected direct-distribution alternative. It is not the default and requires separate compatibility acceptance: old signed downloads are sandboxed, and sandboxed and ordinary report roots can both exist. Never silently select, merge, migrate or overwrite them. TestFlight/App Store builds remain sandboxed and contain no Sparkle framework.

After successful native acceptance, the coordinator uses the established Xcode Developer-ID notarization workflow and existing authenticated account. No named `notarytool` profile is assumed. Notarize, staple and validate the complete final bundle, then create the final archive and checksums; changes after signing/notarization require repeating the affected steps. Keep the owner-installed app untouched during QA.

## Required acceptance evidence

Verify the actual signed sandbox host launching the staged JRE, signed SQLite library, FFmpeg and explicitly selected external tools. Test genuine RAW decoding with its sidecar/runtime, ordinary video, source preservation and saved report/media-journal restoration. A disposable uniquely identified app with its own sandbox container can check the native worker chain without touching owner reports; that does not replace final UI/file-picker/security-scoped bookmark acceptance.

Before publishing a download, verify its exact signed/notarized/stapled bytes, arm64/macOS minimum, dependency and source assets, clean-source provenance and owner-data preservation. Before enabling an update feed, additionally test a genuinely signed older-to-newer install/relaunch and failure recovery in isolated storage. Directory-swap fixtures, ephemeral-key tests and reporting-only prior upgrade evidence do not certify that new native update path.
