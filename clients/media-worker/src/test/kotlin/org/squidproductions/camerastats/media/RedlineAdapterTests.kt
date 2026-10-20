package org.squidproductions.camerastats.media

import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.*
import kotlin.test.*

/** Metadata and argument fixtures below are parser fixtures, not encoded R3D footage. */
class RedlineAdapterTests {
    private val csv="""REDline diagnostic banner
"Clip Name","Frame Width","Frame Height","FPS","Record FPS","Total Frames","UUID","Abs TC","Edge TC","Camera Model","Kelvin"
"A001_C001","5760","3240","23.976","80","240","native-uuid","10:02:03:04","01:00:00:00","KOMODO-X","5600"
"""
    @Test fun playbackRateIsIndependentFromOffSpeedRecordRate() {
        val p=RedlineAdapter.parseMetadata(csv)
        assertEquals("24000/1001",p.rate);assertEquals("1001/24000",p.timeBase);assertEquals(240,p.frames)
        assertEquals("80",p.tags["Record FPS"]);assertEquals("native-uuid",p.tags["UUID"])
        assertEquals("10:02:03:04",p.tags["Abs TC"]);assertTrue(p.tags.getValue("timestampMeaning").contains("not native"))
        assertEquals("10.01",p.duration);assertTrue(p.readable)
    }
    @Test fun preservesQuotedCommasNewlinesAndEscapedQuotes() {
        val rows=RedlineAdapter.parseCSV("\"Camera\",\"Note\"\r\n\"A\",\"Lens, \"\"test\"\"\nsecond line\"\r\n")
        assertEquals(listOf("A","Lens, \"test\"\nsecond line"),rows[1])
        assertFails {RedlineAdapter.parseCSV("\"unterminated")}
    }
    @Test fun cannotSubstituteRecordRateOrAcceptAmbiguousMetadata() {
        assertFails {RedlineAdapter.parseMetadata(csv.replace("\"FPS\",", "\"Unrelated\","))}
        assertFails {RedlineAdapter.parseMetadata(csv.replace("\"240\"", "\"-1\""))}
        assertFails {RedlineAdapter.parseMetadata(csv.replace("\"Frame Height\"", "\"Frame Width\""))}
        assertFails {RedlineAdapter.parseMetadata(csv.replace("\"5760\"", "\"0\""))}
    }
    @Test fun repeatedVendorDisplayLabelsArePreservedButCriticalDuplicatesRejected() {
        val text=csv.replace("\"Kelvin\"","\"Lens\",\"Lens\"").replace("\"5600\"","\"first\",\"second\"")
        val p=RedlineAdapter.parseMetadata(text);assertEquals("first",p.tags["Lens"]);assertEquals("second",p.tags["Lens [2]"])
    }
    @Test fun rationalRatesAreBoundedAndMalformedRatesRejected() {
        assertEquals("24/1",RedlineAdapter.rateRational("48/2"))
        assertEquals("24000/1001",RedlineAdapter.rateRational("23.98"))
        assertEquals("100/1",RedlineAdapter.rateRational("100"));assertEquals("24000/1001",RedlineAdapter.rateRational("23.976024627685547"))
        listOf("0","-24","1/0","NaN","1e1000000","0.00000000000000000001","99999999999999999999/1","/","24000/1001/1").forEach {value->assertFails(value) {RedlineAdapter.rateRational(value)}}
    }
    @Test fun commandSelectsOneZeroBasedFrameIntoOwnedDirectoryWithoutTrimOrSidecarWrites() {
        val source=Path.of("/copied footage/A001_C001_001.R3D");val out=Path.of("/private cache/owned-123")
        val args=RedlineAdapter.frameArguments(source,17,out)
        fun arg(name:String)=args[args.indexOf(name)+1]
        assertEquals("17",arg("--start"));assertEquals("1",arg("--frameCount"));assertEquals("1",arg("--format"))
        assertEquals("3",RedlineAdapter.frameArguments(source,17,out,3).let {it[it.indexOf("--frameCount")+1]});assertEquals("0",arg("--renum"));assertEquals(out.toAbsolutePath().toString(),arg("--outDir"));assertEquals(out.resolve("frame").toAbsolutePath().toString(),arg("--o"))
        assertFalse(args.any {it.contains("trim",true) || it in setOf("--useRMD","--loadRMD","--loadRSX","--ALE","--cdlExport")})
        assertTrue("--noAudio" in args);assertFalse("--noRender" in RedlineAdapter.metadataArguments(source));assertTrue("--useMeta" in RedlineAdapter.metadataArguments(source))
        assertTrue(source.toAbsolutePath().toString() in args)
    }
    @Test fun installedToolIdentifiesItselfWhenExplicitlyOptedIn() {
        val tool=System.getenv("SHOT_REPORTER_REDLINE")?.let(Path::of)
        assumeTrue(tool!=null && Files.isExecutable(tool),"Set SHOT_REPORTER_REDLINE to exercise the separately installed official tool.")
        val adapter=RedlineAdapter(tool!!,Decoder(Path.of("unused"),Path.of("unused")))
        assertTrue(adapter.version().contains("REDline Build"))
    }
    @Test fun extensionAloneNeverMakesInvalidBytesReadableWithInstalledTool() {
        val tool=System.getenv("SHOT_REPORTER_REDLINE")?.let(Path::of)
        assumeTrue(tool!=null && Files.isExecutable(tool),"Installed REDline is required for invalid-file refusal.")
        val root=Files.createTempDirectory("shot-reporter-invalid-r3d-")
        val source=root.resolve("NOT_REAL.R3D");Files.writeString(source,"This is deliberately invalid test data, not RAW footage.")
        val original=sha256(source);val adapter=RedlineAdapter(tool!!,Decoder(Path.of("unused"),Path.of("unused")))
        val probe=adapter.probe(source);assertFalse(probe.readable);assertTrue(probe.issue.isNotBlank());assertEquals(original,sha256(source))
        assertEquals(listOf(source),Files.list(root).use {it.toList()})
    }
    @Test fun realR3DFramesAreDeterministicAndPreserveSourceWhenExplicitSampleProvided() {
        val tool=System.getenv("SHOT_REPORTER_REDLINE")?.let(Path::of)
        val ffmpeg=System.getenv("SHOT_REPORTER_FFMPEG")?.let(Path::of)
        val source=System.getenv("SHOT_REPORTER_R3D_SAMPLE")?.let(Path::of)
        assumeTrue(tool!=null && ffmpeg!=null && source!=null,"Actual R3D acceptance requires an explicitly provided permitted sample and installed tools.")
        val adapter=RedlineAdapter(tool!!,Decoder(ffmpeg!!,Path.of("unused")));val initialHash=sha256(source!!)
        val p=adapter.probe(source);assertTrue(p.readable,p.issue);assertTrue((p.frames ?: 0)>1)
        val cache=Files.createTempDirectory("shot-reporter-redline-test-")
        Files.writeString(cache.resolve("metadata.json"),mediaJSON.encodeToString(p))
        println("Private RED acceptance cache: $cache")
        val a=adapter.exactFrame(source,0,cache.resolve("a.png"));val b=adapter.exactFrame(source,0,cache.resolve("b.png"))
        val middleIndex=p.frames!!/2
        val middle=adapter.exactFrame(source,middleIndex,cache.resolve("middle.png"))
        val batch=adapter.frameBatch(source,middleIndex-1,3,cache.resolve("sequence"))
        assertEquals(listOf(middleIndex-1,middleIndex,middleIndex+1),batch.map {it.first})
        assertEquals(middle.second,sha256(batch[1].second.second))
        val finalIndex=p.frames!!-1;val end=adapter.exactFrame(source,finalIndex,cache.resolve("end.png"))
        assertEquals(a,b);assertEquals("0",a.first);assertEquals(finalIndex.toString(),end.first)
        assertEquals(initialHash,sha256(source));assertFails {adapter.exactFrame(source,p.frames!!,cache.resolve("invalid.png"))}
        // Retain these private local test images for human/vendor comparison; do not put vendor footage in Git.
    }
}
