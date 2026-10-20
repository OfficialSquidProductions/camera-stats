# Windows signing and offline update preparation

The owner currently chooses manual Windows downloads. No signing-service enrollment or live Windows update feed is authorized by that choice. The tooling and options below remain preparation for possible future work.

This is preparation guidance, not an enrolled signing service or active feed. The
released alpha 2 Windows package is unsigned and has no bundled update policy.
One manual bootstrap installation is required. A feed cannot add trust to an
already installed unconfigured app. Keep UpgradeCode
`c3774fda-7962-4264-a9bc-c428176d12b4` and the legacy report data directory.

## Signing service decision

[SignPath Foundation](https://signpath.org/terms) offers free code signing for
eligible released, maintained, documented, OSI-licensed projects. Admission includes
reputation review and is discretionary. Its certificate publisher is SignPath
Foundation. MFA, a public code signing policy, named roles and manual signing
approval are required. The project must exclude proprietary vendor SDKs/runtimes.
Upstream open-source binaries may be included in a signed MSI without signing
those upstream binaries under the project's certificate.

[SignPath's GitHub integration](https://docs.signpath.io/trusted-build-systems/github)
requires approved repository access for its GitHub App, a signing submission API
credential and GitHub-hosted build provenance. These are new access/terms decisions
for the owner; no enrolment or grants have been made by this preparation.

[Microsoft Artifact Signing](https://azure.microsoft.com/en-us/products/artifact-signing)
Basic is advertised at US$9.99/month including 5,000 signatures, then $0.005 each.
Confirm the actual offer, taxes and region before purchase. Billing starts on
account creation and is not prorated, including time spent awaiting verification.
[Individual eligibility/setup](https://learn.microsoft.com/en-us/azure/artifact-signing/quickstart)
requires US/Canada residence, Azure/Entra setup, an Individual billing account and
owner-completed legal ID/address/Authenticator verification. Personal verification
documents must be supplied directly to the provider, never committed or sent in chat.

**Azure is incompatible with the current fixed leaf-certificate pin.** Its
certificates renew daily with 72-hour validity. Microsoft's
[certificate management guidance](https://learn.microsoft.com/en-us/azure/artifact-signing/concept-certificate-management)
requires the profile-specific custom EKU for durable identity. Supporting that
service requires a reviewed policy/schema/helper adaptation preserving valid
Authenticode chain verification, exact signed-feed asset identity and pinned
profile identity. The staging tool deliberately refuses Azure profile certificates
instead of silently relaxing the current policy. Ordinary leaf certificate renewal
also requires planning a reviewed trust transition before the old pin becomes unusable.

## Offline staging contract

The tool supports Python 3.9+ and uses streaming SHA-256. OpenSSL 3+ is required
for detached Ed25519 verification.

`tools/stage_windows_update.py` never creates/uses private keys, downloads files,
installs software, writes into the repository or publishes anything. Both phases
require a **native Windows** check of the actual signed/timestamped MSI using the
OS PowerShell/Windows Installer APIs. A JSON claim that a package is signed is
insufficient. This native production path is not certified by the public fixture
unit tests; a real trusted signing identity is still required.

Inputs:

- An independently reviewed public bootstrap policy matching the app's
  `UpdatePolicy` fields, with exact current version/build/MSI version and the
  dedicated trusted certificate SHA-256. No real trust values are supplied here.
- Ed25519 public key as base64 X.509 SubjectPublicKeyInfo (44 bytes). Sparkle uses
  32-byte raw public keys; conversion adds DER prefix `302a300506032b6570032100`
  to the public bytes only. Use of the existing Shot Reporter Mac key for Windows
  feeds must be coordinated before signing. Do not export its private key.
- Public DER signing certificate; reviewed current/bootstrap and target release
  manifests; target MSI; checksums; exact reviewed target source SHA; release notes.
- Target manifest fields `version`, `msiVersion`, `build`, `sourceCommit`,
  `platform: "windows-x64"`, signed `distribution`, `files[MSI filename]`, and
  `windowsSigning` containing `authenticodeStatus: "Valid"`, `timestamped: true`,
  `signerSHA256`, and actual certificate `publisher` subject. Preserve the original
  unsigned CI manifest; produce a separate manifest/checksum list for the final
  signed bytes. Signatures change the MSI checksum.

The native probe holds a read lock while checking signature, timestamp, signer
DER/EKUs, SHA-256, length, MSI product/version, architecture and UpgradeCode.
Checksums/source manifests are operator-supplied reviewed provenance, not an
independent cryptographic build attestation. Both preparation and finalization
recheck native trust and the actual bytes.

Example commands use paths to owner-approved local inputs; they do not create
trust metadata or authorize signing:

```powershell
python tools/stage_windows_update.py prepare `
  --policy C:/review/bootstrap-policy.json `
  --current-release C:/review/bootstrap-WINDOWS-RELEASE.json `
  --release C:/review/target-WINDOWS-RELEASE.json `
  --certificate C:/review/publisher-public.der `
  --checksums C:/review/SHA256SUMS-Windows.txt `
  --msi C:/review/Shot-Reporter-Windows-VERSION.msi `
  --notes C:/review/release-notes.txt --source-sha REVIEWED_40_HEX_SOURCE_SHA `
  --output C:/review/update-stage
```

The exact output `payload.json` must be signed externally with the approved
Ed25519 signing tool. Supply only its base64-encoded raw 64-byte signature in an
ASCII file. The staging tool never invokes signing and never accepts private keys.
Do not sign a reformatted/reparsed payload. Then:

```powershell
python tools/stage_windows_update.py finalize --stage C:/review/update-stage `
  --signature C:/review/payload.signature.txt --output C:/review/signed-feed-stage
```

OpenSSL 3+ verifies the detached signature over the exact payload. Fresh output
directories prevent overwriting previous review evidence. Target SemVer, numeric
build and MSI ProductVersion must each increase; stable rejects prereleases.
Feeds expire within 31 days, so publication must include a renewal plan. Staging
does not activate or copy the policy into an app. The fixed source URL is
`https://raw.githubusercontent.com/OfficialSquidProductions/shot-reporter/main/updates/windows/feed.json`;
that address is a proposed configuration, not a claim of a published feed.

## Acceptance before activation

Build/sign a bootstrap release with its matching public policy and a newer signed
target. On isolated native Windows, validate authenticated feed retrieval,
download/tamper rejection, save refusal, cancel before/after handoff, actual
installer invocation, app exit/relaunch, non-ASCII paths and preservation of reports,
history, preferences, media journals and registered source paths. Exercise retained
logs/backups on failure. Existing unsigned rejection/manual MSI upgrade evidence
does not prove this signed automatic-install path. Do not trust a self-signed test
certificate on owner systems as a substitute for public trust.

No proprietary SDK/media uploads, mobile changes, paid enrolment, service credential
creation or publisher-trust weakening are authorized by these local preparations.
