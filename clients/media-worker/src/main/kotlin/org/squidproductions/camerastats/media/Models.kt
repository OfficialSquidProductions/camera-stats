package org.squidproductions.camerastats.media

import kotlinx.serialization.Serializable
import org.squidproductions.camerastats.core.identity

@Serializable data class MediaRoot(val id:String=identity(),val path:String,val label:String,val camera:String,val roll:String,val load:String)
@Serializable data class PhysicalFile(val id:String=identity(),val rootId:String,val relative:String,val size:Long,val sha256:String,val modified:Long)
@Serializable data class Probe(val codec:String="",val container:String="",val width:Int=0,val height:Int=0,val rate:String="",val timeBase:String="",val duration:String="",val frames:Long?=null,val tags:Map<String,String> = emptyMap(),val readable:Boolean=false,val issue:String="")
@Serializable data class Clip(val id:String=identity(),val fileId:String,val rootId:String,val originalName:String,val displayName:String,val metadata:Probe,val companions:List<String> = emptyList(),val memberFileIds:List<String> = emptyList(),val packageRoot:String?=null,val packageKind:String?=null,val packageFingerprint:String?=null,val packageIssue:String="")
@Serializable data class FrameRef(val id:String=identity(),val clipId:String,val fileSHA256:String,val index:Long,val pts:String,val timeBase:String,val png:String,val pngSHA256:String,val decoder:String,val packageFingerprint:String?=null)
@Serializable data class PackageApproval(val clipId:String,val fingerprint:String,val confirmedAt:String,val completeSourceConfirmed:Boolean=false)
@Serializable data class ApprovedSlate(val clipId:String,val scene:String,val take:String,val frame:FrameRef,val confirmedAt:String,val source:String="Manual visual confirmation")
@Serializable data class Association(val id:String=identity(),val clipId:String,val reportId:String,val entryId:String,val reportRevision:Long,val reportFingerprint:String,val reasons:List<String>,val confirmedAt:String,val inFrame:Long?=null,val outFrame:Long?=null)
@Serializable data class MatchSuggestion(val entryId:String,val label:String,val score:Int,val reasons:List<String>,val ambiguous:Boolean,val conflicts:List<String>)
@Serializable data class NamingPreset(val id:String=identity(),val version:Int=1,val name:String,val template:String)
@Serializable data class CopyItem(val clipId:String,val fileId:String,val rootId:String,val sourceRelative:String,val sourceSHA256:String,val size:Long,val targetName:String,val scene:String,val take:String,val frame:FrameRef,val association:Association,val resolved:Map<String,String>,val state:String="planned",val stages:List<String> = emptyList(),val publishedKey:String?=null,val undoRelative:String?=null,val verifyMedia:Boolean=true,val sourceProbe:Probe?=null)
@Serializable data class CopyJob(val id:String=identity(),val projectId:String,val reportRevision:Long,val reportFingerprint:String,val preset:NamingPreset,val destination:String,val createdAt:String,val items:List<CopyItem>,val state:String="prepared",val error:String?=null)
@Serializable data class Audit(val id:String=identity(),val subject:String,val action:String,val detail:String,val time:String)
@Serializable data class MediaSnapshot(val roots:List<MediaRoot>,val files:List<PhysicalFile>,val clips:List<Clip>,val slates:List<ApprovedSlate>,val associations:List<Association>,val jobs:List<CopyJob>,val presets:List<NamingPreset>,val audit:List<Audit>,val packageApprovals:List<PackageApproval> = emptyList())
