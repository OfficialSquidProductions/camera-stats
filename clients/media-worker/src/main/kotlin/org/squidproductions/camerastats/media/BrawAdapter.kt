package org.squidproductions.camerastats.media

import kotlinx.serialization.json.*
import org.squidproductions.camerastats.core.Exchange
import java.nio.file.*

/** Original local helper; the user supplies an authorized SDK/runtime. No vendor downloads or writes. */
class BrawAdapter(private val helper:Path,private val decoder:Decoder) {
    fun version():String {
        val value=mediaJSON.parseToJsonElement(decoder.execute(helper,listOf("version"),timeout=15,limit=64*1024).bytes.toString(Charsets.UTF_8)).jsonObject
        require(value["adapter"]?.jsonPrimitive?.content=="shot-reporter-braw") {"The selected tool is not the Shot Reporter BRAW adapter."}
        return "Shot Reporter BRAW / SDK ${value["sdkVersion"]?.jsonPrimitive?.content} / build ${value["sdkBuild"]?.jsonPrimitive?.content} / API ${value["sdkApiVersion"]?.jsonPrimitive?.content}"
    }
    fun probe(path:Path):Probe {
        requireSource(path)
        val result=decoder.execute(helper,listOf("probe",path.toAbsolutePath().toString()),timeout=60,limit=1024*1024)
        val probe=mediaJSON.decodeFromString<Probe>(result.bytes.toString(Charsets.UTF_8))
        require(probe.codec=="braw" && probe.container=="braw" && probe.readable && probe.width in 1..32768 && probe.height in 1..32768 && probe.width.toLong()*probe.height<=67_108_864 && probe.frames in 1L..100_000_001L) {"The BRAW helper returned invalid clip metadata."}
        require(probe.tags["multicardCount"]=="1") {"BRAW multicard packages need native member enumeration and separate validation; no copy or frame was accepted."}
        require(probe.rate.matches(Regex("[1-9][0-9]*/[1-9][0-9]*")) && probe.timeBase==probe.rate.split('/').reversed().joinToString("/")) {"BRAW frame timing could not be identified."}
        return probe
    }
    fun exactFrame(path:Path,index:Long,destination:Path):Pair<String,String> {
        requireSource(path);val probe=probe(path);require(index>=0 && index<probe.frames!!) {"Requested BRAW source frame does not exist."}
        require(!Files.exists(destination,LinkOption.NOFOLLOW_LINKS)) {"Frame destination already exists."}
        val owned=Files.createTempDirectory(canonical(destination.parent),"braw-frame-");val ppm=owned.resolve("frame.ppm")
        try {
            val result=decoder.execute(helper,listOf("frame",path.toAbsolutePath().toString(),index.toString(),ppm.toString()),timeout=60,limit=64*1024)
            val mapping=mediaJSON.parseToJsonElement(result.bytes.toString(Charsets.UTF_8)).jsonObject
            require(mapping["index"]?.jsonPrimitive?.long==index && mapping["pts"]?.jsonPrimitive?.long==index && mapping["width"]?.jsonPrimitive?.int==probe.width && mapping["height"]?.jsonPrimitive?.int==probe.height) {"BRAW SDK frame identity or dimensions did not match the requested source frame."}
            val header="P6\n${probe.width} ${probe.height}\n255\n".toByteArray()
            require(Files.isRegularFile(ppm,LinkOption.NOFOLLOW_LINKS) && Files.size(ppm)==header.size+probe.width.toLong()*probe.height*3) {"BRAW helper returned an incomplete image."}
            require(Files.newInputStream(ppm,LinkOption.NOFOLLOW_LINKS).use {it.readNBytes(header.size)}.contentEquals(header)) {"BRAW helper returned an invalid image header."}
            val image=decoder.execute(decoder.ffmpeg,listOf("-hide_banner","-nostdin","-v","error","-protocol_whitelist","file,pipe","-i",ppm.toString(),"-map","0:v:0","-an","-sn","-vf","scale=960:-2","-frames:v","1","-c:v","png","-f","image2pipe","pipe:1"),timeout=60,limit=16*1024*1024).bytes
            require(image.size>8 && image.copyOfRange(0,8).contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) {"BRAW review conversion did not produce a PNG."}
            writeNew(destination,image);return index.toString() to Exchange.digest(image)
        } finally {
            // Only our new owned PPM is disposable. Sources, partial copy stages and journals stay untouched.
            if(Files.isRegularFile(ppm,LinkOption.NOFOLLOW_LINKS)) Files.deleteIfExists(ppm)
            runCatching {Files.deleteIfExists(owned)}
        }
    }
    fun frameBatch(path:Path,start:Long,count:Int,directory:Path):List<Pair<Long,Pair<String,Path>>> {
        require(start>=0 && count in 1..8);val total=probe(path).frames!!;require(start<total)
        Files.createDirectory(directory)
        return (start until minOf(total,start+count)).map {index->val png=directory.resolve("frame-${index-start}.png");index to (exactFrame(path,index,png).first to png)}
    }
    private fun requireSource(path:Path) {require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) && path.fileName.toString().endsWith(".braw",true)) {"BRAW requires an explicitly selected regular local source file."}}
}
