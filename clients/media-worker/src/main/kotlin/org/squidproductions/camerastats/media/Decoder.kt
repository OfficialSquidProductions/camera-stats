package org.squidproductions.camerastats.media

import kotlinx.serialization.json.*
import org.squidproductions.camerastats.core.Exchange
import java.nio.file.*
import java.util.concurrent.*
import java.io.ByteArrayOutputStream

/** A child process per probe/decode. No shell, URLs, automatic executable/model download or media write. */
class Decoder(val ffmpeg:Path,val ffprobe:Path,val redline:Path?=System.getenv("SHOT_REPORTER_REDLINE")?.takeIf {it.isNotBlank()}?.let(Path::of),val brawHelper:Path?=System.getenv("SHOT_REPORTER_BRAW_HELPER")?.takeIf {it.isNotBlank()}?.let(Path::of)) {
    private fun red()=RedlineAdapter(redline ?: error("Choose an installed REDline executable to review RED media. Vendor software is not downloaded or bundled automatically."),this)
    private fun isRed(path:Path)=path.fileName.toString().endsWith(".r3d",true)
    private fun isBraw(path:Path)=path.fileName.toString().endsWith(".braw",true)
    private fun braw()=BrawAdapter(brawHelper ?: error("Choose the locally built Shot Reporter BRAW helper and an authorized Blackmagic RAW SDK/runtime. Vendor software is never downloaded automatically."),this)
    data class Result(val bytes:ByteArray,val error:String)
    fun requireExecutable(executable:Path) {
        require(Files.isRegularFile(executable)) {"Choose a regular local command-line executable, not an application folder: $executable"}
        require(Files.isExecutable(executable)) {"This tool cannot execute from its current location: $executable. "+if(System.getProperty("os.name").startsWith("Mac")) "macOS Sandbox does not grant execution through a file picker; use a properly installed tool in Applications." else "Check the local tool installation and executable permissions."}
    }
    fun execute(executable:Path,args:List<String>,timeout:Long=120,limit:Int=8*1024*1024):Result {
        requireExecutable(executable)
        val p=ProcessBuilder(listOf(executable.toAbsolutePath().toString())+args).start()
        val pool=Executors.newFixedThreadPool(2)
        fun collect(input:java.io.InputStream,max:Int):ByteArray=input.use {stream ->val out=ByteArrayOutputStream();val buffer=ByteArray(8192);while(true) {val n=stream.read(buffer);if(n<0) break;require(out.size()+n<=max) {"Decoder output exceeded the safe limit."};out.write(buffer,0,n)};out.toByteArray()}
        val output=pool.submit<ByteArray> {collect(p.inputStream,limit)};val error=pool.submit<ByteArray> {collect(p.errorStream,512*1024)}
        try {
            require(p.waitFor(timeout,TimeUnit.SECONDS)) {"Decoder timed out. Source media was not changed."}
            val text=error.get(5,TimeUnit.SECONDS).toString(Charsets.UTF_8)
            require(p.exitValue()==0) {"Local decoder could not read this file: "+text.takeLast(1500)}
            return Result(output.get(5,TimeUnit.SECONDS),text)
        } finally {p.destroyForcibly();pool.shutdownNow()}
    }
    fun probe(path:Path):Probe {
        val ext=path.fileName.toString().substringAfterLast('.').lowercase()
        if(ext=="r3d") return runCatching {red().probe(path)}.getOrElse {Probe(issue=it.message ?: "REDline probe failed.")}
        if(ext=="braw") return runCatching {braw().probe(path)}.getOrElse {Probe(codec="braw",container="braw",issue=it.message ?: "BRAW probe failed.")}
        if(ext !in setOf("mov","mp4")) return Probe(issue=if(ext in setOf("r3d","braw")) "RAW adapter is not certified. Registration only; decode and physical renaming are disabled." else "This milestone decodes standalone MOV/MP4 only.")
        return try {
            val r=execute(ffprobe,listOf("-v","error","-protocol_whitelist","file,pipe","-enable_drefs","0","-show_streams","-show_format","-of","json",path.toAbsolutePath().toString()))
            val data=mediaJSON.parseToJsonElement(r.bytes.toString(Charsets.UTF_8)).jsonObject
            val streams=data["streams"]?.jsonArray.orEmpty();val video=streams.map {it.jsonObject}.firstOrNull {it["codec_type"]?.jsonPrimitive?.content=="video"} ?: error("No video stream.")
            fun value(key:String)=video[key]?.jsonPrimitive?.content.orEmpty()
            val format=data["format"]?.jsonObject ?: JsonObject(emptyMap());val container=format["format_name"]?.jsonPrimitive?.content.orEmpty()
            val codec=value("codec_name");val tags=buildMap {listOf(format,video).forEach {obj -> obj["tags"]?.jsonObject?.forEach {(k,v)->put(k,v.jsonPrimitive.content)}}}
            val supported=codec in setOf("h264","mpeg4","mjpeg","prores") && container.split(',').any {it in setOf("mov","mp4")}
            Probe(codec,container,value("width").toIntOrNull() ?: 0,value("height").toIntOrNull() ?: 0,value("avg_frame_rate"),value("time_base"),value("duration").ifBlank {format["duration"]?.jsonPrimitive?.content.orEmpty()},value("nb_frames").toLongOrNull(),tags,supported,if(supported) "" else "Container/codec is outside the current tested adapter profile.")
        } catch(e:Exception) {Probe(issue=e.message ?: "Metadata probe failed; registration retained.")}
    }
    fun exactFrame(path:Path,index:Long,destination:Path):Pair<String,String> {
        require(index in 0..100_000_000) {"Invalid source frame index."}
        if(isRed(path)) return red().exactFrame(path,index,destination)
        if(isBraw(path)) return braw().exactFrame(path,index,destination)
        // Decode from the beginning, selecting presentation-order frame N; no guessed timestamp or fast seek.
        val r=execute(ffmpeg,listOf("-hide_banner","-nostdin","-v","info","-copyts","-protocol_whitelist","file,pipe","-enable_drefs","0","-noautorotate","-i",path.toAbsolutePath().toString(),"-map","0:v:0","-an","-sn","-vf","select=eq(n\\,$index),showinfo,scale=960:-2","-frames:v","1","-fps_mode","passthrough","-c:v","png","-f","image2pipe","pipe:1"),timeout=300,limit=16*1024*1024)
        require(r.bytes.size>8 && r.bytes.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) {"Frame $index does not exist or could not be decoded."}
        val pts=Regex("\\bpts:\\s*(-?\\d+)").find(r.error)?.groupValues?.get(1) ?: error("Decoder did not identify the exact frame timestamp.")
        writeNew(destination,r.bytes)
        return pts to Exchange.digest(r.bytes)
    }
    fun frameBatch(path:Path,start:Long,count:Int,directory:Path):List<Pair<Long,Pair<String,Path>>> {
        require(start>=0 && count in 1..60)
        if(isRed(path)) {
            val total=probe(path).frames ?: error("RED frame count is unavailable.");require(start<total)
            return red().frameBatch(path,start,minOf(count,8,(total-start).toInt()),directory)
        }
        if(isBraw(path)) return braw().frameBatch(path,start,minOf(count,8),directory)
        Files.createDirectory(directory)
        val end=Math.addExact(start,count.toLong()-1)
        val r=execute(ffmpeg,listOf("-hide_banner","-nostdin","-v","info","-copyts","-protocol_whitelist","file,pipe","-enable_drefs","0","-noautorotate","-i",path.toAbsolutePath().toString(),"-map","0:v:0","-an","-sn","-vf","select=between(n\\,$start\\,$end),showinfo,scale=960:-2","-frames:v",count.toString(),"-fps_mode","passthrough","-start_number","0","-c:v","png","-f","image2",directory.resolve("frame-%03d.png").toString()),timeout=300)
        val pts=Regex("\\bpts:\\s*(-?\\d+)").findAll(r.error).map {it.groupValues[1]}.toList()
        val images=Files.list(directory).use {it.sorted().toList()}
        require(images.isNotEmpty() && images.size==pts.size && images.size<=count) {"Decoded frame sequence has no exact timestamp mapping."}
        return images.mapIndexed {i,p->(start+i) to (pts[i] to p)}
    }
    fun version():String=execute(ffmpeg,listOf("-version"),timeout=10,limit=64*1024).bytes.toString(Charsets.UTF_8).lineSequence().first()
    fun versionFor(path:Path):String=if(isRed(path)) red().version() else if(isBraw(path)) braw().version() else version()
    fun equivalentMetadata(expected:Probe,actual:Probe):Boolean {
        // Real RED relocation changes only this vendor-reported location. All identity/timing/look fields remain compared.
        fun stable(p:Probe)=if(p.container=="r3d") p.copy(tags=p.tags-"File Path") else p
        return stable(expected)==stable(actual)
    }
}
