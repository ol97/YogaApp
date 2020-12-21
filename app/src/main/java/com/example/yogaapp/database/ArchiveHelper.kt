package com.example.yogaapp.database

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.example.yogaapp.TimestampedPose
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

class ArchiveHelper(context: Context) {
    private val database = Archive(context).writableDatabase

    fun insertSession(listOfPoses: List<TimestampedPose>, name: String): Boolean{
        var ok = true
        try
        {
            val values = ContentValues()
            var totalDuration = 0L
            for (timestampedPose in listOfPoses){
                totalDuration += timestampedPose.timestamp
            }
            val currentDate: String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            values.put(ArchiveDbSchema.SessionTable.Cols.NAME, name)
            values.put(ArchiveDbSchema.SessionTable.Cols.TIME, currentTime)
            values.put(ArchiveDbSchema.SessionTable.Cols.DATE, currentDate)
            values.put(ArchiveDbSchema.SessionTable.Cols.DURATION, totalDuration)

            val sessionId = database.insertOrThrow(ArchiveDbSchema.SessionTable.TABLE_NAME, null, values)

            for ((i, timestampedPose) in listOfPoses.withIndex()){
                values.clear()
                values.put(ArchiveDbSchema.PosesInSessionTable.Cols.POSE_NAME, timestampedPose.poseName)
                values.put(ArchiveDbSchema.PosesInSessionTable.Cols.DURATION, timestampedPose.timestamp.toString())
                values.put(ArchiveDbSchema.PosesInSessionTable.Cols.NUMBER_IN_SEQUENCE, i.toString())
                values.put(ArchiveDbSchema.PosesInSessionTable.Cols.SESSION_ID, sessionId.toString())

                database.insertOrThrow(ArchiveDbSchema.PosesInSessionTable.TABLE_NAME, null, values)

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


    fun readSessions(): List<Array<String>> {
        try
        {
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
        catch (e:Exception)
        {
            Log.d("SQL", e.message)
            e.printStackTrace()
            return mutableListOf()
        }
    }


    fun readDetailedSessionData(sessionId: String): List<Array<String>>{
        val list = mutableListOf<Array<String>>()
        try {

            val cursor = database.query(false,
                    ArchiveDbSchema.PosesInSessionTable.TABLE_NAME, null,
                    ArchiveDbSchema.PosesInSessionTable.Cols.SESSION_ID + " = CAST(? AS INTEGER)",
                    arrayOf(sessionId), null, null,
                    "CAST(" + ArchiveDbSchema.PosesInSessionTable.Cols.NUMBER_IN_SEQUENCE + " AS INTEGER)" + " asc", null)
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

    fun changeSessionName(newName:String, sessionId: String):Boolean{
        var ok = true
        val values = ContentValues()
        values.put(ArchiveDbSchema.SessionTable.Cols.NAME, newName)
        try{
            val a = database.update(ArchiveDbSchema.SessionTable.TABLE_NAME, values,
                    ArchiveDbSchema.SessionTable.Cols.ID+" = CAST(? AS INTEGER)", arrayOf(sessionId))
        }
        catch (e: Exception)
        {
            ok = false
            Log.d("SQL", e.message)
            e.printStackTrace()
        }
        return ok
    }

    fun deleteSession(sessionKey: String): Boolean{
        var ok = true
        try{
            database.delete(ArchiveDbSchema.PosesInSessionTable.TABLE_NAME,
           ArchiveDbSchema.PosesInSessionTable.Cols.SESSION_ID + " = CAST(? AS INTEGER)", arrayOf(sessionKey))
            database.delete(ArchiveDbSchema.SessionTable.TABLE_NAME,
                    ArchiveDbSchema.SessionTable.Cols.ID + "= CAST(? AS INTEGER)",
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

    fun readSessionData(sessionId: String): Array<String> {
        try
        {
            val cursor = database.query(false, ArchiveDbSchema.SessionTable.TABLE_NAME,
                    null,
                    ArchiveDbSchema.SessionTable.Cols.ID + " = CAST(? AS INTEGER)",
                    arrayOf(sessionId), null, null ,
                    ArchiveDbSchema.SessionTable.Cols.ID +" desc", null, null)
            if (cursor.moveToFirst())
            {
                val name = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.NAME))
                val date = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.DATE))
                val time = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.TIME))
                val duration = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.DURATION))
                val id = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.ID))

                cursor.close()
                return arrayOf(id, name, date, time, duration)
            }
            else
            {
                cursor.close()
                return arrayOf()
            }
        }catch(e: Exception)
        {
            Log.d("SQL", e.message)
            e.printStackTrace()
            return arrayOf()
        }
    }

    fun readSessionNames(): MutableList<String> {
        try
        {
            val cursor = database.query(true, ArchiveDbSchema.SessionTable.TABLE_NAME,
                    arrayOf(ArchiveDbSchema.SessionTable.Cols.NAME), null, null,
                    null, null, null, null)

            val list = mutableListOf<String>()
            if (cursor.moveToFirst())
            {
                do
                {
                    val name = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionTable.Cols.NAME))
                    list.add(name)
                }
                while (cursor.moveToNext())
            }
            cursor.close()
            return list

        }catch(e: Exception)
        {
            Log.d("SQL", e.message)
            e.printStackTrace()
            return mutableListOf()
        }

    }

    companion object{
        private var instance: ArchiveHelper? = null
        fun getInstance(context: Context): ArchiveHelper? {
            return if (instance == null){
                instance = ArchiveHelper(context)
                instance
            } else {
                instance
            }
        }
    }
}