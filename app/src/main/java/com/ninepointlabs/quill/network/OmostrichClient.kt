package com.ninepointlabs.quill.network

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class OmostrichClient(
    private val host: String = "100.118.152.0",
    private val port: Int = 4737,
    private val scope: CoroutineScope
) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun connect() = withContext(Dispatchers.IO) {
        if (socket?.isConnected == true && socket?.isClosed == false) return@withContext
        try {
            socket = Socket(host, port)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream()))
        } catch (e: Exception) {
            e.printStackTrace()
            socket = null
        }
    }

    private suspend fun sendCommand(cmd: JsonObject): OmostrichResponse = withContext(Dispatchers.IO) {
        mutex.withLock {
            var retries = 0
            while (retries < 2) {
                try {
                    connect()
                    if (socket == null || writer == null || reader == null) {
                        return@withContext OmostrichResponse(ok = false, error = "Not connected")
                    }
                    
                    writer!!.write(cmd.toString() + "\n")
                    writer!!.flush()

                    val responseLine = reader!!.readLine()
                    if (responseLine == null) {
                        // Connection dropped
                        close()
                        retries++
                        continue
                    }

                    return@withContext json.decodeFromString<OmostrichResponse>(responseLine)
                } catch (e: Exception) {
                    close()
                    retries++
                    if (retries >= 2) {
                        return@withContext OmostrichResponse(ok = false, error = e.message ?: "Unknown error")
                    }
                }
            }
            return@withContext OmostrichResponse(ok = false, error = "Failed after retries")
        }
    }

    suspend fun status(): OmostrichStatus? {
        val req = buildJsonObject { put("cmd", "status") }
        val res = sendCommand(req)
        if (res.ok && res.data != null) {
            return try {
                json.decodeFromJsonElement<OmostrichStatus>(res.data)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        return null
    }

    suspend fun publish(content: String, tags: List<List<String>> = emptyList()): PublishResult? {
        val req = buildJsonObject {
            put("cmd", "publish")
            put("content", content)
            val tagsArray = buildJsonArray {
                tags.forEach { tagList ->
                    add(buildJsonArray {
                        tagList.forEach { add(JsonPrimitive(it)) }
                    })
                }
            }
            put("tags", tagsArray)
        }
        val res = sendCommand(req)
        if (res.ok && res.data != null) {
            return try {
                json.decodeFromJsonElement<PublishResult>(res.data)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        return null
    }

    private fun close() {
        try {
            socket?.close()
        } catch (e: Exception) {}
        socket = null
        reader = null
        writer = null
    }
}
