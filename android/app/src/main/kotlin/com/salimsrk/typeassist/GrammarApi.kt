package com.salimsrk.typeassist

import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Talks to the free public LanguageTool API (no API key required).
 * https://languagetool.org/http-api/
 */
object GrammarApi {

    private const val ENDPOINT = "https://api.languagetool.org/v2/check"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class GrammarResult(
        val issueCount: Int,
        val correctedText: String,
    )

    fun check(text: String, callback: (GrammarResult?) -> Unit) {
        val body = FormBody.Builder()
            .add("text", text)
            .add("language", "en-US")
            .build()

        val request = Request.Builder()
            .url(ENDPOINT)
            .post(body)
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
                        val matches: JSONArray = json.optJSONArray("matches") ?: JSONArray()
                        callback(buildCorrection(text, matches))
                    } catch (e: Exception) {
                        callback(null)
                    }
                }
            }
        })
    }

    private fun buildCorrection(original: String, matches: JSONArray): GrammarResult {
        // Apply replacements back-to-front so earlier offsets stay valid.
        data class Edit(val offset: Int, val length: Int, val replacement: String)

        val edits = mutableListOf<Edit>()
        for (i in 0 until matches.length()) {
            val match = matches.getJSONObject(i)
            val replacements = match.optJSONArray("replacements") ?: continue
            if (replacements.length() == 0) continue
            val replacement = replacements.getJSONObject(0).optString("value", "")
            if (replacement.isEmpty()) continue
            edits.add(
                Edit(
                    offset = match.optInt("offset"),
                    length = match.optInt("length"),
                    replacement = replacement
                )
            )
        }
        edits.sortByDescending { it.offset }

        val builder = StringBuilder(original)
        for (edit in edits) {
            val end = (edit.offset + edit.length).coerceAtMost(builder.length)
            if (edit.offset in 0..end) {
                builder.replace(edit.offset, end, edit.replacement)
            }
        }

        return GrammarResult(issueCount = matches.length(), correctedText = builder.toString())
    }
}
