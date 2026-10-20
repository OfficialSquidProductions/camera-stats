# Synthetic desktop review fixtures

These clips were generated from repository code, under the same MIT license. They contain fictional **The Last Light** labels and numbered frames; no camera recordings, vendor samples or private production data. All have 24 frames at 12 fps, 640 × 360 pixels, red background for frames 0–11 and blue for 12–23, with scene 3A / take 2 / camera A. They do not establish camera compatibility.

| File | Encoding / provenance | SHA-256 |
| --- | --- | --- |
| `numbered-24.mp4` | MPEG-4 Part 2 with B frames; `tools/GenerateMediaFixtures.java`, JDK 21, local FFmpeg 9.0.2 | `9ac35b634fb5d25e3eaee0a52f6d11db60ccb7ed3601d45d39baf2a2c2a2bc34` |
| `h264-numbered-24.mp4` | H.264 High, frame reordering allowed; `tools/generate_h264_fixture.swift`, macOS AVFoundation | `99d0134e6f01863cfaa720c17bb9c5e373e9c9f5febaae062c3c3bee3f0386a6` |
| `h264-numbered-24.mov` | H.264 in MOV; same native generator | `bd9b78f259ecde74c2696960566fa2e86ac0ac4941778e94444b75e23de220dc` |

The MPEG-4 time base is 1/12288 (frame 7 PTS 7168); H.264 files use 1/600. Tests verify actual rational timestamps, presentation order, decoded visual identity and source/copy bytes. Re-encoding can differ with fonts or encoder versions; retain these committed files for fixed tests. Generators refuse existing outputs. Reproduce to **new** locations:

```sh
java tools/GenerateMediaFixtures.java /path/to/ffmpeg /tmp/new-numbered-24.mp4
swift tools/generate_h264_fixture.swift /tmp/new-h264-numbered-24.mp4
swift tools/generate_h264_fixture.swift /tmp/new-h264-numbered-24.mov
```
