package com.salimsrk.typeassist

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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
        overlayManager = OverlayManager(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> handleTextChanged(event)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // Hide any stale suggestion when the user moves to a new field/app.
                pendingCheck?.let { mainHandler.removeCallbacks(it) }
                overlayManager?.hide()
            }
            else -> Unit
        }
    }

    override fun onInterrupt() {
        overlayManager?.hide()
    }

    private fun handleTextChanged(event: AccessibilityEvent) {
        val source = event.source ?: return
        val text = source.text?.toString().orEmpty()
        val packageName = event.packageName?.toString().orEmpty()

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
    }

    private fun runGrammarCheck(text: String, packageName: String, requestId: Int) {
        overlayManager?.showChecking()
        val online = NetworkUtils.isOnline(applicationContext)

        val onResult: (GrammarApi.GrammarResult?) -> Unit = { result ->
            mainHandler.post {
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
        overlayManager?.showRewriting()
        RewriteApi.rewrite(text, packageName) { rewritten ->
            mainHandler.post {
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
            }
        }
    }

    private fun applyText(newText: String) {
        val node = lastSourceNode ?: return
        val arguments = Bundle()
        arguments.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            newText
        )
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        overlayManager?.hide()
    }
}
