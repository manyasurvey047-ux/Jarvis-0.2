package com.example.vision

import android.graphics.Rect

/**
 * Represents an interactive or visible UI component discovered on the current screen.
 */
data class UiElementInfo(
    val id: Int,
    val text: String,
    val contentDescription: String,
    val className: String,
    val viewId: String,
    val bounds: Rect,
    val isClickable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean
) {
    val centerX: Float get() = bounds.centerX().toFloat()
    val centerY: Float get() = bounds.centerY().toFloat()

    val friendlyType: String
        get() {
            val lower = className.lowercase()
            return when {
                lower.contains("switch") -> "Switch (checked=$isChecked)"
                lower.contains("checkbox") -> "CheckBox (checked=$isChecked)"
                lower.contains("edittext") || isEditable -> "InputField"
                lower.contains("button") -> "Button"
                lower.contains("imageview") && isClickable -> "IconButton"
                lower.contains("textview") && isClickable -> "ClickableText"
                isClickable -> "ClickableItem"
                else -> "Text/Label"
            }
        }

    fun toPromptString(): String {
        val label = if (text.isNotBlank()) "\"$text\"" else if (contentDescription.isNotBlank()) "[$contentDescription]" else "(unlabeled)"
        return "#$id [$friendlyType] $label at center=(${centerX.toInt()}, ${centerY.toInt()}) bounds=[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]"
    }
}

/**
 * Full snapshot of the active screen for multimodal vision analysis.
 */
data class ScreenUiContext(
    val elements: List<UiElementInfo>,
    val screenshotBase64: String?,
    val screenWidth: Int,
    val screenHeight: Int,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toPromptSummary(): String {
        if (elements.isEmpty()) {
            return "Active Package: $packageName. Screen Size: ${screenWidth}x${screenHeight}. No accessibility elements detected."
        }
        val builder = StringBuilder()
        builder.append("Active Package: $packageName\n")
        builder.append("Screen Resolution: ${screenWidth}x${screenHeight}\n")
        builder.append("Detected UI Elements (${elements.size} items):\n")
        elements.take(60).forEach { el ->
            builder.append("• ").append(el.toPromptString()).append("\n")
        }
        return builder.toString()
    }
}

/**
 * Structured autonomous action decided by Gemini Vision.
 */
data class ScreenVisionDecision(
    val action: String, // "CLICK", "TYPE", "SCROLL", "NAVIGATE_APP", "BACK", "HOME", "CONFIRM_SAFETY", "CLARIFY", "COMPLETE", "WAIT"
    val targetId: Int? = null,
    val targetDescription: String = "",
    val clickX: Float? = null,
    val clickY: Float? = null,
    val inputText: String? = null,
    val scrollDirection: String? = null, // "down", "up", "left", "right"
    val appName: String? = null,
    val safetyRisk: Boolean = false,
    val safetyQuestion: String? = null,
    val reasoning: String = "",
    val verificationExpectation: String? = null,
    val isCompleted: Boolean = false,
    val spokenMessage: String = ""
)
