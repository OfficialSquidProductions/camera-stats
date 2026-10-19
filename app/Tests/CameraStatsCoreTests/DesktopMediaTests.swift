#if os(macOS)
import XCTest
import Foundation
import AppKit
import SwiftUI
@testable import CameraStatsCore
@testable import CameraStats

final class DesktopMediaTests:XCTestCase {
    func temporary()throws->URL {
        let url=FileManager.default.temporaryDirectory.appendingPathComponent("ShotReporterDesktop-"+UUID().uuidString,isDirectory:true)
        try FileManager.default.createDirectory(at:url,withIntermediateDirectories:true)
        addTeardownBlock {try? FileManager.default.removeItem(at:url)};return url
    }
    func tools()throws->(URL,URL) {
        let env=ProcessInfo.processInfo.environment
        guard let worker=env["SHOT_REPORTER_WORKER_HOME"],let fixture=env["SHOT_REPORTER_TEST_MEDIA"] else {throw XCTSkip("Optional desktop integration needs a locally built worker and synthetic media; see docs/DESKTOP_MILESTONE.md.")}
        return (URL(fileURLWithPath:worker),URL(fileURLWithPath:fixture))
    }
    @MainActor func wait(_ model:DesktopMediaModel) async throws {
        for _ in 0..<1200 {if !model.busy {XCTAssertNil(model.error);return};try await Task.sleep(for:.milliseconds(25))}
        model.cancel();XCTFail("Desktop worker did not finish in 30 seconds")
    }
    @MainActor func testNativeBridgeCompletesReviewCopyAndUndoWithoutChangingReportsOrSources() async throws {
        let (worker,fixture)=try tools(),root=try temporary(),source=root.appendingPathComponent("source"),destination=root.appendingPathComponent("copies")
        try FileManager.default.createDirectory(at:source,withIntermediateDirectories:true);try FileManager.default.createDirectory(at:destination,withIntermediateDirectories:true)
        let video=source.appendingPathComponent("A001_C001.mp4");try FileManager.default.copyItem(at:fixture,to:video)
        let original=try Data(contentsOf:video),prefs=try XCTUnwrap(UserDefaults(suiteName:"ShotReporterDesktopTests-"+UUID().uuidString))
        let store=Store(root:root,preferences:prefs);store.open(.demo());let report=store.report
        let before=try Exchange.encoder().encode(report)
        let model=DesktopMediaModel(root:root,reportID:report.id,worker:worker,preferences:prefs)
        model.register(source,camera:"A",roll:"A001",load:"native-fixture");try await wait(model)
        let clip=try XCTUnwrap(model.snapshot.clips.first);XCTAssertTrue(clip.metadata.readable);XCTAssertEqual(model.snapshot.clips.count,1)
        model.selected=clip.id;model.decode(7);try await wait(model);XCTAssertEqual(model.frame?.index,7)
        model.approve(scene:"3A",take:"2");try await wait(model);XCTAssertEqual(model.snapshot.slates.first?.frame.index,7)
        model.suggest(report);try await wait(model);XCTAssertFalse(model.suggestions.isEmpty);XCTAssertTrue(model.snapshot.associations.isEmpty)
        model.link(try XCTUnwrap(report.active.first?.id),report:report);try await wait(model)
        let preset=DesktopPreset(name:"Movie",template:DesktopPreset.movie)
        model.preflight([clip.id],report:report,preset:preset,destination:destination);try await wait(model)
        let job=try XCTUnwrap(model.snapshot.jobs.first);XCTAssertEqual(job.state,"prepared");XCTAssertFalse(FileManager.default.fileExists(atPath:job.destination))
        model.execute(job.id);try await wait(model);XCTAssertEqual(model.snapshot.jobs.first?.state,"completed")
        let output=URL(fileURLWithPath:job.destination).appendingPathComponent(try XCTUnwrap(job.items.first?.targetName))
        XCTAssertEqual(try Data(contentsOf:output),original);XCTAssertEqual(try Data(contentsOf:video),original)
        XCTAssertEqual(try Exchange.encoder().encode(store.report),before)
        model.refresh();try await wait(model);XCTAssertEqual(model.snapshot.presets.first?.id,preset.id)
        model.execute(job.id,undo:true);try await wait(model);XCTAssertEqual(model.snapshot.jobs.first?.state,"undone")
        XCTAssertFalse(FileManager.default.fileExists(atPath:output.path));XCTAssertEqual(try Data(contentsOf:video),original)
        XCTAssertEqual(try Exchange.encoder().encode(Store(root:root,preferences:prefs).report),before)
        model.decode(0);model.cancel();try await wait(model)
        XCTAssertEqual(model.snapshot.jobs.first?.state,"undone");XCTAssertEqual(try Data(contentsOf:video),original)
    }
    @MainActor func testNativeDesktopWorkspaceRendersLightAndDarkWithRealDecodedFixture() async throws {
        let (worker,fixture)=try tools(),root=try temporary(),source=root.appendingPathComponent("source")
        try FileManager.default.createDirectory(at:source,withIntermediateDirectories:true);try FileManager.default.copyItem(at:fixture,to:source.appendingPathComponent("A001_C001.mp4"))
        let prefs=try XCTUnwrap(UserDefaults(suiteName:"ShotReporterDesktopLayout-"+UUID().uuidString)),store=Store(root:root,preferences:prefs);store.open(.demo())
        let model=DesktopMediaModel(root:root,reportID:store.report.id,worker:worker,preferences:prefs)
        model.register(source,camera:"A",roll:"A001",load:"layout-fixture");try await wait(model)
        model.selected=try XCTUnwrap(model.snapshot.clips.first?.id);model.decode(7);try await wait(model)
        _=NSApplication.shared
        for dark in [false,true] {
            let hosting=NSHostingView(rootView:DesktopMediaWorkspace(store:store,model:model).background(dark ? Color(red:0.10,green:0.08,blue:0.10):Color(red:0.98,green:0.97,blue:0.98)).environment(\.colorScheme,dark ? .dark:.light))
            let window=NSWindow(contentRect:NSRect(x:0,y:0,width:1280,height:900),styleMask:[.titled,.resizable],backing:.buffered,defer:false)
            window.isReleasedWhenClosed=false;window.contentView=hosting;window.appearance=NSAppearance(named:dark ? .darkAqua:.aqua);window.orderFront(nil)
            hosting.frame=NSRect(x:0,y:0,width:1280,height:900);hosting.layoutSubtreeIfNeeded();window.displayIfNeeded()
            // Give SwiftUI's first render and journal refresh a bounded opportunity to complete.
            try await Task.sleep(for:.milliseconds(350));try await wait(model);hosting.layoutSubtreeIfNeeded()
            let captured=try XCTUnwrap(CGWindowListCreateImage(.null,.optionIncludingWindow,CGWindowID(window.windowNumber),.boundsIgnoreFraming))
            let image=NSBitmapImageRep(cgImage:captured)
            XCTAssertGreaterThanOrEqual(image.pixelsWide,1280);XCTAssertGreaterThanOrEqual(image.pixelsHigh,900)
            let png=try XCTUnwrap(image.representation(using:.png,properties:[:]))
            XCTAssertGreaterThan(png.count,20000)
            if let path=ProcessInfo.processInfo.environment["SHOT_REPORTER_SCREENSHOTS"] {let folder=URL(fileURLWithPath:path);try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true);try png.write(to:folder.appendingPathComponent(dark ? "desktop-media-dark.png":"desktop-media-light.png"))}
            window.close()
        }
    }
}
#endif
