package org.squidproductions.camerastats.media

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.squidproductions.camerastats.core.*
import java.nio.file.*
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.math.BigInteger

object DesktopNaming {
    const val movie="{project}_SC{scene}_TK{take:03}_CAM-{camera}_{original}"
    const val television="{project}_S{season:02}E{episode:02}_SC{scene}_TK{take:03}_CAM-{camera}_{original}"
    fun evaluate(template:String,values:Map<String,String>):String {
        require(template.length in 1..500)
        val result=Regex("\\{([A-Za-z_]+)(?::(0?\\d{1,2}))?}").replace(template) {match ->
            val key=match.groupValues[1];val value=values[key]?.takeIf {it.isNotBlank()} ?: error("Missing naming value: $key. No fallback was substituted.")
            val width=match.groupValues[2].toIntOrNull()
            if(width!=null) {require(width in 1..32 && value.all {it.isDigit() && it.code<128}) {"Padding requires a numeric $key, or remove its padding rule."};BigInteger(value).toString().padStart(width,'0')} else value
        }
        // Reuse the existing portable-filename rules without executing user-provided templates.
        return Naming.preview(result,Report(values=emptyMap()),Entry(values=emptyMap()))
    }
}

/** Copy-first only. Original paths are never destinations and no source file is renamed or removed. */
class CopyJobs(val service:MediaService,private val checkpoint:(String,CopyJob)->Unit={_,_->}) {
    private val repo=service.repo
    private fun persist(job:CopyJob):CopyJob {repo.put("job",job.id,job);return job}
    private fun job(id:String)=repo.all<CopyJob>("job").single {it.id==id}
    private fun folder(job:CopyJob):Path=Path.of(job.destination)
    private fun target(job:CopyJob,item:CopyItem)=localPath(folder(job),item.targetName)
    private fun owner(job:CopyJob):ByteArray=("Shot Reporter copy journal 1\n"+job.id+"\n"+job.projectId+"\n"+job.createdAt+"\n").toByteArray()
    private fun validateFolder(job:CopyJob,create:Boolean=false):Path {
        val path=folder(job);canonical(path.parent)
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) {require(create) {"Copy destination is missing; no recovery operation was applied."};Files.createDirectory(path);writeNew(path.resolve(".shot-reporter-owner"),owner(job));forceDirectory(path.parent)}
        canonical(path)
        require(Files.readAllBytes(localPath(path,".shot-reporter-owner")).contentEquals(owner(job))) {"Destination ownership marker changed. Recovery requires review."}
        return path
    }
    fun prepare(clipIds:List<String>,report:Report,preset:NamingPreset,destination:Path):CopyJob=repo.exclusive {
        require(clipIds.isNotEmpty() && clipIds.size<=10000 && clipIds.distinct().size==clipIds.size) {"Select a nonempty unique clip batch."}
        val parent=canonical(destination);require(Files.isWritable(parent)) {"Destination is not writable."}
        val snapshot=repo.snapshot();val reportHash=Exchange.digest(Exchange.encode(report));val id=identity();val output=parent.resolve("Shot-Reporter-"+id)
        require(!Files.exists(output,LinkOption.NOFOLLOW_LINKS)) {"Destination batch folder already exists."}
        for(root in snapshot.roots) {val source=Path.of(root.path).toAbsolutePath().normalize();require(!parent.startsWith(source) && !source.startsWith(output)) {"Choose a destination outside registered source roots."}}
        val items=clipIds.flatMap {clipId ->
            val clip=snapshot.clips.single {it.id==clipId};val root=snapshot.roots.single {it.id==clip.rootId}
            require(clip.metadata.readable && (clip.packageRoot!=null || clip.originalName.substringAfterLast('.').lowercase() in setOf("mov","mp4"))) {"${clip.originalName}: configure and validate an available decoder before preparing copies."}
            require(clip.packageRoot!=null || clip.companions.isEmpty()) {"${clip.originalName}: related files need a group-aware adapter (${clip.companions.joinToString()}). No files were changed."}
            if(clip.packageRoot!=null) require(snapshot.packageApprovals.any {it.clipId==clip.id && it.fingerprint==clip.packageFingerprint && (clip.packageKind!="red-rdc" || it.completeSourceConfirmed)}) {"Review and confirm the complete RAW package inventory before copying."}
            val (file,_)=service.source(clip)
            val slate=snapshot.slates.singleOrNull {it.clipId==clipId} ?: error("Confirm the chosen source slate frame for ${clip.originalName} first.")
            require(slate.frame.fileSHA256==file.sha256 && slate.frame.packageFingerprint==clip.packageFingerprint && sha256(localPath(repo.home,slate.frame.png))==slate.frame.pngSHA256) {"Slate evidence changed."}
            val links=snapshot.associations.filter {it.clipId==clipId && it.reportId==report.id}
            require(links.size==1) {"${clip.originalName}: select exactly one reviewed report association before whole-file naming. Multiple take ranges are preserved but require their own naming decision."}
            val link=links.single();require(link.reportFingerprint==reportHash && link.reportRevision==report.revision && report.active.any {it.id==link.entryId}) {"Report changed. Review and confirm its association again."}
            val values=report.values+report.active.single {it.id==link.entryId}.values+mapOf("project" to report.values["title"].orEmpty(),"show" to report.values["title"].orEmpty(),"scene" to slate.scene,"take" to slate.take,"camera" to root.camera,"roll" to root.roll,"load" to root.load,"original" to clip.originalName.substringBeforeLast('.'),"asset" to clip.id.take(8),"shoot_date" to report.values["date"].orEmpty())
            val name=DesktopNaming.evaluate(preset.template,values)
            val members=if(clip.packageRoot!=null) clip.memberFileIds.map {id->snapshot.files.single {it.id==id}} else listOf(file)
            members.map {member ->
                val target=if(clip.packageRoot!=null) {
                    val directory=RawPackages.directory(Path.of(root.path),clip.packageRoot);val source=service.sourceFile(member.id).second
                    name+(if(clip.packageKind=="red-rdc") ".RDC" else "")+"/"+directory.relativize(source).toString().replace('\\','/')
                } else name+"."+clip.originalName.substringAfterLast('.')
                require(output.resolve(target).toString().length<=240) {"Proposed Windows-compatible path exceeds 240 characters. Choose a shorter destination or name."}
                for(part in target.split('/')) {require(part !in setOf(".","..") && Naming.preview(part,Report(values=emptyMap()),Entry(values=emptyMap()))==part) {"An internal package name is not portable; original names were retained and copy blocked."}}
                CopyItem(clip.id,member.id,root.id,member.relative,member.sha256,member.size,target,slate.scene,slate.take,slate.frame,link,values,verifyMedia=member.id==file.id,sourceProbe=clip.metadata)
            }
        }
        require(items.map {collisionKey(it.targetName)}.distinct().size==items.size) {"Case-insensitive or Unicode-normalized filename collision in the batch. Add roll/original/asset tokens; no unexplained counters were added."}
        val paths=mutableMapOf<String,String>();for(item in items) {var prefix="";for(part in item.targetName.split('/')) {prefix=if(prefix.isEmpty()) part else "$prefix/$part";val previous=paths.putIfAbsent(collisionKey(prefix),prefix);require(previous==null || previous==prefix) {"Package paths collide on a Windows destination. No internal names were changed."}}}
        val total=items.fold(0L) {sum,item->Math.addExact(sum,item.size)}
        require(Files.getFileStore(parent).usableSpace>Math.addExact(total,16L*1024*1024)) {"Insufficient destination space for this copy batch."}
        val result=CopyJob(id,report.id,report.revision,reportHash,preset,output.toString(),now(),items)
        repo.transaction {repo.rememberPreset(preset);persist(result);repo.audit(id,"Prepare copies","Preview frozen for ${items.size} files; originals retained. Preset ${preset.name} v${preset.version}.")};result
    }
    private fun manifest(job:CopyJob):ByteArray = mediaJSON.encodeToString(buildJsonObject {
        put("format","org.squidproductions.shot-reporter.copy-manifest");put("schema",1);put("jobID",job.id);put("reportID",job.projectId);put("reportRevision",job.reportRevision);put("reportFingerprint",job.reportFingerprint);put("preset",mediaJSON.encodeToJsonElement(job.preset));put("createdAt",job.createdAt)
        put("files",buildJsonArray {job.items.forEach {item ->add(buildJsonObject {put("assetID",item.clipId);put("physicalFileID",item.fileId);put("sourceRootID",item.rootId);put("originalRelativePath",item.sourceRelative);put("currentRelativePath",item.targetName);put("size",item.size);put("sha256",item.sourceSHA256);put("primaryMedia",item.verifyMedia);put("nativeMetadata",mediaJSON.encodeToJsonElement(item.sourceProbe));put("approvedSlate",mediaJSON.encodeToJsonElement(item.frame));put("scene",item.scene);put("take",item.take);put("association",mediaJSON.encodeToJsonElement(item.association));put("resolvedValues",mediaJSON.encodeToJsonElement(item.resolved))})}})
        put("capability","Byte-verified copies with unchanged package internal paths, primary metadata and approved-frame verification. No embedded metadata rewritten; no NLE relink certification.")
    }).toByteArray()
    fun execute(id:String):CopyJob=repo.exclusive {
        var current=job(id);require(current.state !in setOf("undone","undoing")) {"This job is being undone or already undone. Make a new preview to create new copies."}
        try {
            val root=validateFolder(current,create=true);current=persist(current.copy(state="copying",error=null))
            current.items.map {it.clipId}.distinct().forEach {clipId->val clip=repo.all<Clip>("clip").single {it.id==clipId};service.source(clip);require(current.items.filter {it.clipId==clipId}.all {it.frame.packageFingerprint==clip.packageFingerprint}) {"Package changed since the frozen preview."}}
            for(index in current.items.indices) {
                var item=current.items[index];val (file,source)=service.sourceFile(item.fileId)
                require(file.sha256==item.sourceSHA256 && file.size==item.size) {"Source version differs from the frozen preview."}
                val output=target(current,item)
                RawPackages.ensureParents(root,output)
                if(Files.exists(output,LinkOption.NOFOLLOW_LINKS)) {
                    require(sha256(output)==item.sourceSHA256) {"A destination file differs; it was not overwritten: ${item.targetName}"}
                    if(item.publishedKey!=null) require(fileKey(output)==item.publishedKey) {"A newer file replaced this output. Recovery needs review."}
                    else require(item.stages.any {stage ->runCatching {val p=localPath(root,stage);Files.isSameFile(p,output) && sha256(p)==item.sourceSHA256}.getOrDefault(false)}) {"Existing output cannot be proven to belong to this job."}
                } else {
                    // Interrupted partial files are retained. A resumed copy always receives a fresh CREATE_NEW stage.
                    val stageName=".partial-"+identity();val stage=localPath(root,stageName)
                    item=item.copy(state="copying",stages=item.stages+stageName);current=persist(current.copy(items=current.items.toMutableList().also {it[index]=item}))
                    FileChannel.open(source,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS).use {input ->
                        FileChannel.open(stage,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS).use {outputChannel ->
                            val buffer=ByteBuffer.allocateDirect(1024*1024)
                            while(input.read(buffer)!=-1) {if(Thread.currentThread().isInterrupted) error("Copy cancelled. Source and journal retained.");buffer.flip();while(buffer.hasRemaining()) outputChannel.write(buffer);buffer.clear()};outputChannel.force(true)
                        }
                    }
                    checkpoint("copied",current)
                    require(Files.size(stage)==item.size && sha256(stage)==item.sourceSHA256 && sha256(source)==item.sourceSHA256) {"Byte verification failed. Partial copy retained; original never changed."}
                    item=item.copy(state="staged");current=persist(current.copy(items=current.items.toMutableList().also {it[index]=item}));checkpoint("staged",current)
                    // Hard-link publication is atomic and cannot replace a destination. Unsupported volumes fail safely.
                    try {Files.createLink(output,stage)} catch(e:Exception) {throw IllegalStateException("Destination cannot safely publish this verified copy (existing file or hard links unavailable). The staged copy and journal are retained; choose APFS/NTFS or another supported volume.",e)}
                    forceDirectory(output.parent);checkpoint("published",current)
                }
                require(sha256(output)==item.sourceSHA256) {"Published copy failed SHA-256 verification."}
                item=item.copy(state="bytes-verified",publishedKey=fileKey(output));current=persist(current.copy(items=current.items.toMutableList().also {it[index]=item}))
                // Stages are retained as recovery evidence until an explicit maintenance feature is implemented.
            }
            // Decode only after the entire package is present: primary readers may need later spans/sidecars.
            for(index in current.items.indices) {
                var item=current.items[index]
                if(item.verifyMedia) {
                    val clip=repo.all<Clip>("clip").single {it.id==item.clipId};service.source(clip)
                    val output=target(current,item);val probe=service.decoder.probe(output);require(service.decoder.equivalentMetadata(item.sourceProbe ?: clip.metadata,probe)) {"Copied package metadata differs from the frozen registered original."}
                    val proof=repo.home.resolve("copy-check-"+identity()+".png")
                    val (_,frameHash)=service.decoder.exactFrame(output,item.frame.index,proof);require(frameHash==item.frame.pngSHA256) {"Copied package's approved source frame differs."};Files.delete(proof)
                }
                item=item.copy(state="verified");current=persist(current.copy(items=current.items.toMutableList().also {it[index]=item}));checkpoint("verified",current)
            }
            val manifest=root.resolve("relink-manifest.json");val bytes=manifest(current)
            if(Files.exists(manifest,LinkOption.NOFOLLOW_LINKS)) require(Files.readAllBytes(manifest).contentEquals(bytes)) {"Existing relink manifest differs; it was preserved."} else writeNew(manifest,bytes)
            current=persist(current.copy(state="completed",error=null));repo.audit(id,"Copy verified","Every output passed SHA-256, metadata comparison and approved-frame decode. Original files retained.");current
        } catch(e:Exception) {persist(current.copy(state="blocked",error=e.message));throw e}
    }
    fun undo(id:String):CopyJob=repo.exclusive {
        var current=job(id);if(current.state=="undone") return@exclusive current
        val root=validateFolder(current)
        try {
            // Review the entire group before moving anything. Changed/replaced output files cause a full refusal.
            for(item in current.items) {
                val path=target(current,item)
                if(item.state=="undone") continue
                val recovered=item.undoRelative?.let {localPath(root,it)}?.takeIf {Files.exists(it,LinkOption.NOFOLLOW_LINKS)}
                val candidate=if(recovered!=null) {require(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) {"Undo recovery collision: a newer output exists. Both copies retained."};recovered} else path
                require(Files.exists(candidate,LinkOption.NOFOLLOW_LINKS) && sha256(candidate)==item.sourceSHA256) {"Undo refused: output is missing or changed. No newer file is overwritten."}
                if(item.publishedKey!=null) require(fileKey(candidate)==item.publishedKey) {"Undo refused: output identity changed."}
                else require(item.stages.any {stage ->runCatching {val p=localPath(root,stage);Files.isSameFile(p,candidate) && sha256(p)==item.sourceSHA256}.getOrDefault(false)}) {"Undo refused: output ownership could not be established."}
            }
            current=persist(current.copy(state="undoing",error=null))
            val undoRoot=root.resolve(".undone");if(!Files.exists(undoRoot)) Files.createDirectory(undoRoot);canonical(undoRoot)
            for(index in current.items.indices) {
                var item=current.items[index];if(item.state=="undone") continue
                val relative=item.undoRelative ?: (".undone/"+current.id+"/"+item.targetName)
                item=item.copy(undoRelative=relative);current=persist(current.copy(items=current.items.toMutableList().also {it[index]=item}))
                val from=target(current,item);val to=localPath(root,relative)
                RawPackages.ensureParents(root,to)
                if(Files.exists(to,LinkOption.NOFOLLOW_LINKS)) {require(!Files.exists(from) && sha256(to)==item.sourceSHA256) {"Undo recovery collision; every file was retained."}}
                else {
                    Files.move(from,to,StandardCopyOption.ATOMIC_MOVE)
                    require(sha256(to)==item.sourceSHA256 && (item.publishedKey==null || fileKey(to)==item.publishedKey)) {"Output changed during undo. It was retained in the recovery folder; review required."}
                }
                checkpoint("undo-moved",current)
                item=item.copy(state="undone");current=persist(current.copy(items=current.items.toMutableList().also {it[index]=item}));forceDirectory(root);checkpoint("undone-item",current)
            }
            current=persist(current.copy(state="undone",error=null));repo.audit(id,"Undo copies","Verified outputs moved to this batch's .undone recovery folder. Originals and manifests retained; no files deleted.");current
        } catch(e:Exception) {persist(current.copy(error=e.message));throw e}
    }
}
