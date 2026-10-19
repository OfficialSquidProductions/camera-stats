package org.squidproductions.camerastats.desktop

import org.junit.jupiter.api.Test
import kotlin.test.*
import java.nio.file.Files
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import org.squidproductions.camerastats.core.*

class DesktopTests {
    @Test fun packagedDemoAndPdfRenderWithoutFontRedistribution() {
        val host=DesktopGateway(Files.createTempDirectory("camera-pdf-test"));val demo=host.demo().separateDemo()
        assertEquals(3,demo.active.size);val pdf=host.pdf(demo)
        Loader.loadPDF(pdf).use {doc ->assertTrue(doc.numberOfPages>1);val image=PDFRenderer(doc).renderImage(0);assertEquals(842,image.width);assertEquals(595,image.height);assertTrue((0 until image.width step 4).any {x ->(0 until image.height step 4).any {y ->(image.getRGB(x,y) and 0xffffff)!=0xffffff}})}
        val output=java.io.File("build/qa").apply {mkdirs()};java.io.File(output,"desktop-demo.pdf").writeBytes(pdf)
    }
    @Test fun durableExportRoundtripAndNoTemporaryDebris() {
        val root=Files.createTempDirectory("camera-export-test");val demo=DesktopGateway(root).demo().separateDemo();val path=root.resolve("saved.camreport")
        durableExport(path,Exchange.export(demo,"Desktop test"));assertEquals(demo,Exchange.read(Files.readAllBytes(path)).report)
        val changed=demo.next("3A","Desktop test");durableExport(path,Exchange.export(changed,"Desktop test"));assertEquals(changed,Exchange.read(Files.readAllBytes(path)).report)
        Files.list(root).use {files ->assertFalse(files.anyMatch {it.fileName.toString().startsWith(".camera-stats-export-")})}
    }
}
