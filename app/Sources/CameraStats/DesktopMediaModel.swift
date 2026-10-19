#if os(macOS)
import Foundation
import Observation
import AppKit
import CameraStatsCore

final class DesktopWorkerClient:@unchecked Sendable {
    let home:URL
    let worker:URL
    private let lock=NSLock()
    private var active:Process?
    private var cancelled=false
    init(home:URL,worker:URL?=nil) {
        self.home=home
        self.worker=worker ?? ProcessInfo.processInfo.environment["SHOT_REPORTER_WORKER_HOME"].map {URL(fileURLWithPath:$0,isDirectory:true)} ?? (Bundle.main.resourceURL ?? URL(fileURLWithPath:"/missing-resources")).appendingPathComponent("DesktopMedia",isDirectory:true)
    }
    func prepare() {lock.lock();cancelled=false;lock.unlock()}
    func cancel() {lock.lock();cancelled=true;if active?.isRunning==true {active?.terminate()};lock.unlock()}
    func invoke(_ input:Data)throws->Data {
        let java=worker.appendingPathComponent("runtime/bin/java")
        guard FileManager.default.isExecutableFile(atPath:java.path) else {throw ReportError.invalid("The desktop worker is not bundled in this build. Build the local desktop development app with tools/build_desktop_media_dev.py. Existing reports remain available.")}
        let process=Process(),stdin=Pipe(),stdout=Pipe()
        process.executableURL=java
        process.arguments=["-Xmx512m","-cp",worker.appendingPathComponent("lib/*").path,"org.squidproductions.camerastats.media.WorkerKt",home.path,worker.appendingPathComponent("bin/ffmpeg").path,worker.appendingPathComponent("bin/ffprobe").path]
        process.standardInput=stdin;process.standardOutput=stdout;process.standardError=FileHandle.nullDevice
        lock.lock();guard active==nil,!cancelled else {lock.unlock();throw ReportError.invalid("The desktop worker was cancelled or is still stopping. Originals and journals retained.")};active=process
        do {try process.run();lock.unlock()} catch {active=nil;lock.unlock();throw error}
        defer {lock.lock();active=nil;lock.unlock()}
        do {try stdin.fileHandleForWriting.write(contentsOf:input);try stdin.fileHandleForWriting.close()} catch {process.terminate();throw error}
        var output=Data()
        do {while let chunk=try stdout.fileHandleForReading.read(upToCount:65536),!chunk.isEmpty {guard output.count+chunk.count<=32*1024*1024 else {process.terminate();throw ReportError.invalid("Desktop response exceeds the development limit. Registered records were retained.")};output.append(chunk)}} catch {if process.isRunning {process.terminate()};throw error}
        process.waitUntilExit()
        guard process.terminationStatus==0 else {throw ReportError.invalid("Desktop worker stopped. Originals and any operation journal were retained.")}
        return output
    }
}

private final class DesktopScopedAccess:@unchecked Sendable {
    private let lock=NSLock()
    private var urls:[URL]=[]
    func add(_ url:URL) {lock.lock();urls.append(url);lock.unlock()}
    deinit {for url in urls {url.stopAccessingSecurityScopedResource()}}
}

