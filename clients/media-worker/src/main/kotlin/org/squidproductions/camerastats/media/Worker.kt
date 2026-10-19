package org.squidproductions.camerastats.media

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.squidproductions.camerastats.core.*
import java.nio.file.*

/** Local desktop pipe protocol, one bounded request per process; stdout contains exactly one JSON response. */
fun main(args:Array<String>) {
    Runtime.getRuntime().addShutdownHook(Thread {ProcessHandle.current().descendants().forEach {it.destroyForcibly()}})
    try {
        require(args.size==3) {"worker <private-media-directory> <ffmpeg-executable> <ffprobe-executable>"}
        val raw=System.`in`.readNBytes(16*1024*1024+1);require(raw.size<=16*1024*1024) {"Request exceeds the desktop worker limit."}
        val request=mediaJSON.parseToJsonElement(raw.toString(Charsets.UTF_8)).jsonObject
        fun value(key:String)=request[key]?.jsonPrimitive?.content ?: error("Missing $key")
        // Preserve the native encoder's numeric date representation through the outer JSON envelope.
        val report=request["reportJSON"]?.jsonPrimitive?.content?.let {Exchange.decode(it.toByteArray())}
            ?: request["report"]?.let {Exchange.decode(it.toString().toByteArray())}
        MediaRepository(Path.of(args[0])).use {repo ->
            fun tool(key:String,environment:String)=request[key]?.jsonPrimitive?.content?.takeIf {it.isNotBlank()}?.let(Path::of) ?: System.getenv(environment)?.takeIf {it.isNotBlank()}?.let(Path::of)
            val service=MediaService(repo,Decoder(Path.of(args[1]),Path.of(args[2]),tool("redline","SHOT_REPORTER_REDLINE"),tool("brawHelper","SHOT_REPORTER_BRAW_HELPER")));val jobs=CopyJobs(service)
            val result:JsonElement=when(value("command")) {
                "snapshot" -> mediaJSON.encodeToJsonElement(repo.snapshot())
                "register" -> mediaJSON.encodeToJsonElement(service.register(Path.of(value("path")),value("camera"),value("roll"),value("load")))
                "relink" -> mediaJSON.encodeToJsonElement(service.relinkRoot(value("rootID"),Path.of(value("path"))))
                "frame" -> mediaJSON.encodeToJsonElement(service.frame(value("clipID"),value("index").toLong()))
                "frames" -> mediaJSON.encodeToJsonElement(service.frames(value("clipID"),value("index").toLong()))
                "approve" -> mediaJSON.encodeToJsonElement(service.approve(value("clipID"),value("frameID"),value("scene"),value("take")))
                "approvePackage" -> mediaJSON.encodeToJsonElement(service.approvePackage(value("clipID"),value("fingerprint"),request["completeSourceConfirmed"]?.jsonPrimitive?.booleanOrNull ?: false))
                "suggestions" -> mediaJSON.encodeToJsonElement(service.suggestions(value("clipID"),checkNotNull(report)))
                "link" -> mediaJSON.encodeToJsonElement(service.link(value("clipID"),checkNotNull(report),value("entryID")))
                "unlink" -> mediaJSON.encodeToJsonElement(service.unlink(value("associationID")))
                "preset" -> mediaJSON.encodeToJsonElement(service.savePreset(value("name"),value("template"),request["id"]?.jsonPrimitive?.content))
                "prepare" -> mediaJSON.encodeToJsonElement(jobs.prepare(request["clipIDs"]!!.jsonArray.map {it.jsonPrimitive.content},checkNotNull(report),mediaJSON.decodeFromJsonElement<NamingPreset>(request["preset"]!!),Path.of(value("destination"))))
                "copy" -> mediaJSON.encodeToJsonElement(jobs.execute(value("jobID")))
                "undo" -> mediaJSON.encodeToJsonElement(jobs.undo(value("jobID")))
                else -> error("Unknown desktop media command.")
            }
            println(buildJsonObject {put("ok",true);put("result",result)})
        }
    } catch(e:Exception) {println(buildJsonObject {put("ok",false);put("error",e.message ?: "Media operation failed; journal and originals retained.")})}
}
