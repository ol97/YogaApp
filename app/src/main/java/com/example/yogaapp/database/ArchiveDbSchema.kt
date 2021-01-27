package com.example.yogaapp.database

// Helper class containing names of all tables and their columns. Basically a database schema.

class ArchiveDbSchema {
    class SessionTable {
        companion object{
            const val TABLE_NAME = "sessions"
        }

        class Cols {
            companion object{
                const val ID: String = "id"
                const val DATE: String = "date"
                const val DURATION: String = "duration"
                const val TIME: String = "time"
                const val NAME: String = "name"
            }
        }
    }

    class SessionDetailsTable {
        companion object{
            const val TABLE_NAME = "sessiondetails"
        }

        class Cols {
            companion object{
                const val POSE_NAME: String = "posename"
                const val SESSION_ID: String = "sessionid"
                const val DURATION: String = "duration"
                const val NUMBER_IN_SEQUENCE: String = "numberinsequence"
            }
        }
    }
}