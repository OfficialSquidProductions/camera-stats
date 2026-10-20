package org.squidproductions.camerastats.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import kotlin.test.*
import java.nio.file.*
import java.io.File
import org.squidproductions.camerastats.core.*
import org.squidproductions.camerastats.media.*
import org.squidproductions.camerastats.storage.JdbcDriver
import org.squidproductions.camerastats.ui.AppState

class MediaUiTests {
    @get:Rule val ui=createComposeRule()
    @Test fun desktopFootageDecodesConfirmsSlateAndRequiresReviewedAssociation() {
        assumeTrue("Local decoder tools are required for desktop media UI integration",System.getenv("SHOT_REPORTER_FFMPEG")!=null && System.getenv("SHOT_REPORTER_FFPROBE")!=null)
        val root=Files.createTempDirectory("shot-reporter-media-ui");val source=Files.createDirectory(root.resolve("source"))
        Files.copy(Path.of(System.getProperty("sampleMedia")),source.resolve("A001_C001.mp4"))
        val host=DesktopGateway(root);val reportRepo=Repository(JdbcDriver(root.resolve("reports.sqlite3")));val app=AppState(reportRepo,host);app.open(host.demo().separateDemo())
        val home=root.resolve("desktop-media").resolve(Exchange.digest(app.report.id.toByteArray()));val decoder=Decoder(Path.of(System.getenv("SHOT_REPORTER_FFMPEG")),Path.of(System.getenv("SHOT_REPORTER_FFPROBE")))
        MediaRepository(home).use {MediaService(it,decoder).register(source,"A","A001","ui-fixture")}
        val media=DesktopMediaState(home)
        try {
            ui.setContent {MediaWorkspace(app.report,app,root,media)}
            ui.waitUntil(15000) {media.snapshot.clips.size==1 && !media.busy}
            ui.onNodeWithText("A001_C001.mp4",substring=false).performClick()
            ui.onNodeWithText("Source frame index",substring=false).performTextClearance();ui.onNodeWithText("Source frame index",substring=false).performTextInput("7")
            ui.onNodeWithText("Decode exact frame",substring=false).performClick()
            ui.waitUntil(15000) {media.frame?.index==7L && !media.busy};assertEquals("7168",media.frame?.pts)
            ui.onNodeWithText("Confirmed scene",substring=false).performTextInput("3A");ui.onNodeWithText("Confirmed take",substring=false).performTextInput("2")
            ui.onNodeWithText("Confirm values from this frame",substring=false).performScrollTo().performClick()
            ui.waitUntil(15000) {media.snapshot.slates.size==1 && !media.busy}
            ui.onNodeWithText("Review report associations",substring=false).performScrollTo().performClick()
            ui.waitUntil(15000) {media.suggestions.isNotEmpty() && !media.busy}
            assertTrue(media.snapshot.associations.isEmpty())
            ui.onAllNodesWithText("Confirm this association",substring=false)[0].performScrollTo().performClick()
            ui.waitUntil(15000) {media.snapshot.associations.size==1 && !media.busy}
            assertEquals("3A",media.snapshot.slates.single().scene);assertEquals(7,media.snapshot.slates.single().frame.index)
            val image=ui.onRoot().captureToImage();File("build/qa").mkdirs();File("build/qa/desktop-media.png").writeBytes(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            assertEquals(Exchange.digest(Files.readAllBytes(Path.of(System.getProperty("sampleMedia")))) ,sha256(source.resolve("A001_C001.mp4")))
        } finally {reportRepo.close()}
    }
}
