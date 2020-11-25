package com.example.yogaapp.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.util.Log
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

class ArchiveHelper(context: Context) {
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
                values.put(ArchiveDbSchema.PosesInSessionTable.Cols.DURATION, pair.second)
                values.put(ArchiveDbSchema.PosesInSessionTable.Cols.NUMBER_IN_SEQUENCE, i)
                values.put(ArchiveDbSchema.PosesInSessionTable.Cols.SESSION_ID, sessionId)

                database.insert(ArchiveDbSchema.PosesInSessionTable.TABLE_NAME, null ,values)
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
        val cursor = database.rawQuery("Select * from " +
                ArchiveDbSchema.SessionTable.TABLE_NAME, null)
        val list = mutableListOf<Array<String>>()
        if (cursor.moveToFirst())
        {
            do
            {
                val name = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.NAME))
                val date = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.DATE))
                val time = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.TIME))
                val duration = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.DURATION))
                val rowid = cursor.getString(cursor.getColumnIndex("rowid"))

                list.add(arrayOf(rowid, name, date, time, duration))
            }
            while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }


    public fun readDetailedSessionData(sessionKey: String): List<Array<String>>{
        val cursor = database.rawQuery("Select * from " +
                ArchiveDbSchema.PosesInSessionTable.TABLE_NAME + ", " +
                ArchiveDbSchema.SessionTable.TABLE_NAME +
                " where rowid =  ?", arrayOf(sessionKey))

        val list = mutableListOf<Array<String>>()
        if (cursor.moveToFirst())
        {
            do
            {
                val sessionName = cursor.getString(
                        cursor.getColumnIndex(ArchiveDbSchema.SessionTable.TABLE_NAME +
                                "." + ArchiveDbSchema.SessionTable.Cols.NAME))
                val date = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.DATE))
                val sessionTime = cursor.getString(cursor.getColumnIndex(
                        ArchiveDbSchema.SessionTable.TABLE_NAME + "."
                                +ArchiveDbSchema.SessionTable.Cols.TIME))
                val sessionDuration = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.DURATION))
                val rowid = cursor.getString(cursor.getColumnIndex("rowid"))
                val numberInSequence = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.PosesInSessionTable.Cols.NUMBER_IN_SEQUENCE))
                val poseDuration = cursor.getString(cursor.getColumnIndex(
                        ArchiveDbSchema.PosesInSessionTable.TABLE_NAME + "," +
                                ArchiveDbSchema.PosesInSessionTable.Cols.DURATION))
                val poseName = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.PosesInSessionTable.Cols.POSE_NAME))

                list.add(arrayOf(rowid, sessionName, date, sessionTime, sessionDuration, poseName, numberInSequence, poseDuration))
            }
            while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }

    public fun changeSessionName(newName:String, rowid: String):Boolean{
        var ok: Boolean = true
        val values = ContentValues()
        values.put(ArchiveDbSchema.SessionTable.Cols.NAME, newName)
        try{
            database.update(ArchiveDbSchema.SessionTable.TABLE_NAME, values, "rowid = ?", arrayOf(rowid))
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