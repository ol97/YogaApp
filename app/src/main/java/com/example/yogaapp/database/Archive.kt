package com.example.yogaapp.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class Archive(context: Context) : SQLiteOpenHelper(context,"Archive.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase?) {
        if (db != null) {
            db.execSQL("create table" + ArchiveDbSchema.SessionTable.TABLE_NAME + "(" +
                    " _id integer primary key autoincrement, " +
                    ArchiveDbSchema.SessionTable.Cols.NAME + " TEXT, " +
                    ArchiveDbSchema.SessionTable.Cols.DATE + " TEXT, " +
                    ArchiveDbSchema.SessionTable.Cols.TIME + " TEXT, " +
                    ArchiveDbSchema.SessionTable.Cols.DURATION + " INTEGER)" )

            db.execSQL("create table" + ArchiveDbSchema.PosesInSessionTable.TABLE_NAME + "(" +
                    " _id integer primary key autoincrement, " +
                    "FOREIGN KEY(" + ArchiveDbSchema.PosesInSessionTable.Cols.SESSION_ID +
                    ") REFERENCES " + ArchiveDbSchema.SessionTable.TABLE_NAME + "(_id), " +
                    ArchiveDbSchema.PosesInSessionTable.Cols.DURATION + " INTEGER, " +
                    ArchiveDbSchema.PosesInSessionTable.Cols.NUMBER_IN_SEQUENCE + " INTEGER)" )

        }

    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
    }


}