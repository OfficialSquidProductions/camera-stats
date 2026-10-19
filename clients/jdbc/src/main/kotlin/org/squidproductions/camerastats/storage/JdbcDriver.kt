package org.squidproductions.camerastats.storage

import org.squidproductions.camerastats.core.SqlDriver
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.nio.file.Path
import java.nio.file.Files

class JdbcDriver(path:Path):SqlDriver {
    private val connection:Connection
    init {Files.createDirectories(path.toAbsolutePath().parent);Class.forName("org.sqlite.JDBC");connection=DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}")}
    private fun statement(sql:String,args:List<Any?>):PreparedStatement = connection.prepareStatement(sql).also { s -> args.forEachIndexed { i,v -> if(v is ByteArray) s.setBytes(i+1,v) else s.setObject(i+1,v) } }
    override fun execute(sql:String,args:List<Any?>) {statement(sql,args).use { it.execute() }}
    override fun query(sql:String,args:List<Any?>):List<List<Any?>> = statement(sql,args).use { s -> s.executeQuery().use { r -> buildList {while(r.next()) add((1..r.metaData.columnCount).map { r.getObject(it) }) } } }
    override fun close()=connection.close()
}
