package com.havish.ledlight

object Constants {
    val SWATCHES = listOf(
        0xFFFF0000.toInt() to "Red",
        0xFFFF5500.toInt() to "Orange",
        0xFFFFCC00.toInt() to "Yellow",
        0xFF00FF00.toInt() to "Green",
        0xFF00FFCC.toInt() to "Teal",
        0xFF00CCFF.toInt() to "Cyan",
        0xFF0000FF.toInt() to "Blue",
        0xFF7C00FF.toInt() to "Purple",
        0xFFFF00FF.toInt() to "Magenta",
        0xFFFF2D7A.toInt() to "Pink",
        0xFFFFD9A0.toInt() to "Warm white",
        0xFFFFFFFF.toInt() to "White"
    )

    val ELK_MODES = mapOf(
        "Rainbow fade" to 0x8A,
        "Jump RGB" to 0x87,
        "Jump 7" to 0x88,
        "Fade RGB" to 0x89,
        "Fade Red-Green" to 0x8B,
        "Fade Red-Blue" to 0x8C,
        "Fade Green-Blue" to 0x8D,
        "Crossfade R-G" to 0x8E,
        "Crossfade R-B" to 0x8F,
        "Crossfade G-B" to 0x90,
        "Strobe Red" to 0x91,
        "Strobe Green" to 0x92,
        "Strobe Blue" to 0x93,
        "Strobe Yellow" to 0x94,
        "Strobe Cyan" to 0x95,
        "Strobe Magenta" to 0x96,
        "Strobe White" to 0x97,
        "Strobe 7" to 0x9A
    )
}
