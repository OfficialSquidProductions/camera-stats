# REDline adapter: local Mac validation profile

This adapter calls the user's separately installed official REDline. It does not bundle REDline, vendor SDKs, vendor sample source or test footage. The tested profile is an actual RED V-RAPTOR [X] broadcast R3D clip with two numbered spans, 3840 × 2160, nominal 24000/1001 playback FPS and 3,494 frames. Validation ran on macOS with REDline 65.0.20 / underlying R3D SDK 9.1.1. Other camera generations, HDRx, new R3D NE, grading fidelity and Windows native decoding remain unvalidated.

## Metadata and image boundary

The metadata command uses `--printMeta 3 --useMeta --noAudio`. In the installed REDline version, a successful metadata-only query returns exit status **1**, while an actual successful render returns **0**. `--noRender` suppresses metadata before it is printed and must not be used here. The adapter accepts status 1 only for metadata and still requires a structurally valid CSV with valid geometry, playback rate and frame count. Render failures remain errors.

Vendor CSV contains duplicate noncritical display labels (for example Lens/Aperture). Each value is preserved with an occurrence suffix, while ambiguous critical fields are rejected. Camera Record FPS and clip playback FPS remain separate. The observed playback value was a float approximation of 24000/1001; the adapter labels nominal-rational normalization and retains the original decimal. Absolute and edge timecode, UUID, native leaf names, camera and recording settings remain available as separate metadata tags. Reopening the same complete clip in a different directory changed only `File Path` in this tested CSV profile; that field is location data, not native identity.

Review uses zero-based source presentation-frame indices, with clip-relative time at playback FPS. This is **not native stream PTS**. The single-frame command uses `--start N --frameCount 1`. Short sequences are bounded to eight frames, renumbered from zero and checked for a complete consecutive TIFF mapping. Both output path options target a fresh private cache folder. No source trim, source rename or RMD write operation is invoked.

Frames are CPU-decoded at half-high quality, then downscaled to a 960-pixel PNG review proxy using an explicitly configured local TIFF-enabled FFmpeg. The preview uses IPP2/BT.709 settings, embedded defaults and no frame guides. It is not a mastering or NLE appearance guarantee. External RMD appearance is not currently applied.

## Actual test evidence

The official broadcast sample URL is listed in the user-downloaded RED SDK Readme. It was downloaded privately for internal validation; ZIP CRCs and SHA-256 of both extracted spans were verified. No vendor files are included in this repository.

Actual vendor-tool checks passed for first frame 0, middle frame 1747, final frame 3493, and a contiguous sequence at 3070/3071/3072 across the transition into the second span. A single frame 3071, the corresponding sequence frame and the same frame reopened from a relocated complete copy produced identical PNG hashes. Both original span hashes remained unchanged and no source sidecars were created.

These checks validate the command path and observed temporal mapping for that sample. They do not establish compatibility with every RED format or damaged/incomplete media. Public distribution and native Windows runtime validation remain separate gates.

## Missing trailing spans are not automatically detectable

The full sample reported 3,494 frames and two file segments. A deliberately incomplete private copy containing only `_001.R3D` reported 3,071 frames and one segment with the **same native UUID**. REDline treats the available prefix as a shorter clip. Consecutive filenames, native UUID and reported File Segments therefore cannot prove that a trailing span was never omitted before import.

Require a complete-card copy or an independently trusted source manifest/operator confirmation when completeness matters. Once a complete group is registered and fingerprinted, removal/replacement of a tracked member must invalidate copy preflight and recovery checks. Preserve every registered span and sidecar under its original leaf name; name only the outer wrapper/copy folder. Do not claim automatic detection of every missing trailing span.

## License and remaining acceptance

The RED SDK license has separate source/header/document confidentiality and runtime-distribution restrictions. The SDK is kept outside this MIT repository; no native SDK-facing helper or binary is published. Any future bundled SDK adapter needs a reviewed licensing and distribution approach before release. No new SDK or sample agreement was accepted by this work.

The [official RED sample page](https://www.reddigitalcinema.com/sample-r3d-files) limits samples to internal testing. The [KOMODO-X Dancer-2 download](https://www.reddigitalcinema.com/download/sample-r3d-file-komodo-x-6k-s35-dancer-2) additionally presented an explicit sample-license checkbox in the authenticated browser; it was left unchecked. No test footage or derived previews may be added to Git or public build artifacts.

The opt-in Gradle tests use `SHOT_REPORTER_REDLINE`, `SHOT_REPORTER_FFMPEG` (TIFF-enabled) and `SHOT_REPORTER_R3D_SAMPLE`. Constructed CSV tests are explicitly labeled parser fixtures. Add independent real-camera clips, off-speed and NTSC samples, RMD/LUT/audio sidecars, external volumes, and the actual Windows installed decoder before widening support claims.
