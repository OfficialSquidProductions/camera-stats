import Foundation
import Observation
import CameraStatsCore

@MainActor @Observable final class Store {
    private(set) var report = Report()
    private(set) var saved: Date?
    var error: String? {didSet {if error != nil {DiagnosticsCenter.shared.capture(.reports)}}}
    var slot = UUID().uuidString
    var documents: [(String,String)] = []
    var blocked = false
    var pendingImport = false
    var lastExportedRevision: Int? { preferences.object(forKey:"exported-"+slot) as? Int }
    let root: URL
    let device: String
    private let preferences:UserDefaults
    private var repository:Repository?
    private var fingerprint:String?
    init(root:URL? = nil,preferences:UserDefaults = .standard) {
        self.preferences = preferences; device = ProcessInfo.processInfo.hostName
        self.root = root ?? FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask)[0].appendingPathComponent("org.squidproductions.camerastats",isDirectory:true)
        do {
            let repo = try Repository(url:self.root.appendingPathComponent("reports.sqlite3")); repository = repo
            // One-time, idempotent migration. Legacy snapshots stay untouched as recovery files.
            let known = Set(try repo.list().map(\.slot))
            for url in try FileManager.default.contentsOfDirectory(at:self.root,includingPropertiesForKeys:nil) where url.pathExtension == "camreport" && !url.lastPathComponent.contains("previous") {
                let id=url.deletingPathExtension().lastPathComponent
                if !known.contains(id) {let legacy=try Exchange.read(Data(contentsOf:url)); _=try repo.save(slot:id,report:legacy.report,device:device,expectedFingerprint:nil)}
            }
            if let legacyActive = preferences.string(forKey:"activeReport"), (try repo.list()).contains(where:{$0.slot == legacyActive}) { _=try repo.select(legacyActive); preferences.removeObject(forKey:"activeReport") }
            if let selected = try repo.active() { let loaded=try repo.load(selected); slot=selected; report=loaded.report; fingerprint=loaded.fingerprint; saved=Date() }
            refresh()
        } catch { self.error = "Local report could not be read. Its bytes were preserved. Export/recover it before creating a new report. \(error.localizedDescription)"; blocked = true }
    }
    func refresh() {
        documents = ((try? repository?.list()) ?? []).map{($0.slot,$0.title + " · r\($0.revision)")}
    }
    func persist(_ next: Report) throws {
        guard let repository else {throw ReportError.invalid("Local database is unavailable; existing files are preserved")}
        let accepted=try repository.save(slot:slot,report:next,device:device,expectedFingerprint:fingerprint)
        report = next; fingerprint=accepted; saved = Date()
        documents.removeAll{$0.0 == slot}; documents.append((slot,next.values["title",default:"Untitled"] + " · r\(next.revision)")); documents.sort{$0.1 < $1.1}
    }
    func change(_ summary: String = "Edited report", _ action: (inout Report)->Void) {
        guard !blocked else { error = "Recovery is required before edits can be accepted."; return }
        var next = report; action(&next); if next.revision == report.revision { next.record(summary,device:device) }
        do { try persist(next) } catch { self.error = "Save failed; this change was not accepted. \(error.localizedDescription)" }
    }
    func open(_ r: Report) {
        let prior = slot, priorFingerprint=fingerprint; slot = UUID().uuidString; fingerprint=nil
        do { try persist(r); blocked = false } catch { slot = prior; fingerprint=priorFingerprint; self.error = error.localizedDescription }
    }
    func select(_ id: String) {
        do { guard let repository else {throw ReportError.invalid("Local database unavailable")}; let loaded=try repository.select(id); slot=id; report=loaded.report; fingerprint=loaded.fingerprint; blocked=false; saved=Date() } catch { self.error = error.localizedDescription }
    }
    func restore() {
        do {
            guard let repository else {throw ReportError.invalid("Local database unavailable")}
            if let previous = try? repository.load(slot,previous:true) {open(previous.report)}
            else {let legacy=try Exchange.read(Data(contentsOf:root.appendingPathComponent(slot+".previous.camreport"))); open(legacy.report)}
        } catch { self.error = "No readable previous snapshot: \(error.localizedDescription)" }
    }
}
