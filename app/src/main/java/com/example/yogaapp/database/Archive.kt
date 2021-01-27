package com.example.yogaapp.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import java.lang.Exception

// Main class in which database is defined, created and updated.
// Database schema is defined in ArchiveDbSchema
// All I/O methods are defined in ArchiveHelper

class Archive(context: Context) : SQLiteOpenHelper(context,"Archive.db", null, 2) {


    // creates database
    override fun onCreate(db: SQLiteDatabase?) {
        if (db != null) {
            try{
                db.execSQL("create table " + ArchiveDbSchema.SessionTable.TABLE_NAME + "(" +
                        "id integer primary key autoincrement, " +
                        ArchiveDbSchema.SessionTable.Cols.NAME + " unique , " +
                        ArchiveDbSchema.SessionTable.Cols.DATE + ", " +
                        ArchiveDbSchema.SessionTable.Cols.TIME + ", " +
                        ArchiveDbSchema.SessionTable.Cols.DURATION + ")" )

                db.execSQL("create table " + ArchiveDbSchema.SessionDetailsTable.TABLE_NAME +
                        " ( id integer primary key autoincrement, " +
                        ArchiveDbSchema.SessionDetailsTable.Cols.DURATION + ", " +
                        ArchiveDbSchema.SessionDetailsTable.Cols.POSE_NAME + ", " +
                        ArchiveDbSchema.SessionDetailsTable.Cols.NUMBER_IN_SEQUENCE + ", " +
                        ArchiveDbSchema.SessionDetailsTable.Cols.SESSION_ID + ", " +
                        "FOREIGN KEY (" + ArchiveDbSchema.SessionDetailsTable.Cols.SESSION_ID +
                        ") REFERENCES " + ArchiveDbSchema.SessionTable.TABLE_NAME + " (id))" )
            }
            catch(e:Exception)
            {
                Log.d("SQL_creation", e.message)
                e.printStackTrace()
            }

        }

    }

    // updates database if old and new versions are different.
    // Of course "Drop table" is only good for testing, not real life application.
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS " + ArchiveDbSchema.SessionTable.TABLE_NAME)
        db?.execSQL("DROP TABLE IF EXISTS " + ArchiveDbSchema.SessionDetailsTable.TABLE_NAME)

        onCreate(db)
    }


}