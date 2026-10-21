import Foundation
import CrashCapture

public enum DiagnosticArea:String,Codable,CaseIterable {case reports,media,updates,export,application}
public enum DiagnosticKind:String,Codable {case crash,failure,interrupted}
public struct DiagnosticIncident:Codable,Equatable,Identifiable {
    public let id:UUID
    public let date:Date
    public let kind:DiagnosticKind
    public let detail:String
}
public enum NativeCrashCapture {
    public static func install(at url:URL)->Bool {url.path.withCString {sr_crash_install($0) == 0}}
    public static func recordException() {sr_crash_record_exception()}
    public static func uninstall() {sr_crash_uninstall()}
}

/// Allowlisted diagnostic codes only. Never accepts a project, media path,
/// localized error description, exception reason, stack, environment or hostname.
public final class DiagnosticsJournal {
    public static let maximumIncidents=20
    public static let maximumFileBytes=32_768
    public static let retention:TimeInterval=14*24*60*60
    public let root:URL
    public var signalURL:URL {root.appendingPathComponent("fatal.marker")}
    private let lock=NSLock()
    private let now:()->Date
    private let fm=FileManager.default
    private var incidents:[DiagnosticIncident]=[]
    private var storage:URL {root.appendingPathComponent("incidents.json")}
    private var session:URL {root.appendingPathComponent("session.json")}
    public init(root:URL,now:@escaping ()->Date = Date.init) throws {
        self.root=root;self.now=now
        try fm.createDirectory(at:root,withIntermediateDirectories:true,attributes:[.posixPermissions:0o700])
        if (try? root.resourceValues(forKeys:[.isSymbolicLinkKey]).isSymbolicLink) == true {throw CocoaError(.fileWriteInvalidFileName)}
        if let size=try? storage.resourceValues(forKeys:[.fileSizeKey]).fileSize,size <= Self.maximumFileBytes,
           let bytes=try? Data(contentsOf:storage),let loaded=try? JSONDecoder().decode([DiagnosticIncident].self,from:bytes) {incidents=loaded.filter {Self.safeDetail($0.detail) == $0.detail}}
        prune()
    }
    public func pending()->[DiagnosticIncident] {lock.lock();defer{lock.unlock()};let count=incidents.count;prune();if incidents.count != count {try? save()};return incidents}
    public func begin(trackInterrupted:Bool) throws {
        lock.lock();defer{lock.unlock()}
        let signalSize=(try? signalURL.resourceValues(forKeys:[.fileSizeKey]).fileSize) ?? 0
        let bytes=signalSize > 0 && signalSize <= 16 ? (try? Data(contentsOf:signalURL)) : nil
        if let bytes,bytes.count >= 2,bytes[0] == 1,let detail=Self.fatalDescription(bytes[1]) {
            append(.crash,detail)
        } else if trackInterrupted,fm.fileExists(atPath:session.path) {
            append(.interrupted,"Previous session ended without a clean-shutdown marker. This may be a force quit, power loss or OS termination; a crash is not confirmed.")
        }
        try? fm.removeItem(at:signalURL)
        try save()
        if trackInterrupted {try protectedWrite(Data("active\n".utf8),to:session)}
    }
    public func cleanShutdown() {lock.lock();defer{lock.unlock()};try? fm.removeItem(at:session)}
    public func failure(_ area:DiagnosticArea,error:NSError?=nil) throws {
        lock.lock();defer{lock.unlock()}
        let allowed=[NSCocoaErrorDomain,NSPOSIXErrorDomain,NSURLErrorDomain,NSOSStatusErrorDomain]
        let detail=area.rawValue + " operation failed" + (error.map {"; domain="+(allowed.contains($0.domain) ? $0.domain : "other")+"; code=\($0.code)"} ?? "")
        append(.failure,detail);try save()
    }
    public func dismiss(_ ids:Set<UUID>) throws {lock.lock();defer{lock.unlock()};incidents.removeAll{ids.contains($0.id)};try save()}
    private func append(_ kind:DiagnosticKind,_ detail:String) {incidents.append(DiagnosticIncident(id:UUID(),date:now(),kind:kind,detail:detail));prune()}
    private func prune() {incidents=Array(incidents.filter {now().timeIntervalSince($0.date) <= Self.retention && $0.date.timeIntervalSince(now()) < 300}.suffix(Self.maximumIncidents))}
    private func save() throws {prune();let data=try JSONEncoder().encode(incidents);guard data.count <= Self.maximumFileBytes else {throw CocoaError(.fileWriteOutOfSpace)};try protectedWrite(data,to:storage)}
    private func protectedWrite(_ data:Data,to url:URL) throws {
        if fm.fileExists(atPath:url.path),(try url.resourceValues(forKeys:[.isSymbolicLinkKey])).isSymbolicLink == true {throw CocoaError(.fileWriteInvalidFileName)}
        try data.write(to:url,options:.atomic);try fm.setAttributes([.posixPermissions:0o600],ofItemAtPath:url.path)
    }
    private static func fatalDescription(_ signal:UInt8)->String? {
        let signals:[UInt8:String]=[6:"SIGABRT",11:"SIGSEGV",10:"SIGBUS",4:"SIGILL",8:"SIGFPE",5:"SIGTRAP",255:"uncaught Objective-C exception"]
        return signals[signal].map {"Native crash captured: \($0). No exception text, stack contents or project data collected."}
    }
    static func safeDetail(_ detail:String)->String {
        let fixed=["Previous session ended without a clean-shutdown marker. This may be a force quit, power loss or OS termination; a crash is not confirmed."]+[UInt8(6),11,10,4,8,5,255].compactMap(fatalDescription)
        if fixed.contains(detail) {return detail}
        let pattern=#"^(reports|media|updates|export|application) operation failed(; domain=(NSCocoaErrorDomain|NSPOSIXErrorDomain|NSURLErrorDomain|NSOSStatusErrorDomain|other); code=-?[0-9]{1,20})?$"#
        return detail.range(of:pattern,options:.regularExpression) != nil ? detail : "Diagnostic details unavailable."
    }
}

