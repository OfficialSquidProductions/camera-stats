# Bug and crash reporting

The Apple app (Mac, iPhone and iPad shared SwiftUI source) offers **Report a Bug** in the workspace header and Today screen. Mac also has Help → Report a Bug. The separate Kotlin Windows/Android clients have not been changed by this Mac implementation task.

Reports are addressed exactly to **alexanderhosier@squidproductions.org**. Users enter a description and reproduction steps, choose whether to include diagnostic categories, and review the complete text before any sharing action. Opening an email draft is not delivery confirmation. The app never sends mail, stores mail credentials, opens a draft automatically, or uploads diagnostics.

The automatic portion includes app version/build, OS version and an allowlist of failure areas, native fatal-signal names or a generic uncaught Objective-C exception marker. It excludes error messages, exception reasons, stack contents, project/script/media contents, hostnames, environment variables and personal file paths. Raw system crash dumps are not read. Common paths, email addresses, URLs, credential-shaped text and private-key blocks in user text are redacted before preview, copy or export. Arbitrary identifying prose cannot be recognized reliably; the user must review what they type.

The email action uses the existing default mail client and a percent-encoded `mailto` URL. No handler, failed opening or a URL longer than the conservative 1,800-byte limit yields an explanatory message and lossless **Copy report** / **Export report as text** alternatives. The preview is selectable. Each user field is limited to 4,000 characters. No real email is needed for tests.

## Local capture and retention

Diagnostics are separate from reports, in the app support folder's `Diagnostics` directory. Files are private to the app's sandbox. The structured journal holds at most 20 incidents in a maximum 32 KiB JSON file; incidents older than 14 days are removed on launch, journal access and an hourly in-app refresh. Corrupt or oversized journals are discarded rather than attached. Users can dismiss the retained incidents explicitly.

A small C handler preopens a two-byte marker file. Fatal handlers only write a format byte and signal number, then restore native fatal termination; they do not allocate, walk stacks, open files or collect application data. SIGABRT, SIGSEGV, SIGBUS, SIGILL, SIGFPE and SIGTRAP are recognized. An uncaught Objective-C exception writes a generic marker without inspecting its name/reason. Markers are consumed on next launch, and a crash report is offered until dismissed or expired.

On Mac, a session marker is removed only by `applicationWillTerminate`, after the existing updater/save termination gate permits quitting. An unclosed session without a fatal marker is labeled **interrupted / crash not confirmed**, since force quit, power loss and OS termination cannot be distinguished. iOS does not infer crashes from missing clean-shutdown callbacks because normal background eviction often has no callback. SIGKILL, power loss and OS resource termination cannot produce an in-process fatal marker. Worker-process failures are captured as sanitized media-operation failures, not falsely labeled native app crashes.

## Local validation and release status

Run `swift test --package-path app` with `SHOT_REPORTER_TEST_PROFILE` set to an absolute disposable directory whose final component begins `ShotReporterTest-`. The explicit profile isolates the Store, preferences and diagnostics used by native model tests. A sandboxed GUI profile must be inside that app's container or explicitly user-selected; arbitrary external paths are not granted access by this option.

Tests cover a real fatal child process and next-launch recovery, clean/interrupted/iOS termination classification, bounded size/count/age, malformed storage, dismissal/restart, unknown-domain/error-text exclusion, sensitive-text filtering and exact recipient/query encoding. The mail opener is never invoked by automated tests. A separate locally built Mac QA app also passed a real SIGABRT/relaunch recovery test in a guarded blank sandbox; the owner's report fingerprints remained unchanged.

Mac and iOS Simulator compilation passed. Native UI inspection/copy/export and mail-client interaction remain manual acceptance items: app-control access to the QA build entered a permission loop and was aborted by the user. That operation was not retried after the task restart. No real email was sent. No release was published and no update feed or website was promoted for this feature. Existing RAW picker/reopen and genuine build 5→6 updater acceptance gates remain pending.
