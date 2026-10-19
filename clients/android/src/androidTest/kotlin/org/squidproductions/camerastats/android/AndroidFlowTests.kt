package org.squidproductions.camerastats.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.squidproductions.camerastats.core.*
import java.io.File

class AndroidFlowTests {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun demoNextTakeAndDatabaseReopen() {
        ui.onNodeWithText("Open The Last Light demo").performClick()
        ui.onNode(hasText("The Last Light · DEMO") and !hasSetTextAction()).assertExists()
        ui.onNodeWithText("Log").performClick()
        ui.onNodeWithText("Scene 3A · Take 2").assertExists()
        ui.onNodeWithText("Scene for next take").performTextInput("3A")
        ui.onNodeWithText("Next take",substring=false).performScrollTo().performClick()
        ui.onNodeWithText("Scene 3A · Take 4").assertExists()
        ui.activityRule.scenario.recreate()
        ui.onNode(hasText("The Last Light · DEMO") and !hasSetTextAction()).assertExists()
        ui.runOnIdle {assertEquals(4,ui.activity.model.report.active.size);assertEquals("4",ui.activity.model.report.active.last().values["take"]);assertEquals("24mm",ui.activity.model.report.active.last().values["lens"])}
    }
    @Test fun nativePdfAndPortableSaveRoundtrip() {
        val context=ui.activity;val report=context.demo().separateDemo();val pdf=context.pdf(report)
        assertTrue(pdf.copyOfRange(0,5).toString(Charsets.US_ASCII)=="%PDF-");assertTrue(pdf.size>1000)
        val exported=Exchange.export(report,"Android instrumented test");assertEquals(report,Exchange.read(exported).report)
        val database=File(context.cacheDir,"isolated-${identity()}.sqlite3");val slot=identity()
        Repository(AndroidDriver(database)).use {r ->val hash=r.save(slot,report,"Android test",null);r.save(slot,report.next("3A","Android test"),"Android test",hash);assertEquals(report,r.load(slot,true).report)}
        Repository(AndroidDriver(database)).use {r ->assertEquals(4,r.load(slot).report.active.size)}
    }
}
