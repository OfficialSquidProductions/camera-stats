# Blackmagic RAW adapter

Status: a local macOS CPU adapter builds against the separately supplied SDK 6.0 and decodes the SDK's genuine one-frame BRAW sample. Eleven private acceptance checks pass. Additional short multi-frame checks are recorded below; Windows, long-clip, multicard, complete camera-card and release-distribution certification remain open.

Follow-up local validation found an already-installed Blackmagic RAW Speed Test `profile.braw` with four distinguishable frames at 60 fps. Ten additional native helper checks pass. JVM, native Swift and packaged-runtime tests verify sequence mapping, arbitrary seeking/reseeking, forward/reverse review, copy/reopen/undo and source preservation. This establishes the documented short four-frame profile; long-clip, multicard, Windows and camera-card/release certification remain open. No benchmark resource or image is redistributed.

The user-provided Blackmagic RAW 6.0 Mac disk image contains a separate `BlackmagicRawSDK.pkg`, including Mac headers, dispatch source, the API framework, examples, and `Media/sample.braw` with `sample.sidecar`. It also includes Windows API declarations/dispatch source, but its package inventory does not include Windows API binary libraries. A Windows SDK/runtime package is needed to validate Windows execution.

Disk image SHA-256: `38e938a59362f8e49011d73a2187e40f0756b49a1065f39a1cf38ec6b0fabb12`.

Inspection mounted the image read-only and expanded installer metadata into private development storage. It did not run Installer, package scripts, an SDK binary, or a sample application, and did not accept an agreement. After the user explicitly authorized the Developer SDK agreement and bundled-sample local use, the SDK was extracted into private validation storage outside Git. No global SDK installation occurred.

## License gate

The installer resources contain both the player agreement and a separate **Developer License Agreement for the Blackmagic RAW Software Development Kit**. The latter explicitly applies to downloading, installing, copying or using the SDK. The user explicitly authorized local SDK and bundled-sample use on 2026-10-19. That authorization does not approve public redistribution of the SDK, runtime, or sample footage.

The agreement describes evaluation/development use and conditional redistribution of API libraries in object form and modified sample source. It does not give blanket permission to publish the SDK. Its separate restrictions, third-party technology provisions, vendor-mark conditions and termination provisions require release review. Shot Reporter's MIT license does not replace the vendor agreement.

Do not commit or distribute vendor headers, libraries, the installer, license text, SDK examples or sample footage in this source tree. Development sample use must be separately authorized; no decoded sample images should enter public fixtures without permission.

Official references:

- [Blackmagic developer SDK downloads](https://www.blackmagicdesign.com/developer/products/braw/sdk-and-software)
- [Blackmagic RAW SDK manual](https://documents.blackmagicdesign.com/DeveloperManuals/BlackmagicRAW-SDK.pdf)

## Local build and validation

Only the original adapter and local build/check scripts are included here. Vendor SDK files remain outside the repository. The Mac helper loads the local framework through the SDK's original dispatch implementation at the path supplied during compilation; `SHOT_REPORTER_BRAW_LIBRARY` may override that local library directory. No runtime download or installation occurs.

```sh
python3 native/braw/build_local.py --sdk "/private/path/Blackmagic RAW SDK" --output /private/path/shot-reporter-braw
python3 native/braw/test_local.py --helper /private/path/shot-reporter-braw --sample "/private/path/SDK/Media/sample.braw" --output /private/path/new-validation-directory
```

The output executable and test directory must be new paths outside the repository. Use an authorized SDK and sample. Neither script accepts a license, signs a binary, creates a release, or publishes data.

The sandboxed Mac app cannot execute a helper from an arbitrary private development folder, even after choosing it in a file picker. Install the original locally built helper in an Applications location before selecting it. The selector accepts a regular executable or an application bundle containing `Contents/MacOS/ShotReporterBRAW`; RED's application bundle resolves `Contents/MacOS/REDline`. Invalid selections leave the prior setting intact. `--library-dir "/Applications/Your Authorized BRAW Tool.app/Contents/Frameworks"` compiles a default pointing to an already installed, separately authorized runtime matching the SDK version. This option does not install or copy that runtime. The build script and helper reject a different framework version before using its ABI. An installed 5.1 player does not satisfy a 6.0 SDK build. Sandbox and library-validation settings remain unchanged.

`version` emits JSON containing framework version/build and the SDK API's own version result. In this supplied package those are `6.0`, `60000.40.271`, and `0.0` respectively; the API result is retained honestly rather than relabeled.

Validated checks include repeated deterministic full-resolution decode, exact SDK frame index, PPM byte count, out-of-range/malformed indices, output collision protection, symlink refusal, explicit sidecar/no-sidecar rendering state, malformed-sidecar refusal, actual pixel changes when selecting the sidecar LUT, and source/sidecar preservation after cancellation. The SDK image was visually inspected. That bundled clip is **one frame**; temporal checks use the separate already-installed four-frame benchmark described above.

## Local adapter contract

The adapter uses the official SDK to open an explicitly selected local logical clip, report native clip metadata and decode an explicitly indexed presentation frame. The worker supplies a source path and bounded integer frame index through argument arrays; it never invokes a shell. The adapter preserves native frame count, rate/timebase information when available, source timecode and SDK version in structured output.

Frame output is a new PPM file in a worker-owned directory, followed by independent worker validation and hashing. Output must not overwrite a path. Decoding must finish before the worker accepts the frame reference; failures and unsupported SDK capabilities remain visible. SDK sidecar state affects decode identity and must be included in source-group validation. No sidecar write, trim, export or source rename API is needed for this first adapter.

The SDK-provided real clip passes deterministic frame, output protection and cancellation checks. Short temporal acceptance uses the separate local benchmark. Longer camera clips, copied card structures and sidecars are additionally needed for wider physical-media acceptance. The full logical group copy, naming, journal and undo remain owned by the shared media worker rather than the decoder.

The CPU output is full-resolution RGB8 PPM (64 million pixel limit), preserving SDK processing defaults and automatically attached sidecar state. It is a review image, not sensor-raw export. The parent worker must impose a hard process timeout and accept output only after successful process exit; the helper also has a 45-second callback deadline. Some SDK cleanup calls can block after a vendor failure, so the process timeout remains mandatory.

The SDK `GetFrameRate` API supplies a float32. The reported rate is an exact rational representation of that float, with an inverse frame-index timebase. Tags explicitly distinguish this from a source-native rational or packet PTS. Source timecode is reported separately.

On successful jobs the read-job object is released **before** `FlushJobs`; the SDK waits for job destruction. Output uses exclusive creation and never replaces an existing path. Partial output from a killed/failed process must stay unaccepted and be discarded only as an owned worker temporary file.

Windows needs its own authorized runtime plus native compilation/execution tests. SDK/runtime redistribution and signed production packaging remain separate release gates. Do not advertise camera or complete-card support from the single bundled frame.
