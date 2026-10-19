#if os(macOS)
import SwiftUI
import AppKit
import CameraStatsCore

@MainActor struct DesktopMediaWorkspace:View {
    @Bindable var store:Store
    @State private var model:DesktopMediaModel
    @State private var query=""
    @State private var checked=Set<String>()
    @State private var camera:String
    @State private var roll:String
    @State private var load:String
    @State private var scene=""
    @State private var take=""
    @State private var index="0"
    @State private var preset=DesktopPreset(name:"Movie",template:DesktopPreset.movie)
    init(store:Store,model:DesktopMediaModel?=nil) {
        self.store=store
        _model=State(initialValue:model ?? DesktopMediaModel(root:store.root,reportID:store.report.id))
        _camera=State(initialValue:store.report.defaults["camera",default:"A"]);_roll=State(initialValue:store.report.defaults["roll",default:"A001"]);_load=State(initialValue:store.report.defaults["load",default:UUID().uuidString])
    }
    var body:some View {
        VStack(alignment:.leading,spacing:12) {
            toolbar
            Text("Sources stay unchanged. Standalone MOV/MP4 profile only. RAW registration does not enable RAW playback or renaming. Silent frame review; slate OCR is not implemented.").font(.caption).foregroundStyle(.secondary)
            HSplitView {
                mediaList.frame(minWidth:230,idealWidth:270,maxWidth:330)
                ScrollView {VStack(alignment:.leading,spacing:14) {
                    if let clip=model.clip {clipInspector(clip)} else {ContentUnavailableView("Review desktop footage",systemImage:"film.stack",description:Text("Register a working-copy folder, select a clip and decode a source frame. Report associations require explicit review."))}
                    Divider();naming
                    ForEach(model.snapshot.jobs.sorted {$0.createdAt>$1.createdAt}) {job in DesktopCopyJobView(job:job,model:model)}
                }.padding(.horizontal,12).frame(maxWidth:.infinity,alignment:.leading)}.frame(minWidth:550)
            }
            if let error=model.error {Text(error).font(.callout).foregroundStyle(.red).textSelection(.enabled)}
            if let notice=model.notice {Text(notice).font(.caption).foregroundStyle(.secondary)}
        }.padding(16).frame(minWidth:850,minHeight:620).background(Color("Canvas"))
        .task {model.refresh()}
        .onDisappear {model.playing=false;if model.busy {model.cancel()}}
        .onChange(of:model.selected) {_,_ in
            model.playing=false;model.frame=nil;model.suggestions=[];index="0"
            let approved=model.snapshot.slates.first {$0.clipId==model.selected};scene=approved?.scene ?? "";take=approved?.take ?? ""
        }
        .onChange(of:model.frame?.id,initial:true) {_,_ in if let frame=model.frame {index=String(frame.index)}}
        .task(id:model.playing) {
            guard model.playing else {return}
            for (i,frame) in model.frames.enumerated() {
                guard !Task.isCancelled,model.playing else {return}
                model.frame=frame;index=String(frame.index)
                let next=model.frames.indices.contains(i+1) ? model.frames[i+1]:nil
                let rate=frame.timeBase.split(separator:"/").compactMap {Double($0)}
                let unit=rate.count==2 && rate[1] != 0 ? rate[0]/rate[1]:0
                let interval=next.flatMap {n->Double? in guard let a=Double(frame.pts),let b=Double(n.pts),unit>0 else {return nil};return abs(b-a)*unit} ?? 0.08
                do {try await Task.sleep(for:.seconds(min(2,max(0.01,interval))))} catch {return}
            }
            model.playing=false
        }
    }
    private var toolbar:some View {
        HStack(alignment:.bottom,spacing:10) {
            VStack(alignment:.leading) {Text("Desktop footage").font(.title2.bold());Text("\(model.snapshot.clips.count) registered · \(checked.count) checked · report r\(store.report.revision)").font(.caption).foregroundStyle(.secondary)}
            Spacer()
            labeled("Camera",text:$camera,width:70);labeled("Roll",text:$roll,width:90);labeled("Load identity",text:$load,width:140)
            Button("Register folder") {if let path=model.chooseDirectory("Register a working-copy folder") {model.register(path,camera:camera,roll:roll,load:load)}}.disabled(model.busy).buttonStyle(.borderedProminent)
            if model.busy {ProgressView().controlSize(.small);Button("Cancel") {model.cancel()}} else {Button("Refresh journals") {model.refresh()}}
        }
    }
    private func labeled(_ title:String,text:Binding<String>,width:CGFloat)->some View {VStack(alignment:.leading,spacing:4) {Text(title).font(.caption);TextField(title,text:text).textFieldStyle(.roundedBorder).frame(width:width)}.disabled(model.busy)}
    private var mediaList:some View {
        VStack(alignment:.leading) {
            TextField("Search filename / confirmed slate",text:$query).textFieldStyle(.roundedBorder)
            List(selection:$model.selected) {
                ForEach(model.snapshot.clips.filter {clip in let slate=model.snapshot.slates.first {$0.clipId==clip.id};return query.isEmpty || (clip.originalName+" "+(slate?.scene ?? "")+" "+(slate?.take ?? "")).localizedCaseInsensitiveContains(query)}) {clip in
                    HStack(alignment:.top) {
                        Toggle("Include \(clip.originalName)",isOn:Binding(get:{checked.contains(clip.id)},set:{value in if value {checked.insert(clip.id)} else {checked.remove(clip.id)}})).labelsHidden()
                        VStack(alignment:.leading,spacing:4) {Text(clip.originalName).font(.headline);Text(clip.metadata.codec.isEmpty ? "Unavailable decode":clip.metadata.codec).font(.caption);Text(model.snapshot.slates.contains {$0.clipId==clip.id} ? "Slate confirmed":"Needs slate review").font(.caption).foregroundStyle(.secondary)}
                    }.tag(clip.id)
                }
            }.disabled(model.busy)
            ForEach(model.snapshot.roots) {root in Button("Relink \(root.camera) / \(root.roll)") {if let path=model.chooseDirectory("Relink \(root.label) · every file must match") {model.relink(root.id,path:path)}}.disabled(model.busy)}
        }
    }
    private func clipInspector(_ clip:DesktopClip)->some View {
        VStack(alignment:.leading,spacing:10) {
            Text(clip.originalName).font(.title3.bold()).textSelection(.enabled)
            Text("\(clip.metadata.width) × \(clip.metadata.height) · \(clip.metadata.rate) · \(clip.metadata.duration)s · \(clip.metadata.frames.map(String.init) ?? "unknown") frames").font(.caption).foregroundStyle(.secondary)
            if !clip.metadata.issue.isEmpty {Text(clip.metadata.issue).foregroundStyle(.red)}
            if let frame=model.frame {
                DesktopSourceFrame(frame:frame,home:model.home,onReverse:{if !model.busy {model.play(reverse:true)}},onPause:{model.playing=false},onPlay:{if !model.busy {model.play()}})
            }
            HStack {
                labeled("Source frame index",text:$index,width:110)
                Button("Decode exact frame") {if let n=Int64(index) {model.decode(n)}}.disabled(model.busy || !clip.metadata.readable)
                Button("< Frame") {model.decode((model.frame?.index ?? 1)-1)}.disabled(model.busy || (model.frame?.index ?? 0)<1)
                Button("Frame >") {model.decode((model.frame?.index ?? -1)+1)}.disabled(model.busy || !clip.metadata.readable)
                Button(model.playing ? "Pause":"Play silent review") {if model.playing {model.playing=false} else {model.play()}}.disabled(model.busy || !clip.metadata.readable)
            }
            Divider()
            HStack {labeled("Confirmed scene",text:$scene,width:140);labeled("Confirmed take",text:$take,width:100);Button("Confirm values from this frame") {model.approve(scene:scene,take:take)}.disabled(model.busy || model.playing || model.frame?.clipId != clip.id || scene.isEmpty || take.isEmpty)}
            if let approved=model.snapshot.slates.first(where:{$0.clipId==clip.id}) {Label("Approved \(approved.scene) / \(approved.take) · frame \(approved.frame.index)",systemImage:"checkmark.seal").font(.callout)}
            Button("Review report associations") {model.suggest(store.report)}.disabled(model.busy)
            ForEach(model.suggestions) {suggestion in
                GroupBox {
                    VStack(alignment:.leading,spacing:5) {
                        Text(suggestion.label).font(.headline);Text(suggestion.reasons.joined(separator:" · ")).font(.caption)
                        if suggestion.ambiguous {Text("Ambiguous suggestion · manual review required").font(.caption.bold())}
                        ForEach(suggestion.conflicts,id:\.self) {Text($0).font(.caption).foregroundStyle(.red)}
                        Button("Confirm this association") {model.link(suggestion.entryId,report:store.report)}.disabled(model.busy)
                    }.frame(maxWidth:.infinity,alignment:.leading)
                }
            }
            ForEach(model.snapshot.associations.filter {$0.clipId==clip.id && $0.reportId==store.report.id}) {link in HStack {Text("Linked entry \(link.entryId.prefix(8)) · r\(link.reportRevision)");Spacer();Button("Unlink") {model.unlink(link.id)}.disabled(model.busy)}}
            if !clip.companions.isEmpty {Text("Related files need a group-aware adapter: \(clip.companions.joined(separator:", ")). Physical copy/rename is blocked.").foregroundStyle(.red)}
        }
    }
    private var naming:some View {
        VStack(alignment:.leading,spacing:8) {
            Text("Preview renamed working copies").font(.headline)
            HStack {Button("Movie") {preset=DesktopPreset(name:"Movie",template:DesktopPreset.movie)};Button("TV") {preset=DesktopPreset(name:"TV",template:DesktopPreset.television)};Menu("Saved presets") {ForEach(model.snapshot.presets) {saved in Button("\(saved.name) v\(saved.version)") {preset=saved}}}.disabled(model.snapshot.presets.isEmpty);Text("Preset v\(preset.version)").font(.caption).foregroundStyle(.secondary)}
            TextField("Preset name",text:Binding(get:{preset.name},set:{preset.name=$0;preset.version += 1})).textFieldStyle(.roundedBorder)
            TextField("Declarative naming template",text:Binding(get:{preset.template},set:{preset.template=$0;preset.version += 1})).textFieldStyle(.roundedBorder)
            Text("Tokens include project, season, episode, scene, take:03, camera, roll, original and asset. Missing values and collisions block the entire batch. Checked clips need a confirmed frame and one current report association.").font(.caption).foregroundStyle(.secondary)
            Button("Preflight \(checked.count) checked clips") {if let destination=model.chooseDirectory("Choose a copy destination outside source folders") {model.preflight(checked,report:store.report,preset:preset,destination:destination)}}.disabled(model.busy || checked.isEmpty)
        }.disabled(model.busy)
    }
}

