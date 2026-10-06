package com.ninepointlabs.quill.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OmostrichResponse(
    val ok: Boolean,
    val data: JsonObject? = null,
    val error: String? = null
)

@Serializable
data class OmostrichStatus(
    val locked: Boolean,
    val npub: String? = null,
    val pubkeyHex: String? = null,
    val relays: List<String>? = null
)

@Serializable
data class RelayFailure(
    val url: String,
    val error: String
)

@Serializable
data class SignedEvent(
    val id: String,
    val pubkey: String,
    val created_at: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String
)

@Serializable
data class PublishResult(
    val event: SignedEvent,
    val publishedTo: List<String>,
    val failed: List<RelayFailure> = emptyList()
)

enum class RelayState {
    CONNECTING,
    CONNECTED,
    RECEIVING,
    EOSE,
    ERROR,
    DISCONNECTED
}

enum class OmostrichConnectionState {
    CHECKING,
    CONNECTED_UNLOCKED,
    CONNECTED_LOCKED,
    DISCONNECTED
}
