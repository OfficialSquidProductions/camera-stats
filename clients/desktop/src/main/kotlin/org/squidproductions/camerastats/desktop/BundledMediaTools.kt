package org.squidproductions.camerastats.desktop

import kotlinx.serialization.json.*
import java.nio.file.*
import org.squidproductions.camerastats.updates.sha256

/** Only the installed application's resource directory is searched; PATH and download locations are not. */
fun bundledMediaTool(name:String):String? {
    require(name in setOf("ffmpeg","ffprobe"))
    if(!System.getProperty("os.name").startsWith("Windows")) return null
    val resources=System.getProperty("compose.application.resources.dir") ?: return null
    return runCatching {
        val home=Path.of(resources).resolve("DesktopCodecs");val metadata=home.resolve("BUILD.json")
        check(Files.isRegularFile(metadata,LinkOption.NOFOLLOW_LINKS) && Files.size(metadata)<1024*1024)
        val data=Json.parseToJsonElement(Files.readString(metadata)).jsonObject
        val executable=home.resolve("bin/$name.exe")
        val expected=data.getValue("binaries").jsonObject.getValue("$name.exe").jsonPrimitive.content
        check(Regex("[0-9a-f]{64}").matches(expected) && Files.isRegularFile(executable,LinkOption.NOFOLLOW_LINKS) && sha256(executable)==expected)
        executable.toRealPath().toString()
    }.getOrNull()
}

fun bundledMediaLegalNotices():String {
    val resources=System.getProperty("compose.application.resources.dir") ?: return ""
    val home=Path.of(resources).resolve("DesktopCodecs/legal")
    if(!Files.isDirectory(home,LinkOption.NOFOLLOW_LINKS)) return ""
    return listOf("FFmpeg-LGPL-2.1.txt","zlib-LICENSE.txt").joinToString("\n\n") {name->
        val path=home.resolve(name);check(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) && Files.size(path)<256*1024)
        "$name\n"+Files.readString(path)
    }
}
