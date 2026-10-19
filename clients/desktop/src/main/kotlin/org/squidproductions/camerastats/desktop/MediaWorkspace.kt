package org.squidproductions.camerastats.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.Image
import org.squidproductions.camerastats.core.*
import org.squidproductions.camerastats.media.*
import org.squidproductions.camerastats.ui.AppState
import java.nio.file.*
import java.util.concurrent.*
import javax.swing.*
import kotlinx.coroutines.delay

class DesktopMediaState(val home:Path):AutoCloseable {
    var snapshot by mutableStateOf(MediaSnapshot(emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList(),emptyList()));private set
    var busy by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null)
    var selected by mutableStateOf<String?>(null)
    var frame by mutableStateOf<FrameRef?>(null)
    var suggestions by mutableStateOf(emptyList<MatchSuggestion>())
    var frames by mutableStateOf(emptyList<FrameRef>());private set
    var playing by mutableStateOf(false)
    var reverse by mutableStateOf(false)
    var ffmpeg by mutableStateOf(System.getenv("SHOT_REPORTER_FFMPEG") ?: "")
    var ffprobe by mutableStateOf(System.getenv("SHOT_REPORTER_FFPROBE") ?: "")
    private val pool=Executors.newSingleThreadExecutor {r->Thread(r,"Shot Reporter media worker").apply {isDaemon=true}}
    private var future:Future<*>?=null
    private var serial=0L
    private var closed=false
    private var repository:MediaRepository?=null
    init {run({repo,_ ->repo.snapshot()}) {snapshot=it}}
    fun <T> run(action:(MediaRepository,MediaService)->T,done:(T)->Unit={}) {
        if(busy) return
        busy=true;error=null;val operation=++serial
        val decoder=Decoder(Path.of(ffmpeg.ifBlank {"missing-ffmpeg"}),Path.of(ffprobe.ifBlank {"missing-ffprobe"}))
        future=pool.submit {
            try {
                val repo=repository ?: MediaRepository(home).also {repository=it};val service=MediaService(repo,decoder)
                val result=action(repo,service);val latest=repo.snapshot()
                SwingUtilities.invokeLater {if(!closed && operation==serial) {snapshot=latest;done(result);busy=false}}
            } catch(e:Exception) {
                Thread.interrupted()
                val latest=runCatching {repository?.snapshot()}.getOrNull()
                SwingUtilities.invokeLater {if(!closed && operation==serial) {latest?.let {snapshot=it};error=if(e is InterruptedException) "Operation cancelled; originals and journal retained." else e.message;busy=false;playing=false}}
            }
        }
    }
    fun refresh()=run({repo,_->repo.snapshot()}) {snapshot=it}
    fun decode(index:Long) {val id=selected ?: return;playing=false;run({_,svc->svc.frame(id,index)}) {frame=it}}
    fun play(backwards:Boolean=false) {
        val id=selected ?: return;reverse=backwards
        val current=frame?.index ?: 0L;val start=if(backwards) maxOf(0,current-47) else current
        run({_,svc->svc.frames(id,start,48)}) {result ->frames=if(backwards) result.filter {it.index<=current}.reversed() else result;playing=true}
    }
    fun cancel() {serial++;playing=false;future?.cancel(true);busy=false;refresh();error="Cancellation requested. The journal refreshes after the worker stops; retained copies can be resumed."}
    fun chooseTools() {
        val chooser=JFileChooser().apply {dialogTitle="Choose local FFmpeg bin folder";fileSelectionMode=JFileChooser.DIRECTORIES_ONLY}
        if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) {val dir=chooser.selectedFile.toPath();val ext=if(System.getProperty("os.name").startsWith("Windows")) ".exe" else "";ffmpeg=dir.resolve("ffmpeg$ext").toString();ffprobe=dir.resolve("ffprobe$ext").toString()}
    }
    override fun close() {closed=true;serial++;future?.cancel(true);pool.execute {repository?.close()};pool.shutdown()}
}
fun chooseMediaDirectory(title:String):Path? {
    val chooser=JFileChooser().apply {dialogTitle=title;fileSelectionMode=JFileChooser.DIRECTORIES_ONLY;isAcceptAllFileFilterUsed=false}
    return if(chooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun MediaWorkspace(report:Report,appState:AppState,root:Path,providedState:DesktopMediaState?=null) {
    val home=remember(report.id) {root.resolve("desktop-media").resolve(Exchange.digest(report.id.toByteArray()))}
    val state=remember(home) {providedState ?: DesktopMediaState(home)}
    DisposableEffect(state) {onDispose {state.close()}}
    var query by remember {mutableStateOf("")};var checked by remember {mutableStateOf(setOf<String>())}
    var camera by remember {mutableStateOf(report.defaults["camera"] ?: "A")};var roll by remember {mutableStateOf(report.defaults["roll"] ?: "A001")};var load by remember {mutableStateOf(report.defaults["load"] ?: identity())}
    var scene by remember {mutableStateOf("")};var take by remember {mutableStateOf("")};var index by remember {mutableStateOf("0")}
    var template by remember {mutableStateOf(DesktopNaming.movie)};var presetName by remember {mutableStateOf("Movie")}
    var presetID by remember {mutableStateOf(identity())};var presetVersion by remember {mutableStateOf(1)};var presetMenu by remember {mutableStateOf(false)}
    val clip=state.snapshot.clips.firstOrNull {it.id==state.selected};val focus=remember {FocusRequester()}
    val dark=appState.platform.preference("theme","System").let {it=="Dark" || it=="System" && isSystemInDarkTheme()}
    val palette=if(dark) darkColorScheme(primary=androidx.compose.ui.graphics.Color(0xff95df8d),background=androidx.compose.ui.graphics.Color(0xff191419),surface=androidx.compose.ui.graphics.Color(0xff251d23),onSurface=androidx.compose.ui.graphics.Color(0xfff3eaf0)) else lightColorScheme(primary=androidx.compose.ui.graphics.Color(0xff237b20),background=androidx.compose.ui.graphics.Color(0xfffaf8f9),surface=androidx.compose.ui.graphics.Color.White)
    LaunchedEffect(state.playing,state.frames) {
        if(state.playing) {
            for((i,frame) in state.frames.withIndex()) {
                if(!state.playing) break
                state.frame=frame;index=frame.index.toString()
                val next=state.frames.getOrNull(i+1)
                val rate=frame.timeBase.split('/').mapNotNull {it.toDoubleOrNull()};val unit=if(rate.size==2 && rate[1]!=0.0) rate[0]/rate[1] else 0.0
                val interval=if(next!=null && unit>0) kotlin.math.abs((next.pts.toDouble()-frame.pts.toDouble())*unit*1000).toLong() else 80
                delay(interval.coerceIn(10,2000))
            }
            state.playing=false
        }
    }
    MaterialTheme(colorScheme=palette) {Surface(Modifier.fillMaxSize(),color=palette.background) {Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Image(appState.platform.brand,null,Modifier.size(32.dp,44.dp));Column(Modifier.weight(1f)) {Text("Shot Reporter",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Black);Text("A Squid Productions Software",style=MaterialTheme.typography.labelMedium)}
            Column {Text("Desktop footage · development",fontWeight=FontWeight.Bold);Text("${report.values["title"] ?: "Untitled"} · report r${report.revision}",style=MaterialTheme.typography.bodySmall)}
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(camera,{camera=it},label={Text("Camera")},modifier=Modifier.width(100.dp),singleLine=true,enabled=!state.busy)
            OutlinedTextField(roll,{roll=it},label={Text("Roll")},modifier=Modifier.width(110.dp),singleLine=true,enabled=!state.busy)
            OutlinedTextField(load,{load=it},label={Text("Load identity")},modifier=Modifier.width(180.dp),singleLine=true,enabled=!state.busy)
            Button(enabled=!state.busy,onClick={chooseMediaDirectory("Register working-copy folder")?.let {path->state.run({_,svc->svc.register(path,camera,roll,load)})}}) {Text("Register folder")}
            OutlinedButton(enabled=!state.busy,onClick={state.chooseTools()}) {Text("Local decoder tools")}
            if(state.busy) {CircularProgressIndicator(Modifier.size(22.dp));TextButton(onClick={state.cancel()}) {Text("Cancel · retain journal")}}
        }
        Text("Sources stay unchanged. Standalone MOV/MP4 profile only; RAW registration does not enable RAW playback or renaming. Silent frame review; OCR is not implemented.",style=MaterialTheme.typography.bodySmall)
        Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(280.dp).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query,{query=it},label={Text("Search clips / confirmed slates")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Text("${state.snapshot.clips.size} registered · ${checked.size} checked",style=MaterialTheme.typography.labelMedium)
                LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    items(state.snapshot.clips.filter {candidate ->val slate=state.snapshot.slates.firstOrNull {it.clipId==candidate.id};(candidate.originalName+" "+slate?.scene+" "+slate?.take).contains(query,true)},key={it.id}) {item ->
                        Card(Modifier.fillMaxWidth().clickable(enabled=!state.busy) {state.selected=item.id;state.frame=null;state.playing=false;state.suggestions=emptyList();index="0";val approved=state.snapshot.slates.firstOrNull {it.clipId==item.id};scene=approved?.scene.orEmpty();take=approved?.take.orEmpty()}) {
                            Row(Modifier.padding(8.dp),verticalAlignment=Alignment.Top) {
                                Checkbox(item.id in checked,{value->checked=if(value) checked+item.id else checked-item.id},enabled=!state.busy)
                                Column {Text(item.originalName,fontWeight=if(item.id==state.selected) FontWeight.Black else FontWeight.Bold);Text(item.metadata.codec.ifBlank {"Unavailable decode"},style=MaterialTheme.typography.bodySmall);Text(if(state.snapshot.slates.any {it.clipId==item.id}) "Slate confirmed" else "Needs slate review",style=MaterialTheme.typography.bodySmall)}
                            }
                        }
                    }
                }
                state.snapshot.roots.forEach {registered ->TextButton(enabled=!state.busy,onClick={chooseMediaDirectory("Relink ${registered.label} · every file must match")?.let {path->state.run({_,svc->svc.relinkRoot(registered.id,path)})}}) {Text("Relink ${registered.camera} / ${registered.roll}")}}
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                if(clip==null) {Text("Register a folder, then select footage to review.",style=MaterialTheme.typography.titleLarge);Text("Import or open your .camreport under Reports. Media matching always requires explicit review; ambiguous suggestions never create links.")}
                else {
                    Text(clip.originalName,style=MaterialTheme.typography.titleLarge)
                    Text("${clip.metadata.width} × ${clip.metadata.height} · ${clip.metadata.rate} · ${clip.metadata.duration}s · ${clip.metadata.frames ?: "unknown"} frames",style=MaterialTheme.typography.bodySmall)
                    if(clip.metadata.issue.isNotBlank()) Text(clip.metadata.issue,color=palette.error)
                    state.frame?.let {frame ->
                        val bitmap=remember(frame.id) {runCatching {Image.makeFromEncoded(Files.readAllBytes(home.resolve(frame.png))).toComposeImageBitmap()}.getOrNull()}
                        Box(Modifier.fillMaxWidth().height(270.dp).focusRequester(focus).focusable().clickable {focus.requestFocus()}.onKeyEvent {event ->if(event.type==KeyEventType.KeyDown) {when(event.key) {Key.J->{if(!state.busy) state.play(true);true};Key.K->{state.playing=false;true};Key.L->{if(!state.busy) state.play();true};else->false}} else false},contentAlignment=Alignment.Center) {bitmap?.let {Image(it,"Decoded source frame ${frame.index}",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}}
                        Text("Source frame ${frame.index} · PTS ${frame.pts} × ${frame.timeBase}. Click frame for J / K / L.",style=MaterialTheme.typography.labelMedium)
                    }
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(index,{index=it},label={Text("Source frame index")},modifier=Modifier.width(160.dp),enabled=!state.busy)
                        OutlinedButton(enabled=!state.busy && clip.metadata.readable,onClick={index.toLongOrNull()?.let {state.decode(it)}}) {Text("Decode exact frame")}
                        TextButton(enabled=!state.busy && (state.frame?.index ?: 0)>0,onClick={state.decode((state.frame?.index ?: 1)-1)}) {Text("< Frame")}
                        TextButton(enabled=!state.busy && clip.metadata.readable,onClick={state.decode((state.frame?.index ?: -1)+1)}) {Text("Frame >")}
                        Button(enabled=!state.busy && clip.metadata.readable,onClick={if(state.playing) state.playing=false else state.play()}) {Text(if(state.playing) "Pause" else "Play silent review")}
                    }
                    HorizontalDivider()
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(scene,{scene=it},label={Text("Confirmed scene")},modifier=Modifier.weight(1f),enabled=!state.busy)
                        OutlinedTextField(take,{take=it},label={Text("Confirmed take")},modifier=Modifier.weight(1f),enabled=!state.busy)
                    }
                    Button(enabled=!state.busy && !state.playing && state.frame?.clipId==clip.id && scene.isNotBlank() && take.isNotBlank(),onClick={val frame=state.frame!!;state.run({_,svc->svc.approve(clip.id,frame.id,scene,take)})}) {Text("Confirm values from this frame")}
                    state.snapshot.slates.firstOrNull {it.clipId==clip.id}?.let {Text("Approved ${it.scene} / ${it.take} · frame ${it.frame.index} · ${it.source}")}
                    OutlinedButton(enabled=!state.busy,onClick={state.run({_,svc->svc.suggestions(clip.id,report)}) {state.suggestions=it}}) {Text("Review report associations")}
                    state.suggestions.forEach {suggestion ->Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(10.dp)) {
                        Text(suggestion.label,fontWeight=FontWeight.Bold);Text(suggestion.reasons.joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                        if(suggestion.ambiguous) Text("Ambiguous suggestion · manual review required",style=MaterialTheme.typography.labelMedium)
                        suggestion.conflicts.forEach {Text(it,color=palette.error,style=MaterialTheme.typography.bodySmall)}
                        TextButton(enabled=!state.busy,onClick={state.run({_,svc->svc.link(clip.id,report,suggestion.entryId)})}) {Text("Confirm this association")}
                    }}}
                    state.snapshot.associations.filter {it.clipId==clip.id && it.reportId==report.id}.forEach {link ->Row(verticalAlignment=Alignment.CenterVertically) {Text("Linked entry ${link.entryId.take(8)} · r${link.reportRevision}",Modifier.weight(1f));TextButton(enabled=!state.busy,onClick={state.run({_,svc->svc.unlink(link.id)})}) {Text("Unlink")}}}
                    if(clip.companions.isNotEmpty()) Text("Related files require a group-aware adapter: ${clip.companions.joinToString()}. Physical copy/rename is blocked.",color=palette.error)
                }
                HorizontalDivider();Text("Preview renamed working copies",style=MaterialTheme.typography.titleMedium)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(enabled=!state.busy,onClick={template=DesktopNaming.movie;presetName="Movie";presetID=identity();presetVersion=1}) {Text("Movie")}
                    TextButton(enabled=!state.busy,onClick={template=DesktopNaming.television;presetName="TV";presetID=identity();presetVersion=1}) {Text("TV")}
                    Box {TextButton(enabled=!state.busy && state.snapshot.presets.isNotEmpty(),onClick={presetMenu=true}) {Text("Saved presets")};DropdownMenu(presetMenu,{presetMenu=false}) {state.snapshot.presets.forEach {saved->DropdownMenuItem(text={Text("${saved.name} v${saved.version}")},onClick={presetID=saved.id;presetVersion=saved.version;presetName=saved.name;template=saved.template;presetMenu=false})}}}
                    Text("Preset v$presetVersion",style=MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(presetName,{presetName=it;presetVersion++},label={Text("Preset name")},enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(template,{template=it;presetVersion++},label={Text("Declarative naming template")},enabled=!state.busy,modifier=Modifier.fillMaxWidth())
                Text("Tokens: project, season, episode, scene, take:03, camera, roll, original, asset. Missing values and collisions block the complete batch. Selected clips need a confirmed frame and one current report association.",style=MaterialTheme.typography.bodySmall)
                Button(enabled=!state.busy && checked.isNotEmpty(),onClick={chooseMediaDirectory("Choose copy destination outside source folders")?.let {destination->val preset=NamingPreset(presetID,presetVersion,presetName,template);state.run({_,svc->CopyJobs(svc).prepare(checked.toList(),report,preset,destination)})}}) {Text("Preflight ${checked.size} checked clips")}
                state.snapshot.jobs.sortedByDescending {it.createdAt}.forEach {job ->CopyJobCard(job,state)}
            }
        }
        state.error?.let {message->Text(message,color=palette.error,style=MaterialTheme.typography.bodySmall)}
    }}}
}
@Composable private fun CopyJobCard(job:CopyJob,state:DesktopMediaState) {
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("${job.state.uppercase()} · ${job.items.size} files · report r${job.reportRevision}",fontWeight=FontWeight.Bold)
        Text("${job.preset.name} v${job.preset.version} · ${job.destination}",style=MaterialTheme.typography.bodySmall)
        job.items.forEach {Text("${it.sourceRelative} → ${it.targetName} · ${it.state}",style=MaterialTheme.typography.bodySmall)}
        job.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Row {
            if(job.state in setOf("prepared","copying","blocked")) Button(enabled=!state.busy,onClick={state.run({_,svc->CopyJobs(svc).execute(job.id)})}) {Text(if(job.state=="prepared") "Create verified copies" else "Resume journal")}
            if(job.state in setOf("completed","undoing")) TextButton(enabled=!state.busy,onClick={state.run({_,svc->CopyJobs(svc).undo(job.id)})}) {Text("Undo · retain recovery copies")}
        }
        if(job.state=="completed") Text("SHA-256, metadata and approved-frame checks passed. Relink manifest saved with copies. This is not approval to reuse a camera card.",style=MaterialTheme.typography.bodySmall)
    }}
}
