package org.squidproductions.camerastats.media

import org.squidproductions.camerastats.core.Exchange
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.nio.file.*
import java.util.Locale
import java.util.concurrent.*
import java.io.ByteArrayOutputStream

/** Calls the user's separately installed REDline. RED binaries and sample footage are never bundled.
 * This adapter is experimental; an actual two-span Mac profile is validated, wider format/platform acceptance is still required.
 * Source frame numbers are zero-based REDline --start indices; timestamps are clip-relative, not native PTS.
 */
class RedlineAdapter(private val tool:Path,private val decoder:Decoder) {
    fun version():String {
        // REDline 65 returns status 1 for --help even after printing its version and help successfully.
        // This fallback identifies version only; probe/render still require successful exit status.
        val text=try {val result=decoder.execute(tool,listOf("--help"),timeout=15,limit=256*1024)
            result.error+"\n"+result.bytes.toString(Charsets.UTF_8)
        } catch(e:IllegalArgumentException) {e.message.orEmpty()}
        return Regex("REDline Build [0-9]+(?:\\.[0-9]+)+[^\\r\\n]*").find(text)?.value
            ?: error("The configured executable did not identify itself as REDline.")
    }
    fun probe(path:Path):Probe {
        requireSource(path)
        return try {
            parseMetadata(queryMetadata(path))
        } catch(e:Exception) {Probe(codec="redcode",container="r3d",issue="REDline metadata failed: "+(e.message ?: "unknown error"))}
    }
    // Kept vendor-specific: REDline metadata-only success is status 1. Render processes remain strict status 0.
    private fun queryMetadata(path:Path):String {
        require(Files.isRegularFile(tool) && Files.isExecutable(tool)) {"Configure the separately installed REDline executable."}
        val process=ProcessBuilder(listOf(tool.toAbsolutePath().toString())+metadataArguments(path)).start()
        val pool=Executors.newFixedThreadPool(2)
        fun collect(input:java.io.InputStream,limit:Int)=input.use {stream->
            val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {val n=stream.read(buffer);if(n<0) break;require(out.size()+n<=limit) {"RED metadata output exceeded its safe limit."};out.write(buffer,0,n)}
            out.toString(Charsets.UTF_8)
        }
        val output=pool.submit<String> {collect(process.inputStream,2*1024*1024)}
        val diagnostic=pool.submit<String> {collect(process.errorStream,512*1024)}
        try {
            require(process.waitFor(60,TimeUnit.SECONDS)) {"RED metadata query timed out."}
            val error=diagnostic.get(5,TimeUnit.SECONDS)
            require(process.exitValue() in setOf(0,1)) {"RED metadata query failed: "+error.takeLast(1500)}
            return output.get(5,TimeUnit.SECONDS)
        } finally {process.destroyForcibly();pool.shutdownNow()}
    }
    fun exactFrame(path:Path,index:Long,destination:Path):Pair<String,String> {
        requireSource(path)
        require(index in 0..Int.MAX_VALUE.toLong()) {"REDline source frame index is outside its supported integer range."}
        require(!Files.exists(destination,LinkOption.NOFOLLOW_LINKS)) {"Frame destination already exists."}
        require(Files.isDirectory(destination.parent,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(destination.parent)) {"Choose a private cache directory."}
        val metadata=probe(path)
        require(metadata.readable && index < (metadata.frames ?: 0)) {"Requested RED source frame does not exist, or metadata is unavailable."}
        val owned=Files.createTempDirectory(destination.parent,"redline-frame-")
        try {
            decoder.execute(tool,frameArguments(path,index,owned),timeout=300,limit=2*1024*1024)
            val images=Files.list(owned).use {files->files.toList()}
            require(images.size==1) {"REDline did not return exactly one image. No frame was accepted."}
            val image=images.single()
            val converted=convertTIFF(image)
            writeNew(destination,converted)
            // The corresponding timeBase is rate.denominator/rate.numerator. This is deliberately not called native PTS.
            return index.toString() to Exchange.digest(converted)
        } finally {
            // Only remove files created in our fresh private staging folder, never recurse into links or sources.
            Files.list(owned).use {files->files.forEach {file->if(!Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS)) Files.deleteIfExists(file)}}
            runCatching {Files.deleteIfExists(owned)}
        }
    }
    /** One bounded vendor decode invocation for a short contiguous review sequence. */
    fun frameBatch(path:Path,start:Long,count:Int,directory:Path):List<Pair<Long,Pair<String,Path>>> {
        requireSource(path);require(start in 0..Int.MAX_VALUE.toLong() && count in 1..8)
        val metadata=probe(path);require(metadata.readable && start < (metadata.frames ?: 0)) {"RED review frames are unavailable."}
        val actualCount=minOf(count.toLong(),metadata.frames!!-start).toInt()
        Files.createDirectory(directory)
        val owned=Files.createTempDirectory(directory,"redline-sequence-")
        try {
            decoder.execute(tool,frameArguments(path,start,owned,actualCount),timeout=300,limit=2*1024*1024)
            val images=Files.list(owned).use {it.sorted().toList()}
            require(images.size==actualCount) {"REDline returned an incomplete review sequence."}
            val numbered=Regex("(?:^|[^0-9])([0-9]{6})\\.(?:tif|tiff)$",RegexOption.IGNORE_CASE)
            return images.mapIndexed {offset,image->
                val number=numbered.find(image.fileName.toString())?.groupValues?.get(1)?.toIntOrNull()
                require(number==offset && Files.isRegularFile(image,LinkOption.NOFOLLOW_LINKS)) {"REDline image sequence has no reliable source-frame mapping."}
                val output=directory.resolve("frame-%03d.png".format(offset))
                writeNew(output,convertTIFF(image));val index=start+offset
                index to (index.toString() to output)
            }
        } finally {
            Files.list(owned).use {files->files.forEach {file->if(!Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS)) Files.deleteIfExists(file)}}
            runCatching {Files.deleteIfExists(owned)}
        }
    }
    private fun convertTIFF(image:Path):ByteArray {
        require(Files.isRegularFile(image,LinkOption.NOFOLLOW_LINKS) && image.extensionLower() in setOf("tif","tiff") && Files.size(image) in 8..128L*1024*1024) {"REDline output is not a bounded regular TIFF image."}
        val prefix=Files.newInputStream(image,LinkOption.NOFOLLOW_LINKS).use {it.readNBytes(4)}
        require(prefix.contentEquals(byteArrayOf(73,73,42,0)) || prefix.contentEquals(byteArrayOf(77,77,0,42))) {"REDline output has an invalid TIFF signature."}
        val converted=decoder.execute(decoder.ffmpeg,listOf("-hide_banner","-nostdin","-v","error","-protocol_whitelist","file,pipe","-i",image.toString(),"-map","0:v:0","-an","-sn","-vf","scale=960:-2","-frames:v","1","-c:v","png","-f","image2pipe","pipe:1"),timeout=60,limit=16*1024*1024).bytes
        require(converted.size>8 && converted.copyOfRange(0,8).contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) {"The local image decoder did not produce a PNG."}
        return converted
    }
    private fun requireSource(path:Path) {
        require(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) && path.extensionLower()=="r3d") {"REDline requires a regular local R3D source file."}
    }
    companion object {
        internal fun metadataArguments(path:Path)=listOf("--i",path.toAbsolutePath().toString(),"--printMeta","3","--useMeta","--noAudio")
        internal fun frameArguments(path:Path,index:Long,directory:Path,count:Int=1):List<String> = listOf(
            "--i",path.toAbsolutePath().toString(),"--outDir",directory.toAbsolutePath().toString(),"--o",directory.resolve("frame").toAbsolutePath().toString(),
            "--format","1","--start",index.toString(),"--frameCount",count.toString(),"--renum","0","--pad","6",
            "--res","2","--useMeta","--metaIgnoreFrameGuide","--colorSciVersion","3","--colorSpace","1","--gammaCurve","1",
            "--outputToneMap","1","--rollOff","3","--noAudio","--gpuPlatform","0","--numGraphs","1","--decodeThreads","2","--maxMemory","1073741824")
        internal fun parseMetadata(text:String):Probe {
            val rows=parseCSV(text)
            val headerIndex=rows.indexOfFirst {row->row.any {it.trim()=="Frame Width"} && row.any {it.trim()=="Total Frames"}}
            require(headerIndex>=0 && headerIndex+1<rows.size) {"REDline returned no recognized metadata CSV header."}
            val headers=rows[headerIndex].map {it.trim().removePrefix("\uFEFF")}
            val values=rows[headerIndex+1]
            require(headers.size==values.size) {"REDline metadata CSV is incomplete."}
            listOf("Frame Width","Frame Height","Total Frames","FPS").forEach {key->require(headers.count {it==key}<=1) {"REDline critical metadata is ambiguous: $key"}}
            val seen=mutableMapOf<String,Int>()
            val tags=headers.zip(values).associate {(key,value)->val number=(seen[key] ?: 0)+1;seen[key]=number;(if(number==1) key else "$key [$number]") to value}
            fun field(vararg names:String)=names.firstNotNullOfOrNull {tags[it]?.trim()?.takeIf(String::isNotEmpty)} ?: ""
            val width=field("Frame Width").toIntOrNull() ?: error("Missing RED frame width.")
            val height=field("Frame Height").toIntOrNull() ?: error("Missing RED frame height.")
            val frames=field("Total Frames").toLongOrNull() ?: error("Missing RED logical clip frame count.")
            // Playback FPS and camera record FPS can differ on off-speed footage. Never substitute Record FPS.
            val rawRate=field("FPS","Frame Rate","Clip FPS","Project FPS")
            val rate=rateRational(rawRate)
            require(width in 1..65536 && height in 1..65536 && frames in 1..Int.MAX_VALUE.toLong()) {"Invalid RED clip geometry or frame count."}
            val parts=rate.split('/');val numerator=parts[0].toBigDecimal();val denominator=parts[1].toBigDecimal()
            val duration=BigDecimal(frames).multiply(denominator).divide(numerator,MathContext.DECIMAL64).stripTrailingZeros().toPlainString()
            return Probe("redcode","r3d",width,height,rate,"${parts[1]}/${parts[0]}",duration,frames,tags+mapOf(
                "adapter" to "REDline (separately installed)","adapterStatus" to "Experimental; locally tested Mac two-span profile; wider format/platform acceptance required",
                "timestampMeaning" to "Zero-based clip presentation-frame index at playback FPS; clip-relative time, not native stream PTS",
                "rateMeaning" to if(rawRate==rate) "REDline playback FPS rational" else "REDline floating-point playback FPS normalized to a nominal rational; original value retained below",
                "playbackFPSOriginal" to rawRate,"reviewProcessing" to "REDline half-resolution high-quality CPU IPP2, BT.709 preview; embedded look defaults, frame guides ignored; external RMD not applied"),true,"")
        }
        internal fun rateRational(value:String):String {
            require(value.length in 1..32 && value.matches(Regex("[0-9./]+"))) {"Invalid RED playback FPS representation."}
            val pair=if(value.contains('/')) {
                val parts=value.split('/');require(parts.size==2);parts[0].trim().toBigInteger() to parts[1].trim().toBigInteger()
            } else {
                // RED prints nominal NTSC rates to a limited number of decimals. Preserve the original text in metadata.
                val rate=value.toBigDecimalOrNull() ?: error("Missing RED playback FPS; record FPS cannot safely substitute.")
                val ntsc=mapOf("23.976" to (24000L to 1001L),"23.98" to (24000L to 1001L),"29.97" to (30000L to 1001L),"47.952" to (48000L to 1001L),"47.95" to (48000L to 1001L),"59.94" to (60000L to 1001L),"119.88" to (120000L to 1001L))
                val nominal=ntsc[rate.stripTrailingZeros().toPlainString()] ?: listOf(24000L,30000L,48000L,60000L,72000L,90000L,96000L,120000L,144000L,180000L,192000L,240000L,360000L).firstOrNull {n->
                    val expected=BigDecimal(n).divide(BigDecimal(1001),MathContext.DECIMAL64)
                    rate.subtract(expected).abs()<=maxOf(BigDecimal("0.000005"),expected.multiply(BigDecimal("0.0000001")))
                }?.let {it to 1001L}
                if(nominal!=null) nominal.first.toBigInteger() to nominal.second.toBigInteger()
                else {val normalized=rate.stripTrailingZeros();require(normalized.scale() in -3..9) {"RED playback FPS precision exceeds the supported range."};if(normalized.scale()>=0) normalized.unscaledValue() to BigInteger.TEN.pow(normalized.scale()) else normalized.toBigIntegerExact() to BigInteger.ONE}
            }
            require(pair.first>BigInteger.ZERO && pair.second>BigInteger.ZERO && pair.second<=BigInteger.valueOf(1_000_000_000) && pair.first<=BigInteger.valueOf(2_000_000_000) && pair.first.toBigDecimal().divide(pair.second.toBigDecimal(),MathContext.DECIMAL64)<=BigDecimal(2000)) {"Invalid RED playback FPS."}
            val gcd=pair.first.gcd(pair.second);return "${pair.first/gcd}/${pair.second/gcd}"
        }
        internal fun parseCSV(text:String):List<List<String>> {
            require(text.length<=2*1024*1024) {"RED metadata exceeded its safe limit."}
            val rows=mutableListOf<List<String>>();val row=mutableListOf<String>();val field=StringBuilder();var quoted=false;var i=0
            while(i<text.length) {val c=text[i]
                when {
                    c=='"' && quoted && i+1<text.length && text[i+1]=='"' -> {field.append('"');i++}
                    c=='"' && (quoted || field.isEmpty()) -> quoted=!quoted
                    c==',' && !quoted -> {row+=field.toString();field.setLength(0)}
                    (c=='\r' || c=='\n') && !quoted -> {if(c=='\r' && i+1<text.length && text[i+1]=='\n') i++;row+=field.toString();field.setLength(0);if(row.any(String::isNotBlank)) rows+=row.toList();row.clear()}
                    else -> field.append(c)
                };i++
            }
            require(!quoted) {"Unterminated quoted RED metadata field."}
            if(field.isNotEmpty() || row.isNotEmpty()) {row+=field.toString();rows+=row.toList()}
            return rows
        }
    }
}
private fun Path.extensionLower()=fileName.toString().substringAfterLast('.').lowercase(Locale.ROOT)
