package com.salimsrk.typeassist

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Calls the free-tier Google Gemini API to rewrite a message so it sounds more
 * natural / human, with a tone appropriate to the app the user is typing in.
 *
 * Get a free key at https://aistudio.google.com/apikey - it is injected at
 * build time via BuildConfig.GEMINI_API_KEY (see app/build.gradle), so it is
 * never committed to source control.
 */
object RewriteApi {

    // If Google renames/retires this model, swap the id here.
    private const val MODEL = "gemini-2.0-flash"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun rewrite(text: String, packageName: String, callback: (String?) -> Unit) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            callback(null)
            return
        }

        val tone = when {
            packageName.contains("whatsapp") -> "casual, friendly, and natural, like a real person texting a friend"
            packageName.contains("gmail") || packageName.contains("outlook") -> "polished, professional, and clear, suitable for a work email"
            packageName.contains("linkedin") -> "professional but warm, suitable for a LinkedIn message or post"
            else -> "natural and human, correcting any awkward phrasing"
        }

        val prompt = "Rewrite the following message so it sounds $tone. " +
            "Fix any grammar or spelling mistakes. Keep the original meaning and language. " +
            "Reply with ONLY the rewritten message, no quotes, no explanation.\n\n" +
            "Message:\n$text"

        val payload = JSONObject().apply {
            put("contents", org.json.JSONArray().put(
                JSONObject().put("parts", org.json.JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            ))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        callback(null)
                        return
                    }
                    try {
                        val json = JSONObject(resp.body?.string() ?: "")
                        val text = json.getJSONArray("candidates")
                            .getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")
                        callback(text.trim())
                    } catch (e: Exception) {
                        callback(null)
                    }
                }
            }
        })
    }
}
