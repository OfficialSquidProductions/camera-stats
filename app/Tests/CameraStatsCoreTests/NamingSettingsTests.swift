import XCTest
import SwiftUI
import AppKit
@testable import CameraStatsCore
@testable import CameraStats

final class NamingSettingsTests: XCTestCase {
    func testMovieTVAndCustomSwitchingRetainsValuesAndChoices() throws {
        var report = Report.fixture()
        report.values["season"] = "2"; report.values["episode"] = "7"
        let metadata = report.values
        report.updateNaming {$0.preset = .television; $0.setEnabled("camera", false)}
        XCTAssertTrue(report.naming.reportTemplate.contains("{season}"))
        XCTAssertFalse(report.naming.reportTemplate.contains("{camera}"))
        report.updateNaming {$0.preset = .movie}
        XCTAssertFalse(report.naming.visibleComponents.contains("season"))
        XCTAssertFalse(report.naming.copyTemplate.contains("season"))
        XCTAssertFalse(try report.exportName(extension: "csv").contains("S2E7"))
        report.updateNaming {$0.preset = .television}
        XCTAssertFalse(report.naming.enabled("camera"))
        XCTAssertTrue(try report.exportName(extension: "csv").contains("S2E7"))
        XCTAssertEqual(report.values, metadata)
        report.updateNaming {$0.preset = .custom; var value = $0.selection; value.reportTemplate = "custom_{project}_S{season:02}E{episode:02}"; value.copyTemplate = "custom_{project}_{roll}_{original}"; $0.selection = value}
        let custom = report.naming.selection
        report.updateNaming {$0.preset = .movie}; report.updateNaming {$0.preset = .custom}
        XCTAssertEqual(report.naming.selection, custom)
        XCTAssertTrue(try report.exportName(extension: "html").contains("S02E07"))
    }
    func testDisabledEmptyFieldsAndNumericValidation() throws {
        var report = Report.fixture(); report.values["season"] = ""; report.values["episode"] = " "
        report.updateNaming {$0.preset = .movie}
        XCTAssertNoThrow(try Naming.preview(template: report.naming.reportTemplate, report: report, entry: report.entries[0]))
        report.updateNaming {$0.preset = .television}
        XCTAssertTrue(report.reportNameNeedsValues)
        XCTAssertEqual(try report.exportName(extension: "csv"), "Shot-Reporter-r\(report.revision).csv")
        XCTAssertThrowsError(try Naming.preview(template: report.naming.reportTemplate, report: report, entry: report.entries[0]))
        report.updateNaming {$0.setEnabled("season", false); $0.setEnabled("episode", false)}
        XCTAssertNoThrow(try report.exportName(extension: "csv"))
        XCTAssertFalse(report.reportNameNeedsValues)
        XCTAssertFalse(report.naming.copyTemplate.contains("_SE_"))
        report.updateNaming {$0.setEnabled("project", false)}
        XCTAssertEqual(try report.exportName(extension: "csv"), "Shot-Reporter-r\(report.revision).csv")
        XCTAssertThrowsError(try Naming.preview(template: "{take:99}", report: report, entry: report.entries[0]))
        XCTAssertThrowsError(try Naming.preview(template: "{take:}", report: report, entry: report.entries[0]))
    }
    func testHiddenTokensInEditedMovieTemplatesAreAlwaysOmitted() {
        var settings = NamingSettings()
        var selection = settings.selection
        selection.reportTemplate = "{project}_S{season}E{episode}_SC{scene}"
        selection.copyTemplate = "{project}_{season}_{episode}_{original}"
        settings.selection = selection
        XCTAssertEqual(settings.reportTemplate, "{project}_SC{scene}")
        XCTAssertEqual(settings.copyTemplate, "{project}_{original}")
        XCTAssertEqual(settings.selection, selection)
    }
    func testSavedPresetsKeepIndependentCustomizations() throws {
        var settings = NamingSettings()
        settings.preset = .custom
        var value = settings.selection; value.copyTemplate = "MY_{project}_{asset}"; settings.selection = value
        settings.loadSaved(id: "older:v4", name: "Saved studio", template: "STUDIO_{project}_{original}")
        settings.setEnabled("project", false)
        let saved = settings.selection, id = settings.selectionID
        settings.preset = .custom
        XCTAssertEqual(settings.copyTemplate, "MY_{project}_{asset}")
        settings.select(id)
        XCTAssertEqual(settings.selection, saved)
        settings.loadSaved(id: "older:v4", name: "Saved studio", template: "STUDIO_{project}_{original}")
        XCTAssertEqual(settings.selection, saved)
        XCTAssertEqual(try JSONDecoder().decode(NamingSettings.self, from: Exchange.encoder().encode(settings)), settings)
    }
    @MainActor func testPersistenceExchangeRecoveryAndLegacyMigration() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("ShotReporterTest-Naming-" + UUID().uuidString)
        defer {try? FileManager.default.removeItem(at: root)}
        let preferences = UserDefaults(suiteName: "ShotReporterTest-Naming-" + UUID().uuidString)!
        let store = Store(root: root, preferences: preferences); store.open(.fixture())
        let original = store.report
        store.change {report in report.updateNaming {$0.preset = .television; $0.setEnabled("camera", false); $0.setEnabled("take", false)}}
        let expected = store.report
        let reopened = Store(root: root, preferences: preferences)
        XCTAssertEqual(reopened.report, expected)
        XCTAssertEqual(try Exchange.read(Exchange.export(expected, device: "test")).report, expected)
        let repo = try Repository(url: root.appendingPathComponent("reports.sqlite3"))
        XCTAssertEqual(try repo.load(store.slot, previous: true).report, original)
        for _ in 0..<10 {XCTAssertEqual(try Exchange.encoder().encode(expected), try Exchange.encoder().encode(reopened.report))}
        var legacy = Report.fixture(); legacy.namingTemplate = "handmade_{project}_{roll}"
        XCTAssertNil(legacy.namingSettings); XCTAssertEqual(legacy.naming.preset, .custom)
        XCTAssertEqual(legacy.naming.reportTemplate, legacy.namingTemplate)
        // Actual pre-feature archive has no namingSettings key.
        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: Exchange.encoder().encode(legacy)) as? [String: Any]); json.removeValue(forKey: "namingSettings")
        let decoded = try JSONDecoder().decode(Report.self, from: JSONSerialization.data(withJSONObject: json))
        XCTAssertEqual(decoded.naming.reportTemplate, legacy.namingTemplate)
        var incoming = expected
        incoming.updateNaming {var selection = $0.selection; selection.copyTemplate += "_{asset}"; $0.selection = selection}
        let package = try Exchange.read(Exchange.export(incoming, device: "other"))
        XCTAssertFalse(try Exchange.preview(local: expected, incoming: package).conflicts.isEmpty)
        XCTAssertThrowsError(try Exchange.mergeAdditions(local: expected, incoming: package, device: "test"))
    }
    @MainActor func testEffectivePresetsProduceExactWorkerCopyNames() async throws {
        guard ProcessInfo.processInfo.environment["SHOT_REPORTER_WORKER_HOME"] != nil else {throw XCTSkip("A local media worker is required for physical copy acceptance")}
        let helper = DesktopMediaTests(), (worker, fixture) = try helper.tools()
        for preset in NamingSettings.Preset.allCases {
            let root = FileManager.default.temporaryDirectory.appendingPathComponent("ShotReporterTest-NamingCopy-" + UUID().uuidString)
            defer {try? FileManager.default.removeItem(at: root)}
            let source = root.appendingPathComponent("source"), destination = root.appendingPathComponent("copies")
            try FileManager.default.createDirectory(at: source, withIntermediateDirectories: true)
            try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: true)
            let input = source.appendingPathComponent("A001_C001.mp4"); try FileManager.default.copyItem(at: fixture, to: input)
            let original = try Data(contentsOf: input)
            let store = Store(root: root, preferences: UserDefaults(suiteName: "ShotReporterTest-NamingCopy-" + UUID().uuidString)!)
            store.open(.demo())
            store.change {report in
                report.values["season"] = "2"; report.values["episode"] = "7"
                report.updateNaming {settings in
                    settings.preset = preset
                    if preset == .custom {var value = settings.selection; value.copyTemplate = "{project}_S{season:02}E{episode:02}_{original}_{asset}"; settings.selection = value}
                    settings.setEnabled("camera", false)
                }
            }
            let report = store.report, before = try Exchange.encoder().encode(report)
            let model = DesktopMediaModel(root: root, reportID: report.id, worker: worker)
            model.register(source, camera: "A", roll: "A001", load: "naming-fixture"); try await helper.wait(model)
            let clip = try XCTUnwrap(model.snapshot.clips.first); model.selected = clip.id
            model.decode(7); try await helper.wait(model)
            model.approve(scene: "3A", take: "2"); try await helper.wait(model)
            model.link(try XCTUnwrap(report.active.first?.id), report: report); try await helper.wait(model)
            model.preflight([clip.id], report: report, preset: store.namingCopyPreset, destination: destination); try await helper.wait(model)
            let job = try XCTUnwrap(model.snapshot.jobs.first), item = try XCTUnwrap(job.items.first)
            let expected = try Naming.preview(template: report.naming.copyTemplate, report: report, entry: Entry(values: item.resolved)) + ".mp4"
            XCTAssertEqual(item.targetName, expected)
            XCTAssertFalse(expected.contains("CAM-"))
            XCTAssertEqual(expected.contains("S02E07"), preset != .movie)
            model.execute(job.id); try await helper.wait(model)
            XCTAssertEqual(try Data(contentsOf: URL(fileURLWithPath: job.destination).appendingPathComponent(item.targetName)), original)
            XCTAssertEqual(try Data(contentsOf: input), original)
            XCTAssertEqual(try Exchange.encoder().encode(Store(root: root, preferences: UserDefaults(suiteName: "ShotReporterTest-NamingCopy-Reopen-" + UUID().uuidString)!).report), before)
            model.execute(job.id, undo: true); try await helper.wait(model)
        }
    }
    @MainActor func testSettingsRendersMovieAndTV() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("ShotReporterTest-SettingsRender-" + UUID().uuidString)
        defer {try? FileManager.default.removeItem(at: root)}
        let store = Store(root: root, preferences: UserDefaults(suiteName: "ShotReporterTest-SettingsRender-" + UUID().uuidString)!)
        store.open(.fixture()); _ = NSApplication.shared
        for preset in [NamingSettings.Preset.movie, .television] {
            store.change {report in report.updateNaming {$0.preset = preset}}
            let host = NSHostingView(rootView: NamingSettingsView(store: store))
            let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 620, height: 740), styleMask: [.titled, .resizable], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false; window.contentView = host
            host.frame = NSRect(x: 0, y: 0, width: 620, height: 740); host.layoutSubtreeIfNeeded()
            try await Task.sleep(for: .milliseconds(300))
            XCTAssertGreaterThan(host.fittingSize.height, 300)
            if let folder = ProcessInfo.processInfo.environment["SHOT_REPORTER_SCREENSHOTS"], let rep = host.bitmapImageRepForCachingDisplay(in: host.bounds) {
                host.cacheDisplay(in: host.bounds, to: rep)
                try rep.representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: folder).appendingPathComponent(preset == .movie ? "settings-movie.png" : "settings-tv.png"))
            }
            window.close()
        }
    }
}
