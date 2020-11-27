package com.example.yogaapp.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

public class ArchiveHelper(context: Context) {
    private val database = Archive(context).writableDatabase

    public fun insertSession(listOfPoses: List<Pair<String, Long>>, name: String): Boolean{
        var ok = true
        try
        {
            val values = ContentValues()
            var totalDuration = 0L
            for (pair in listOfPoses){
                totalDuration += pair.second
            }
            val currentDate: String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            values.put(ArchiveDbSchema.SessionTable.Cols.NAME, name)
            values.put(ArchiveDbSchema.SessionTable.Cols.TIME, currentTime)
            values.put(ArchiveDbSchema.SessionTable.Cols.DATE, currentDate)
            values.put(ArchiveDbSchema.SessionTable.Cols.DURATION, totalDuration)

            val sessionId = database.insert(ArchiveDbSchema.SessionTable.TABLE_NAME, null, values)

            for ((i, pair) in listOfPoses.withIndex()){
                values.clear()
                values.put(ArchiveDbSchema.PosesInSessionTable.Cols.POSE_NAME, pair.first)
                values.put(ArchiveDbSchema.PosesInSessionTable.Cols.DURATION, pair.second.toString())
                values.put(ArchiveDbSchema.PosesInSessionTable.Cols.NUMBER_IN_SEQUENCE, i.toString())
                values.put(ArchiveDbSchema.PosesInSessionTable.Cols.SESSION_ID, sessionId.toString())

                val rowid = database.insertOrThrow(ArchiveDbSchema.PosesInSessionTable.TABLE_NAME, null, values)

            }
        }
        catch(e: Exception)
        {
            e.printStackTrace()
            Log.d("SQL", e.message)
            ok = false
        }
        return ok
    }


    public fun readBasicSessionData(): List<Array<String>> {
        val cursor = database.query(false, ArchiveDbSchema.SessionTable.TABLE_NAME,
            null, null, null, null, null ,
            ArchiveDbSchema.SessionTable.Cols.ID +" desc", null, null)
        val list = mutableListOf<Array<String>>()
        if (cursor.moveToFirst())
        {
            do
            {
                val name = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.NAME))
                val date = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.DATE))
                val time = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.TIME))
                val duration = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.DURATION))
                val id = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.ID))

                list.add(arrayOf(id, name, date, time, duration))
            }
            while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }


    public fun readDetailedSessionData(sessionId: String): List<Array<String>>{
        val list = mutableListOf<Array<String>>()
        try {

            val distinct = false
            val table = ArchiveDbSchema.PosesInSessionTable.TABLE_NAME
            val columns = null
            val selection = ArchiveDbSchema.PosesInSessionTable.Cols.SESSION_ID + " = CAST(? AS INTEGER)"
            val selectionArgs = arrayOf(sessionId)
            val groupBy = null
            val having = null
            val orderBy = "CAST(" + ArchiveDbSchema.PosesInSessionTable.Cols.NUMBER_IN_SEQUENCE + " AS INTEGER)" + " asc"
            val limit = null

            val cursor = database.query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit)
            if (cursor.moveToFirst())
            {
                do
                {
                    val numberInSequence = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.PosesInSessionTable.Cols.NUMBER_IN_SEQUENCE))
                    val poseDuration = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.PosesInSessionTable.Cols.DURATION))
                    val poseName = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.PosesInSessionTable.Cols.POSE_NAME))

                    list.add(arrayOf(numberInSequence, poseName, poseDuration))
                }
                while (cursor.moveToNext())
            }
            cursor.close()
        }
        catch (e:Exception){
            e.printStackTrace()
            Log.d("SQL", e.message)
        }

        return list
    }

    public fun changeSessionName(newName:String, sessionId: String):Boolean{
        var ok: Boolean = true
        val values = ContentValues()
        values.put(ArchiveDbSchema.SessionTable.Cols.NAME, newName)
        try{
            database.update(ArchiveDbSchema.SessionTable.TABLE_NAME, values, ArchiveDbSchema.SessionTable.Cols.NAME+" = ?", arrayOf(sessionId))
        }
        catch (e: Exception)
        {
            ok = false
            Log.d("SQL", e.message)
            e.printStackTrace()
        }
        return ok
    }

    public fun deleteSession(sessionKey: String): Boolean{
        var ok = true
        try{
            database.delete(ArchiveDbSchema.PosesInSessionTable.TABLE_NAME,
           ArchiveDbSchema.PosesInSessionTable.Cols.SESSION_ID + " = ?", arrayOf(sessionKey))
            database.delete(ArchiveDbSchema.SessionTable.TABLE_NAME, "rowid = ?",
            arrayOf(sessionKey))
        }
        catch(e: Exception)
        {
            ok = false
            Log.d("SQL", e.message)
            e.printStackTrace()
        }
        return ok
    }

    companion object{
        private var instance: ArchiveHelper? = null
        public fun getInstance(context: Context): ArchiveHelper? {
            if (instance == null){
                instance = ArchiveHelper(context)
                return instance
            }
            else {return instance}
        }
    }
}