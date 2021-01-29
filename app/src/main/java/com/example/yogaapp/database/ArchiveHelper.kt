package com.example.yogaapp.database

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.example.yogaapp.TimestampedPose
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*

//Helper singleton class for handling all common I/O operations on the database.

class ArchiveHelper(context: Context) {
    private val database = Archive(context).writableDatabase

    // Insert a list of poses with their duration into database.
    // Parameter "name" is the name of the recording given by the user.
    // Parameter "listOfPoses" is a list of pose names and their duration.
    // Returns Boolean which indicates whether the operation was successful or not.
    // Used for saving recording in "Recording Mode".
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

            // insert basic information about the training
            val sessionId = database.insertOrThrow(ArchiveDbSchema.SessionTable.TABLE_NAME, null, values)

            // iterate through list of all poses and write them to database one by one.
            for ((i, timestampedPose) in listOfPoses.withIndex()){
                values.clear()
                values.put(ArchiveDbSchema.SessionDetailsTable.Cols.POSE_NAME, timestampedPose.poseName)
                values.put(ArchiveDbSchema.SessionDetailsTable.Cols.DURATION, timestampedPose.timestamp.toString())
                values.put(ArchiveDbSchema.SessionDetailsTable.Cols.NUMBER_IN_SEQUENCE, i.toString())
                values.put(ArchiveDbSchema.SessionDetailsTable.Cols.SESSION_ID, sessionId.toString())

                database.insertOrThrow(ArchiveDbSchema.SessionDetailsTable.TABLE_NAME, null, values)

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

    // Reads basic details of all training sessions saved in database and returns them in a list.
    // Used for populating RecyclerView in "History" screen.
    // If no results are found then empty list is returned.
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

    // Read the sequence of poses in a saved training. Used in detailed view in "History".
    fun readPoseSequence(sessionId: String): List<Array<String>>{
        val list = mutableListOf<Array<String>>()
        try {

            val cursor = database.query(false,
                    ArchiveDbSchema.SessionDetailsTable.TABLE_NAME, null,
                    ArchiveDbSchema.SessionDetailsTable.Cols.SESSION_ID + " = CAST(? AS INTEGER)",
                    arrayOf(sessionId), null, null,
                    "CAST(" + ArchiveDbSchema.SessionDetailsTable.Cols.NUMBER_IN_SEQUENCE + " AS INTEGER)" + " asc", null)
            if (cursor.moveToFirst())
            {
                do
                {
                    val numberInSequence = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionDetailsTable.Cols.NUMBER_IN_SEQUENCE))
                    val poseDuration = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionDetailsTable.Cols.DURATION))
                    val poseName = cursor.getString(cursor.getColumnIndex(ArchiveDbSchema.SessionDetailsTable.Cols.POSE_NAME))

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

    // Renaming a saved training. Used in detailed view in "History".
    // Returns Boolean which indicates whether the operation was successful.
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

    // Delete saved training session from database. Used in detailed view in "History".
    // Returns Boolean which indicates whether the operation was successful.
    fun deleteSession(sessionId: String): Boolean{
        var ok = true
        try{
            database.delete(ArchiveDbSchema.SessionDetailsTable.TABLE_NAME,
           ArchiveDbSchema.SessionDetailsTable.Cols.SESSION_ID + " = CAST(? AS INTEGER)", arrayOf(sessionId))
            database.delete(ArchiveDbSchema.SessionTable.TABLE_NAME,
                    ArchiveDbSchema.SessionTable.Cols.ID + "= CAST(? AS INTEGER)",
                    arrayOf(sessionId))
        }
        catch(e: Exception)
        {
            ok = false
            Log.d("SQL", e.message)
            e.printStackTrace()
        }
        return ok
    }

    // Read all the basic session details like name, date, time, duration.
    // Used in detailed view in "History".
    // Most of the necessary data is passed from list elements, this method is used when
    // data needs to be re-read,
    // for example when the training session is renamed
    fun readBasicSessionDetails(sessionId: String): Array<String> {
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

    // Reads only names of saved trainings and return them as a list. Used to ensure that
    // the user inserts a name that isn't already taken when saving a recording in "Recording Mode".
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

    // Static getInstance method to make this a singleton. This entire class should be a "object"
    // rather than a "class", but object can't have a constructor and this needs a Context to connect
    // to the database. There probably is a better workaround, but I don't know it.
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
