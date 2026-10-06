package com.ninepointlabs.quill.network

import com.ninepointlabs.quill.data.NostrDb
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import okhttp3.*
import kotlin.math.pow

class RelayConnection(
    val url: String,
    private val client: OkHttpClient,
    private val ndbPtr: Long,
    private val scope: CoroutineScope
) {
    private var webSocket: WebSocket? = null
    private val _state = MutableStateFlow(RelayState.DISCONNECTED)
    val state: StateFlow<RelayState> = _state.asStateFlow()

    private var reconnectAttempt = 0
    private var isClosed = false
    
    // active subscriptions to resubscribe on reconnect
    private val activeSubscriptions = mutableMapOf<String, String>()

    fun connect() {
        if (isClosed) return
        _state.value = RelayState.CONNECTING
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                _state.value = RelayState.CONNECTED
                activeSubscriptions.forEach { (subId, filters) ->
                    val req = """["REQ","$subId",$filters]"""
                    webSocket.send(req)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val array = Json.parseToJsonElement(text).jsonArray
                    if (array.isEmpty()) return
                    val type = array[0].jsonPrimitive.content
                    
                    if (array.size > 1) {
                        val subId = array[1].jsonPrimitive.content
                        when (type) {
                            "EVENT" -> {
                                _state.value = RelayState.RECEIVING
                                if (array.size > 2) {
                                    val eventJson = array[2].jsonObject.toString()
                                    NostrDb.ndbIngestEvent(ndbPtr, eventJson)
                                }
                            }
                            "EOSE" -> {
                                _state.value = RelayState.EOSE
                            }
                            "NOTICE" -> {
                                // handle notice
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = RelayState.ERROR
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = RelayState.DISCONNECTED
                scheduleReconnect()
            }
        })
    }

    fun subscribe(subId: String, filtersJson: String) {
        activeSubscriptions[subId] = filtersJson
        if (_state.value == RelayState.CONNECTED) {
            val req = """["REQ","$subId",$filtersJson]"""
            webSocket?.send(req)
        }
    }

    fun unsubscribe(subId: String) {
        activeSubscriptions.remove(subId)
        if (_state.value == RelayState.CONNECTED) {
            val req = """["CLOSE","$subId"]"""
            webSocket?.send(req)
        }
    }

    private fun scheduleReconnect() {
        if (isClosed) return
        val delayMs = (2.0.pow(reconnectAttempt).toLong() * 1000).coerceAtMost(30000)
        reconnectAttempt++
        scope.launch {
            delay(delayMs)
            connect()
        }
    }

    fun disconnect() {
        isClosed = true
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        _state.value = RelayState.DISCONNECTED
    }
}

class RelayClient(
    private val client: OkHttpClient,
    private val ndbPtr: Long,
    private val scope: CoroutineScope
) {
    private val connections = mutableMapOf<String, RelayConnection>()

    fun addRelay(url: String) {
        if (!connections.containsKey(url)) {
            val conn = RelayConnection(url, client, ndbPtr, scope)
            connections[url] = conn
            conn.connect()
        }
    }

    fun removeRelay(url: String) {
        connections.remove(url)?.disconnect()
    }

    fun subscribe(subId: String, filtersJson: String) {
        connections.values.forEach { it.subscribe(subId, filtersJson) }
    }

    fun unsubscribe(subId: String) {
        connections.values.forEach { it.unsubscribe(subId) }
    }
    
    fun getState(url: String): StateFlow<RelayState>? {
        return connections[url]?.state
    }
    
    fun disconnectAll() {
        connections.values.forEach { it.disconnect() }
        connections.clear()
    }
}
