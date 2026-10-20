package org.squidproductions.camerastats.media

import java.nio.file.*
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

fun sha256(path:Path):String {
    require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) {"Expected a regular local file: ${path.fileName}"}
    val digest=MessageDigest.getInstance("SHA-256")
    Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS).use {input ->val buffer=ByteArray(1024*1024);while(true) {if(Thread.currentThread().isInterrupted) error("Cancelled; journal retained.");val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n)}}
    return digest.digest().joinToString("") {"%02x".format(it.toInt() and 255)}
}
fun canonical(path:Path):Path {require(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)) {"Choose an existing local directory, not a link."};return path.toRealPath()}
fun localPath(root:Path,relative:String):Path {
    val rel=Path.of(relative);require(!rel.isAbsolute && rel.none {it.toString() in listOf("..","")}) {"Unsafe relative path."}
    val base=canonical(root);val path=base.resolve(rel).normalize();require(path.startsWith(base) && path!=base) {"Path escaped its registered root."}
    var cursor=base
    for(part in base.relativize(path)) {cursor=cursor.resolve(part);require(!Files.isSymbolicLink(cursor)) {"Symbolic links cannot be followed: $relative"}}
    return path
}
fun collisionKey(name:String)=Normalizer.normalize(name,Normalizer.Form.NFC).lowercase(Locale.ROOT)
fun fileKey(path:Path):String?=Files.readAttributes(path,java.nio.file.attribute.BasicFileAttributes::class.java,LinkOption.NOFOLLOW_LINKS).fileKey()?.toString()
fun forceDirectory(path:Path) {runCatching {FileChannel.open(path,StandardOpenOption.READ).use {it.force(true)}}}
fun writeNew(path:Path,bytes:ByteArray) {
    FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS).use {c ->val b=ByteBuffer.wrap(bytes);while(b.hasRemaining()) c.write(b);c.force(true)};forceDirectory(path.parent)
}
