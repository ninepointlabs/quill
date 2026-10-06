package com.ninepointlabs.quill.data

import android.content.Context
import com.ninepointlabs.quill.network.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import java.io.File
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object DataRepository {
    private var ndbPtr: Long = 0
    private val httpClient = OkHttpClient()
    
    private var scope: CoroutineScope? = null
    lateinit var omostrichClient: OmostrichClient
        private set
    lateinit var relayClient: RelayClient
        private set

    fun initDb(context: Context, coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
        omostrichClient = OmostrichClient(scope = coroutineScope)

        val dbDir = File(context.filesDir, "nostrdb")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        ndbPtr = NostrDb.ndbOpen(dbDir.absolutePath)
        relayClient = RelayClient(httpClient, ndbPtr, scope!!)
    }

    fun closeDb() {
        if (this::relayClient.isInitialized) {
            relayClient.disconnectAll()
        }
        if (ndbPtr != 0L) {
            NostrDb.ndbClose(ndbPtr)
            ndbPtr = 0
        }
    }

    fun subscribeToFollowFeed(npub: String) {
        val filter = buildJsonObject {
            put("kinds", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(1)) })
            put("authors", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(npub)) })
            put("limit", 50)
        }
        relayClient.subscribe("follow_feed", filter.toString())
    }

    suspend fun publishNote(content: String): PublishResult? {
        return omostrichClient.publish(content)
    }

    fun getFeed(limit: Int = 50): Flow<String> = flow {
        if (ndbPtr != 0L) {
            val result = NostrDb.ndbQueryNotes(ndbPtr, limit)
            emit(result)
        }
    }
}
