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
        "Rainbow fade" to 0x86, "Red fade" to 0x87, "Green fade" to 0x88, "Blue fade" to 0x89,
        "Yellow fade" to 0x8A, "Cyan fade" to 0x8B, "Magenta fade" to 0x8C, "White fade" to 0x8D,
        "Red-Green" to 0x8E, "Red-Blue" to 0x8F, "Green-Blue" to 0x90, "Jump 7" to 0x8F,
        "Strobe all" to 0x97, "Strobe red" to 0x98, "Strobe green" to 0x99, "Strobe blue" to 0x9A
    )
}
