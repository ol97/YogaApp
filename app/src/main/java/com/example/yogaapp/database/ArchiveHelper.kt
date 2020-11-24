package com.example.yogaapp.database

import android.content.ContentValues
import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

class ArchiveHelper(context: Context) {
    private val database = Archive(context).writableDatabase

    public fun insertSession(listOfPoses: List<Pair<String, Int>>){
        val values = ContentValues()
        var totalDuration = 0
        for (pair in listOfPoses){
            totalDuration += pair.second
        }
        val currentDate: String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        values.put(ArchiveDbSchema.SessionTable.Cols.NAME, "YogaSession $currentDate")
        values.put(ArchiveDbSchema.SessionTable.Cols.TIME, currentTime)
        values.put(ArchiveDbSchema.SessionTable.Cols.DATE, currentDate)
        values.put(ArchiveDbSchema.SessionTable.Cols.DURATION, totalDuration)

        database.insert(ArchiveDbSchema.SessionTable.TABLE_NAME, null, values)

        val sessionId = database.rawQuery("Select last_inserted_rowid();", null )

        for ((i, pair) in listOfPoses.withIndex()){
            values.clear()
            values.put(ArchiveDbSchema.PosesInSessionTable.Cols.POSE_NAME, pair.first)
            values.put(ArchiveDbSchema.PosesInSessionTable.Cols.DURATION, pair.second)
            values.put(ArchiveDbSchema.PosesInSessionTable.Cols.NUMBER_IN_SEQUENCE, i)
            values.put(ArchiveDbSchema.PosesInSessionTable.Cols.SESSION_ID, sessionId.getInt(0))

            database.insert(ArchiveDbSchema.PosesInSessionTable.TABLE_NAME, null ,values)
        }
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