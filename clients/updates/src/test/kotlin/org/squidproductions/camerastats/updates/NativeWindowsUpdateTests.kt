package org.squidproductions.camerastats.updates

import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.*
import org.junit.jupiter.api.io.TempDir
import java.nio.file.*
import java.io.RandomAccessFile
import kotlin.test.*

@EnabledOnOs(OS.WINDOWS)
@EnabledIfEnvironmentVariable(named="SHOT_REPORTER_UPDATE_TEST_MSI",matches=".+")
class NativeWindowsUpdateTests {
    @TempDir lateinit var root:Path
    private fun reject(tampered:Boolean) {
        val source=Path.of(System.getenv("SHOT_REPORTER_UPDATE_TEST_MSI"));val installer=root.resolve("installer.msi");Files.copy(source,installer)
        val expected=sha256(installer);val size=Files.size(installer)
        if(tampered) RandomAccessFile(installer.toFile(),"rw").use {file->file.seek(4096);val byte=file.readByte();file.seek(4096);file.writeByte(byte.toInt() xor 1)}
        val ticket=InstallTicket(parentPID=ProcessHandle.current().pid(),installer=installer.toString(),sha256=expected,size=size,signerSHA256="1".repeat(64),msiVersion="0.2.0",relaunch=root.resolve("nonexistent-must-not-launch.exe").toString(),version="0.2.0-alpha.1",build=3)
        val path=root.resolve("install-ticket.json");durableNew(path,updateJSON.encodeToString(ticket).toByteArray())
        assertFails {WindowsInstallerLauncher().launch(path)}
        val result=Files.readString(root.resolve("install-result.json")).removePrefix("\uFEFF")
        assertTrue(result.contains(if(tampered) "SHA-256 mismatch" else "publisher verification failed"),result)
        assertFalse(Files.exists(root.resolve("helper-ready.txt")));assertFalse(Files.exists(root.resolve("msi-install.log")))
    }
    @Test fun actualUnsignedMSIIsRejectedBeforeReadinessOrInstall()=reject(false)
    @Test fun sameLengthTamperedMSIIsRejectedBeforePublisherCheck()=reject(true)
}
