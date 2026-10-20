package org.squidproductions.camerastats.media

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import org.squidproductions.camerastats.core.*
import java.nio.file.*
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.*
import java.io.File
import java.util.concurrent.TimeUnit
import org.squidproductions.camerastats.storage.JdbcDriver

class PowerLoss:Error("simulated power loss")
class MediaTests {
    @TempDir lateinit var temp:Path
    private fun decoder()=Decoder(Path.of(checkNotNull(System.getenv("SHOT_REPORTER_FFMPEG"))),Path.of(checkNotNull(System.getenv("SHOT_REPORTER_FFPROBE"))))
    private fun seed():Triple<MediaRepository,MediaService,Report> {
        val source=temp.resolve("source");Files.createDirectory(source)
        Files.copy(Path.of(checkNotNull(System.getProperty("sampleMedia"))),source.resolve("A001_C001.mp4"))
        val repo=MediaRepository(temp.resolve("project"));val service=MediaService(repo,decoder())
        service.register(source,"A","A001","load-1")
        val original=Exchange.read(File(System.getProperty("fixtures"),"swift-demo.camreport").readBytes()).report
        val entry=original.entries.first().copy(values=original.entries.first().values+mapOf("originalFilename" to "A001_C001.mp4","camera" to "A","roll" to "A001","load" to "load-1"))
        val report=original.copy(entries=listOf(entry));return Triple(repo,service,report)
    }
    private fun approve(repo:MediaRepository,service:MediaService,report:Report):Clip {
        val clip=repo.snapshot().clips.single();assertTrue(clip.metadata.readable,clip.metadata.issue)
        val frame=service.frame(clip.id,7);assertEquals(7,frame.index);assertEquals("1/12288",frame.timeBase)
        service.approve(clip.id,frame.id,"3A","2");service.link(clip.id,report,report.active.single().id);return clip
    }
    private fun destination()=temp.resolve("exports").also {Files.createDirectory(it)}
    @Test fun completeReadReviewMatchCopyReopenAndUndoKeepsOriginals() {
        val (repo,service,report)=seed();repo.use {
            val original=temp.resolve("source/A001_C001.mp4");val sourceHash=sha256(original);val clip=approve(repo,service,report)
            val suggestions=service.suggestions(clip.id,report);assertTrue(suggestions.single().reasons.contains("Exact recorded source filename"));assertTrue(repo.snapshot().associations.single().reasons.contains("Explicit user-reviewed association"))
            val preset=service.savePreset("Movie",DesktopNaming.movie);val engine=CopyJobs(service)
            val plan=engine.prepare(listOf(clip.id),report,preset,destination());assertFalse(Files.exists(Path.of(plan.destination)));assertTrue(plan.items.single().targetName.contains("SC3A_TK002_CAM-A"))
            val completed=engine.execute(plan.id);assertEquals("completed",completed.state)
            val output=Path.of(plan.destination).resolve(completed.items.single().targetName);assertEquals(sourceHash,sha256(output));assertEquals(sourceHash,sha256(original));assertTrue(Files.exists(output.parent.resolve("relink-manifest.json")))
            MediaRepository(repo.home).use {other ->assertEquals("completed",other.snapshot().jobs.single().state);assertEquals(clip.id,other.snapshot().slates.single().frame.clipId)}
            val undone=engine.undo(plan.id);assertEquals("undone",undone.state);assertFalse(Files.exists(output));assertEquals(sourceHash,sha256(original));assertEquals(sourceHash,sha256(Path.of(plan.destination).resolve(undone.items.single().undoRelative!!)))
        }
    }
    @Test fun exactFramesHaveDifferentTimestampsAndKnownVisualIdentity() {
        val (repo,service,_)=seed();repo.use {
            val clip=repo.snapshot().clips.single();val a=service.frame(clip.id,0);val b=service.frame(clip.id,23)
            assertNotEquals(a.pts,b.pts);assertEquals("0",a.pts)
            val first=ImageIO.read(repo.home.resolve(a.png).toFile());val last=ImageIO.read(repo.home.resolve(b.png).toFile())
            assertTrue(Color(first.getRGB(15,15)).red>180);assertTrue(Color(last.getRGB(15,15)).blue>180)
            assertFails {service.frame(clip.id,9999)}
        }
    }
    @Test fun ambiguousMatchesNeverLinkAndBadSlatesNeverPrepareCopies() {
        val (repo,service,report)=seed();repo.use {
            val clip=repo.snapshot().clips.single();val duplicate=report.copy(entries=report.entries+report.entries.single().copy(id=identity()))
            assertTrue(service.suggestions(clip.id,duplicate).all {it.ambiguous});assertTrue(repo.snapshot().associations.isEmpty())
            val preset=NamingPreset(name="Movie",template=DesktopNaming.movie)
            assertFails {CopyJobs(service).prepare(listOf(clip.id),report,preset,destination())}
            val frame=service.frame(clip.id,0);assertFails {service.approve(clip.id,frame.id,"","2")}
        }
    }
    @Test fun staleReportAndSourceChangesBlockCopies() {
        val (repo,service,report)=seed();repo.use {
            val clip=approve(repo,service,report);val target=destination();val engine=CopyJobs(service);val preset=NamingPreset(name="Movie",template=DesktopNaming.movie)
            assertFails {engine.prepare(listOf(clip.id),report.copy(revision=report.revision+1),preset,target)}
            val job=engine.prepare(listOf(clip.id),report,preset,target)
            Files.write(temp.resolve("source/A001_C001.mp4"),byteArrayOf(1,2,3));assertFails {engine.execute(job.id)}
            assertEquals("blocked",repo.snapshot().jobs.single().state);assertFalse(Files.exists(Path.of(job.destination).resolve(job.items.single().targetName)))
        }
    }
    @Test fun powerLossAfterPublishRecoversWithoutOverwriteOrDuplicate() {
        val (repo,service,report)=seed();repo.use {
            val clip=approve(repo,service,report);val job=CopyJobs(service).prepare(listOf(clip.id),report,NamingPreset(name="Movie",template=DesktopNaming.movie),destination())
            assertFailsWith<PowerLoss> {CopyJobs(service) {point,_->if(point=="published") throw PowerLoss()}.execute(job.id)}
            val unfinished=repo.snapshot().jobs.single();assertEquals("staged",unfinished.items.single().state)
            val done=CopyJobs(service).execute(job.id);assertEquals("completed",done.state);assertEquals(1,done.items.single().stages.size)
        }
    }
    @Test fun interruptedPartialIsRetainedAndResumeUsesNewStage() {
        val (repo,service,report)=seed();repo.use {
            val clip=approve(repo,service,report);val job=CopyJobs(service).prepare(listOf(clip.id),report,NamingPreset(name="Movie",template=DesktopNaming.movie),destination())
            assertFailsWith<PowerLoss> {CopyJobs(service) {point,_->if(point=="copied") throw PowerLoss()}.execute(job.id)}
            val stage=Path.of(job.destination).resolve(repo.snapshot().jobs.single().items.single().stages.single());Files.write(stage,byteArrayOf(4,5,6))
            val done=CopyJobs(service).execute(job.id);assertEquals("completed",done.state);assertEquals(2,done.items.single().stages.size);assertContentEquals(byteArrayOf(4,5,6),Files.readAllBytes(stage))
        }
    }
    @Test fun changedOutputRefusesUndoAndCrashDuringUndoResumesSafely() {
        val (repo,service,report)=seed();repo.use {
            val clip=approve(repo,service,report);val engine=CopyJobs(service);val job=engine.prepare(listOf(clip.id),report,NamingPreset(name="Movie",template=DesktopNaming.movie),destination());val complete=engine.execute(job.id)
            val output=Path.of(job.destination).resolve(complete.items.single().targetName);val bytes=Files.readAllBytes(output)
            Files.write(output,"newer user data".toByteArray());assertFails {engine.undo(job.id)};assertEquals("newer user data",Files.readString(output))
            Files.write(output,bytes)
            assertFailsWith<PowerLoss> {CopyJobs(service) {point,_->if(point=="undo-moved") throw PowerLoss()}.undo(job.id)}
            assertEquals("undone",engine.undo(job.id).state)
        }
    }
    @Test fun rawAndSidecarGroupsStayRegistrationOnly() {
        val (repo,service,report)=seed();repo.use {
            val source=temp.resolve("source");Files.write(source.resolve("B001.R3D"),byteArrayOf(1,2));Files.write(source.resolve("A001_C001.xmp"),"<fixture/>".toByteArray())
            service.register(source,"A","A001","load-1")
            val raw=repo.snapshot().clips.single {it.originalName.endsWith("R3D")};assertFalse(raw.metadata.readable);assertFails {service.frame(raw.id,0)}
            val existing=repo.snapshot().clips.single {it.originalName.endsWith("mp4")};assertTrue(existing.companions.contains("A001_C001.xmp"))
        }
    }
    @Test fun relinkPreservesIdentityAndRejectsWrongCopyAndEscapingSymlinks() {
        val (repo,service,_)=seed();repo.use {
            val old=repo.snapshot();val copy=temp.resolve("relinked");Files.createDirectory(copy);Files.copy(temp.resolve("source/A001_C001.mp4"),copy.resolve("A001_C001.mp4"))
            service.relinkRoot(old.roots.single().id,copy);assertEquals(old.clips,repo.snapshot().clips)
            Files.write(copy.resolve("A001_C001.mp4"),byteArrayOf(7));assertFails {service.relinkRoot(old.roots.single().id,copy)}
            assertFails {localPath(copy,"../outside.mp4")}
            runCatching {Files.createSymbolicLink(copy.resolve("outside"),temp)}.onSuccess {assertFails {localPath(copy,"outside/anything.mp4")}}
        }
    }
    @Test fun namingIsDeclarativePortableAndCollisionAware() {
        assertEquals("Harbor_SC12B_TK003",DesktopNaming.evaluate("{project}_SC{scene}_TK{take:03}",mapOf("project" to "Harbor","scene" to "12B","take" to "3")))
        for(template in listOf("CON","NUL.mp4","bad/escape","{episode:02}","{take:99}","$(whoami)","{unknown}")) {
            if(template=="$(whoami)") assertEquals(template,DesktopNaming.evaluate(template,emptyMap())) else assertFails {DesktopNaming.evaluate(template,mapOf("take" to "2"))}
        }
        assertEquals(collisionKey("CAFÉ.mp4"),collisionKey("cafe\u0301.MP4"))
        assertFails {DesktopNaming.evaluate("{take:03}",mapOf("take" to "3A"))}
    }
    @Test fun sequentialReviewMapsEveryPresentationFrameToExactEvidence() {
        val (repo,service,_)=seed();repo.use {
            val clip=repo.snapshot().clips.single();val frames=service.frames(clip.id,0,48)
            assertEquals((0L..23L).toList(),frames.map {it.index})
            assertEquals((0L..23L).map {(it*1024).toString()},frames.map {it.pts})
            for(index in listOf(0,7,12,23)) assertEquals(service.frame(clip.id,index.toLong()).pngSHA256,frames[index].pngSHA256)
            assertEquals(listOf(22L,23L),service.frames(clip.id,22,48).map {it.index})
        }
    }
    @Test fun nonzeroSourcePresentationTimestampsArePreservedInsteadOfRebased() {
        val output=temp.resolve("offset.mp4");val decoder=decoder()
        decoder.execute(decoder.ffmpeg,listOf("-hide_banner","-nostdin","-v","error","-protocol_whitelist","file,pipe","-itsoffset","10","-i",System.getProperty("sampleMedia"),"-map","0:v:0","-c:v","copy","-copyts","-f","mp4",output.toString()))
        val first=decoder.exactFrame(output,0,temp.resolve("first.png"));val seventh=decoder.exactFrame(output,7,temp.resolve("seventh.png"))
        assertEquals("122880",first.first);assertEquals("130048",seventh.first)
        val batch=decoder.frameBatch(output,0,24,temp.resolve("batch"));assertEquals(first.first,batch.first().second.first);assertEquals(seventh.first,batch[7].second.first)
        val unshifted=decoder.exactFrame(Path.of(System.getProperty("sampleMedia")),7,temp.resolve("unshifted.png"));assertEquals(unshifted.second,seventh.second)
    }
    @Test fun mismatchedSlateAndLoadRemainVisibleInExplicitAssociationHistory() {
        val (repo,service,report)=seed();repo.use {
            val clip=approve(repo,service,report);val changed=report.copy(entries=report.entries.map {it.copy(values=it.values+mapOf("scene" to "DIFFERENT","take" to "99","load" to "another-load"))})
            val suggestion=service.suggestions(clip.id,changed).single()
            assertTrue(suggestion.conflicts.any {it.contains("slate scene")});assertTrue(suggestion.conflicts.any {it.contains("slate take")});assertTrue(suggestion.conflicts.contains("Media-load identity differs"))
            service.link(clip.id,changed,changed.active.single().id)
            assertTrue(repo.snapshot().associations.single().reasons.any {it.startsWith("Acknowledged discrepancy: Confirmed slate take")})
        }
    }
    @Test fun syntheticH264MOVAndMP4PassExactFrameAndRenamedCopyVerification() {
        for(extension in listOf("mov","mp4")) {
            val folder=Files.createDirectory(temp.resolve("h264-$extension"));val source=Files.createDirectory(folder.resolve("source"));val input=source.resolve("A001_H264.$extension")
            Files.copy(Path.of(System.getProperty("sampleMedia")).parent.resolve("h264-numbered-24.$extension"),input)
            MediaRepository(folder.resolve("project")).use {repo->
                val service=MediaService(repo,decoder());service.register(source,"A","A001","h264-$extension")
                val clip=repo.snapshot().clips.single();assertEquals("h264",clip.metadata.codec);assertTrue(clip.metadata.readable)
                val frames=service.frames(clip.id,0,48);assertEquals(24,frames.size)
                val frame=service.frame(clip.id,7);assertEquals(frame.pngSHA256,frames[7].pngSHA256)
                val timeBase=frame.timeBase.split('/').map {it.toDouble()};assertEquals(7.0/12,frame.pts.toDouble()*timeBase[0]/timeBase[1],0.000001)
                val first=ImageIO.read(repo.home.resolve(frames.first().png).toFile());val last=ImageIO.read(repo.home.resolve(frames.last().png).toFile())
                assertTrue(Color(first.getRGB(15,15)).red>180);assertTrue(Color(last.getRGB(15,15)).blue>180)
                service.approve(clip.id,frame.id,"3A","2")
                val report=Exchange.read(File(System.getProperty("fixtures"),"swift-demo.camreport").readBytes()).report
                service.link(clip.id,report,report.active.first().id)
                val engine=CopyJobs(service);val job=engine.prepare(listOf(clip.id),report,NamingPreset(name="Movie",template=DesktopNaming.movie),Files.createDirectory(folder.resolve("copies")))
                assertEquals("completed",engine.execute(job.id).state);assertEquals(sha256(input),sha256(Path.of(job.destination).resolve(job.items.single().targetName)))
            }
        }
    }
    @Test fun presetVersionsAreImmutableAndPreflightSavesTheirFullRules() {
        val (repo,service,report)=seed();repo.use {
            val clip=approve(repo,service,report);val engine=CopyJobs(service);val folder=destination()
            val one=service.savePreset("My movie",DesktopNaming.movie)
            val first=engine.prepare(listOf(clip.id),report,one,folder)
            val two=service.savePreset("My revision","{original}_SC{scene}_T{take:02}",one.id)
            assertEquals(one.id,two.id);assertEquals(2,two.version)
            engine.prepare(listOf(clip.id),report,two,folder)
            assertEquals(one,repo.snapshot().jobs.single {it.id==first.id}.preset)
            assertEquals(listOf(one,two),repo.all<NamingPreset>("presetVersion").sortedBy {it.version})
            assertFails {engine.prepare(listOf(clip.id),report,two.copy(template="changed"),folder)}
            assertEquals(2,repo.snapshot().jobs.size)
        }
    }
    private fun addSecond(repo:MediaRepository,service:MediaService,report:Report):Clip {
        Files.copy(temp.resolve("source/A001_C001.mp4"),temp.resolve("source/A001_C002.mp4"))
        service.register(temp.resolve("source"),"A","A001","load-1")
        val clip=repo.snapshot().clips.single {it.originalName=="A001_C002.mp4"}
        val frame=service.frame(clip.id,7);service.approve(clip.id,frame.id,"3A","2");service.link(clip.id,report,report.active.single().id)
        return clip
    }
    @Test fun batchCollisionBlocksEverythingAndUndoChecksEntireBatchFirst() {
        val (repo,service,report)=seed();repo.use {
            val first=approve(repo,service,report);val second=addSecond(repo,service,report);val engine=CopyJobs(service);val folder=destination()
            assertFails {engine.prepare(listOf(first.id,second.id),report,NamingPreset(name="Collision",template="{scene}_{take}"),folder)}
            assertTrue(repo.snapshot().jobs.isEmpty());assertEquals(0,Files.list(folder).use {it.count()})
            val job=engine.prepare(listOf(first.id,second.id),report,NamingPreset(name="Movie",template=DesktopNaming.movie),folder)
            val done=engine.execute(job.id);val outputs=done.items.map {Path.of(done.destination).resolve(it.targetName)}
            Files.writeString(outputs[1],"a subsequent user edit")
            assertFails {engine.undo(job.id)}
            assertEquals(done.items[0].sourceSHA256,sha256(outputs[0]));assertEquals("a subsequent user edit",Files.readString(outputs[1]))
            assertFalse(Files.exists(Path.of(done.destination).resolve(".undone")))
        }
    }
    @Test fun foreignDestinationFileWinsPublicationRaceAndIsNeverOverwritten() {
        val (repo,service,report)=seed();repo.use {
            val clip=approve(repo,service,report);val engine=CopyJobs(service);val job=engine.prepare(listOf(clip.id),report,NamingPreset(name="Movie",template=DesktopNaming.movie),destination())
            val output=Path.of(job.destination).resolve(job.items.single().targetName)
            assertFails {CopyJobs(service) {point,_->if(point=="staged") Files.writeString(output,"another user's file",StandardOpenOption.CREATE_NEW)}.execute(job.id)}
            assertEquals("another user's file",Files.readString(output));assertEquals("blocked",repo.snapshot().jobs.single().state)
            assertFails {engine.execute(job.id)};assertFails {engine.undo(job.id)}
            assertEquals("another user's file",Files.readString(output));assertTrue(Files.exists(temp.resolve("source/A001_C001.mp4")))
        }
    }
    @Test fun abruptJVMDeathAfterPublicationAndDuringUndoRecoversFromDurableJournal() {
        val (repo,service,report)=seed();repo.use {
            val clip=approve(repo,service,report);val engine=CopyJobs(service);val job=engine.prepare(listOf(clip.id),report,NamingPreset(name="Movie",template=DesktopNaming.movie),destination())
            fun crash(point:String) {
                val java=Path.of(System.getProperty("java.home"),"bin",if(System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java").toString()
                val process=ProcessBuilder(java,"-cp",System.getProperty("workerTestClasspath"),"org.squidproductions.camerastats.media.CrashHarnessKt",repo.home.toString(),job.id,decoder().ffmpeg.toString(),decoder().ffprobe.toString(),point).redirectErrorStream(true).redirectOutput(temp.resolve("crash-$point.log").toFile()).start()
                try {assertTrue(process.waitFor(45,TimeUnit.SECONDS));assertEquals(73,process.exitValue(),Files.readString(temp.resolve("crash-$point.log")))} finally {process.destroyForcibly()}
            }
            crash("published");assertEquals("staged",repo.snapshot().jobs.single().items.single().state)
            assertEquals("completed",engine.execute(job.id).state)
            crash("undo-moved");assertEquals("undoing",repo.snapshot().jobs.single().state)
            assertEquals("undone",engine.undo(job.id).state)
            assertEquals(job.items.single().sourceSHA256,sha256(temp.resolve("source/A001_C001.mp4")))
        }
    }
    @Test fun unrecognizedDatabaseIdentityIsRejectedBeforeSchemaOrJournalChanges() {
        val home=temp.resolve("foreign");Files.createDirectory(home);val path=home.resolve("media.sqlite3")
        JdbcDriver(path).use {db->db.execute("CREATE TABLE private_data(value TEXT)");db.execute("INSERT INTO private_data VALUES('preserve me')")}
        val hash=sha256(path);assertFails {MediaRepository(home)};assertEquals(hash,sha256(path))
        JdbcDriver(path).use {db->assertEquals("preserve me",db.query("SELECT value FROM private_data").single().single())}
        val owned=temp.resolve("newer");MediaRepository(owned).close()
        JdbcDriver(owned.resolve("media.sqlite3")).use {it.execute("PRAGMA user_version=99")}
        val newerHash=sha256(owned.resolve("media.sqlite3"));assertFails {MediaRepository(owned)};assertEquals(newerHash,sha256(owned.resolve("media.sqlite3")))
    }
}
