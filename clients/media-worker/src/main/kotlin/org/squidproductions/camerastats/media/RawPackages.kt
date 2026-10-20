package org.squidproductions.camerastats.media

import java.nio.file.*
import org.squidproductions.camerastats.core.Exchange

/** Conservative packages. Internal names are never rewritten, even when the outer copy folder is named. */
data class RawPackage(val directory:Path,val primary:Path,val kind:String,val files:List<Path>,val issue:String="")
object RawPackages {
    fun issue(directory:Path):String=Files.walk(canonical(directory)).use {paths->if(paths.anyMatch {Files.isSymbolicLink(it) || (!Files.isDirectory(it,LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(it,LinkOption.NOFOLLOW_LINKS))}) "Package contains links or special files; make a complete regular-file test copy first." else ""}
    fun files(directory:Path):List<Path> = Files.walk(canonical(directory)).use {paths->paths.filter {Files.isRegularFile(it,LinkOption.NOFOLLOW_LINKS)}.sorted().toList()}
    fun discover(root:Path):List<RawPackage> {
        val paths=Files.walk(canonical(root)).use {it.sorted().toList()};val result=mutableListOf<RawPackage>()
        val red=paths.filter {Files.isDirectory(it,LinkOption.NOFOLLOW_LINKS) && it.fileName.toString().endsWith(".RDC",true)}
        for(folder in red.filter {child->red.none {it!=child && child.startsWith(it)}}) {
            val members=files(folder);val spans=members.filter {it.fileName.toString().endsWith(".R3D",true)}
            if(spans.isEmpty()) continue
            val first=spans.firstOrNull {it.fileName.toString().endsWith("_001.R3D",true)} ?: spans.first()
            val stem=first.fileName.toString().replace(Regex("_[0-9]{3}\\.[Rr]3[Dd]$"),"")
            val pattern=Regex(Regex.escape(stem)+"_([0-9]{3})\\.[Rr]3[Dd]$")
            val numbers=spans.mapNotNull {pattern.matchEntire(it.fileName.toString())?.groupValues?.get(1)?.toInt()}.sorted()
            val issue=when {spans.any {it.parent!=folder}->"Nested R3D files need native package enumeration.";numbers.size!=spans.size->"Multiple or unrecognized R3D clip identities share this package.";numbers!=(1..spans.size).toList()->"R3D span sequence is missing or ambiguous.";else->""}
            result+=RawPackage(folder,first,"red-rdc",members,issue.ifBlank {issue(folder)})
        }
        val braw=paths.filter {Files.isRegularFile(it,LinkOption.NOFOLLOW_LINKS) && it.fileName.toString().endsWith(".braw",true) && result.none {g->it.startsWith(g.directory)}}
        for((folder,clips) in braw.groupBy {it.parent}) {
            if(clips.size!=1 || result.any {it.directory.startsWith(folder)}) continue // Never let an overlapping parent swallow another RAW package or standalone media.
            val members=files(folder)
            val issue=if(members.any {it!=clips.single() && it.fileName.toString().endsWith(".braw",true)} || result.any {it.directory.startsWith(folder)}) "Overlapping or multiple RAW packages require isolated working-copy folders; membership will not be guessed." else ""
            result+=RawPackage(folder,clips.single(),"braw-folder",members,issue.ifBlank {issue(folder)})
        }
        return result
    }
    fun fingerprint(files:List<PhysicalFile>):String=Exchange.digest(files.sortedBy {it.relative}.joinToString("\n") {"${it.relative}\u0000${it.size}\u0000${it.sha256}"}.toByteArray())
    fun directory(root:Path,relative:String):Path=if(relative==".") canonical(root) else canonical(localPath(root,relative))
    fun ensureParents(root:Path,target:Path) {
        var current=canonical(root)
        val parent=target.parent;require(parent.startsWith(current))
        for(part in current.relativize(parent)) {
            current=current.resolve(part)
            if(!Files.exists(current,LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(current)
            require(Files.isDirectory(current,LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(current)) {"Output directory changed or became a link; journal retained."}
        }
    }
}
