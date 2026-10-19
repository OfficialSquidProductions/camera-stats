package org.squidproductions.camerastats.android

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.squidproductions.camerastats.core.SqlDriver
import java.io.File

class AndroidDriver(file:File):SqlDriver {
    private val database:SQLiteDatabase
    init {file.parentFile?.mkdirs();database=SQLiteDatabase.openOrCreateDatabase(file,null)}
    override fun execute(sql:String,args:List<Any?>) {if(args.isEmpty()) database.execSQL(sql) else database.execSQL(sql,args.toTypedArray())}
    override fun query(sql:String,args:List<Any?>):List<List<Any?>> = database.rawQuery(sql,args.map {it?.toString()}.toTypedArray()).use {cursor -> buildList {while(cursor.moveToNext()) add((0 until cursor.columnCount).map {index -> when(cursor.getType(index)) {Cursor.FIELD_TYPE_NULL ->null;Cursor.FIELD_TYPE_INTEGER ->cursor.getLong(index);Cursor.FIELD_TYPE_FLOAT ->cursor.getDouble(index);Cursor.FIELD_TYPE_BLOB ->cursor.getBlob(index);else ->cursor.getString(index)}})}}
    override fun close()=database.close()
}
