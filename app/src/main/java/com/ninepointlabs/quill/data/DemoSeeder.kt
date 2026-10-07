package com.ninepointlabs.quill.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import java.io.File
import com.ninepointlabs.quill.network.OmostrichConnectionState
import com.ninepointlabs.quill.network.Profile
import kotlinx.coroutines.launch
import com.ninepointlabs.quill.network.OmostrichClient
import com.ninepointlabs.quill.network.RelayClient
import okhttp3.OkHttpClient

fun DataRepository.initDbDemo(context: Context, coroutineScope: CoroutineScope) {
    // 1. Initialize the db
    val dbDir = File(context.filesDir, "nostrdb")
    if (!dbDir.exists()) {
        dbDir.mkdirs()
    }
    ndbPtr = NostrDb.ndbOpen(dbDir.absolutePath)
    
    // Create relayClient and omostrichClient so they are not uninitialized
    omostrichClient = OmostrichClient(scope = coroutineScope)
    relayClient = RelayClient(OkHttpClient(), ndbPtr, coroutineScope)
    
    // 2. Set connection state and user
    _connectionState.value = OmostrichConnectionState.CONNECTED_UNLOCKED
    val demoUserHex = "1111111111111111111111111111111111111111111111111111111111111111"
    _userPubkeyHex.value = demoUserHex

    // 3. Seed data
    seedDemoData()
    
    // 4. Force feed fetch
    fetchFollows(demoUserHex)
    subscribeToOwnNotes(demoUserHex)
    
    // Pretend follows arrived to show feed
    val demoFollows = listOf(
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
        "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
        "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    )
    subscribeToFeed(demoFollows)
    
    _newEvents.tryEmit(Unit)
}

fun DataRepository.seedDemoData() {
    val pubkeys = listOf(
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc",
        "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd",
        "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    )
    val names = listOf("Alice", "Bob", "Carol", "Dave", "Erin")
    val usernames = listOf("alice", "bob", "carol", "dave", "erin")
    
    val profilesMap = mutableMapOf<String, Profile>()
    
    val currentTime = (System.currentTimeMillis() / 1000).toInt()
    
    // Seed Profiles
    for (i in 0 until 5) {
        val pk = pubkeys[i]
        val display = names[i]
        val name = usernames[i]
        val pic = "https://i.pravatar.cc/150?img=${i + 1}"
        val nip05 = "$name@nostr.pro"
        
        val content = """{"display_name":"$display","name":"$name","picture":"$pic","nip05":"$nip05"}"""
        val eventJson = """
            {
                "id": "${"0".repeat(63)}$i",
                "pubkey": "$pk",
                "created_at": $currentTime,
                "kind": 0,
                "tags": [],
                "content": ${content.let { kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer(), it) }},
                "sig": "${"f".repeat(64)}"
            }
        """.trimIndent()
        
        NostrDb.ndbIngestEvent(ndbPtr, eventJson)
        profilesMap[pk] = Profile(pk, display, name, pic, nip05)
    }
    
    // Demo User Profile
    val demoUserHex = "1111111111111111111111111111111111111111111111111111111111111111"
    val demoContent = """{"display_name":"You","name":"demo","picture":"https://i.pravatar.cc/150?img=10"}"""
    val demoProfileJson = """
        {
            "id": "1111111111111111111111111111111111111111111111111111111111111110",
            "pubkey": "$demoUserHex",
            "created_at": $currentTime,
            "kind": 0,
            "tags": [],
            "content": ${demoContent.let { kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer(), it) }},
            "sig": "${"f".repeat(64)}"
        }
    """.trimIndent()
    NostrDb.ndbIngestEvent(ndbPtr, demoProfileJson)
    profilesMap[demoUserHex] = Profile(demoUserHex, "You", "demo", "https://i.pravatar.cc/150?img=10", null)
    
    _profiles.value = profilesMap
    
    // Seed Notes
    // 2-3 plain text notes
    val notes = mutableListOf<String>()
    
    notes.add(makeNote("2".repeat(64), pubkeys[0], currentTime - 100, "Just setting up my nostr client! 🚀"))
    notes.add(makeNote("3".repeat(64), pubkeys[1], currentTime - 200, "Beautiful day today. Spending it coding!"))
    
    // 1 note with a URL
    notes.add(makeNote("4".repeat(64), pubkeys[2], currentTime - 300, "Has anyone checked out Nostr lately? https://github.com/nostr-protocol/nostr"))
    
    // 1 note with an image URL
    notes.add(makeNote("5".repeat(64), pubkeys[3], currentTime - 400, "Check out this beautiful scenery: https://picsum.photos/600/400"))
    
    // 1 note with a nostr:npub1... mention
    val npubBob = "nostr:npub1..." // We can just put a dummy npub or encode Bob's hex to npub. 
    // Actually, just nostr:npub1... is fine for the regex probably. Let's make it a valid length npub or just text. 
    notes.add(makeNote("6".repeat(64), pubkeys[4], currentTime - 500, "Hey nostr:npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6 check this out!"))
    
    // 3 replies forming a thread
    val threadRootId = "7".repeat(64)
    notes.add(makeNote(threadRootId, pubkeys[0], currentTime - 600, "What are everyone's favorite Android features?"))
    notes.add(makeReply("8".repeat(64), pubkeys[1], currentTime - 550, "Definitely Jetpack Compose!", threadRootId, pubkeys[0]))
    notes.add(makeReply("9".repeat(64), pubkeys[2], currentTime - 500, "I agree, Compose makes UI so much better.", threadRootId, pubkeys[0]))
    
    // 3 notes authored by demo user
    notes.add(makeNote("a".repeat(64), demoUserHex, currentTime - 700, "Hello world, this is my first post!"))
    notes.add(makeNote("b".repeat(64), demoUserHex, currentTime - 800, "Writing notes from Quill is awesome."))
    notes.add(makeNote("c".repeat(64), demoUserHex, currentTime - 900, "Having a great time testing out the new features."))
    
    for (noteJson in notes) {
        NostrDb.ndbIngestEvent(ndbPtr, noteJson)
    }
}

private fun makeNote(id: String, pubkey: String, time: Int, content: String): String {
    val escapedContent = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer(), content)
    return """
        {
            "id": "$id",
            "pubkey": "$pubkey",
            "created_at": $time,
            "kind": 1,
            "tags": [],
            "content": $escapedContent,
            "sig": "${"f".repeat(64)}"
        }
    """.trimIndent()
}

private fun makeReply(id: String, pubkey: String, time: Int, content: String, replyToId: String, replyToPubkey: String): String {
    val escapedContent = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.serializer(), content)
    return """
        {
            "id": "$id",
            "pubkey": "$pubkey",
            "created_at": $time,
            "kind": 1,
            "tags": [
                ["e", "$replyToId", "", "reply"],
                ["p", "$replyToPubkey"]
            ],
            "content": $escapedContent,
            "sig": "${"f".repeat(64)}"
        }
    """.trimIndent()
}
