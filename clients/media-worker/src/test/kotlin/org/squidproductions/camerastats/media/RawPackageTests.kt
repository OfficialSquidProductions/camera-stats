package org.squidproductions.camerastats.media

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.squidproductions.camerastats.core.*
import org.squidproductions.camerastats.storage.JdbcDriver
import java.nio.file.*
import kotlin.test.*

/** Package mechanics use synthetic regular files/MOV; these tests make no RAW decoder claim. */
class RawPackageTests {
    @TempDir lateinit var temp:Path
    @Test fun redInventoryIncludesUnknownSidecarsAndRejectsMissingSpansOrLinks() {
        val folder=Files.createDirectory(temp.resolve("A.RDC"));Files.write(folder.resolve("A_001.R3D"),byteArrayOf(1));Files.write(folder.resolve("A_002.R3D"),byteArrayOf(2));Files.write(folder.resolve("unknown.private"),byteArrayOf(3))
        val group=RawPackages.discover(temp).single();assertEquals(3,group.files.size);assertEquals("",group.issue)
        Files.move(folder.resolve("A_002.R3D"),folder.resolve("A_003.R3D"));assertTrue(RawPackages.discover(temp).single().issue.contains("sequence"))
        Files.move(folder.resolve("A_003.R3D"),folder.resolve("A_002.R3D"))
        Files.createSymbolicLink(folder.resolve("external.sidecar"),temp.resolve("outside"));assertTrue(RawPackages.discover(temp).single().issue.contains("links"))
    }
    @Test fun brawFolderScopeIsConservativeAndMultipleClipsAreNotGuessed() {
        val folder=Files.createDirectory(temp.resolve("braw"));Files.write(folder.resolve("clip.braw"),byteArrayOf(1));Files.write(folder.resolve("anything.txt"),byteArrayOf(2))
        assertEquals(2,RawPackages.discover(temp).single().files.size)
        Files.write(folder.resolve("second.braw"),byteArrayOf(3));assertTrue(RawPackages.discover(temp).isEmpty())
    }
    private fun syntheticGroup():Triple<MediaRepository,MediaService,Report> {
        val folder=Files.createDirectories(temp.resolve("source/Group.RDC"));Files.copy(Path.of(System.getProperty("sampleMedia")),folder.resolve("primary.mp4"));Files.write(folder.resolve("unknown.sidecar"),"sidecar fixture".toByteArray())
        val repo=MediaRepository(temp.resolve("project"));val decoder=Decoder(Path.of(checkNotNull(System.getenv("SHOT_REPORTER_FFMPEG"))),Path.of(checkNotNull(System.getenv("SHOT_REPORTER_FFPROBE"))),null);val svc=MediaService(repo,decoder)
        svc.register(folder.parent,"A","A001","load")
        val clip=repo.snapshot().clips.single();val extra=folder.resolve("unknown.sidecar");val member=PhysicalFile(rootId=clip.rootId,relative="Group.RDC/unknown.sidecar",size=Files.size(extra),sha256=sha256(extra),modified=0)
        repo.put("file",member.id,member);val files=repo.all<PhysicalFile>("file");val group=clip.copy(memberFileIds=files.map {it.id},packageRoot="Group.RDC",packageKind="synthetic-package",packageFingerprint=RawPackages.fingerprint(files))
        repo.put("clip",group.id,group)
        val report=Exchange.read(Files.readAllBytes(Path.of(System.getProperty("fixtures"),"swift-demo.camreport"))).report.let {it.copy(entries=listOf(it.entries.first()))}
        val frame=svc.frame(group.id,7);svc.approve(group.id,frame.id,"3A","2");svc.link(group.id,report,report.active.single().id)
        return Triple(repo,svc,report)
    }
    private fun prepare(repo:MediaRepository,svc:MediaService,report:Report):CopyJob {
        val clip=repo.snapshot().clips.single();svc.approvePackage(clip.id,clip.packageFingerprint!!)
        return CopyJobs(svc).prepare(listOf(clip.id),report,NamingPreset(name="fixture",template="Package-{asset}"),Files.createDirectory(temp.resolve("exports")))
    }
    @Test fun groupApprovalWholePackageCopyCrashRecoveryAndUndoPreserveStructure() {
        val (repo,svc,report)=syntheticGroup();repo.use {
            val clip=repo.snapshot().clips.single();val dest=Files.createDirectory(temp.resolve("early"))
            assertFails {CopyJobs(svc).prepare(listOf(clip.id),report,NamingPreset(name="fixture",template="Package-{asset}"),dest)}
            val plan=prepare(repo,svc,report);assertEquals(2,plan.items.size);assertEquals(1,plan.items.count {it.verifyMedia})
            assertFailsWith<PowerLoss> {CopyJobs(svc) {point,_->if(point=="published") throw PowerLoss()}.execute(plan.id)}
            val done=CopyJobs(svc).execute(plan.id);assertEquals("completed",done.state)
            done.items.forEach {assertEquals(it.sourceSHA256,sha256(Path.of(done.destination).resolve(it.targetName)))}
            assertFailsWith<PowerLoss> {CopyJobs(svc) {point,_->if(point=="undo-moved") throw PowerLoss()}.undo(plan.id)}
            val undone=CopyJobs(svc).undo(plan.id);assertEquals("undone",undone.state)
            undone.items.forEach {assertEquals(".undone/${plan.id}/${it.targetName}",it.undoRelative);assertEquals(it.sourceSHA256,sha256(Path.of(plan.destination).resolve(it.undoRelative!!)))}
            assertEquals("sidecar fixture",Files.readString(temp.resolve("source/Group.RDC/unknown.sidecar")))
        }
    }
    @Test fun addedChangedOrRemovedPackageMemberBlocksFrozenCopyAndFrameEvidence() {
        val (repo,svc,report)=syntheticGroup();repo.use {
            val plan=prepare(repo,svc,report);val clip=repo.snapshot().clips.single();val extra=temp.resolve("source/Group.RDC/new.sidecar");Files.write(extra,byteArrayOf(8))
            assertFails {CopyJobs(svc).execute(plan.id)};assertFails {svc.frame(clip.id,0)};Files.delete(extra)
            Files.write(temp.resolve("source/Group.RDC/unknown.sidecar"),byteArrayOf(9));assertFails {CopyJobs(svc).execute(plan.id)}
            Files.delete(temp.resolve("source/Group.RDC/unknown.sidecar"));assertFails {svc.source(clip)}
        }
    }
    @Test fun v1MigrationKeepsConsistentRecoveryDatabaseAndUnknownRecords() {
        val home=Files.createDirectory(temp.resolve("old"));JdbcDriver(home.resolve("media.sqlite3")).use {db->db.execute("CREATE TABLE records(kind TEXT NOT NULL,id TEXT NOT NULL,json TEXT NOT NULL,PRIMARY KEY(kind,id))");db.execute("PRAGMA application_id=1397902660");db.execute("PRAGMA user_version=1");db.execute("INSERT INTO records VALUES('future','x','{\"keep\":true}')")}
        MediaRepository(home).use {assertEquals(listOf("{\"keep\":true}"),it.allJSON("future"))}
        val backup=Files.list(home).use {it.filter {p->p.fileName.toString().startsWith("media-before-v2-")}.toList()}.single()
        JdbcDriver(backup).use {db->assertEquals(1,(db.query("PRAGMA user_version").single().single() as Number).toInt());assertEquals("{\"keep\":true}",db.query("SELECT json FROM records").single().single())}
        MediaRepository(home).close();assertEquals(1,Files.list(home).use {it.filter {p->p.fileName.toString().startsWith("media-before-v2-")}.count()})
    }
}
