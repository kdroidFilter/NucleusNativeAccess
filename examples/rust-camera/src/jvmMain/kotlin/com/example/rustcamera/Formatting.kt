package com.example.rustcamera

fun formatResolution(w: Int, h: Int): String = "${w}x${h}"

fun formatPixelCount(w: Int, h: Int): String {
    val mp = (w.toLong() * h) / 1_000_000.0
    return if (mp >= 1.0) "%.1f MP".format(mp) else "${w * h} px"
}

fun formatAspectRatio(w: Int, h: Int): String {
    val gcd = gcd(w, h)
    return if (gcd > 0) "${w / gcd}:${h / gcd}" else "N/A"
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