@MainActor @Observable final class DesktopMediaModel {
    let home:URL
    let client:DesktopWorkerClient
    var snapshot=DesktopMediaSnapshot()
    var busy=false
    var error:String?
    var notice:String?
    var selected:String?
    var frame:DesktopFrame?
    var suggestions:[DesktopSuggestion]=[]
    var frames:[DesktopFrame]=[]
    var playing=false
    private var cancellationRequested=false
    private let preferences:UserDefaults
    private let scopeKey:String
    private let access=DesktopScopedAccess()
    init(root:URL,reportID:String,worker:URL?=nil,preferences:UserDefaults = .standard) {
        let hash=Exchange.digest(Data(reportID.utf8))
        home=root.appendingPathComponent("desktop-media/"+hash,isDirectory:true);client=DesktopWorkerClient(home:home,worker:worker);self.preferences=preferences;scopeKey="desktop-media-scopes-"+hash
        for data in preferences.array(forKey:scopeKey) as? [Data] ?? [] {var stale=false;if let url=try? URL(resolvingBookmarkData:data,options:[.withSecurityScope,.withoutUI],bookmarkDataIsStale:&stale),url.startAccessingSecurityScopedResource() {access.add(url)}}
    }
    deinit {client.cancel()}
    var clip:DesktopClip? {snapshot.clips.first {$0.id==selected}}
    func keepAccess(_ url:URL) {
        if url.startAccessingSecurityScopedResource() {access.add(url)}
        if let data=try? url.bookmarkData(options:.withSecurityScope,includingResourceValuesForKeys:nil,relativeTo:nil) {var values=preferences.array(forKey:scopeKey) as? [Data] ?? [];if !values.contains(data) {values.append(data);preferences.set(values,forKey:scopeKey)}}
    }
    func chooseDirectory(_ title:String)->URL? {let panel=NSOpenPanel();panel.title=title;panel.canChooseDirectories=true;panel.canChooseFiles=false;panel.allowsMultipleSelection=false;guard panel.runModal() == .OK,let url=panel.url else {return nil};keepAccess(url);return url}
    func request<T:Decodable>(_ payload:[String:Any],as type:T.Type,accept:@escaping (T)->Void) {
        guard !busy else {return};busy=true;error=nil;cancellationRequested=false
        do {
            let input=try JSONSerialization.data(withJSONObject:payload,options:[.sortedKeys])
            let client=self.client
            client.prepare()
            Task {
                var failure:String?
                do {
                    let bytes=try await Task.detached(priority:.userInitiated) {try client.invoke(input)}.value
                    let response=try JSONDecoder().decode(DesktopWorkerResponse<T>.self,from:bytes)
                    guard response.ok,let result=response.result else {throw ReportError.invalid(response.error ?? "Desktop operation failed; journal retained.")}
                    if !self.cancellationRequested {accept(result)}
                } catch {if !self.cancellationRequested {failure=error.localizedDescription;self.error=failure}}
                self.busy=false
                if self.cancellationRequested {self.cancellationRequested=false;self.notice="Cancelled. Originals and recovery journals were retained.";self.refresh()}
                else if let failure,payload["command"] as? String != "snapshot" {self.refresh(preserving:failure)}
            }
        } catch {self.error=error.localizedDescription;busy=false}
    }
    func refresh(preserving message:String?=nil) {request(["command":"snapshot"],as:DesktopMediaSnapshot.self) {self.snapshot=$0;self.error=message}}
    func cancel() {playing=false;if busy {cancellationRequested=true;client.cancel()}}
    func reportObject(_ report:Report)throws->String {String(decoding:try Exchange.encoder().encode(report),as:UTF8.self)}
    func register(_ path:URL,camera:String,roll:String,load:String) {request(["command":"register","path":path.path,"camera":camera,"roll":roll,"load":load],as:DesktopMediaSnapshot.self) {self.snapshot=$0}}
    func relink(_ id:String,path:URL) {request(["command":"relink","rootID":id,"path":path.path],as:DesktopMediaSnapshot.self) {self.snapshot=$0}}
    func decode(_ index:Int64) {guard let selected else {return};playing=false;request(["command":"frame","clipID":selected,"index":index],as:DesktopFrame.self) {self.frame=$0}}
    func play(reverse:Bool=false) {guard let selected else {return};let current=frame?.index ?? 0;let start=reverse ? max(0,current-47):current;request(["command":"frames","clipID":selected,"index":start],as:[DesktopFrame].self) {values in self.frames=reverse ? values.filter {$0.index<=current}.reversed():values;self.playing=true}}
    func approve(scene:String,take:String) {guard let frame else {return};request(["command":"approve","clipID":frame.clipId,"frameID":frame.id,"scene":scene,"take":take],as:DesktopMediaSnapshot.self) {self.snapshot=$0}}
    func suggest(_ report:Report) {guard let selected else {return};do {request(["command":"suggestions","clipID":selected,"reportJSON":try reportObject(report)],as:[DesktopSuggestion].self) {self.suggestions=$0}} catch {self.error=error.localizedDescription}}
    func link(_ entry:String,report:Report) {guard let selected else {return};do {request(["command":"link","clipID":selected,"entryID":entry,"reportJSON":try reportObject(report)],as:DesktopMediaSnapshot.self) {self.snapshot=$0}} catch {self.error=error.localizedDescription}}
    func unlink(_ id:String) {request(["command":"unlink","associationID":id],as:DesktopMediaSnapshot.self) {self.snapshot=$0}}
    func preflight(_ ids:Set<String>,report:Report,preset:DesktopPreset,destination:URL) {
        do {let json=try JSONSerialization.jsonObject(with:JSONEncoder().encode(preset));request(["command":"prepare","clipIDs":Array(ids).sorted(),"reportJSON":try reportObject(report),"preset":json,"destination":destination.path],as:DesktopCopyJob.self) {job in self.snapshot.jobs.append(job);if !self.snapshot.presets.contains(where:{$0.id==job.preset.id && $0.version>job.preset.version}) {self.snapshot.presets.removeAll {$0.id==job.preset.id};self.snapshot.presets.append(job.preset)}}} catch {self.error=error.localizedDescription}
    }
    func execute(_ job:String,undo:Bool=false) {request(["command":undo ? "undo":"copy","jobID":job],as:DesktopCopyJob.self) {result in self.snapshot.jobs.removeAll {$0.id==result.id};self.snapshot.jobs.append(result)}}
}
#endif
