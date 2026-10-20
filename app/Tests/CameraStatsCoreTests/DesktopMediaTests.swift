#if os(macOS)
import XCTest
import Foundation
import AppKit
import SwiftUI
@testable import CameraStatsCore
@testable import CameraStats

final class DesktopMediaTests:XCTestCase {
    func testToolSelectionResolvesInstalledBundleAndRejectsDirectoriesAndNonExecutables()throws {
        let root=try temporary(),bundle=root.appendingPathComponent("Helper.app"),folder=bundle.appendingPathComponent("Contents/MacOS")
        try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true)
        let executable=folder.appendingPathComponent("ShotReporterBRAW")
        try Data("local executable fixture".utf8).write(to:executable)
        try FileManager.default.setAttributes([.posixPermissions:0o700],ofItemAtPath:executable.path)
        XCTAssertEqual(try DesktopToolSelection.executable(in:bundle,name:"ShotReporterBRAW"),executable)
        XCTAssertEqual(try DesktopToolSelection.executable(in:executable,name:"ShotReporterBRAW"),executable)
        XCTAssertThrowsError(try DesktopToolSelection.executable(in:folder,name:"ShotReporterBRAW"))
        try FileManager.default.setAttributes([.posixPermissions:0o600],ofItemAtPath:executable.path)
        XCTAssertThrowsError(try DesktopToolSelection.executable(in:bundle,name:"ShotReporterBRAW"))
        XCTAssertThrowsError(try DesktopToolSelection.executable(in:bundle,name:"REDline"))
    }
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
    @MainActor func testNativeBrawBridgeReviewsPackageCopiesReopensAndUndoes() async throws {
        let (worker,_)=try tools(),env=ProcessInfo.processInfo.environment
        guard let sample=env["SHOT_REPORTER_BRAW_SAMPLE"],let helper=env["SHOT_REPORTER_BRAW_HELPER"] else {throw XCTSkip("Authorized local BRAW SDK sample/helper are not configured.")}
        let root=try temporary(),source=root.appendingPathComponent("braw-source"),destination=root.appendingPathComponent("copies")
        try FileManager.default.createDirectory(at:source,withIntermediateDirectories:true);try FileManager.default.createDirectory(at:destination,withIntermediateDirectories:true)
        let original=URL(fileURLWithPath:sample),clipFile=source.appendingPathComponent(original.lastPathComponent)
        try FileManager.default.copyItem(at:original,to:clipFile)
        let sidecar=original.deletingPathExtension().appendingPathExtension("sidecar")
        if FileManager.default.fileExists(atPath:sidecar.path) {try FileManager.default.copyItem(at:sidecar,to:source.appendingPathComponent(sidecar.lastPathComponent))}
        let originalBytes=try Data(contentsOf:clipFile),prefs=try XCTUnwrap(UserDefaults(suiteName:"ShotReporterBraw-"+UUID().uuidString))
        let store=Store(root:root,preferences:prefs);store.open(.demo());let before=try Exchange.encoder().encode(store.report)
        let model=DesktopMediaModel(root:root,reportID:store.report.id,worker:worker,preferences:prefs);model.brawHelper=helper
        model.register(source,camera:"A",roll:"SDK",load:"authorized-braw-fixture");try await wait(model)
        let clip=try XCTUnwrap(model.snapshot.clips.first);XCTAssertEqual(clip.packageKind,"braw-folder");XCTAssertTrue(clip.metadata.readable,clip.metadata.issue);XCTAssertEqual(clip.memberFileIds?.count,2)
        model.selected=clip.id;model.decode(0);try await wait(model);XCTAssertEqual(model.frame?.index,0);XCTAssertEqual(model.frame?.packageFingerprint,clip.packageFingerprint)
        model.approvePackage(clip);try await wait(model);XCTAssertEqual(model.snapshot.packageApprovals?.count,1)
        model.approve(scene:"TEST",take:"1");try await wait(model);model.link(try XCTUnwrap(store.report.active.first?.id),report:store.report);try await wait(model)
        model.preflight([clip.id],report:store.report,preset:DesktopPreset(name:"RAW fixture",template:"R-{asset}"),destination:destination);try await wait(model)
        let job=try XCTUnwrap(model.snapshot.jobs.first);XCTAssertEqual(job.items.count,2);XCTAssertEqual(Set(job.items.map(\.id)).count,2)
        model.execute(job.id);try await wait(model);XCTAssertEqual(model.snapshot.jobs.first?.state,"completed")
        let reopened=DesktopMediaModel(root:root,reportID:store.report.id,worker:worker,preferences:prefs);reopened.refresh();try await wait(reopened);XCTAssertEqual(reopened.snapshot.jobs.first?.state,"completed")
        model.execute(job.id,undo:true);try await wait(model);XCTAssertEqual(model.snapshot.jobs.first?.state,"undone")
        XCTAssertEqual(try Data(contentsOf:clipFile),originalBytes);XCTAssertEqual(try Exchange.encoder().encode(Store(root:root,preferences:prefs).report),before)
        if let path=env["SHOT_REPORTER_SCREENSHOTS"] {
            _=NSApplication.shared
            for dark in [false,true] {
                let hosting=NSHostingView(rootView:DesktopMediaWorkspace(store:store,model:model).environment(\.colorScheme,dark ? .dark:.light))
                let window=NSWindow(contentRect:NSRect(x:0,y:0,width:1280,height:800),styleMask:[.titled,.resizable],backing:.buffered,defer:false)
                window.isReleasedWhenClosed=false;window.contentView=hosting;window.appearance=NSAppearance(named:dark ? .darkAqua:.aqua);window.orderFront(nil);hosting.frame=NSRect(origin:.zero,size:window.contentLayoutRect.size)
                try await Task.sleep(for:.milliseconds(350));try await wait(model);hosting.layoutSubtreeIfNeeded();window.displayIfNeeded()
                let capture=try XCTUnwrap(CGWindowListCreateImage(.null,.optionIncludingWindow,CGWindowID(window.windowNumber),.boundsIgnoreFraming))
                let png=try XCTUnwrap(NSBitmapImageRep(cgImage:capture).representation(using:.png,properties:[:]))
                try FileManager.default.createDirectory(atPath:path,withIntermediateDirectories:true);try png.write(to:URL(fileURLWithPath:path).appendingPathComponent(dark ? "braw-workspace-dark.png":"braw-workspace-light.png"));window.close()
            }
        }
    }
    @MainActor func testNativeBrawMultiFrameForwardReverseAndReseek() async throws {
        let (worker,_)=try tools(),env=ProcessInfo.processInfo.environment
        guard let sample=env["SHOT_REPORTER_BRAW_SEQUENCE_SAMPLE"],let helper=env["SHOT_REPORTER_BRAW_HELPER"] else {throw XCTSkip("Authorized local multi-frame BRAW fixture/helper are not configured.")}
        let root=try temporary(),source=root.appendingPathComponent("multi-frame")
        try FileManager.default.createDirectory(at:source,withIntermediateDirectories:true)
        let original=URL(fileURLWithPath:sample),copy=source.appendingPathComponent(original.lastPathComponent),before=try Data(contentsOf:original)
        try before.write(to:copy,options:.withoutOverwriting)
        let prefs=try XCTUnwrap(UserDefaults(suiteName:"ShotReporterBrawSequence-"+UUID().uuidString)),store=Store(root:root,preferences:prefs);store.open(.demo())
        let model=DesktopMediaModel(root:root,reportID:store.report.id,worker:worker,preferences:prefs);model.brawHelper=helper
        model.register(source,camera:"A",roll:"PROFILE",load:"local-temporal-fixture");try await wait(model)
        let clip=try XCTUnwrap(model.snapshot.clips.first),count=try XCTUnwrap(clip.metadata.frames);XCTAssertGreaterThan(count,1);XCTAssertLessThanOrEqual(count,8)
        model.selected=clip.id;model.decode(0);try await wait(model);let first=try XCTUnwrap(model.frame)
        model.play();try await wait(model);model.playing=false
        XCTAssertEqual(model.frames.map(\.index),Array(0..<count));XCTAssertEqual(model.frames.first?.pngSHA256,first.pngSHA256)
        let forward=model.frames;XCTAssertGreaterThan(Set(forward.map(\.pngSHA256)).count,1)
        model.decode(count-1);try await wait(model);XCTAssertEqual(model.frame?.pngSHA256,forward.last?.pngSHA256)
        model.play(reverse:true);try await wait(model);model.playing=false
        XCTAssertEqual(model.frames.map(\.index),Array((0..<count).reversed()));XCTAssertEqual(model.frames.map(\.pngSHA256),forward.reversed().map(\.pngSHA256))
        model.decode(0);try await wait(model);XCTAssertEqual(model.frame?.pngSHA256,first.pngSHA256)
        XCTAssertEqual(try Data(contentsOf:original),before);XCTAssertEqual(try Data(contentsOf:copy),before)
    }
}
#endif
