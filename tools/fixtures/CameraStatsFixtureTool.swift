import Foundation

@main struct CameraStatsFixtureTool {
    static func main() throws {
        let args=CommandLine.arguments
        guard args.count>=3 else {throw ReportError.invalid("Usage: fixture-tool generate <directory> | verify <camreport>")}
        if args[1]=="verify" {
            let package=try Exchange.read(Data(contentsOf:URL(fileURLWithPath:args[2])))
            print("PASS Swift reader: \(package.report.values["title",default:"Untitled"]) / \(package.report.entries.count) takes / revision \(package.report.revision)")
            return
        }
        let root=URL(fileURLWithPath:args[2]);try FileManager.default.createDirectory(at:root,withIntermediateDirectories:true)
        var base=Report.demo();base.id="10000000-0000-4000-8000-000000000001";base.defaults["load"]="20000000-0000-4000-8000-000000000001"
        for i in base.entries.indices {base.entries[i].id=String(format:"30000000-0000-4000-8000-%012d",i+1);base.entries[i].created=Date(timeIntervalSinceReferenceDate:0);base.entries[i].values["load"]=base.defaults["load"]}
        base.history=[Change(device:"Conformance fixture",summary:"Shared demo fixture")];base.history[0].date=Date(timeIntervalSinceReferenceDate:0)
        try Exchange.encoder().encode(base).write(to:root.appendingPathComponent("owner-demo.json"))
        base.values["unicodeTest"]="Maya · Eli · Jonah · café · カメラ · 🎬"
        base.entries[0].flags=["MOS","Tail slate"]
        func write(_ name:String,_ report:Report) throws {
            let body=try Exchange.encoder().encode(report)
            var manifest=Exchange.Manifest(reportID:report.id,revision:report.revision,device:"Conformance fixture",sha256:Exchange.digest(body));manifest.packageID="40000000-0000-4000-8000-"+String(format:"%012d",name.utf8.reduce(0){$0+Int($1)})
            let data=try StoredZIP.write([("manifest.json",try Exchange.encoder().encode(manifest)),("report.json",body)])
            try data.write(to:root.appendingPathComponent(name+".camreport"))
        }
        try write("swift-demo",base);try Exchange.encoder().encode(base).write(to:root.appendingPathComponent("demo-report.json"))
        var add=base;_=add.next(scene:"8B",device:"Conformance fixture");add.entries[add.entries.count-1].id="30000000-0000-4000-8000-000000000099";add.entries[add.entries.count-1].created=Date(timeIntervalSinceReferenceDate:0);for i in add.history.indices {add.history[i].date=Date(timeIntervalSinceReferenceDate:0)};try write("swift-additions",add)
        var conflict=base;conflict.entries[0].values["remarks"]="Competing edit";conflict.record("Conflicting edit",device:"Conformance fixture");for i in conflict.history.indices {conflict.history[i].date=Date(timeIntervalSinceReferenceDate:0)};try write("swift-conflict",conflict)
        var deletion=base;deletion.entries[0].deleted=true;try write("swift-tombstone",deletion)
        var scale=base;for i in 4...1000 {let id=scale.next(scene:"1A",device:"Scale fixture");let n=scale.entries.firstIndex{$0.id==id}!;scale.entries[n].id=String(format:"50000000-0000-4000-8000-%012d",i);scale.entries[n].created=Date(timeIntervalSinceReferenceDate:0)};scale.history=[];try write("swift-1000-takes",scale)
        print("Generated Swift .camreport conformance fixtures; no private device or account data.")
    }
}
