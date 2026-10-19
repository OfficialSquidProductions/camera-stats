package org.squidproductions.camerastats.updates

import kotlinx.serialization.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.*
import java.security.*
import java.time.Instant
import java.util.Base64
import kotlin.test.*

class UpdateTests {
    @TempDir lateinit var temp:Path
    @org.junit.jupiter.api.BeforeEach fun canonicalTemp() {temp=temp.toRealPath()}
    private val keys=KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val now=Instant.parse("2026-10-19T12:00:00Z")
    private val publisher="1".repeat(64)
    private val policy get()=UpdatePolicy("https://updates.example.test/windows.json",Base64.getEncoder().encodeToString(keys.public.encoded),setOf("updates.example.test"),publisher,"0.1.1-alpha.3",3)
    private val data="synthetic MSI-shaped test bytes; never executable".toByteArray()
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
    private fun release(version:String="0.2.0-alpha.1",build:Long=4,channel:String="alpha")=UpdateRelease(version,build,channel,"Camera RAW review\n<script>never execute remote release notes</script>",UpdateAsset("https://updates.example.test/Shot-Reporter.msi",data.size.toLong(),hash(data),MSI_UPGRADE_CODE,publisher,"0.2.0"))
    private fun signed(releases:List<UpdateRelease> = listOf(release()),expires:Instant=now.plusSeconds(86400),app:String=APPLICATION_ID):ByteArray {
        val payload=updateJSON.encodeToString(UpdateFeed(1,app,now.minusSeconds(10).toString(),expires.toString(),releases)).toByteArray()
        val signature=Signature.getInstance("Ed25519").run {initSign(keys.private);update(payload);sign()}
        return updateJSON.encodeToString(SignedEnvelope(Base64.getEncoder().encodeToString(payload),Base64.getEncoder().encodeToString(signature))).toByteArray()
    }
    @Test fun authenticFeedSelectsChannelWithoutDowngrade() {
        val alpha=release();val stable=release("0.2.0",5,"stable")
        assertEquals(stable,verifyFeed(signed(listOf(alpha,stable)),policy,"stable",now))
        assertNull(verifyFeed(signed(),policy,"stable",now))
        assertNull(verifyFeed(signed(),policy,"alpha",now,highestBuild=5))
        assertEquals(alpha,verifyFeed(signed(),policy,"alpha",now,highestBuild=4))
        assertTrue(Version.parse("0.2.0-alpha.10")>Version.parse("0.2.0-alpha.2"));assertTrue(Version.parse("0.2.0")>Version.parse("0.2.0-alpha.99"))
        assertFails {Version.parse("1.0.0-alpha.01")};assertFails {validateMSIVersion("256.0.0")};assertFails {validateMSIVersion("1.0.65536")};assertFails {validateMSIVersion("1.0.1+4")}
    }
    @Test fun signatureExpiryAppPublisherAndURLFailClosed() {
        val raw=updateJSON.decodeFromString<SignedEnvelope>(signed().toString(Charsets.UTF_8));val tampered=updateJSON.encodeToString(raw.copy(payload=Base64.getEncoder().encodeToString("{}".toByteArray()))).toByteArray()
        assertFails {verifyFeed(tampered,policy,"alpha",now)}
        assertFails {verifyFeed(signed(expires=now),policy,"alpha",now)}
        assertFails {verifyFeed(signed(app="other.app"),policy,"alpha",now)}
        listOf("http://updates.example.test/x.msi","https://evil.test/x.msi","https://updates.example.test@evil.test/x.msi","https://updates.example.test:8443/x.msi").forEach {url->assertFails {verifyFeed(signed(listOf(release().copy(windowsX64=release().windowsX64!!.copy(url=url)))),policy,"alpha",now)}}
        assertFails {verifyFeed(signed(listOf(release().copy(windowsX64=release().windowsX64!!.copy(signerSHA256="2".repeat(64))))),policy,"alpha",now)}
        assertNull(verifyFeed(signed(listOf(release().copy(windowsX64=release().windowsX64!!.copy(msiVersion="0.1.1")))),policy,"alpha",now))
        assertFails {verifyFeed(signed(listOf(release(channel="stable"))),policy,"stable",now)}
    }
    @Test fun verifiedDownloadIsExactAndNeverReusesAnExistingFile() {
        val downloader=UpdateDownloader(policy,temp,object:UpdateTransport {override fun open(uri:java.net.URI)=data.inputStream()})
        val first=downloader.download(release());val second=downloader.download(release())
        assertNotEquals(first,second);assertContentEquals(data,Files.readAllBytes(first));assertEquals(hash(data),sha256(first))
        assertTrue(Files.exists(first.parent.resolve("verified.json")));assertFalse(Files.exists(first.parent.resolve("download.partial")))
    }
    @Test fun truncatedOversizeChangedAndCancelledDownloadsNeverBecomeInstallable() {
        listOf(data.dropLast(1).toByteArray(),data+byteArrayOf(1),data.map {if(it==data[0]) 0 else it}.toByteArray()).forEachIndexed {i,bytes->
            val root=temp.resolve("failure-$i")
            assertFails {UpdateDownloader(policy,root,object:UpdateTransport {override fun open(uri:java.net.URI)=bytes.inputStream()}).download(release())}
            Files.walk(root).use {paths->assertFalse(paths.anyMatch {it.fileName.toString()=="Shot-Reporter-update.msi"})}
        }
        assertFailsWith<InterruptedException> {UpdateDownloader(policy,temp.resolve("cancelled"),object:UpdateTransport {override fun open(uri:java.net.URI)=object:java.io.ByteArrayInputStream(data) {override fun read(b:ByteArray,o:Int,l:Int):Int {Thread.currentThread().interrupt();return super.read(b,o,l)}}}).download(release())}
        Thread.interrupted()
    }
    @Test fun changedStagingOrFailedSaveCannotStartInstaller() {
        val file=temp.resolve("Shot-Reporter-update.msi");Files.write(file,data);val exe=temp.resolve("Shot Reporter.exe");Files.writeString(exe,"synthetic executable marker")
        var launches=0;val installer=WindowsUpdateInstaller(policy,InstallerLauncher {launches++})
        assertFails {installer.prepare(release(),file,exe) {error("simulated disk-full save failure")}}
        assertEquals(0,launches)
        Files.write(file,data+1)
        assertFails {installer.prepare(release(),file,exe) {error("must not reach save")}}
        assertEquals(0,launches)
    }
    @Test fun helperFailureReleasesSaveGateAndSuccessfulHandoffRetainsIt() {
        val file=temp.resolve("Shot-Reporter-update.msi");Files.write(file,data);val exe=temp.resolve("Shot Reporter.exe");Files.writeString(exe,"synthetic executable marker")
        var released=false
        assertFails {WindowsUpdateInstaller(policy,InstallerLauncher {error("helper refused publisher")}).prepare(release(),file,exe) {AutoCloseable {released=true}}}
        assertTrue(released);released=false
        val gate=WindowsUpdateInstaller(policy,InstallerLauncher {ticket->val parsed=updateJSON.decodeFromString<InstallTicket>(Files.readString(ticket));assertEquals(MSI_UPGRADE_CODE,parsed.upgradeCode);assertEquals(file.toRealPath().toString(),parsed.installer);assertEquals(exe.toRealPath().toString(),parsed.relaunch)}).prepare(release(),file,exe) {AutoCloseable {released=true}}
        assertFalse(released);gate.close();assertTrue(released)
    }
    @Test fun updateStorageRejectsSymlinksAndBoundedReadsRejectLargeResponses() {
        Files.createDirectory(temp.resolve("real"));Files.createSymbolicLink(temp.resolve("link"),temp.resolve("real"))
        assertFails {safeDirectory(temp.resolve("link/cache"))};assertFails {readBounded(ByteArray(11).inputStream(),10)}
    }
    @Test fun authenticatedBuildWatermarkPersistsAcrossRelaunchAndBlocksReplay() {
        UpdateRollbackStore(temp,3).remember(5)
        val reopened=UpdateRollbackStore(temp,3)
        assertEquals(5,reopened.load());reopened.remember(4);assertEquals(5,reopened.load())
        assertNull(verifyFeed(signed(),policy,"alpha",now,highestBuild=reopened.load()))
        Files.writeString(temp.resolve("highest-authenticated-build.txt"),"corrupt")
        assertFails {reopened.load()}
    }

