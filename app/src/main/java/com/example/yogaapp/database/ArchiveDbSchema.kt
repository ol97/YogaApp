package com.example.yogaapp.database

class ArchiveDbSchema {
    public class SessionTable {
        companion object{
            public val TABLE_NAME = "sessions"
        }

        public class Cols {
            companion object{
                public val DATE: String = "date"
                public val DURATION: String = "duration"
                public val TIME: String = "time"
                public val NAME: String = "name"
            }
        }
    }

    public class PosesInSessionTable {
        companion object{
            public val TABLE_NAME = "poseinsession"
        }

        public class Cols {
            companion object{
                public val POSE_NAME: String = "posename"
                public val SESSION_ID: String = "sessionid"
                public val DURATION: String = "duration"
                public val NUMBER_IN_SEQUENCE: String = "numberinsequence"
            }
        }
    }
}