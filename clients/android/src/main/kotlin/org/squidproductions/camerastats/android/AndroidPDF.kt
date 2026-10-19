package org.squidproductions.camerastats.android

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.squidproductions.camerastats.core.*
import java.io.ByteArrayOutputStream

object AndroidPDF {
    fun render(report:Report):ByteArray {
        val document=PdfDocument();var page:PdfDocument.Page?=null;var count=0;var y=0f
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.rgb(36,29,33);textSize=10f;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
        fun begin() {
            page?.let {document.finishPage(it)};count++;page=document.startPage(PdfDocument.PageInfo.Builder(842,595,count).create());y=68f
            val header=Paint(paint).apply {textSize=14f;typeface=Typeface.create("sans-serif",Typeface.BOLD)}
            page!!.canvas.drawText("Shot Reporter · Camera Report",28f,30f,header);page!!.canvas.drawText("A Squid Productions Software · Revision ${report.revision} · Page $count",28f,46f,paint)
        }
        try {
            begin()
            Reports.printable(report).forEach {paragraph ->
                if(paragraph.isBlank()) {y+=12f;return@forEach}
                val layout=StaticLayout.Builder.obtain(paragraph,0,paragraph.length,paint,786).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
                for(line in 0 until layout.lineCount) {
                    val height=(layout.getLineBottom(line)-layout.getLineTop(line)).toFloat()+4
                    if(y+height>567f) begin()
                    val start=layout.getLineStart(line);val end=layout.getLineEnd(line)
                    val piece=paragraph.substring(start,end).trimEnd('\n')
                    val single=StaticLayout.Builder.obtain(piece,0,piece.length,paint,786).setIncludePad(false).build()
                    page!!.canvas.save();page!!.canvas.translate(28f,y);single.draw(page!!.canvas);page!!.canvas.restore();y+=height
                }
            }
            page?.let {document.finishPage(it)};return ByteArrayOutputStream().use {document.writeTo(it);it.toByteArray()}
        } finally {document.close()}
    }
}
