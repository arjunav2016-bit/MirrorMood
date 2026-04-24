package com.mirrormood.ui.wellness

object WellnessSessionDisplay {
    const val TYPE_BREATHING = "Breathing"
    const val TYPE_BODY_SCAN = "Body Scan"
    const val TYPE_GRATITUDE = "Gratitude"

    fun emojiFor(type: String): String = when (type) {
        TYPE_BREATHING -> "\uD83E\uDEE7"
        TYPE_BODY_SCAN -> "\uD83E\uDDD8"
        TYPE_GRATITUDE -> "\uD83D\uDE4F"
        else -> "\u2728"
    }
}
