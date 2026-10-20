# Reproducing local RAW verification

Use only an authorized SDK/runtime and already available private fixtures. These commands download nothing and do not publish/sign/create update feeds. The local Mac app contains its JRE and FFmpeg; vendor executables remain separate.

## Mac configuration

The Footage **REDline…** control selects the installed REDline executable, normally `/Applications/REDCINE-X Professional/REDCINE-X PRO.app/Contents/MacOS/REDline`. **BRAW…** selects the executable produced by `native/braw/build_local.py` against the separately supplied SDK. The native app saves these selections in its existing preferences. Register the isolated working-copy folder again after changing tools. Without either setting/environment variable, RAW remains unavailable with a visible error; MOV/MP4 reporting/review remains available through the bundled tools.

For this machine's private development app, `START_HERE.txt` and `Launch RAW Test.command` sit beside the built app outside Git. The launcher sets only the two process-local tool variables and executes the exact new app binary, avoiding an older Launch Services instance. It does not change global preferences, signing or security settings. Quit an older instance first. The guide gives the actual private test-folder/helper paths and the demo/report/copy/undo steps. No owner preferences were silently configured during verification.

## Native SDK checks

With an existing authorized local helper, the installed benchmark can be tested directly:

```sh
python3 native/braw/test_local.py \
  --helper /absolute/private/path/shot-reporter-braw \
  --sample '/Applications/Blackmagic RAW/Blackmagic RAW Speed Test.app/Contents/Resources/profile.braw' \
  --output /absolute/private/path/new-profile-checks
```

The output path must be new and outside the repository. The benchmark used here has four distinguishable 4608×2592 frames at 60 fps; the SDK's `Media/sample.braw` instead has one frame with a sidecar. The helper checks retain all source hashes and never modify the original app resources.

## Worker and native UI checks

Configure JDK 21 and the cached Gradle toolchain, plus these local paths:

```sh
export SHOT_REPORTER_FFMPEG=/absolute/path/ffmpeg
export SHOT_REPORTER_FFPROBE=/absolute/path/ffprobe
export SHOT_REPORTER_BRAW_HELPER=/absolute/private/path/shot-reporter-braw
export SHOT_REPORTER_BRAW_SEQUENCE_SAMPLE='/Applications/Blackmagic RAW/Blackmagic RAW Speed Test.app/Contents/Resources/profile.braw'
cd clients
./gradlew -PskipAndroid=true :media-worker:test \
  --tests '*RawIntegrationTests.actualBrawLocalMulti*' --console=plain
```

The JVM check compares all sequence indices/pixel hashes against arbitrary exact seeks, rejects an out-of-range frame, and completes approved final-frame package copy/reopen/undo. It uses an isolated copy, not the installed application directory as a working package. For other real tests, configure `SHOT_REPORTER_BRAW_SAMPLE`, `SHOT_REPORTER_R3D_SAMPLE` and `SHOT_REPORTER_REDLINE` explicitly. The selected RED fixture must include all spans in its complete `.RDC` directory.

For the native UI bridge, build a fresh local worker using `tools/build_desktop_media_dev.py --worker-only`, then from the repository root:

```sh
export SHOT_REPORTER_WORKER_HOME=/absolute/private/path/local-worker
export SHOT_REPORTER_TEST_MEDIA="$PWD/fixtures/media/numbered-24.mp4"
swift test --package-path app --disable-automatic-resolution \
  --filter DesktopMediaTests/testNativeBrawMultiFrameForwardReverseAndReseek
```

Keep the BRAW helper/sequence variables from the worker example. This check exercises the actual Swift bridge with the slim worker runtime, forward/reverse frame order and reseeking. The private packaged-app smoke harness additionally runs the new app bundle's own runtime/tools through sequence, naming/copy, a fresh-process journal reopen and retained undo. Public synthetic tests remain separate from vendor-media acceptance.

## Why native Windows acceptance is still blocked

This Mac has no available Windows VM, mounted Windows image, PowerShell, Wine, QEMU, Parallels/Fusion execution CLI or configured remote Windows host. Existing GitHub Windows runners execute repository source after an external push; the development workflow has no manual dispatch and is fixed to its older branch. The broader field-client workflow also uploads evidence/assets, so it must not be run as a substitute for local verification under the current no-upload/no-push instruction.

The safe next step is an owner-provided disposable Windows VM/PC. Authorize access to that named machine and installation of test app versions in an isolated profile; transfer the reviewed source locally. Install/use only already authorized Windows REDline/BRAW runtimes and private fixtures supplied directly by the owner. The supplied Mac SDK contains Windows declarations, but no Windows BRAW binary runtime. Do not upload vendor SDKs or footage to a public runner.

Run the SDK-free JVM regressions there first, then actual RAW decode/sequence/span/sidecar/copy/recovery checks using local tools. Validate the PowerShell helper's signature/MSI checks, cancellation, modified/unsigned rejection and report/media preservation. A real successful older-to-newer updater test additionally needs two already signed trusted-publisher MSI versions with the same UpgradeCode, increasing ProductVersion/build, and reviewed test policy/feed signatures. Missing publisher/update identities cannot be bypassed. Importing test trust certificates, creating persistent keys, using a signing identity or buying a certificate requires separate approval; no such action happened here. Native tests must cover successful install/relaunch, cancellation/rollback and failed save/active media locks before feed activation.

An alternative SDK-free GitHub test would require explicit permission to push the reviewed source and add/run a Windows-only workflow with artifact/release uploads removed. That could test current platform-independent code and native unsigned-package refusal; it would not establish proprietary RAW support or a trusted signed install path. Live feeds, production signing, publication and TestFlight remain outside this verification scope.
