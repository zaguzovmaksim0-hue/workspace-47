package dev.junta.firmamobile.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import dev.junta.firmamobile.BuildConfig
import dev.junta.firmamobile.R
import dev.junta.firmamobile.feedback.CompatibilityFeedbackReport

internal fun currentCompatibilityFeedbackReport(): CompatibilityFeedbackReport =
    CompatibilityFeedbackReport.build(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, Build.VERSION.SDK_INT)

// Only explicit UI actions call these functions. Neither reads clipboard contents or app/session data.
internal fun copyCompatibilityFeedback(context: Context, report: CompatibilityFeedbackReport): Boolean =
    try {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        if (clipboard == null) false else {
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.compatibility_feedback_preview), report.body))
            true
        }
    } catch (_: RuntimeException) {
        false
    }

internal fun openCompatibilityFeedback(context: Context, report: CompatibilityFeedbackReport): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(report.issueFormUrl())).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
