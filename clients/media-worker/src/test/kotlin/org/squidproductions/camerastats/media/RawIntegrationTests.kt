package org.squidproductions.camerastats.media

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.squidproductions.camerastats.core.*
import java.nio.file.*
import kotlin.test.*

/** Opt-in local acceptance. Vendor media never becomes a public fixture. */
class RawIntegrationTests {
    @TempDir lateinit var temp:Path
    private fun decoder():Decoder=Decoder(Path.of(checkNotNull(System.getenv("SHOT_REPORTER_FFMPEG"))),Path.of(checkNotNull(System.getenv("SHOT_REPORTER_FFPROBE"))))
    private fun report():Report=Exchange.read(Files.readAllBytes(Path.of(System.getProperty("fixtures"),"swift-demo.camreport"))).report.let {it.copy(entries=listOf(it.entries.first()))}
    private fun sample(variable:String):Path {val value=System.getenv(variable);assumeTrue(value!=null,"Authorized local RAW fixture $variable is not configured.");return Path.of(value!!)}
    private fun workflow(source:Path,kind:String,index:Long) {
        val decoder=decoder();val report=report();val before=Exchange.encode(report)
        val originals=RawPackages.files(source).associateWith(::sha256)
        val home=temp.resolve("project")
        var id="";var jobID="";var output=""
        MediaRepository(home).use {repo->
            val service=MediaService(repo,decoder);val snapshot=service.register(source,"A","A001","authorized-raw-fixture")
            val clip=snapshot.clips.single();id=clip.id;assertEquals(kind,clip.packageKind);assertTrue(clip.metadata.readable,clip.metadata.issue);assertTrue(clip.packageIssue.isEmpty())
            val frame=service.frame(clip.id,index);assertEquals(index,frame.index);assertEquals(index.toString(),frame.pts);assertEquals(clip.packageFingerprint,frame.packageFingerprint)
            // Deliberate fixture labels for exercising approval/linking; no slate/OCR result is inferred.
            service.approve(clip.id,frame.id,"TEST","1");service.link(clip.id,report,report.active.single().id)
            if(kind=="red-rdc") assertFails {service.approvePackage(clip.id,clip.packageFingerprint!!)}
            service.approvePackage(clip.id,clip.packageFingerprint!!,completeSourceConfirmed=true)
            val job=CopyJobs(service).prepare(listOf(clip.id),report,NamingPreset(name="RAW fixture",template="R-{asset}"),Files.createDirectory(temp.resolve("exports")))
            jobID=job.id;output=job.destination;assertEquals(originals.size,job.items.size);assertEquals(1,job.items.count {it.verifyMedia})
            val done=CopyJobs(service).execute(job.id);assertEquals("completed",done.state)
            val primary=done.items.single {it.verifyMedia};val copied=Path.of(done.destination).resolve(primary.targetName)
            assertTrue(decoder.equivalentMetadata(clip.metadata,decoder.probe(copied)))
            assertEquals(frame.pngSHA256,decoder.exactFrame(copied,index,temp.resolve("proof.png")).second)
            done.items.forEach {assertEquals(it.sourceSHA256,sha256(Path.of(done.destination).resolve(it.targetName)));assertTrue(it.targetName.endsWith(Path.of(it.sourceRelative).fileName.toString()))}
        }
        MediaRepository(home).use {repo->
            assertEquals(id,repo.snapshot().clips.single().id);assertEquals("completed",repo.snapshot().jobs.single().state)
            val undone=CopyJobs(MediaService(repo,decoder)).undo(jobID);assertEquals("undone",undone.state)
            undone.items.forEach {assertFalse(Files.exists(Path.of(output).resolve(it.targetName)));assertEquals(it.sourceSHA256,sha256(Path.of(output).resolve(it.undoRelative!!)))}
        }
        originals.forEach {(file,hash)->assertEquals(hash,sha256(file))};assertContentEquals(before,Exchange.encode(report))
    }
    @Test fun actualBrawSdkClipAndSidecarCompleteCopyReopenAndUndo() {
        val file=sample("SHOT_REPORTER_BRAW_SAMPLE");assumeTrue(System.getenv("SHOT_REPORTER_BRAW_HELPER")!=null)
        // Isolate from the rest of the SDK media directory; copy only the authorized clip and its sidecar.
        val source=Files.createDirectory(temp.resolve("braw-source")).toRealPath();Files.copy(file,source.resolve(file.fileName))
        val sidecar=file.resolveSibling(file.fileName.toString().substringBeforeLast('.')+".sidecar");if(Files.exists(sidecar)) Files.copy(sidecar,source.resolve(sidecar.fileName))
        val decoder=decoder();val probe=decoder.probe(source.resolve(file.fileName));assertTrue(probe.readable,probe.issue)
        val first=decoder.exactFrame(source.resolve(file.fileName),0,temp.resolve("first.png"));assertEquals("0",first.first)
        assertEquals(first.second,decoder.exactFrame(source.resolve(file.fileName),0,temp.resolve("repeat.png")).second)
        assertFails {decoder.exactFrame(source.resolve(file.fileName),probe.frames!!,temp.resolve("outside.png"))}
        assertFails {decoder.exactFrame(source.resolve(file.fileName),0,temp.resolve("first.png"))}
        val batch=decoder.frameBatch(source.resolve(file.fileName),0,8,temp.resolve("sequence"));assertEquals(minOf(8L,probe.frames!!).toInt(),batch.size);assertEquals(first.second,sha256(batch.first().second.second))
        workflow(source,"braw-folder",0)
    }
    @Test fun actualRedTwoSpanPackageCompleteCopyReopenAndUndo() {
        val primary=sample("SHOT_REPORTER_R3D_SAMPLE");assumeTrue(System.getenv("SHOT_REPORTER_REDLINE")!=null)
        assertTrue(primary.parent.fileName.toString().endsWith(".RDC",true));assertEquals(2,RawPackages.files(primary.parent).count {it.fileName.toString().endsWith(".R3D",true)})
        workflow(primary.parent,"red-rdc",3071)
    }
    @Test fun relocationComparisonIgnoresOnlyRedLocationAndKeepsNativeIdentity() {
        val decoder=Decoder(Path.of("unused"),Path.of("unused"),null,null)
        val a=Probe(codec="redcode",container="r3d",tags=mapOf("File Path" to "/a","UUID" to "one"),readable=true)
        assertTrue(decoder.equivalentMetadata(a,a.copy(tags=a.tags+("File Path" to "/b"))))
        assertFalse(decoder.equivalentMetadata(a,a.copy(tags=a.tags+("UUID" to "two"))))
        assertFalse(decoder.equivalentMetadata(a,a.copy(frames=2)))
        val b=a.copy(codec="braw",container="braw");assertFalse(decoder.equivalentMetadata(b,b.copy(tags=b.tags+("File Path" to "/b"))))
    }
}
