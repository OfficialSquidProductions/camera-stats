package org.squidproductions.camerastats.desktop

import kotlinx.serialization.encodeToString
import org.squidproductions.camerastats.core.*
import org.squidproductions.camerastats.storage.JdbcDriver
import org.squidproductions.camerastats.media.*
import java.nio.file.*

/** Explicit command-line QA entry points use only supplied runner directories and fictional fixtures. */
fun windowsDesktopReleaseSmoke(output:Path,fixture:Path,providedDecoder:Decoder?=null) {
    check(!Files.exists(output));Files.createDirectories(output)
    val decoder=providedDecoder ?: Decoder(Path.of(checkNotNull(bundledMediaTool("ffmpeg")) {"Bundled verified FFmpeg is missing"}),Path.of(checkNotNull(bundledMediaTool("ffprobe")) {"Bundled verified ffprobe is missing"}))
    val ffmpeg=decoder.ffmpeg
    val version=decoder.execute(ffmpeg,listOf("-version"),timeout=10).bytes.toString(Charsets.UTF_8)
    check(version.contains("--disable-network") && version.contains("--disable-autodetect") && !version.contains("--enable-gpl") && !version.contains("--enable-nonfree"))
    val source=output.resolve("synthetic-source");Files.createDirectory(source);val original=source.resolve("A001_C001.mp4");Files.copy(fixture,original)
    val originalHash=sha256(original)
    val demo=DesktopGateway(output).demo().separateDemo();val base=demo.active.first {it.values["scene"]=="3A" && it.values["take"]=="2"}
    val entry=base.copy(values=base.values+mapOf("originalFilename" to original.fileName.toString(),"camera" to "A","roll" to "A001","load" to "release-load"))
    val report=demo.copy(entries=listOf(entry))
    MediaRepository(output.resolve("media")).use {repo->
        val service=MediaService(repo,decoder);service.register(source,"A","A001","release-load")
        val clip=repo.snapshot().clips.single();check(clip.metadata.readable) {clip.metadata.issue}
        val first=service.frame(clip.id,0);val last=service.frame(clip.id,23);check(first.pts!=last.pts && first.pngSHA256!=last.pngSHA256)
        check(service.frames(clip.id,4,8).map {it.index}==(4L..11L).toList())
        service.approve(clip.id,first.id,"3A","2");service.link(clip.id,report,entry.id)
        val destination=output.resolve("verified-copies");Files.createDirectory(destination)
        val jobs=CopyJobs(service);val job=jobs.prepare(listOf(clip.id),report,NamingPreset(name="Release fixture",template=DesktopNaming.movie),destination)
        val done=jobs.execute(job.id);check(done.state=="completed")
        val copied=Path.of(done.destination).resolve(done.items.single().targetName);check(sha256(copied)==originalHash)
        check(jobs.undo(job.id).state=="undone");check(sha256(original)==originalHash)
    }
    Files.writeString(output.resolve("PASS.txt"),"PASS: installed bundled LGPL FFmpeg/ffprobe hashes, exact frames/PTS/batch review, explicit match, verified copy, undo and unchanged synthetic originals. No RAW vendor runtime/hardware certification.\n")
}
fun seedDesktopUpgradeJournal() {
    val home=reportDataRoot().resolve("desktop-media/release-fixture");check(!Files.exists(home));Files.createDirectories(home)
    JdbcDriver(home.resolve("media.sqlite3")).use {db->
        db.execute("CREATE TABLE records(kind TEXT NOT NULL,id TEXT NOT NULL,json TEXT NOT NULL,PRIMARY KEY(kind,id))")
        db.execute("PRAGMA application_id=1397902660");db.execute("PRAGMA user_version=1")
        val audit=Audit(id="release-fixture",subject="synthetic-clip",action="interrupted-copy-fixture",detail="Synthetic prior desktop schema fixture; no camera media",time="2026-10-19T00:00:00Z")
        db.execute("INSERT INTO records VALUES('audit',?,?)",listOf(audit.id,mediaJSON.encodeToString(audit)))
    }
}
fun verifyDesktopUpgrade(expected:Path,output:Path) {
    verifyReportUpgrade(expected,output.resolve("report"))
    val home=reportDataRoot().resolve("desktop-media/release-fixture")
    MediaRepository(home).use {repo->check(repo.snapshot().audit.single().action=="interrupted-copy-fixture")}
    check(Files.list(home).use {it.anyMatch {path->path.fileName.toString().startsWith("media-before-v2-")}})
    verifyPriorDesktopMedia(reportDataRoot().resolve("desktop-media/alpha1-fixture"))
    Files.writeString(output.resolve("PASS.txt"),"PASS: previous public alpha1/MSI0.2.0 -> alpha2/MSI0.2.1; reports/history/preferences and actual alpha1 v2 media journal preserved; synthetic v1 migration kept independent prior-schema backup.\n")
}

/** Read back the real previous installed runtime's completed media workflow after the MSI upgrade. */
fun verifyPriorDesktopMedia(home:Path) {
    MediaRepository(home).use {repo->
        val state=repo.snapshot();val clip=state.clips.single();val file=state.files.single()
        check(state.roots.single().id==clip.rootId && file.id==clip.fileId)
        check(sha256(Path.of(state.roots.single().path).resolve(file.relative))==file.sha256)
        check(state.slates.single().let {it.clipId==clip.id && it.scene=="3A" && it.take=="2"})
        check(state.associations.single().clipId==clip.id)
        check(state.jobs.single().let {it.state=="undone" && it.items.single().sourceSHA256==file.sha256})
        check(state.audit.isNotEmpty())
    }
}
