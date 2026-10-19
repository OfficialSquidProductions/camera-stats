# Revision 6 implementation and acceptance tracker

Source: full 29-page Camera Reporting Product Plan supplied in Library, Revision 6, October 19, 2026. Its references describe proposed behavior, not proof of implementation. Stages and acceptance must remain explicit.

| Plan area | Current implementation | Outstanding acceptance / work |
| --- | --- | --- |
| Local-only runtime | Apple clients have no network services, accounts, telemetry or cloud sync | Verify long offline day on physical devices |
| Platforms | Shared native iPhone/iPad/Mac app and domain package | Implement and test Android phones/tablets and Windows; no divergence accepted as complete |
| Student complete report | All fields mapped in section 3, unlimited entries, missing-field list, self-review, draft PDF | Compare against original school form; test entire phone completion, conditional N/A/Unknown rules, exact landscape visual layout |
| Profiles / detail / positions | Four named profiles; independent detail setting and multiple positions; all fields preserved | Editable custom field schemas/requirements, responsibility filtering/reordering, requirement-change preview/versioning |
| Fast take logging | Per-camera next take, manual string slates, camera defaults, unique load IDs, flags | New-setup inheritance review, real lens/filter packages, typed measurements and rational-rate validation |
| Local durability | SQLite WAL/FULL transactional snapshots, previous snapshot and active selection in the same transaction; stale-save protection; visible errors and saved status | Record-level search index and richer operation journal; 12-hour kill/reopen, low-storage and power-loss evidence |
| Portable saves | Stored ZIP, manifest, stable IDs, revision/device, CRC+SHA256, preview, repeat idempotence, no footage | Scoped handoffs, attachments, profile/preset version records, encryption and cross-platform reader |
| Conflict handling | Conservative merge of independent additions; conflicting versions remain separately openable | Common-base field merge, explicit conflict decisions and provenance, ownership transfer conventions |
| Production edge cases | String slates, midnight-independent shooting-date string, independent camera labels, load ID, deleted-take tombstones | Fully separate performance/recording/logicalclip/file/range/link entities; spans and multiple takes per clip |
| Defaults/history | Per-entry snapshots and change history; changing view/profile does not delete data | Full before/after field provenance, export history and revision recovery UI |
| Report outputs | Landscape multipage PDF preview/export with continued long notes/flags; generic CSV and HTML | Exact supplied form parity; HTML contact sheets/thumbnails, editorial bundles, report/template version freeze records |
| Notes/continuity | Separate camera, DP, continuity and editor text, circling | Independently attributed selects, attachments, wardrobe/prop tags, daily editorial notes, correction packages |
| Naming | TV/Movie/custom declarative string-token preview; missing-value and portable filename checks | Token builder, padding/case/optional policy, versioned presets and import collision decisions, batch collision checks |
| Desktop media | Explicitly unavailable in alpha | Read-only registration, worker queues, original identity, spans, verification evidence, explainable reversible matching |
| Slate search/OCR | Explicitly unavailable in alpha | Exact-frame source mapping, JKL/step/skim, crop/rotate, local models, board detection then OCR, field confidence and human approval |
| RED/BRAW physical rename | No filesystem mutation feature; unavailable | SDK terms/access, real fixtures, group-aware plans, copy/hash/journal/recovery/undo, both desktop OSes and NLE conform certification |
| NLE adapters | CSV clearly generic | Named Resolve/Avid/Premiere/FCP versions, real import and proxy-to-original relink/conform fixtures |
| Scale | Core round-trip test with 1,000-entry day | End-to-end device entry/search latency, progressive indexing, 20,000-clip test on named devices |
| Branding | Original green SP retained, exact suite credit, theme preference and palette assets | Final UI QA in both themes at all sizes; licensed suite display font choice; small-size icon validation |
| Demo | Explicit separate fictional The Last Light report, three takes for 3A, shared 16-shot plan, and full supplied sample script | Temporary testing feature; remove only when owner asks |
| Releases | Local builds/tests and website draft | MIT confirmed, organization/admin verified; final source audit, signed archives, TestFlight owner-only delivery, verified download URLs/domain |

The plan estimates 9–12 months with 4–6 contributors for the complete product. The current owner-test field alpha is a useful milestone, not completion of its RAW or production release gates. No claim of complete Stage 1 acceptance is made while required profile/merge/backup/platform work remains.
