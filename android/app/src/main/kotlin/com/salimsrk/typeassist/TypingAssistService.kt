package com.salimsrk.typeassist

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

private const val TAG = "TypeAssistSvc"

/**
 * Background service that watches text fields in every app (WhatsApp, Gmail,
 * LinkedIn, etc.), checks the typed text for grammar/spelling issues via
 * LanguageTool, and offers an on-demand "humanize" rewrite via Gemini.
 *
 * Enabled by the user from Settings > Accessibility > TypeAssist.
 */
class TypingAssistService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingCheck: Runnable? = null
    private val debounceMillis = 900L

    private var overlayManager: OverlayManager? = null
    private var lastSourceNode: AccessibilityNodeInfo? = null
    private var lastOriginalText: String = ""
    private var lastPackageName: String = ""
    private var requestId = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "onServiceConnected")
        try {
            // Important: TYPE_ACCESSIBILITY_OVERLAY windows must be created
            // using the AccessibilityService's own context ("this"), not
            // applicationContext - applicationContext has no valid window
            // token for this window type and addView() throws
            // BadTokenException ("token null is not valid").
            overlayManager = OverlayManager(this)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create OverlayManager", t)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            Log.d(TAG, "onAccessibilityEvent type=${event.eventType} pkg=${event.packageName}")

            // Our own floating overlay card is itself a window, so adding/
            // removing it can generate window-state/focus events for our
            // own package. If we don't ignore those, the card hides itself
            // within a second of appearing. Only react to events coming
            // from OTHER apps.
            if (event.packageName?.toString() == applicationContext.packageName) return

            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTextChanged(event)
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // A real app/window switch (not just focus moving within
                    // the same screen) - hide any stale suggestion.
                    pendingCheck?.let { mainHandler.removeCallbacks(it) }
                    overlayManager?.hide()
                }
                else -> Unit
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onAccessibilityEvent crashed", t)
        }
    }

    override fun onInterrupt() {
        overlayManager?.hide()
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        try {
            val source = event.source ?: return
            val text = source.text?.toString().orEmpty()
            val packageName = event.packageName?.toString().orEmpty()

            Log.d(TAG, "handleTextChanged pkg=$packageName textLen=${text.length}")

            // Don't bother checking our own app or very short fragments.
            if (packageName == applicationContext.packageName) return
            if (text.trim().length < 4) {
                overlayManager?.hide()
                return
            }

            lastSourceNode = source
            lastOriginalText = text
            lastPackageName = packageName

            pendingCheck?.let { mainHandler.removeCallbacks(it) }
            val thisRequestId = ++requestId
            val runnable = Runnable { runGrammarCheck(text, packageName, thisRequestId) }
            pendingCheck = runnable
            mainHandler.postDelayed(runnable, debounceMillis)
        } catch (t: Throwable) {
            Log.e(TAG, "handleTextChanged crashed", t)
        }
    }

    private fun runGrammarCheck(text: String, packageName: String, requestId: Int) {
        Log.d(TAG, "runGrammarCheck pkg=$packageName")
        try {
            overlayManager?.showChecking()
        } catch (t: Throwable) {
            Log.e(TAG, "showChecking crashed", t)
        }
        val online = NetworkUtils.isOnline(applicationContext)

        val onResult: (GrammarApi.GrammarResult?) -> Unit = { result ->
            mainHandler.post {
                try {
                    Log.d(TAG, "grammar onResult issueCount=${result?.issueCount}")
                    if (requestId != this.requestId) return@post // a newer keystroke made this stale
                    if (result == null) {
                        overlayManager?.hide()
                        return@post
                    }
                    if (result.issueCount == 0 || result.correctedText == text) {
                        overlayManager?.showNoIssues()
                        return@post
                    }
                    overlayManager?.showGrammarSuggestion(
                        issueCount = result.issueCount,
                        offline = !online,
                        onApply = { applyText(result.correctedText) },
                        onRewrite = {
                            if (NetworkUtils.isOnline(applicationContext)) {
                                runRewrite(text, packageName, requestId)
                            }
                        },
                        onDismiss = { overlayManager?.hide() }
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "onResult handling crashed", t)
                }
            }
        }

        if (online) {
            GrammarApi.check(text, onResult)
        } else {
            // No internet - fall back to Android's built-in on-device spell
            // checker so basic spelling mistakes are still caught offline.
            OfflineSpellChecker.check(applicationContext, text, onResult)
        }
    }

    private fun runRewrite(text: String, packageName: String, requestId: Int) {
        try {
            overlayManager?.showRewriting()
        } catch (t: Throwable) {
            Log.e(TAG, "showRewriting crashed", t)
        }
        RewriteApi.rewrite(text, packageName) { rewritten ->
            mainHandler.post {
                try {
                    if (requestId != this.requestId) return@post
                    if (rewritten.isNullOrBlank()) {
                        overlayManager?.showError("Couldn't rewrite right now. Check your Gemini API key / internet connection.")
                        return@post
                    }
                    overlayManager?.showRewriteResult(
                        rewritten = rewritten,
                        onApply = { applyText(rewritten) },
                        onDismiss = { overlayManager?.hide() }
                    )
                } catch (t: Throwable) {
                    Log.e(TAG, "rewrite result handling crashed", t)
                }
            }
        }
    }

    private fun applyText(newText: String) {
        try {
            val node = lastSourceNode ?: return
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            overlayManager?.hide()
        } catch (t: Throwable) {
            Log.e(TAG, "applyText crashed", t)
        }
    }
}
