package com.ninepointlabs.quill.data

object NostrDb {
    init {
        System.loadLibrary("nostrdb_bridge")
    }

    external fun ndbOpen(path: String): Long
    external fun ndbClose(ptr: Long)
    external fun ndbIngestEvent(ptr: Long, eventJson: String): Boolean
    external fun ndbQueryNotes(ptr: Long, limit: Int): String
    external fun ndbQueryNotesByAuthor(ptr: Long, authorHex: String, limit: Int): String
    external fun ndbCountEvents(ptr: Long): Long
    external fun ndbQueryThread(ptr: Long, eventIdHex: String, limit: Int): String
    external fun ndbQueryNoteById(ptr: Long, eventIdHex: String): String
}