    @Test fun preparedHandoffRequiresExplicitCommitAndCancellationWinsBeforeCommit() {
        val cancelled=temp.resolve("cancelled-handoff");Files.createDirectory(cancelled)
        var releases=0
        val abandoned=PreparedInstall(cancelled.resolve("install-ticket.json"),AutoCloseable {releases++})
        abandoned.close();abandoned.close()
        assertEquals(1,releases);assertTrue(Files.exists(cancelled.resolve("install-cancelled.txt")));assertFalse(Files.exists(cancelled.resolve("install-committed.txt")))
        assertFails {abandoned.commit()}
        val committed=temp.resolve("committed-handoff");Files.createDirectory(committed)
        val prepared=PreparedInstall(committed.resolve("install-ticket.json"),AutoCloseable {releases++})
        prepared.commit();prepared.commit();prepared.close()
        assertEquals("committed",Files.readString(committed.resolve("install-committed.txt")).trim())
        assertFalse(Files.exists(committed.resolve("install-cancelled.txt")));assertEquals(2,releases)
    }
    @Test fun competingDisposeAndCommitCannotPublishConflictingHandoffs() {
        repeat(30) {index->
            val directory=temp.resolve("race-$index");Files.createDirectory(directory)
            val released=java.util.concurrent.atomic.AtomicInteger()
            val prepared=PreparedInstall(directory.resolve("install-ticket.json"),AutoCloseable {released.incrementAndGet()})
            val start=java.util.concurrent.CountDownLatch(1)
            val commit=Thread {start.await();runCatching {prepared.commit()}}
            val dispose=Thread {start.await();prepared.close()}
            commit.start();dispose.start();start.countDown();commit.join();dispose.join()
            assertEquals(1,released.get())
            assertNotEquals(Files.exists(directory.resolve("install-committed.txt")),Files.exists(directory.resolve("install-cancelled.txt")))
        }
    }
    @Test fun cancelledAndFailedInstallerOutcomesAreReadableWithoutRawJSON() {
        val cancelled=UpdateInstallResult(success=false,exitCode=1602,finishedAt=now.toString())
        assertTrue(installResultMessage(cancelled).contains("cancelled"))
        assertTrue(installResultMessage(cancelled.copy(success=true,exitCode=3010,version="0.2.0")).contains("restart is required"))
        assertFails {installResultMessage(cancelled.copy(schema=9))}
    }

    @Test fun independentUpdaterInstancesCannotRollBackEachOthersWatermark() {
        val first=UpdateRollbackStore(temp,3);val second=UpdateRollbackStore(temp,3)
        val start=java.util.concurrent.CountDownLatch(1);val errors=java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val low=Thread {try {start.await();(4L..45L).forEach(first::remember)} catch(e:Throwable) {errors.add(e)}}
        val high=Thread {try {start.await();(46L..90L).forEach(second::remember)} catch(e:Throwable) {errors.add(e)}}
        low.start();high.start();start.countDown();low.join();high.join()
        assertTrue(errors.isEmpty(),errors.joinToString());assertEquals(90,UpdateRollbackStore(temp,3).load())
    }

}
