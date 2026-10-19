import Foundation
import Darwin

// Compiled with the domain sources by verify_crash.py; no UI or app-control permission is used.
@main struct CrashCommitProbe {
    static func main() throws {
        let mode=CommandLine.arguments[1], root=URL(fileURLWithPath:CommandLine.arguments[2])
        let repository=try Repository(url:root.appendingPathComponent("reports.sqlite3"))
        if mode == "commit-and-kill" {
            var report=Report.demo(); report.values["title"]="Committed before SIGKILL"
            _=try repository.save(slot:UUID().uuidString,report:report,device:"Crash fixture",expectedFingerprint:nil)
            // Deliberately bypass Repository deinit, SQLite close and ordinary process cleanup.
            kill(getpid(),SIGKILL)
        } else {
            guard let active=try repository.active() else {throw ReportError.invalid("No active report after process crash")}
            let loaded=try repository.load(active)
            let result:[String:Any] = ["title":loaded.report.values["title",default:""],"entries":loaded.report.active.count,"documents":try repository.list().count]
            print(String(data:try JSONSerialization.data(withJSONObject:result,options:.sortedKeys),encoding:.utf8)!)
        }
    }
}
