package com.ninepointlabs.quill.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val domain: String
)

object LinkPreviewFetcher {
    private val client = OkHttpClient()

    private fun extractMetaTag(html: String, property: String): String? {
        val metaTagRegex = Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
        for (match in metaTagRegex.findAll(html)) {
            val tag = match.value
            val hasProperty = tag.contains("property=\"$property\"", ignoreCase = true) || 
                              tag.contains("property='$property'", ignoreCase = true) ||
                              tag.contains("name=\"$property\"", ignoreCase = true) || 
                              tag.contains("name='$property'", ignoreCase = true)
                              
            if (hasProperty) {
                val contentMatch = Regex("""content=(["'])(.*?)\1""", RegexOption.IGNORE_CASE).find(tag)
                if (contentMatch != null) {
                    return contentMatch.groupValues[2]
                }
            }
        }
        return null
    }

    suspend fun fetch(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        try {
            val uri = URI(url)
            val domain = uri.host?.removePrefix("www.") ?: uri.host ?: url

            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val html = response.body?.string() ?: return@withContext null
                
                var title = extractMetaTag(html, "og:title")
                if (title == null) {
                    // Fallback to <title>
                    val titleMatch = Regex("""<title[^>]*>(.*?)</title>""", RegexOption.IGNORE_CASE).find(html)
                    title = titleMatch?.groupValues?.get(1)
                }
                
                val description = extractMetaTag(html, "og:description")
                val imageUrl = extractMetaTag(html, "og:image")

                if (title == null && description == null && imageUrl == null) {
                    return@withContext LinkPreview(url, null, null, null, domain)
                }

                LinkPreview(url, title, description, imageUrl, domain)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