@MainActor private struct DesktopSourceFrame:View {
    let frame:DesktopFrame;let home:URL
    let onReverse:()->Void;let onPause:()->Void;let onPlay:()->Void
    var body:some View {
        VStack(alignment:.leading,spacing:5) {
            if let image=NSImage(contentsOf:home.appendingPathComponent(frame.png)) {Image(nsImage:image).resizable().scaledToFit().frame(maxWidth:.infinity,maxHeight:300).focusable().onKeyPress("j") {onReverse();return .handled}.onKeyPress("k") {onPause();return .handled}.onKeyPress("l") {onPlay();return .handled}.accessibilityLabel("Decoded source frame \(frame.index)")}
            Text("Source frame \(frame.index) · PTS \(frame.pts) × \(frame.timeBase) · focus frame for J / K / L").font(.system(.caption,design:.monospaced))
        }
    }
}
@MainActor private struct DesktopCopyJobView:View {
    let job:DesktopCopyJob
    @Bindable var model:DesktopMediaModel
    var body:some View {
        GroupBox {
            VStack(alignment:.leading,spacing:7) {
                Text("\(job.state.uppercased()) · \(job.items.count) files · report r\(job.reportRevision)").font(.headline)
                Text("\(job.preset.name) v\(job.preset.version) · \(job.destination)").font(.caption).textSelection(.enabled)
                ForEach(job.items) {item in Text("\(item.sourceRelative) → \(item.targetName) · \(item.state)").font(.caption).textSelection(.enabled)}
                if let error=job.error {Text(error).foregroundStyle(.red)}
                HStack {
                    if ["prepared","copying","blocked"].contains(job.state) {Button(job.state=="prepared" ? "Create verified copies":"Resume journal") {model.execute(job.id)}.buttonStyle(.borderedProminent)}
                    if ["completed","undoing"].contains(job.state) {Button("Undo · retain recovery copies") {model.execute(job.id,undo:true)}}
                }.disabled(model.busy)
                if job.state=="completed" {Text("SHA-256, metadata and approved-frame checks passed. Relink manifest saved beside copies. This is not approval to reuse a camera card.").font(.caption).foregroundStyle(.secondary)}
            }.frame(maxWidth:.infinity,alignment:.leading)
        }
    }
}
#endif
