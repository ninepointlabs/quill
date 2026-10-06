package com.ninepointlabs.quill.utils

import java.net.URI

data class ParsedContent(
    val text: String,           // plain text with URLs stripped
    val imageUrls: List<String>, // detected image URLs
    val linkUrl: String?,        // first non-image URL (for preview)
    val nostrRefs: List<String>, // nostr: references
    val regularUrls: List<String> // http/https links
)

object ContentParser {
    private val urlRegex = Regex("""https?://[^\s]+""")
    private val nostrRegex = Regex("""nostr:(npub1|nprofile1|note1|nevent1|naddr1)[a-zA-Z0-9]+""")
    
    private val imageExts = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp")
    private val imageHosts = listOf("imgur.com", "i.redd.it", "blossom.band")

    private fun isImageUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        if (imageExts.any { lowerUrl.endsWith(it) || lowerUrl.contains("$it?") || lowerUrl.contains("$it#") }) return true
        
        try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false
            return imageHosts.any { host.contains(it) }
        } catch (e: Exception) {
            return false
        }
    }

    fun parse(content: String): ParsedContent {
        val imageUrls = mutableListOf<String>()
        var linkUrl: String? = null
        val nostrRefs = mutableListOf<String>()
        val regularUrls = mutableListOf<String>()
        
        val urlMatches = urlRegex.findAll(content)
        val urlsToStrip = mutableListOf<String>()
        
        for (match in urlMatches) {
            val url = match.value
            if (isImageUrl(url)) {
                imageUrls.add(url)
                urlsToStrip.add(url)
            } else {
                if (linkUrl == null) {
                    linkUrl = url
                }
                regularUrls.add(url)
            }
        }
        
        var text = content
        for (url in urlsToStrip) {
            text = text.replace(url, "").replace("  ", " ") // simple cleanup
        }
        
        val nostrMatches = nostrRegex.findAll(text)
        for (match in nostrMatches) {
            nostrRefs.add(match.value)
        }
        
        return ParsedContent(
            text = text.trim(),
            imageUrls = imageUrls,
            linkUrl = linkUrl,
            nostrRefs = nostrRefs,
            regularUrls = regularUrls
        )
    }
}
