# Settings and naming presets

The macOS sidebar has a Settings button immediately above Local Desktop Alpha. Command-comma opens Settings; Escape closes it. Saves and Footage link to the same settings. Native pickers, switches and text fields expose their labels to accessibility.

Movie hides season and episode controls in Settings and Today and omits those components from generated names, including edited Movie templates. TV / Episodic includes both by default. Component choices and advanced report/copy templates are retained separately for Movie, TV, Custom and each historical saved preset version. Switching does not clear production metadata, alter source footage or modify prepared copy jobs.

Settings save with the report through the existing transactional repository and previous snapshot. They survive selecting another report, restarting, exchanging a report and restoring a snapshot. Old reports infer their initial preset from their existing naming template; custom templates stay intact. Opening an old report alone does not add settings. Imports detect settings conflicts rather than silently discarding choices.

The checkboxes filter saved source templates. An underscore-separated component whose tokens are all disabled is omitted; combined season/episode components also support selecting only one. Turning a component back on restores its original formatting. Newly enabled components are appended only if absent. Advanced templates remain available for the existing worker tokens and numeric padding. Original and asset are footage-only values.

Take previews use the effective report template. Footage preflight receives the effective copy template from the same durable settings; its frozen names are the actual copy destinations. Missing enabled footage values and collisions block preflight. Report export names use project-level template components plus the revision for CAMREPORT, CSV, HTML and PDF. Incomplete or invalid report naming values use the explicit `Shot-Reporter-rN` fallback shown in Settings, so drafts and recovery backups remain exportable. PDF freezes its name with its contents.

The optional `namingSettings` report field is understood by this version. Older strict schema-1 clients reject reports containing it rather than silently losing settings; keep a backup when exchanging with older apps. The unchanged Java worker receives a schema-1 projection without UI settings and the fully resolved effective preset. Windows/Android settings UI is outside this change.

## Local preview and validation

Use an isolated checkout based on `962e3ab` (the local bug/crash reporter). No release, installed-app replacement, site change or feed activation is part of this feature.

`tools/prepare_settings_preview.py` takes a fresh Xcode build and a local worker bundle. It refuses existing outputs/profiles, creates dedicated data, diagnostic, log and backup directories, pins an absolute `ShotReporterTest-*` profile in the preview Info.plist, uses a separate bundle identifier, removes production document associations and disables updates. The embedded profile takes precedence over an environment override, so Finder and direct-executable launches remain isolated. Invalid explicit profiles stop startup before opening the normal library.

The local preview is ad-hoc signed for development. Its worker/runtime/codecs are copied locally, without modifying the historical candidates or installed app. A read-only hash baseline covers owner reports and previous snapshots before and after testing. The preview keeps an independent SQLite backup in its own `Backups` directory.

Relevant acceptance checks are in `NamingSettingsTests`: switching and custom retention, saved-preset independence, Movie omission, empty fields, padding, legacy migration, transactional persistence/reopen, archive roundtrip, conflict detection, previous snapshot and actual worker copy/undo names with unchanged source bytes. Native acceptance uses the isolated preview for sidebar placement, keyboard shortcut/menu arrows/Escape, TV fields, component selection, custom templates and direct quit/relaunch. Full test and build logs live in the checkout's ignored `.verification/logs` directory.

RAW-specific integration tests still require separately authorized vendor fixtures. This change does not satisfy earlier release gates for RAW reopening or a signed updater installation. iOS Simulator compilation checks shared-source compatibility; iOS device and Windows/Android runtime acceptance are not claimed.
