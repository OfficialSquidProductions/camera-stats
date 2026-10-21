import XCTest
@testable import CameraStatsCore

final class DiagnosticsTests:XCTestCase {
    func profile() throws -> URL {let root=FileManager.default.temporaryDirectory.appendingPathComponent("ShotReporterTest-"+UUID().uuidString,isDirectory:true);try FileManager.default.createDirectory(at:root,withIntermediateDirectories:true);addTeardownBlock {try? FileManager.default.removeItem(at:root)};return root}
    func testCleanShutdownAndInterruptedSessionAreDistinctFromCrash() throws {
        let root=try profile();let first=try DiagnosticsJournal(root:root);try first.begin(trackInterrupted:true);first.cleanShutdown()
        let clean=try DiagnosticsJournal(root:root);try clean.begin(trackInterrupted:true);XCTAssertTrue(clean.pending().isEmpty)
        let interrupted=try DiagnosticsJournal(root:root);try interrupted.begin(trackInterrupted:true)
        XCTAssertEqual(interrupted.pending().map(\.kind),[.interrupted]);XCTAssertTrue(interrupted.pending()[0].detail.contains("crash is not confirmed"))
        interrupted.cleanShutdown()
    }
    func testIOSBackgroundTerminationIsNotCalledACrash() throws {
        let root=try profile();try DiagnosticsJournal(root:root).begin(trackInterrupted:false)
        let next=try DiagnosticsJournal(root:root);try next.begin(trackInterrupted:false);XCTAssertTrue(next.pending().isEmpty)
    }
    func testFailureCapturesOnlyAllowlistedCodesAndSurvivesRestartAndDismissal() throws {
        let root=try profile();let log=try DiagnosticsJournal(root:root)
        try log.failure(.media,error:NSError(domain:"secret-project-title",code:19,userInfo:[NSLocalizedDescriptionKey:"/Users/Private/secret.mov token=VERYSECRET"]))
        let recovered=try DiagnosticsJournal(root:root);XCTAssertEqual(recovered.pending().count,1)
        let bytes=try String(contentsOf:root.appendingPathComponent("incidents.json"))
        for secret in ["Private","VERYSECRET","secret-project-title","secret.mov"] {XCTAssertFalse(bytes.contains(secret))}
        XCTAssertEqual(recovered.pending()[0].detail,"media operation failed; domain=other; code=19")
        try recovered.dismiss(Set(recovered.pending().map(\.id)));XCTAssertTrue(try DiagnosticsJournal(root:root).pending().isEmpty)
    }
    func testBoundedRetentionAndOversizedOrMalformedStorage() throws {
        let root=try profile();var time=Date(timeIntervalSince1970:1_700_000_000)
        let journal=try DiagnosticsJournal(root:root,now:{time})
        for _ in 0..<100 {try journal.failure(.application)}
        XCTAssertEqual(journal.pending().count,20)
        XCTAssertLessThan(try Data(contentsOf:root.appendingPathComponent("incidents.json")).count,DiagnosticsJournal.maximumFileBytes)
        time=time.addingTimeInterval(DiagnosticsJournal.retention+1);XCTAssertTrue(journal.pending().isEmpty)
        XCTAssertEqual(try JSONDecoder().decode([DiagnosticIncident].self,from:Data(contentsOf:root.appendingPathComponent("incidents.json"))),[])
        try Data(repeating:1,count:40_000).write(to:root.appendingPathComponent("incidents.json"))
        XCTAssertTrue(try DiagnosticsJournal(root:root).pending().isEmpty)
        try Data("not JSON".utf8).write(to:root.appendingPathComponent("incidents.json"))
        XCTAssertTrue(try DiagnosticsJournal(root:root).pending().isEmpty)
    }
    func testReviewRedactsPathsSecretsAndNeverAddsProjectContents() throws {
        let sensitive="/Users/Alex/Private Production/secret.mov\nC:\\Users\\Alex\\Footage\\secret.mov\ntoken=VERYSECRET email@example.com https://example.com/?key=SECRET\n-----BEGIN PRIVATE KEY-----\nHIDDENKEY\n-----END PRIVATE KEY-----"
        let report=SupportReport.body(description:sensitive,steps:"Select a clip",version:"0.2.0 (7)",os:"macOS26",incidents:[],includeDiagnostics:true)
        for word in ["Alex","secret.mov","VERYSECRET","email@example.com","HIDDENKEY","example.com"] {XCTAssertFalse(report.contains(word),word)}
        XCTAssertTrue(report.contains("0.2.0 (7)"));XCTAssertTrue(report.contains("Select a clip"))
        let malicious=DiagnosticIncident(id:UUID(),date:Date(),kind:.failure,detail:"private project script text")
        XCTAssertFalse(SupportReport.body(description:"",steps:"",version:"1",os:"Mac",incidents:[malicious],includeDiagnostics:true).contains(malicious.detail))
    }
    func testMailDraftExactRecipientEncodingAndLongReportFallbackWithoutOpeningMail() throws {
        let url=try XCTUnwrap(SupportReport.mailURL(body:"A & B?\nRepro #2"));let parts=try XCTUnwrap(URLComponents(url:url,resolvingAgainstBaseURL:false))
        XCTAssertEqual(parts.scheme,"mailto");XCTAssertEqual(parts.path,"alexanderhosier@squidproductions.org")
        XCTAssertEqual(parts.queryItems?.first{$0.name == "body"}?.value,"A & B?\nRepro #2")
        XCTAssertNil(SupportReport.mailURL(body:String(repeating:"long report",count:400)))
        XCTAssertNotNil(SupportReport.mailURL(body:SupportReport.body(description:"Button does not respond",steps:"Open Today",version:"0.2.0 (7)",os:"macOS26",incidents:[],includeDiagnostics:true)))
    }
    #if os(macOS)
    func testRealFatalChildProcessRecoveredOnNextLaunchWithoutTouchingAppLibrary() throws {
        let root=try profile();let package=URL(fileURLWithPath:#filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let source=root.appendingPathComponent("probe.c"),binary=root.appendingPathComponent("ShotReporterDiagnosticsProbe")
        try """
        #include "CrashCapture.h"
        #include <signal.h>
        #include <sys/resource.h>
        int main(int argc,char **argv) {struct rlimit l={0,0};setrlimit(RLIMIT_CORE,&l);if(sr_crash_install(argv[1])) return 2;if(argc>2)raise(SIGABRT);sr_crash_uninstall();return 0;}
        """.write(to:source,atomically:true,encoding:.utf8)
        let compile=Process();compile.executableURL=URL(fileURLWithPath:"/usr/bin/clang");compile.arguments=[source.path,package.appendingPathComponent("Sources/CrashCapture/CrashCapture.c").path,"-I",package.appendingPathComponent("Sources/CrashCapture/include").path,"-o",binary.path];try compile.run();compile.waitUntilExit();XCTAssertEqual(compile.terminationStatus,0)
        let journal=try DiagnosticsJournal(root:root);try journal.begin(trackInterrupted:true)
        let child=Process();child.executableURL=binary;child.arguments=[journal.signalURL.path,"abort"];try child.run();child.waitUntilExit();XCTAssertNotEqual(child.terminationStatus,0)
        let restarted=try DiagnosticsJournal(root:root);try restarted.begin(trackInterrupted:true)
        XCTAssertEqual(restarted.pending().map(\.kind),[.crash]);XCTAssertTrue(restarted.pending()[0].detail.contains("SIGABRT"))
        restarted.cleanShutdown();let third=try DiagnosticsJournal(root:root);try third.begin(trackInterrupted:true);XCTAssertEqual(third.pending().count,1,"Marker consumed once; incident retained until dismissal")
    }
    #endif
}
