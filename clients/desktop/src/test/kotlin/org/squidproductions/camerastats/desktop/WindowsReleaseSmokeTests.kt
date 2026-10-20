package org.squidproductions.camerastats.desktop

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import org.squidproductions.camerastats.media.Decoder
import java.nio.file.*
import kotlin.test.*

class WindowsReleaseSmokeTests {
    @TempDir lateinit var output:Path
    @Test fun releaseSmokeActuallyCompletesExactFramesCopyAndUndoWithSyntheticFootage() {
        val ffmpeg=System.getenv("SHOT_REPORTER_FFMPEG");val ffprobe=System.getenv("SHOT_REPORTER_FFPROBE")
        assumeTrue(ffmpeg!=null && ffprobe!=null,"Explicit local CI decoder tools required; no automatic tool download.")
        val destination=output.resolve("fresh-smoke")
        windowsDesktopReleaseSmoke(destination,Path.of(checkNotNull(System.getProperty("sampleMedia"))),Decoder(Path.of(ffmpeg),Path.of(ffprobe)))
        assertTrue(Files.readString(destination.resolve("PASS.txt")).contains("unchanged synthetic originals"))
        assertTrue(Files.isRegularFile(destination.resolve("synthetic-source/A001_C001.mp4")))
        verifyPriorDesktopMedia(destination.resolve("media"))
    }
}
