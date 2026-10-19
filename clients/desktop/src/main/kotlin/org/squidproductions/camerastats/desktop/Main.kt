package org.squidproductions.camerastats.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import org.jetbrains.skia.Image
import org.squidproductions.camerastats.core.*
import org.squidproductions.camerastats.storage.JdbcDriver
import org.squidproductions.camerastats.ui.*
import java.nio.file.*
import java.nio.channels.FileChannel
import java.nio.ByteBuffer
import java.util.Properties
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter

fun main() = application {
    val root=remember {Path.of(System.getenv("LOCALAPPDATA") ?: Path.of(System.getProperty("user.home"),".local","share").toString(),"Squid Productions","Camera Stats")}
    val gateway=remember {DesktopGateway(root)}
    val repositoryResult=remember {runCatching {Repository(JdbcDriver(root.resolve("reports.sqlite3")))}}
    val state=remember {AppState(repositoryResult.getOrNull(),gateway).also {model ->repositoryResult.exceptionOrNull()?.let {model.error="Local reports could not be opened. Existing bytes were preserved. ${it.message}"};gateway.failure={model.fail(it)}}}
    DisposableEffect(Unit) {onDispose {repositoryResult.getOrNull()?.close()}}
    Window(onCloseRequest=::exitApplication,title="Camera Stats",icon=BitmapPainter(gateway.icon),state=rememberWindowState(width=1100.dp,height=840.dp)) {FieldApp(state)}
}
class DesktopGateway(private val root:Path):PlatformGateway {
    override val label="Windows / desktop"
    private fun resource(name:String)=javaClass.getResourceAsStream("/$name")!!.use {it.readBytes()}
    override val brand:ImageBitmap=Image.makeFromEncoded(resource("brand.png")).toComposeImageBitmap()
    val icon:ImageBitmap=Image.makeFromEncoded(resource("icon.png")).toComposeImageBitmap()
    var failure:(Exception)->Unit={}
    private val prefs=Properties().apply {runCatching {Files.newInputStream(root.resolve("preferences.properties")).use {load(it)}}}
    override fun demo():Report=Exchange.decode(resource("demo-report.json"))
    override fun preference(key:String,fallback:String):String=prefs.getProperty(key,fallback)
    override fun preference(key:String,value:String,write:Boolean) {prefs.setProperty(key,value);runCatching {Files.createDirectories(root);Files.newOutputStream(root.resolve("preferences.properties")).use {prefs.store(it,"Camera Stats local display preferences")}}}
    override fun importFile(complete:(ByteArray?)->Unit) {SwingUtilities.invokeLater {
        try {
            val chooser=JFileChooser().apply {dialogTitle="Import Camera Stats report";fileFilter=FileNameExtensionFilter("Camera Stats reports (*.camreport)","camreport");isAcceptAllFileFilterUsed=false}
            if(chooser.showOpenDialog(null)!=JFileChooser.APPROVE_OPTION) {complete(null);return@invokeLater}
            val bytes=Files.newInputStream(chooser.selectedFile.toPath()).use {it.readNBytes(Exchange.LIMIT+1)};require(bytes.size<=Exchange.LIMIT) {"Report exceeds the 32 MiB alpha limit."};complete(bytes)
        } catch(e:Exception) {failure(e)}
    }}
    override fun exportFile(name:String,mime:String,bytes:ByteArray,complete:()->Unit) {SwingUtilities.invokeLater {
        try {
            val chooser=JFileChooser().apply {dialogTitle="Save Camera Stats report";selectedFile=java.io.File(name)}
            if(chooser.showSaveDialog(null)!=JFileChooser.APPROVE_OPTION) return@invokeLater
            val path=chooser.selectedFile.toPath().toAbsolutePath()
            if(Files.exists(path) && JOptionPane.showConfirmDialog(null,"Replace ${path.fileName}?","Replace existing file",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION) return@invokeLater
            durableExport(path,bytes);complete()
        } catch(e:Exception) {failure(e)}
    }}
    override fun pdf(report:Report):ByteArray=DesktopPDF.render(report)
}
fun durableExport(path:Path,bytes:ByteArray) {
    val temp=Files.createTempFile(path.parent,".camera-stats-export-",".tmp")
    try {
        FileChannel.open(temp,StandardOpenOption.WRITE).use {channel ->val buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining()) channel.write(buffer);channel.force(true)}
        try {Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)} catch(_:AtomicMoveNotSupportedException) {Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING)}
    } finally {Files.deleteIfExists(temp)}
}
