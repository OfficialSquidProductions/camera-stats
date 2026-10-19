# Desktop media development milestone

The newly attached 29-page plan and prior Revision 6 PDF are byte-identical: SHA-256 `1b47725109a77eb21a92f7e85b06e8b31f7062961d7d97c159bb5db0ca40242b`. Sections 6, 9–13, 16–18 and 25 guide this increment. Shot Reporter is the final name. This is development source and a local Mac test app, not the complete desktop release. Published 0.1.1 downloads and TestFlight builds remain unchanged.

## Working workflow

1. Open a report or the separate **The Last Light** demo under Reports. Choose **Desktop footage**. Register a **working-copy folder**, with explicit camera, roll and load identity. Original files stay read-only.
2. Registration stores SHA-256, size, relative locations and metadata. Physical files, clips, report entries, frames and associations have separate identities. Rescanning unchanged content retains identity; changed content gets a new physical/clip version. Relink a moved root only after every registered relative file matches its size/hash.
3. Select a clip and **Decode exact frame**, using a zero-based source index. Stepping decodes presentation-order frames from the beginning, displaying actual PTS and rational time base. Forward/reverse silent review decodes up to 48 frames; focus the frame for J/K/L. This is bounded review, not full real-time audiovisual playback.
4. Visually inspect the slate and explicitly confirm scene/take from that frame. This records manual evidence; no OCR ran. Review suggestions and confirm a report association. Filename and camera/roll/load context explain scores; scene/take is a weak key. Ties are ambiguous. Nothing auto-links, and discrepancies remain visible. Unlinking retains report entries and media.
5. Check clips for a batch. Choose Movie, TV or a saved preset, then edit declarative tokens/padding. Preflight into a separate destination outside source roots. Missing values, unsupported groups, stale report associations and Windows/case/Unicode collisions block the entire batch. No fallback or suffix is invented. Preflight freezes preset ID/version/rules, resolved values, source hashes, report revision/fingerprint and frame evidence in a journal; it creates no output directory. Earlier preset versions remain stored.
6. Review original → proposed names and **Create verified copies**. A uniquely owned batch directory receives new staging files. Every output must match full-source SHA-256/size, registered metadata and the approved frame's decoded PNG hash. A JSON original-to-current relink manifest is saved beside copies. Embedded metadata is not rewritten. These checks do not authorize reusing a camera card.
7. Reopen and **Resume journal** after interruption. Existing outputs require correct content and proof of ownership; foreign/replaced files are never overwritten. Incomplete stages are retained and a fresh stage is used. **Undo · retain recovery copies** checks the whole batch first, then moves owned unchanged outputs into `.undone/` within that batch. No originals or recovery copies are deleted. Stages usually share storage through hard links; space-reclamation tooling remains unfinished.

## Architecture and preservation

SwiftUI remains Mac/iPhone/iPad; Kotlin/Compose remains Windows/Android. The desktop-only `clients/media-worker` module is shared by both desktops and excluded from Android. Windows uses a background executor; Mac invokes a bundled JVM through bounded JSON pipes, one request per process. Local FFmpeg/ffprobe children use explicit paths, file/pipe protocols, disabled external MOV references, output limits and timeouts. No shell, service, network/model fetch or automatic SDK installation is involved.

Media state lives in `desktop-media/<SHA256(reportID)>/media.sqlite3` under the existing data root. Application identity/schema are checked before database changes; unknown/newer files are rejected. A process lock serializes mutations, with SQLite WAL/FULL transactions. Mac folder grants use security-scoped bookmarks under a separate preference key. Existing `reports.sqlite3`, stable app/report IDs, paths and `.camreport` schema 1 remain intact. Media records/manifests are not yet included in report exchange.

Publication uses a no-replace hard link from a verified stage, requiring filesystem support. APFS is tested locally; NTFS is the Windows runner target. FAT/exFAT, network volumes and unusual filesystem semantics are **not certified/supported** here; failures retain the stage/journal. A crash between destination-directory creation and ownership-marker writing requires manual review: the worker refuses to assume ownership of an unmarked directory.

## Local builds and tests

Use JDK 21 and the pinned Gradle wrapper. Set explicit local decoder paths; no decoder is downloaded by the app. See [synthetic fixture provenance](../fixtures/media/README.md). Windows uses `gradlew.bat` and `.exe` paths, or selects a bin folder with **Local decoder tools**.

```sh
export JAVA_HOME=/path/to/jdk-21
export SHOT_REPORTER_FFMPEG=/path/to/ffmpeg
export SHOT_REPORTER_FFPROBE=/path/to/ffprobe
cd clients
./gradlew -PskipAndroid=true :core:test :jdbc:test :media-worker:test :desktop:test
./gradlew -PskipAndroid=true :desktop:run
```

On Mac, build a **new local development app** with the worker and slim JVM. The helper never signs, installs, publishes or uploads, and refuses an existing output. Production signing/sandbox validation is separate.

```sh
python3 tools/build_desktop_media_dev.py \
  --java-home /path/to/jdk-21 \
  --ffmpeg-bin /path/to/reviewed-ffmpeg/bin \
  --ffmpeg-source /path/to/ffmpeg-9.0.2 \
  --output '/path/to/new-development/Shot Reporter.app'
```