public enum SupportReport {
    public static let recipient="alexanderhosier@squidproductions.org"
    public static func sanitize(_ input:String)->String {
        var value=String(input.prefix(8_000))
        // User text is reviewed, but common locations/credentials are redacted
        // before preview, copy, export or draft. Automatic diagnostics use codes.
        let patterns=[
            #"(?s)-----BEGIN [^-]*PRIVATE KEY-----.*?-----END [^-]*PRIVATE KEY-----"#,
            #"(?i)\b(?:https?|file)://[^\s<>]+"#,
            #"(?i)(?:/(?:Users|home|Volumes)/|~/)[^\r\n,;<>]*"#,
            #"(?i)\b[A-Z]:[\\/][^\r\n,;<>]*"#,
            #"(?<![\w])/(?:[^\s/<>]+/)*[^\s<>]*"#,
            #"(?i)\b(?:bearer\s+|(?:password|passwd|token|secret|api[_-]?key)\s*[:=]\s*)[^\s,;]+"#,
            #"\b(?:gh[pousr]_[A-Za-z0-9_]+|AKIA[A-Z0-9]{16})\b"#,
            #"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"#
        ]
        for pattern in patterns {value=value.replacingOccurrences(of:pattern,with:"[redacted]",options:.regularExpression)}
        return value.unicodeScalars.filter {$0.value >= 32 || $0 == "\n" || $0 == "\t"}.map(String.init).joined()
    }
    public static func body(description:String,steps:String,version:String,os:String,incidents:[DiagnosticIncident],includeDiagnostics:Bool)->String {
        let header="Shot Reporter support report\nApp: \(sanitize(version))\nOS: \(sanitize(os))\n\nDescription:\n\(sanitize(description))\n\nReproduction steps:\n\(sanitize(steps))"
        let details=includeDiagnostics ? incidents.suffix(20).map {"\($0.date.formatted(.iso8601)) [\($0.kind.rawValue)] \(DiagnosticsJournal.safeDetail($0.detail))"}.joined(separator:"\n") : "Not included."
        return header+"\n\nSanitized diagnostic categories:\n"+details+"\n\nNo project, script, media contents, file paths, environment or raw crash dumps are collected automatically. Review all text before sending.\n"
    }
    public static func mailURL(body:String)->URL? {
        var parts=URLComponents();parts.scheme="mailto";parts.path=recipient
        parts.queryItems=[URLQueryItem(name:"subject",value:"Shot Reporter bug / crash report"),URLQueryItem(name:"body",value:body)]
        // Long mailto URLs are inconsistently supported. Copy/export is lossless.
        guard let url=parts.url,url.absoluteString.utf8.count <= 1_800 else {return nil};return url
    }
}
