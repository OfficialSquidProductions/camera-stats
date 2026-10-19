package org.squidproductions.camerastats.media

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.squidproductions.camerastats.storage.JdbcDriver
import java.nio.file.*
import java.nio.channels.FileChannel
import java.io.Closeable
import java.time.Instant

val mediaJSON=Json {encodeDefaults=true;ignoreUnknownKeys=false}
fun now():String=Instant.now().toString()

/** Separate database: never opens or migrates reports.sqlite3. One mutation worker per project. */
class MediaRepository(val home:Path):Closeable {
    private val db:JdbcDriver
    init {
        Files.createDirectories(home);db=JdbcDriver(home.resolve("media.sqlite3"))
        try {
            // Inspect identity before changing journal mode or schema. Never adopt an unrelated database.
            val version=(db.query("PRAGMA user_version").single().single() as Number).toInt()
            val application=(db.query("PRAGMA application_id").single().single() as Number).toInt()
            val tables=db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")
            val fresh=version==0 && application==0 && tables.isEmpty()
            require(fresh || (application==0x53524d44 && version==1)) {"Unknown or newer desktop media database; its schema and bytes were preserved."}
            check(db.query("PRAGMA quick_check").single().single()=="ok") {"Media database needs recovery; original bytes preserved."}
            db.query("PRAGMA busy_timeout=3000");db.query("PRAGMA journal_mode=WAL");db.execute("PRAGMA synchronous=FULL")
            if(fresh) transaction {
                db.execute("CREATE TABLE records(kind TEXT NOT NULL,id TEXT NOT NULL,json TEXT NOT NULL,PRIMARY KEY(kind,id))")
                db.execute("PRAGMA application_id=1397902660");db.execute("PRAGMA user_version=1")
            }
        } catch(e:Throwable) {db.close();throw e}
    }
    fun <T> exclusive(action:()->T):T = FileChannel.open(home.resolve("worker.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE).use {channel ->
        val lock=runCatching {channel.tryLock()}.getOrNull() ?: error("Another desktop media worker is active. Wait for it to finish.")
        lock.use { action() }
    }
    fun <T> transaction(action:()->T):T {
        db.execute("BEGIN IMMEDIATE")
        try {val result=action();db.execute("COMMIT");return result} catch(e:Throwable) {runCatching {db.execute("ROLLBACK")};throw e}
    }
    inline fun <reified T> put(kind:String,id:String,value:T)=putJSON(kind,id,mediaJSON.encodeToString(value))
    fun putJSON(kind:String,id:String,json:String) {db.execute("INSERT OR REPLACE INTO records(kind,id,json) VALUES(?,?,?)",listOf(kind,id,json))}
    inline fun <reified T> all(kind:String):List<T> = allJSON(kind).map {mediaJSON.decodeFromString<T>(it)}
    fun allJSON(kind:String):List<String> = db.query("SELECT json FROM records WHERE kind=? ORDER BY id",listOf(kind)).map {it[0] as String}
    fun remove(kind:String,id:String) {db.execute("DELETE FROM records WHERE kind=? AND id=?",listOf(kind,id))}
    fun audit(subject:String,action:String,detail:String) {val event=Audit(subject=subject,action=action,detail=detail,time=now());put("audit",event.id,event)}
    fun rememberPreset(preset:NamingPreset) {
        require(preset.id.isNotBlank() && preset.id.length<=100 && preset.version>0 && preset.name.isNotBlank() && preset.name.length<=100 && preset.template.length in 1..500) {"Invalid naming preset."}
        val key="${preset.id}:${preset.version}"
        val previous=all<NamingPreset>("presetVersion").firstOrNull {it.id==preset.id && it.version==preset.version}
        require(previous==null || previous==preset) {"That naming-preset version is already frozen. Increment its version before changing its rules."}
        put("presetVersion",key,preset)
        val latest=all<NamingPreset>("preset").firstOrNull {it.id==preset.id}
        if(latest==null || latest.version<=preset.version) put("preset",preset.id,preset)
    }
    fun snapshot():MediaSnapshot {
        db.execute("BEGIN")
        try {val result=MediaSnapshot(all("root"),all("file"),all("clip"),all("slate"),all("association"),all("job"),all("preset"),all("audit"));db.execute("COMMIT");return result}
        catch(e:Throwable) {runCatching {db.execute("ROLLBACK")};throw e}
    }
    override fun close()=db.close()
}
