package org.squidproductions.camerastats.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import org.squidproductions.camerastats.core.Repository
import org.squidproductions.camerastats.storage.JdbcDriver
import org.squidproductions.camerastats.ui.AppState
import kotlin.test.*

class UpdateUiTests {
    @get:Rule val ui=createComposeRule()
    @Test fun unconfiguredUpdaterShowsControlsWithoutOfferingInstallation() {
        val root=Files.createTempDirectory("shot-reporter-update-ui").toRealPath();val gateway=DesktopGateway(root)
        Repository(JdbcDriver(root.resolve("reports.sqlite3"))).use {repo->
            val state=AppState(repo,gateway);val updates=DesktopUpdates(root,gateway)
            try {
                ui.setContent {DesktopUpdateButton(updates,state) {error("Unconfigured updater must never exit the app")}}
                ui.onNodeWithText("Updates…").performClick()
                ui.onNodeWithText("Shot Reporter updates").assertExists()
                ui.onNodeWithText("Check for updates").performClick()
                ui.onNodeWithText("Save, install and restart").assertDoesNotExist()
                ui.onNodeWithText("Download update").assertDoesNotExist()
                ui.onNodeWithText("Stable").performClick();assertEquals("stable",updates.channel)
                ui.onNodeWithText("Keep working").performClick()
                assertFalse(updates.visible);assertTrue(Files.isRegularFile(root.resolve("reports.sqlite3")))
            } finally {updates.close()}
        }
    }
}
