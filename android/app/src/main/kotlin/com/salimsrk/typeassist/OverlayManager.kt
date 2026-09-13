package com.salimsrk.typeassist

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Shows / hides the small floating suggestion card above the keyboard.
 * Uses TYPE_ACCESSIBILITY_OVERLAY, which an AccessibilityService is allowed to
 * draw without asking for the separate "draw over other apps" permission.
 */
class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun showChecking() {
        val view = ensureView()
        view.findViewById<TextView>(R.id.overlay_title).text = "TypeAssist"
        view.findViewById<TextView>(R.id.overlay_message).text = "Checking your message..."
        view.findViewById<View>(R.id.btn_apply).visibility = View.GONE
        view.findViewById<View>(R.id.btn_rewrite).visibility = View.GONE
    }

    fun showNoIssues() {
        hide()
    }

    fun showGrammarSuggestion(
        issueCount: Int,
        offline: Boolean,
        onApply: () -> Unit,
        onRewrite: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        val view = ensureView()
        val statusLabel = if (offline) {
            "Offline • spelling only"
        } else {
            "$issueCount issue${if (issueCount == 1) "" else "s"} found"
        }
        view.findViewById<TextView>(R.id.overlay_title).text = "TypeAssist  •  $statusLabel"
        view.findViewById<TextView>(R.id.overlay_message).text = if (offline) {
            "No internet - showing spelling fixes only. Rewrite needs internet."
        } else {
            "Tap Apply fix to correct spelling/grammar, or Rewrite for a more natural version."
        }

        val applyBtn = view.findViewById<TextView>(R.id.btn_apply)
        val rewriteBtn = view.findViewById<TextView>(R.id.btn_rewrite)
        val dismissBtn = view.findViewById<TextView>(R.id.btn_dismiss)

        applyBtn.text = "Apply fix"
        applyBtn.visibility = View.VISIBLE
        rewriteBtn.visibility = View.VISIBLE

        if (offline) {
            rewriteBtn.alpha = 0.4f
            rewriteBtn.setOnClickListener {
                view.findViewById<TextView>(R.id.overlay_message).text =
                    "Rewrite needs an internet connection."
            }
        } else {
            rewriteBtn.alpha = 1f
            rewriteBtn.setOnClickListener { onRewrite() }
        }

        applyBtn.setOnClickListener { onApply() }
        dismissBtn.setOnClickListener { onDismiss() }
    }

    fun showRewriting() {
        val view = ensureView()
        view.findViewById<TextView>(R.id.overlay_title).text = "TypeAssist"
        view.findViewById<TextView>(R.id.overlay_message).text = "✨ Rewriting your message..."
        view.findViewById<View>(R.id.btn_apply).visibility = View.GONE
        view.findViewById<View>(R.id.btn_rewrite).visibility = View.GONE
    }

    fun showRewriteResult(rewritten: String, onApply: () -> Unit, onDismiss: () -> Unit) {
        val view = ensureView()
        view.findViewById<TextView>(R.id.overlay_title).text = "TypeAssist  •  Suggested rewrite"
        view.findViewById<TextView>(R.id.overlay_message).text = rewritten

        val applyBtn = view.findViewById<TextView>(R.id.btn_apply)
        val rewriteBtn = view.findViewById<TextView>(R.id.btn_rewrite)
        val dismissBtn = view.findViewById<TextView>(R.id.btn_dismiss)

        applyBtn.text = "Use this"
        applyBtn.visibility = View.VISIBLE
        rewriteBtn.visibility = View.GONE

        applyBtn.setOnClickListener { onApply() }
        dismissBtn.setOnClickListener { onDismiss() }
    }

    fun showError(message: String) {
        val view = ensureView()
        view.findViewById<TextView>(R.id.overlay_title).text = "TypeAssist"
        view.findViewById<TextView>(R.id.overlay_message).text = message
        view.findViewById<View>(R.id.btn_apply).visibility = View.GONE
        view.findViewById<View>(R.id.btn_rewrite).visibility = View.GONE
    }

    fun hide() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: IllegalArgumentException) {
                // already removed
            }
        }
        overlayView = null
    }

    private fun ensureView(): View {
        overlayView?.let { return it }

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.overlay_suggestion, null)

        val overlayType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP
        params.y = 60

        windowManager.addView(view, params)
        overlayView = view
        return view
    }
}
