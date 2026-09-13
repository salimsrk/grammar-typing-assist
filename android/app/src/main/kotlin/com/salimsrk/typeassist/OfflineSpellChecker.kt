package com.salimsrk.typeassist

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager

/**
 * Fallback used when there is no internet connection. Uses Android's
 * built-in, fully on-device spell checker (the same one every keyboard
 * uses) - no download, no API key, works with airplane mode on.
 *
 * It can only catch misspelled words, not grammar/sentence-structure
 * mistakes the way LanguageTool can - that's the trade-off for zero cost
 * and zero setup while offline.
 */
object OfflineSpellChecker {

    private var cachedSession: SpellCheckerSession? = null
    private var pendingCallback: ((GrammarApi.GrammarResult?) -> Unit)? = null
    private var pendingOriginalText: String = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    private val listener = object : SpellCheckerSessionListener {
        override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
            // Not used - we always request sentence-level suggestions instead.
        }

        override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
            val callback = pendingCallback ?: return
            pendingCallback = null
            callback(buildResult(pendingOriginalText, results))
        }
    }

    fun check(context: Context, text: String, callback: (GrammarApi.GrammarResult?) -> Unit) {
        val session = cachedSession ?: createSession(context)
        if (session == null) {
            callback(null)
            return
        }
        cachedSession = session

        pendingCallback = callback
        pendingOriginalText = text
        session.getSentenceSuggestions(arrayOf(TextInfo(text)), 3)

        // Safety timeout - some devices' spell checker service can be slow/absent.
        mainHandler.postDelayed({
            if (pendingCallback === callback) {
                pendingCallback = null
                callback(null)
            }
        }, 4000)
    }

    private fun createSession(context: Context): SpellCheckerSession? {
        val tsm = context.applicationContext
            .getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
            ?: return null
        return tsm.newSpellCheckerSession(null, null, listener, true)
    }

    private fun buildResult(
        original: String,
        results: Array<out SentenceSuggestionsInfo>?,
    ): GrammarApi.GrammarResult {
        if (results == null || results.isEmpty()) {
            return GrammarApi.GrammarResult(issueCount = 0, correctedText = original)
        }

        data class Edit(val offset: Int, val length: Int, val replacement: String)
        val edits = mutableListOf<Edit>()

        for (sentenceInfo in results) {
            for (i in 0 until sentenceInfo.suggestionsCount) {
                val wordInfo = sentenceInfo.getSuggestionsInfoAt(i)
                val looksLikeTypo =
                    (wordInfo.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) != 0
                if (!looksLikeTypo || wordInfo.suggestionsCount == 0) continue
                edits.add(
                    Edit(
                        offset = sentenceInfo.getOffsetAt(i),
                        length = sentenceInfo.getLengthAt(i),
                        replacement = wordInfo.getSuggestionAt(0)
                    )
                )
            }
        }

        edits.sortByDescending { it.offset }
        val builder = StringBuilder(original)
        for (edit in edits) {
            val end = (edit.offset + edit.length).coerceAtMost(builder.length)
            if (edit.offset in 0..end) {
                builder.replace(edit.offset, end, edit.replacement)
            }
        }

        return GrammarApi.GrammarResult(issueCount = edits.size, correctedText = builder.toString())
    }
}
