package org.squidproductions.camerastats.media

import org.squidproductions.camerastats.core.*
import java.nio.file.*
import java.text.Normalizer

class MediaService(val repo:MediaRepository,val decoder:Decoder) {
    fun sourceFile(id:String):Pair<PhysicalFile,Path> {
        val f=repo.all<PhysicalFile>("file").single {it.id==id};val root=repo.all<MediaRoot>("root").single {it.id==f.rootId}
        val path=localPath(Path.of(root.path),f.relative);require(Files.size(path)==f.size && sha256(path)==f.sha256) {"Source changed since registration. Rescan it; prior evidence and links remain preserved."};return f to path
    }
    fun source(clip:Clip):Pair<PhysicalFile,Path> {
        if(clip.packageRoot!=null) {
            require(clip.packageIssue.isBlank()) {clip.packageIssue}
            val root=Path.of(repo.all<MediaRoot>("root").single {it.id==clip.rootId}.path)
            val directory=RawPackages.directory(root,clip.packageRoot)
            require(RawPackages.issue(directory).isBlank()) {"Package now contains links or special files; rescan it."}
            val members=clip.memberFileIds.map {sourceFile(it).first}
            val inventory=RawPackages.files(directory).map {root.relativize(it).toString().replace('\\','/')}.sorted()
            require(inventory==members.map {it.relative}.sorted() && RawPackages.fingerprint(members)==clip.packageFingerprint) {"RAW package membership changed; rescan and confirm the inventory again."}
        }
        return sourceFile(clip.fileId)
    }
    fun approvePackage(clipId:String,fingerprint:String,completeSourceConfirmed:Boolean=false):MediaSnapshot=repo.exclusive {
        val clip=repo.all<Clip>("clip").single {it.id==clipId};require(clip.packageRoot!=null && fingerprint==clip.packageFingerprint) {"Review the current package inventory first."};source(clip)
        require(clip.packageKind!="red-rdc" || completeSourceConfirmed) {"A missing trailing RED span can reopen as a shorter clip with the same UUID. Confirm this package against your complete-card copy or source manifest first."}
        repo.put("packageApproval",clipId,PackageApproval(clipId,fingerprint,now(),completeSourceConfirmed));repo.audit(clipId,"Confirm package inventory","Explicit review of every registered package member; internal paths will be preserved in an outer named copy folder. Complete-source confirmation: $completeSourceConfirmed. This is user evidence, not automatic camera-card certification.");repo.snapshot()
    }
    fun register(folder:Path,camera:String,roll:String,load:String):MediaSnapshot=repo.exclusive {
        require(camera.isNotBlank() && roll.isNotBlank() && load.isNotBlank()) {"Camera, roll and load identify this registration."}
        val path=canonical(folder)
        val root=repo.all<MediaRoot>("root").firstOrNull {it.path==path.toString() && it.camera==camera && it.roll==roll && it.load==load} ?: MediaRoot(path=path.toString(),label=path.fileName.toString(),camera=camera,roll=roll,load=load).also {repo.put("root",it.id,it)}
        val known=repo.all<PhysicalFile>("file").filter {it.rootId==root.id}.groupBy {it.relative}
        fun physical(file:Path):PhysicalFile {
            val relative=path.relativize(file).toString().replace('\\','/');val safe=localPath(path,relative);val hash=sha256(safe)
            return known[relative].orEmpty().firstOrNull {it.sha256==hash} ?: PhysicalFile(rootId=root.id,relative=relative,size=Files.size(safe),sha256=hash,modified=Files.getLastModifiedTime(safe).toMillis()).also {repo.put("file",it.id,it)}
        }
        val packages=RawPackages.discover(path)
        for(group in packages) {
            val members=group.files.map(::physical);val primary=members.single {it.relative==path.relativize(group.primary).toString().replace('\\','/')}
            val prior=repo.all<Clip>("clip").firstOrNull {it.fileId==primary.id}
            val clip=(prior ?: Clip(fileId=primary.id,rootId=root.id,originalName=group.primary.fileName.toString(),displayName=group.directory.fileName.toString(),metadata=Probe())).copy(metadata=decoder.probe(group.primary),memberFileIds=members.map {it.id},packageRoot=path.relativize(group.directory).toString().replace('\\','/').ifBlank {"."},packageKind=group.kind,packageFingerprint=RawPackages.fingerprint(members),packageIssue=group.issue)
            repo.put("clip",clip.id,clip)
        }
        val extensions=setOf("mov","mp4","mxf","r3d","braw","avi","mkv")
        fun ungroupedProbe(file:Path):Probe {val metadata=decoder.probe(file);return if(file.fileName.toString().substringAfterLast('.').lowercase() in setOf("r3d","braw")) metadata.copy(readable=false,issue="RAW review needs an isolated complete package: one BRAW clip with its sidecars, or a complete RED .RDC directory. Multiple/mixed RAW identities cannot be guessed.") else metadata}
        Files.walk(path).use {stream ->stream.filter {Files.isRegularFile(it,LinkOption.NOFOLLOW_LINKS) && packages.none {g->it.startsWith(g.directory)} && it.fileName.toString().substringAfterLast('.').lowercase() in extensions}.sorted().forEach {file ->
            val relative=path.relativize(file).toString().replace('\\','/');val safe=localPath(path,relative);val hash=sha256(safe)
            val existing=known[relative].orEmpty().firstOrNull {it.sha256==hash}
            if(existing!=null) {
                val stem=safe.fileName.toString().substringBeforeLast('.')
                val clip=repo.all<Clip>("clip").singleOrNull {it.fileId==existing.id} ?: Clip(fileId=existing.id,rootId=root.id,originalName=safe.fileName.toString(),displayName=stem,metadata=Probe())
                val companions=Files.list(safe.parent).use {s ->s.filter {it!=safe && it.fileName.toString().substringBeforeLast('.').equals(stem,true)}.map {it.fileName.toString()}.toList()}
                repo.put("clip",clip.id,clip.copy(metadata=ungroupedProbe(safe),companions=companions,memberFileIds=emptyList(),packageRoot=null,packageKind=null,packageFingerprint=null,packageIssue=""))
            } else {
                val physical=PhysicalFile(rootId=root.id,relative=relative,size=Files.size(safe),sha256=hash,modified=Files.getLastModifiedTime(safe).toMillis())
                val stem=safe.fileName.toString().substringBeforeLast('.')
                val companions=Files.list(safe.parent).use {s ->s.filter {it!=safe && it.fileName.toString().substringBeforeLast('.').equals(stem,true)}.map {it.fileName.toString()}.toList()}
                val clip=Clip(fileId=physical.id,rootId=root.id,originalName=safe.fileName.toString(),displayName=stem,metadata=ungroupedProbe(safe),companions=companions)
                repo.transaction {repo.put("file",physical.id,physical);repo.put("clip",clip.id,clip)}
            }
        }}
        repo.audit(root.id,"Register media","Read-only scan of camera $camera / roll $roll / load $load. Hashes are registration evidence, not approval to reuse a camera card.")
        repo.snapshot()
    }
    fun relinkRoot(id:String,folder:Path):MediaSnapshot=repo.exclusive {
        val root=repo.all<MediaRoot>("root").single {it.id==id};val path=canonical(folder)
        for(f in repo.all<PhysicalFile>("file").filter {it.rootId==id}) {val p=localPath(path,f.relative);require(Files.size(p)==f.size && sha256(p)==f.sha256) {"Relink mismatch: ${f.relative}; old root retained."}}
        repo.put("root",id,root.copy(path=path.toString()));repo.audit(id,"Relink root","Every registered relative file matched its size and SHA-256. Previous location: ${root.path}");repo.snapshot()
    }
    fun frame(clipId:String,index:Long):FrameRef=repo.exclusive {
        val clip=repo.all<Clip>("clip").single {it.id==clipId};require(clip.metadata.readable) {clip.metadata.issue}
        val (f,path)=source(clip);val directory=repo.home.resolve("frames").resolve(clip.id);Files.createDirectories(directory)
        val png=directory.resolve(identity()+".png");val (pts,hash)=decoder.exactFrame(path,index,png)
        require(sha256(path)==f.sha256) {"Media changed during decode; evidence was not accepted."}
        source(clip)
        val frame=FrameRef(clipId=clip.id,fileSHA256=f.sha256,index=index,pts=pts,timeBase=clip.metadata.timeBase,png=repo.home.relativize(png).toString().replace('\\','/'),pngSHA256=hash,decoder=decoder.versionFor(path),packageFingerprint=clip.packageFingerprint)
        repo.put("frame",frame.id,frame);frame
    }
    fun frames(clipId:String,start:Long,count:Int=48):List<FrameRef> = repo.exclusive {
        val clip=repo.all<Clip>("clip").single {it.id==clipId};require(clip.metadata.readable) {clip.metadata.issue}
        val (file,path)=source(clip);val parent=repo.home.resolve("frames").resolve(clip.id);Files.createDirectories(parent)
        val batch=decoder.frameBatch(path,start,count,parent.resolve(identity()))
        require(sha256(path)==file.sha256) {"Source changed during preview decode; frames were not accepted."}
        source(clip);val version=decoder.versionFor(path)
        val refs=batch.map {(index,pair)->FrameRef(clipId=clipId,fileSHA256=file.sha256,index=index,pts=pair.first,timeBase=clip.metadata.timeBase,png=repo.home.relativize(pair.second).toString().replace('\\','/'),pngSHA256=sha256(pair.second),decoder=version,packageFingerprint=clip.packageFingerprint)}
        repo.transaction {refs.forEach {repo.put("frame",it.id,it)}};refs
    }
    fun approve(clipId:String,frameId:String,scene:String,take:String):MediaSnapshot=repo.exclusive {
        require(scene.isNotBlank() && take.isNotBlank() && scene.length<=100 && take.length<=100) {"Confirm explicit scene and take labels."}
        val frame=repo.all<FrameRef>("frame").single {it.id==frameId && it.clipId==clipId}
        val clip=repo.all<Clip>("clip").single {it.id==clipId};val (file,_)=source(clip)
        require(frame.fileSHA256==file.sha256 && frame.packageFingerprint==clip.packageFingerprint && sha256(localPath(repo.home,frame.png))==frame.pngSHA256) {"Slate evidence changed; decode the frame again."}
        repo.put("slate",clipId,ApprovedSlate(clipId,scene.trim(),take.trim(),frame,now()))
        repo.audit(clipId,"Confirm slate","Manual visual confirmation of scene ${scene.trim()} / take ${take.trim()} at source frame ${frame.index}; no OCR result was inferred.")
        repo.snapshot()
    }
    fun suggestions(clipId:String,report:Report):List<MatchSuggestion> {
        val clip=repo.all<Clip>("clip").single {it.id==clipId};val root=repo.all<MediaRoot>("root").single {it.id==clip.rootId};val slate=repo.all<ApprovedSlate>("slate").firstOrNull {it.clipId==clipId}
        val candidates=report.active.map {entry ->
            var score=0;val reasons=mutableListOf<String>();val conflicts=mutableListOf<String>();val values=entry.values
            if(values["originalFilename"]?.let {Path.of(it).fileName.toString()==clip.originalName}==true) {score+=70;reasons+="Exact recorded source filename"}
            else if(!values["originalFilename"].isNullOrBlank()) conflicts+="Recorded source filename differs"
            if(values["camera"]==root.camera) {score+=15;reasons+="Camera ${root.camera}"} else if(!values["camera"].isNullOrBlank()) conflicts+="Camera differs"
            if(values["roll"]==root.roll) {score+=15;reasons+="Roll ${root.roll}"} else if(!values["roll"].isNullOrBlank()) conflicts+="Roll differs"
            if(values["load"]==root.load) {score+=15;reasons+="Exact media-load identity"}
            else if(!values["load"].isNullOrBlank()) conflicts+="Media-load identity differs"
            if(slate!=null && values["scene"]==slate.scene && values["take"]==slate.take) {score+=10;reasons+="Confirmed scene/take (weak key alone)"}
            if(slate!=null) {
                if(!values["scene"].isNullOrBlank() && values["scene"]!=slate.scene) conflicts+="Confirmed slate scene ${slate.scene} differs from report scene ${values["scene"]}"
                if(!values["take"].isNullOrBlank() && values["take"]!=slate.take) conflicts+="Confirmed slate take ${slate.take} differs from report take ${values["take"]}"
            }
            MatchSuggestion(entry.id,"${values["scene"].orEmpty()} / ${values["take"].orEmpty()} · ${values["camera"].orEmpty()} / ${values["roll"].orEmpty()}",score,reasons,false,conflicts)
        }.sortedWith(compareByDescending<MatchSuggestion> {it.score}.thenBy {it.entryId})
        return candidates.map {c->c.copy(ambiguous=c.score==0 || candidates.count {it.score==c.score}>1)}
    }
    fun link(clipId:String,report:Report,entryId:String,inFrame:Long?=null,outFrame:Long?=null):MediaSnapshot=repo.exclusive {
        require((inFrame==null && outFrame==null) || (inFrame!=null && outFrame!=null && inFrame>=0 && outFrame>inFrame)) {"Use a complete valid in/out frame range, or the whole clip."}
        val candidate=suggestions(clipId,report).single {it.entryId==entryId}
        val link=Association(clipId=clipId,reportId=report.id,entryId=entryId,reportRevision=report.revision,reportFingerprint=Exchange.digest(Exchange.encode(report)),reasons=listOf("Explicit user-reviewed association")+candidate.reasons+candidate.conflicts.map {"Acknowledged discrepancy: $it"},confirmedAt=now(),inFrame=inFrame,outFrame=outFrame)
        repo.transaction {
            repo.all<Association>("association").filter {it.clipId==clipId && it.reportId==report.id && it.entryId==entryId}.forEach {repo.remove("association",it.id)}
            repo.put("association",link.id,link)
        };repo.audit(link.id,"Link report",link.reasons.joinToString("; "));repo.snapshot()
    }
    fun unlink(id:String):MediaSnapshot=repo.exclusive {repo.remove("association",id);repo.audit(id,"Unlink report","User removed this association. Report entries and media files were retained.");repo.snapshot()}
    fun savePreset(name:String,template:String,id:String?=null):NamingPreset=repo.exclusive {
        require(name.isNotBlank() && name.length<=100 && template.length in 1..500)
        val old=id?.let {key->repo.all<NamingPreset>("preset").single {it.id==key}}
        val preset=NamingPreset(id=old?.id ?: identity(),version=(old?.version ?: 0)+1,name=name,template=template);repo.transaction {repo.rememberPreset(preset)};preset
    }
}
