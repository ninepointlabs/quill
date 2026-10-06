package com.ninepointlabs.quill.data

import android.content.Context
import com.ninepointlabs.quill.network.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    private val _connectionState = MutableStateFlow(OmostrichConnectionState.CHECKING)
    val connectionState: StateFlow<OmostrichConnectionState> = _connectionState.asStateFlow()

    private val _feedError = MutableStateFlow<String?>(null)
    val feedError: StateFlow<String?> = _feedError.asStateFlow()

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private var currentFollowsCreatedAt = -1L
    private val followedPubkeys = mutableSetOf<String>()

    fun initDb(context: Context, coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
        omostrichClient = OmostrichClient(scope = coroutineScope)

        val dbDir = File(context.filesDir, "nostrdb")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        ndbPtr = NostrDb.ndbOpen(dbDir.absolutePath)
        relayClient = RelayClient(httpClient, ndbPtr, scope!!)

        scope!!.launch {
            val status = omostrichClient.status()
            if (status == null) {
                _connectionState.value = OmostrichConnectionState.DISCONNECTED
                _feedError.value = "Cannot reach signer. Is Tailscale connected?"
                // Add some fallback relays to allow reading cache if possible
                relayClient.addRelay("wss://relay.damus.io")
                relayClient.addRelay("wss://relay.primal.net")
                return@launch
            }

            if (status.locked) {
                _connectionState.value = OmostrichConnectionState.CONNECTED_LOCKED
                _feedError.value = "Signer is locked. Unlock Omostrich on your desktop."
            } else {
                _connectionState.value = OmostrichConnectionState.CONNECTED_UNLOCKED
            }

            val relays = status.relays ?: listOf("wss://relay.damus.io", "wss://relay.primal.net", "wss://nos.lol")
            relays.forEach { relayClient.addRelay(it) }

            status.pubkeyHex?.let { userPubkeyHex ->
                fetchFollows(userPubkeyHex)
                
                relayClient.events.collect { eventJson ->
                    try {
                        val event = json.decodeFromString<SignedEvent>(eventJson)
                        if (event.kind == 3 && event.pubkey == userPubkeyHex) {
                            if (event.created_at > currentFollowsCreatedAt) {
                                currentFollowsCreatedAt = event.created_at
                                val pTags = event.tags.filter { it.isNotEmpty() && it[0] == "p" }.mapNotNull { it.getOrNull(1) }
                                
                                followedPubkeys.clear()
                                followedPubkeys.addAll(pTags)

                                if (followedPubkeys.isEmpty()) {
                                    _feedError.value = "No follows found. Follow some people on Nostr first."
                                } else {
                                    if (!status.locked) {
                                        _feedError.value = null
                                    }
                                    subscribeToFeed(followedPubkeys.toList())
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
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

    fun fetchFollows(pubkeyHex: String) {
        val filter = buildJsonObject {
            put("kinds", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(3)) })
            put("authors", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(pubkeyHex)) })
            put("limit", 1)
        }
        relayClient.subscribe("follows", filter.toString())
    }

    fun subscribeToFeed(followedPubkeys: List<String>) {
        val filter = buildJsonObject {
            put("kinds", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(1)) })
            put("authors", buildJsonArray { 
                followedPubkeys.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
            put("limit", 50)
        }
        relayClient.subscribe("feed", filter.toString())
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
