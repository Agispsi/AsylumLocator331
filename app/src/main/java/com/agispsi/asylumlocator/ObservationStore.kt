package com.agispsi.asylumlocator

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class ObservationStore(private val context: Context) : SQLiteOpenHelper(context, "observations.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE observations (id TEXT PRIMARY KEY, name TEXT, alliance TEXT, level INTEGER, server INTEGER, x INTEGER, y INTEGER, seen INTEGER, detail TEXT, tag TEXT, verified INTEGER DEFAULT 0)") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { }
    fun save(p: PlayerIdentity, c: Coordinates, detail: Bitmap, tag: Bitmap) {
        val id = LocatorLogic.observationKey(p,c)
        val existing = all().firstOrNull { it.getString("id") == id }
        val folder = File(context.filesDir, "evidence").apply { mkdirs() }
        val token = UUID.randomUUID().toString()
        val detailFile = File(folder, "$token-detail.png"); val tagFile = File(folder, "$token-tag.png")
        try {
            detailFile.outputStream().use { check(detail.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            tagFile.outputStream().use { check(tag.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val values = ContentValues().apply {
                put("id",id); put("name",p.name); put("alliance",p.alliance ?: ""); put("level",p.level)
                put("server",c.server); put("x",c.x); put("y",c.y); put("seen",System.currentTimeMillis())
                put("detail",detailFile.name); put("tag",tagFile.name); put("verified",0)
            }
            check(writableDatabase.insertWithOnConflict("observations",null,values,SQLiteDatabase.CONFLICT_REPLACE) != -1L)
        } catch (e: Exception) { detailFile.delete(); tagFile.delete(); throw e }
        existing?.let { File(folder,it.getString("detail")).delete(); File(folder,it.getString("tag")).delete() }
    }
    fun all(): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        readableDatabase.rawQuery("SELECT * FROM observations ORDER BY seen DESC",null).use { cursor ->
            while(cursor.moveToNext()) {
                val j = JSONObject()
                cursor.columnNames.forEachIndexed { i,key -> if (key in listOf("level","server","x","y","seen","verified")) j.put(key,cursor.getLong(i)) else j.put(key,cursor.getString(i)) }
                out += j
            }
        }
        return out
    }
    fun confirm(id: String) { writableDatabase.execSQL("UPDATE observations SET verified=1 WHERE id=?",arrayOf(id)) }
    fun export() = JSONObject().put("scope","Observed Sanctuaries only; incomplete server coverage. Coordinates are snapshots at seen timestamps.").put("observations",JSONArray(all())).toString(2)
    fun clear() { writableDatabase.delete("observations",null,null); File(context.filesDir,"evidence").deleteRecursively() }
}
