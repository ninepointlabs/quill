package com.ninepointlabs.quill.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RelayParsingTest {

    @Test
    fun testParseEvent() {
        val jsonString = """["EVENT", "sub_123", {"id": "abcdef", "pubkey": "123456", "created_at": 1670000000, "kind": 1, "tags": [], "content": "hello world", "sig": "signature"}]"""
        
        val array = Json.parseToJsonElement(jsonString).jsonArray
        assertEquals("EVENT", array[0].jsonPrimitive.content)
        assertEquals("sub_123", array[1].jsonPrimitive.content)
        
        val eventObj = array[2].jsonObject
        assertEquals("abcdef", eventObj["id"]?.jsonPrimitive?.content)
        assertEquals("hello world", eventObj["content"]?.jsonPrimitive?.content)
    }

    @Test
    fun testParseEose() {
        val jsonString = """["EOSE", "sub_123"]"""
        val array = Json.parseToJsonElement(jsonString).jsonArray
        assertEquals("EOSE", array[0].jsonPrimitive.content)
        assertEquals("sub_123", array[1].jsonPrimitive.content)
    }
}
