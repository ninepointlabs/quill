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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

    private val _userPubkeyHex = MutableStateFlow<String?>(null)
    val userPubkeyHex: StateFlow<String?> = _userPubkeyHex.asStateFlow()

    private val _feedError = MutableStateFlow<String?>(null)
    val feedError: StateFlow<String?> = _feedError.asStateFlow()

    private val _profiles = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val profiles: StateFlow<Map<String, Profile>> = _profiles.asStateFlow()
    private val queriedProfiles = mutableSetOf<String>()

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private var currentFollowsCreatedAt = -1L
    private val followedPubkeys = mutableSetOf<String>()

    private val _newEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val newEvents: SharedFlow<Unit> = _newEvents.asSharedFlow()

    private fun loadProfilesFromDisk(context: Context) {
        val file = File(context.filesDir, "profiles.json")
        if (file.exists()) {
            try {
                val content = file.readText()
                val map = json.decodeFromString<Map<String, Profile>>(content)
                _profiles.value = map
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveProfilesToDisk(context: Context) {
        scope?.launch(kotlinx.coroutines.Dispatchers.IO) {
            val file = File(context.filesDir, "profiles.json")
            try {
                val content = json.encodeToString(kotlinx.serialization.serializer(), _profiles.value)
                file.writeText(content)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun initDb(context: Context, coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
        omostrichClient = OmostrichClient(scope = coroutineScope)

        val dbDir = File(context.filesDir, "nostrdb")
        if (!dbDir.exists()) {
            dbDir.mkdirs()
        }
        ndbPtr = NostrDb.ndbOpen(dbDir.absolutePath)
        
        loadProfilesFromDisk(context)
        
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
                _userPubkeyHex.value = userPubkeyHex
                fetchFollows(userPubkeyHex)
                subscribeToOwnNotes(userPubkeyHex)
                
                relayClient.events.collect { eventJson ->
                    try {
                        val event = json.decodeFromString<SignedEvent>(eventJson)
                        if (event.kind == 0) {
                            try {
                                val contentJson = json.decodeFromString<kotlinx.serialization.json.JsonObject>(event.content)
                                val displayName = contentJson["display_name"]?.jsonPrimitive?.contentOrNull
                                val name = contentJson["name"]?.jsonPrimitive?.contentOrNull
                                val picture = contentJson["picture"]?.jsonPrimitive?.contentOrNull
                                val nip05 = contentJson["nip05"]?.jsonPrimitive?.contentOrNull
                                
                                val profile = Profile(event.pubkey, displayName, name, picture, nip05)
                                _profiles.update { currentMap ->
                                    currentMap + (event.pubkey to profile)
                                }
                                saveProfilesToDisk(context)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else if (event.kind == 3 && event.pubkey == userPubkeyHex) {
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
                    _newEvents.tryEmit(Unit)
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

    fun subscribeToOwnNotes(userPubkeyHex: String) {
        val filter = buildJsonObject {
            put("kinds", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(1)) })
            put("authors", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(userPubkeyHex)) })
            put("limit", 50)
        }
        relayClient.subscribe("own_notes", filter.toString())
    }

    fun subscribeToFeed(followedPubkeys: List<String>) {
        val filter1 = buildJsonObject {
            put("kinds", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(1)) })
            put("authors", buildJsonArray { 
                followedPubkeys.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
            put("limit", 50)
        }
        val filter0 = buildJsonObject {
            put("kinds", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(0)) })
            put("authors", buildJsonArray { 
                followedPubkeys.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
        }
        relayClient.subscribe("feed", filter1.toString())
        relayClient.subscribe("feed_profiles", filter0.toString())
    }

    fun fetchMissingProfiles(pubkeys: List<String>) {
        val currentProfiles = _profiles.value
        val missing = pubkeys.distinct().filter { 
            !currentProfiles.containsKey(it) && !queriedProfiles.contains(it) 
        }
        if (missing.isEmpty()) return
        
        queriedProfiles.addAll(missing)
        
        missing.chunked(50).forEachIndexed { index, batch ->
            val filter = buildJsonObject {
                put("kinds", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(0)) })
                put("authors", buildJsonArray { 
                    batch.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                })
            }
            relayClient.subscribe("profiles_${System.currentTimeMillis()}_$index", filter.toString())
        }
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

    fun getMyNotes(limit: Int = 50): Flow<String> = flow {
        val userPubkey = _userPubkeyHex.value
        if (ndbPtr != 0L && userPubkey != null) {
            val result = NostrDb.ndbQueryNotesByAuthor(ndbPtr, userPubkey, limit)
            emit(result)
        }
    }

    fun getThread(eventId: String, limit: Int = 50): Flow<String> = flow {
        if (ndbPtr != 0L) {
            emit(NostrDb.ndbQueryThread(ndbPtr, eventId, limit))
        }
    }

    fun getNoteById(eventId: String): Flow<String> = flow {
        if (ndbPtr != 0L) {
            emit(NostrDb.ndbQueryNoteById(ndbPtr, eventId))
        }
    }

    fun subscribeToThread(eventId: String) {
        val filter = buildJsonObject {
            put("kinds", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(1)) })
            put("#e", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(eventId)) })
            put("limit", 50)
        }
        relayClient.subscribe("thread_$eventId", filter.toString())
    }
    
    fun fetchNoteFromRelays(eventId: String) {
        val filter = buildJsonObject {
            put("kinds", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(1)) })
            put("ids", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(eventId)) })
            put("limit", 1)
        }
        relayClient.subscribe("note_$eventId", filter.toString())
    }

    fun getParentAuthor(eventId: String): String? {
        if (ndbPtr == 0L) return null
        val noteJson = NostrDb.ndbQueryNoteById(ndbPtr, eventId)
        if (noteJson == "[]") return null
        try {
            val notes = json.decodeFromString<List<SignedEvent>>(noteJson)
            if (notes.isNotEmpty()) {
                return notes[0].pubkey
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
