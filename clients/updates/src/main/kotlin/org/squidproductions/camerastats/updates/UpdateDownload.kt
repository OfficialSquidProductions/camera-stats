package org.squidproductions.camerastats.updates

import kotlinx.serialization.*
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

interface UpdateTransport {fun open(uri:URI):InputStream}
class HTTPSUpdateTransport(private val policy:UpdatePolicy):UpdateTransport {
    override fun open(uri:URI):InputStream {
        var target=policy.trustedURI(uri.toString())
        repeat(6) {hop->
            if(Thread.currentThread().isInterrupted) throw InterruptedException("Update cancelled.")
            val connection=target.toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects=false;connection.connectTimeout=15000;connection.readTimeout=30000
            connection.setRequestProperty("User-Agent","ShotReporter-Desktop-Updater/1");connection.setRequestProperty("Accept-Encoding","identity")
            try {
                val code=connection.responseCode
                if(code in setOf(301,302,303,307,308)) {
                    check(hop<5) {"Too many update redirects."}
                    val location=connection.getHeaderField("Location") ?: error("Update redirect has no location.")
                    target=policy.trustedURI(target.resolve(location).toString());connection.disconnect()
                } else {
                    check(code==200) {"Update server returned $code."}
                    return object:java.io.FilterInputStream(connection.inputStream) {override fun close() {try {super.close()} finally {connection.disconnect()}}}
                }
            } catch(e:Throwable) {connection.disconnect();throw e}
        }
        error("Too many update redirects.")
    }
}

fun readBounded(input:InputStream,limit:Int):ByteArray=input.use {it.readNBytes(limit+1).also {bytes->check(bytes.size<=limit) {"Update response exceeds its limit."}}}
fun sha256(path:Path):String {
    val digest=MessageDigest.getInstance("SHA-256");Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS).use {input->val buffer=ByteArray(1024*1024);while(true) {if(Thread.currentThread().isInterrupted) throw InterruptedException("Update cancelled.");val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n)}}
    return digest.digest().joinToString("") {"%02x".format(it)}
}
fun safeDirectory(path:Path):Path {
    val absolute=path.toAbsolutePath().normalize();var part=absolute.root
    absolute.forEach {name->part=part.resolve(name);check(!Files.isSymbolicLink(part)) {"Update storage cannot contain symbolic links."}}
    Files.createDirectories(absolute);return absolute.toRealPath(LinkOption.NOFOLLOW_LINKS)
}
fun durableNew(path:Path,bytes:ByteArray) {FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE).use {channel->val buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining()) channel.write(buffer);channel.force(true)}}
@Serializable data class UpdateJournal(val schema:Int=1,val version:String,val build:Long,val stage:String,val createdAt:String,val installer:String,val sha256:String,val size:Long,val signerSHA256:String,val upgradeCode:String,val error:String?=null)
/** Each attempt gets an owned directory. Failed/cancelled downloads cannot become installable. */
class UpdateDownloader(private val policy:UpdatePolicy,private val storage:Path,private val transport:UpdateTransport=HTTPSUpdateTransport(policy)) {
    fun check(channel:String,highestBuild:Long=policy.currentBuild):UpdateRelease? {
        policy.validate();return verifyFeed(readBounded(transport.open(policy.trustedURI(policy.feedURL)),1024*1024),policy,channel,highestBuild=highestBuild)
    }
    fun download(release:UpdateRelease,progress:(Long,Long)->Unit={_,_->}):Path {
        policy.validate();val asset=checkNotNull(release.windowsX64);policy.trustedURI(asset.url)
        check(asset.signerSHA256.equals(policy.signerSHA256,true) && asset.upgradeCode.equals(MSI_UPGRADE_CODE,true))
        val home=safeDirectory(storage).resolve(UUID.randomUUID().toString());Files.createDirectory(home)
        val installer=home.resolve("Shot-Reporter-update.msi");val partial=home.resolve("download.partial")
        val journal=UpdateJournal(version=release.version,build=release.build,stage="downloading",createdAt=Instant.now().toString(),installer=installer.toString(),sha256=asset.sha256.lowercase(),size=asset.size,signerSHA256=asset.signerSHA256.lowercase(),upgradeCode=asset.upgradeCode)
        durableNew(home.resolve("download.json"),updateJSON.encodeToString(journal).toByteArray())
        try {
            var total=0L
            transport.open(policy.trustedURI(asset.url)).use {input->FileChannel.open(partial,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE).use {channel->
                val buffer=ByteArray(1024*1024)
                while(true) {if(Thread.currentThread().isInterrupted) throw InterruptedException("Update download cancelled.");val n=input.read(buffer);if(n<0) break;total+=n;check(total<=asset.size) {"Update exceeds its signed length."};val bytes=ByteBuffer.wrap(buffer,0,n);while(bytes.hasRemaining()) channel.write(bytes);progress(total,asset.size)}
                channel.force(true)
            }}
            check(total==asset.size && sha256(partial).equals(asset.sha256,true)) {"Update length or SHA-256 verification failed."}
            Files.move(partial,installer,StandardCopyOption.ATOMIC_MOVE)
            durableNew(home.resolve("verified.json"),updateJSON.encodeToString(journal.copy(stage="download-verified")).toByteArray())
            return installer
        } catch(e:Throwable) {
            val cancelled=Thread.interrupted() || e is InterruptedException || e is java.nio.channels.ClosedByInterruptException
            runCatching {durableNew(home.resolve("failure.json"),updateJSON.encodeToString(journal.copy(stage=if(cancelled) "cancelled" else "failed",error=if(cancelled) "Update download cancelled." else e.message?.take(1000))).toByteArray())}
            if(cancelled) throw InterruptedException("Update download cancelled.") else throw e
        }
    }
}

/** Persist the highest authenticated build seen, so a still-valid old feed cannot roll availability back. */
class UpdateRollbackStore(private val storage:Path,private val currentBuild:Long) {
    @Synchronized fun load():Long {
        val file=storage.resolve("highest-authenticated-build.txt")
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS)) return currentBuild
        check(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file) && Files.size(file)<=32) {"Update rollback record needs recovery."}
        return maxOf(currentBuild,Files.readString(file).trim().toLong().also {check(it>0)})
    }
    @Synchronized fun remember(build:Long) {
        val home=safeDirectory(storage);val lockPath=home.resolve("watermark.lock")
        check(!Files.isSymbolicLink(lockPath)) {"Unexpected update rollback lock."}
        FileChannel.open(lockPath,StandardOpenOption.CREATE,StandardOpenOption.WRITE).use {channel->
            val deadline=System.nanoTime()+5_000_000_000L
            var lock=runCatching {channel.tryLock()}.getOrNull()
            while(lock==null && System.nanoTime()<deadline) {Thread.sleep(10);lock=runCatching {channel.tryLock()}.getOrNull()}
            checkNotNull(lock) {"Another Shot Reporter instance is recording update availability. Try checking again."}.use {
                val highest=load();if(build<=highest) return
                val next=home.resolve("watermark-${UUID.randomUUID()}.partial")
                durableNew(next,"$build\n".toByteArray());Files.move(next,home.resolve("highest-authenticated-build.txt"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
