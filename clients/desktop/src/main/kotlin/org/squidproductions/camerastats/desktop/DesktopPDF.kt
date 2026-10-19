package org.squidproductions.camerastats.desktop

import org.squidproductions.camerastats.core.*
import org.apache.pdfbox.pdmodel.*
import org.apache.pdfbox.pdmodel.common.PDRectangle
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D
import java.awt.Font
import java.awt.Color
import java.awt.font.TextLayout
import java.awt.font.LineBreakMeasurer
import java.text.AttributedString
import java.awt.font.TextAttribute
import java.io.ByteArrayOutputStream

object DesktopPDF {
    /** Native Java2D font fallback draws glyph outlines into PDF; no proprietary fonts are redistributed. */
    fun render(report:Report):ByteArray = PDDocument().use {document ->
        val width=842f;val height=595f;var y=0f;var page=0;var graphics:PdfBoxGraphics2D?=null
        fun endPage() {graphics?.let {g ->g.dispose();val p=PDPage(PDRectangle(width,height));document.addPage(p);PDPageContentStream(document,p).use {it.drawForm(g.xFormObject)}};graphics=null}
        fun beginPage() {
            endPage();page++;val g=PdfBoxGraphics2D(document,width,height);graphics=g;g.color=Color(36,29,33);g.font=Font("Dialog",Font.BOLD,14);g.drawString("Shot Reporter · Camera Report",28f,30f);g.font=Font("Dialog",Font.PLAIN,9);g.drawString("A Squid Productions Software · Revision ${report.revision} · Page $page",28f,46f);y=68f
        }
        beginPage()
        Reports.printable(report).forEach {paragraph ->
            if(paragraph.isBlank()) {y+=12f;return@forEach}
            val text=AttributedString(paragraph).apply {addAttribute(TextAttribute.FONT,Font("Dialog",Font.PLAIN,10))}
            val iterator=text.iterator;val measurer=LineBreakMeasurer(iterator,graphics!!.fontRenderContext)
            while(measurer.position<iterator.endIndex) {
                val layout=measurer.nextLayout(width-56f)
                if(y+layout.ascent+layout.descent>height-28f) beginPage()
                y+=layout.ascent;layout.draw(graphics,28f,y);y+=layout.descent+layout.leading+4f
            }
        }
        endPage();document.documentInformation=PDDocumentInformation().apply {title=report.values["title"] ?: "Shot Reporter report";creator="Shot Reporter · Squid Productions"}
        ByteArrayOutputStream().use {document.save(it);it.toByteArray()}
    }
}
