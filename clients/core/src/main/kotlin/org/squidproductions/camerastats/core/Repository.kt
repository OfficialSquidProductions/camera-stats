package org.squidproductions.camerastats.core

import java.io.Closeable

interface SqlDriver:Closeable {
    fun execute(sql:String,args:List<Any?> = emptyList())
    fun query(sql:String,args:List<Any?> = emptyList()):List<List<Any?>>
}
data class Document(val slot:String,val title:String,val revision:Long)
data class Loaded(val slot:String,val report:Report,val fingerprint:String)

/** One database per app, never a shared drive. A failed or stale save leaves the prior snapshot intact. */
class Repository(private val db:SqlDriver):Closeable {
    init {
        try {
            db.query("PRAGMA journal_mode=WAL"); db.execute("PRAGMA synchronous=FULL");db.execute("PRAGMA foreign_keys=ON");db.query("PRAGMA busy_timeout=3000")
            val version=(db.query("PRAGMA user_version").single().single() as Number).toInt()
            require(version<=1) { "Newer database schema; existing bytes preserved." }
            if(version==0) transaction {
                db.execute("CREATE TABLE documents(slot TEXT PRIMARY KEY, title TEXT NOT NULL, revision INTEGER NOT NULL, current BLOB NOT NULL, previous BLOB)")
                db.execute("CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
                db.execute("PRAGMA user_version=1")
            }
            require(db.query("PRAGMA quick_check").single().single()=="ok") { "Local database integrity check failed; existing bytes preserved." }
        } catch(e:Exception) {db.close();throw e}
    }
    private fun <T> transaction(block:()->T):T {
        db.execute("BEGIN IMMEDIATE")
        try {val result=block();db.execute("COMMIT");return result} catch(e:Exception) {try {db.execute("ROLLBACK")} catch(_:Exception) {};throw e}
    }
    private fun validate(slot:String) { require(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(slot)) { "Invalid document slot." } }
    private fun bytes(slot:String,previous:Boolean=false):ByteArray? {
        validate(slot);val row=db.query("SELECT ${if(previous) "previous" else "current"} FROM documents WHERE slot=?",listOf(slot)).firstOrNull() ?: return null
        val bytes=row[0] as? ByteArray ?: return null;require(bytes.size<=Exchange.LIMIT) { "Invalid snapshot size." };return bytes
    }
    @Synchronized fun active():String? = db.query("SELECT value FROM metadata WHERE key='active'").firstOrNull()?.first() as? String
    @Synchronized fun list():List<Document> = db.query("SELECT slot,title,revision FROM documents ORDER BY title,slot").map { Document(it[0] as String,it[1] as String,(it[2] as Number).toLong()) }
    @Synchronized fun load(slot:String,previous:Boolean=false):Loaded {
        val bytes=bytes(slot,previous) ?: throw ReportException("Local snapshot not found.")
        return Loaded(slot,Exchange.read(bytes).report,Exchange.digest(bytes))
    }
    @Synchronized fun select(slot:String):Loaded = transaction {
        val loaded=load(slot);setActive(slot);loaded
    }
    private fun setActive(slot:String) {db.execute("INSERT OR REPLACE INTO metadata(key,value) VALUES('active',?)",listOf(slot))}
    @Synchronized fun save(slot:String,report:Report,device:String,expectedFingerprint:String?):String {
        validate(slot);val payload=Exchange.export(report,device);Exchange.read(payload)
        return transaction {
            val current=bytes(slot)
            if(current!=null) {
                Exchange.read(current)
                require(expectedFingerprint==Exchange.digest(current)) { "This report changed in another app instance. Reopen it before saving; no change was accepted." }
            } else require(expectedFingerprint==null) { "Local document disappeared; no change was accepted." }
            if(current==null) db.execute("INSERT INTO documents(slot,title,revision,current,previous) VALUES(?,?,?,?,NULL)",listOf(slot,report.values["title"] ?: "Untitled",report.revision,payload))
            else db.execute("UPDATE documents SET title=?,revision=?,previous=current,current=? WHERE slot=?",listOf(report.values["title"] ?: "Untitled",report.revision,payload,slot))
            setActive(slot);Exchange.digest(payload)
        }
    }
    override fun close()=db.close()
}
