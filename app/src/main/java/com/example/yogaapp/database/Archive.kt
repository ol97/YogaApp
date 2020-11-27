package com.example.yogaapp.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.lang.Exception

class Archive(context: Context) : SQLiteOpenHelper(context,"Archive.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase?) {
        if (db != null) {
            try{
                db.execSQL("create table " + ArchiveDbSchema.SessionTable.TABLE_NAME + "(" +
                        "id integer primary key autoincrement, " +
                        ArchiveDbSchema.SessionTable.Cols.NAME + ", " +
                        ArchiveDbSchema.SessionTable.Cols.DATE + ", " +
                        ArchiveDbSchema.SessionTable.Cols.TIME + ", " +
                        ArchiveDbSchema.SessionTable.Cols.DURATION + ")" )

                db.execSQL("create table " + ArchiveDbSchema.PosesInSessionTable.TABLE_NAME + " (" +
                        " id integer primary key autoincrement, " +
                        ArchiveDbSchema.PosesInSessionTable.Cols.DURATION + ", " +
                        ArchiveDbSchema.PosesInSessionTable.Cols.POSE_NAME + ", " +
                        ArchiveDbSchema.PosesInSessionTable.Cols.NUMBER_IN_SEQUENCE + ", " +
                        ArchiveDbSchema.PosesInSessionTable.Cols.SESSION_ID + ", " +
                        "FOREIGN KEY (" + ArchiveDbSchema.PosesInSessionTable.Cols.SESSION_ID +
                        ") REFERENCES " + ArchiveDbSchema.SessionTable.TABLE_NAME + " (id))" )
            }
            catch(e:Exception)
            {
                Log.d("SQL_creation", e.message)
                e.printStackTrace()
            }

        }

    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS " + ArchiveDbSchema.SessionTable.TABLE_NAME)
        db?.execSQL("DROP TABLE IF EXISTS " + ArchiveDbSchema.PosesInSessionTable.TABLE_NAME)

        onCreate(db)
    }


}