Use `--worker-only` with a new directory to build just the bridge worker; `--gradle /path/to/gradle` selects an existing installation. Native integration tests need these variables, otherwise their two optional cases explicitly skip:

```sh
SHOT_REPORTER_WORKER_HOME=/path/to/development-worker \
SHOT_REPORTER_TEST_MEDIA="$PWD/fixtures/media/numbered-24.mp4" \
swift test --package-path app
```

Optional `SHOT_REPORTER_SCREENSHOTS=/path/to/output` captures only the native test process's own light/dark windows. Tests create synthetic roots; never point them at a personal database or camera card.

## Verification

- 19 media engine tests: synthetic supported codecs; exact frame/PTS and known red/blue identity; nonzero source timestamps without rebasing; sequences; manual approval/ambiguity and explicit scene/take/load discrepancies; copy/reopen/undo; source/report/output changes; immutable presets; batch collisions; foreign destination-file races; RAW/sidecar blocking; relink/symlink checks; unknown/newer database preservation; retained interrupted stages; injected failures and actual abrupt JVM termination during publication/undo.
- 26 Swift tests pass: original 24 report/archive/SQLite/legacy-data cases plus native bridge/layout tests. The bridge completes review → association → frozen preview → verified copy → reopen → undo → cancellation, checking source/report bytes. Mac light/dark workspace captures were visually inspected.
- Shared report/persistence and Compose tests pass on Mac. The media UI test decodes frame 7, confirms a slate and requires an explicit association. The Windows runner runs the same tests with hash-pinned CI-only tools; inspect its run before claiming Windows runtime verification.
- Separate Mac Debug development app and generic iPhone/iPad Simulator builds succeed. No mobile worker dependency was added. This is build/integration evidence, not physical camera or production-package certification.

## Decoder provenance

Mac tools were built with Apple clang from [official FFmpeg 9.0.2 source](https://ffmpeg.org/download.html), archive SHA-256 `8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e`. HTTPS/hash were checked; PGP was not verified. The configuration reports LGPL 2.1-or-later and has no external GPL/nonfree libraries:

```text
--disable-everything --disable-autodetect --disable-network --disable-doc --disable-debug
--enable-ffmpeg --enable-ffprobe --enable-protocol=file,pipe
--enable-demuxer=mov,image2,matroska
--enable-decoder=h264,mpeg4,mjpeg,png,rawvideo,ffv1,prores,aac,pcm_s16le
--enable-parser=h264,mpeg4video --enable-encoder=png,rawvideo,mpeg4,ffv1
--enable-muxer=image2,image2pipe,matroska,mp4,mov,rawvideo
--enable-filter=select,showinfo,scale,format,crop,transpose --enable-swscale --enable-zlib
```

Windows CI temporarily downloads the [BtbN LGPL build](https://github.com/BtbN/FFmpeg-Builds/releases/tag/autobuild-2026-10-19-13-06), `n9.0.2-28-g669f5ab083`, pinned SHA-256 `24c4f2537f6804cd0223fd4da0212e65dabf7b84ae43c1095a0ca8fad9ec4606`. It is never packaged/uploaded. The development workflow has read-only repository permissions and no release/artifact-upload step.

[FFmpeg licensing](https://ffmpeg.org/legal.html) depends on configuration. Release gates include corresponding-source distribution, notices, exact binary/dependency review, Mac worker signing/sandbox/bookmark inheritance/runtime entitlements and Windows distribution checks. The local development helper does not establish these gates.

## Remaining acceptance work

| Area | Current boundary / evidence required |
| --- | --- |
| Codec profile | Synthetic H.264 MOV/MP4 and MPEG-4 Part 2 MP4 pass. MJPEG/ProRes are decoder candidates, not certified profiles. Real camera metadata, VFR, rotation, drop-frame timecode, long/damaged media remain unvalidated. |
| RED / BRAW | Registration only. Native SDK adapters, spans/sidecars/proxy groups and redistribution review remain. [Blackmagic publishes cross-platform SDK downloads](https://www.blackmagicdesign.com/developer/products/braw/sdk-and-software); [RED has a developer portal](https://www.reddigitalcinema.com/developers) and [sample files](https://www.reddigitalcinema.com/sample-r3d-files). No SDK denial, NDA or fee has been established. Actual terms and complete camera-card fixtures need review. |
| Slate OCR | Manual evidence only. Detection, crop/rotation, OCR/confidence, corrections, local models and multi-slate/range UI remain. |
| Multi-take footage | Backend retains distinct associations/optional ranges. UI confirms whole clips; multiple associations block naming pending a user-selected policy. No segmentation is inferred. |
| Editorial | JSON relink manifest only; no named-NLE relink/conform, XML/AAF/EDL/OTIO certification or timecode reinterpretation. |
| Scale/durability | Full hashing and decode-from-start prioritize evidence. 20,000 clips, 12-hour offline days, real low storage/physical power loss remain acceptance tests. |
| Releases | No new release, TestFlight upload, identity, website change or installed-owner-app replacement in this increment. |
