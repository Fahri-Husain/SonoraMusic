package com.fahrii.musicapp.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

private const val HEX = "0123456789ABCDEF"

/**
 * Sama persis dengan android.net.Uri.encode: huruf, angka, dan _-!.~'()* dibiarkan, sisanya
 * jadi %XX (UTF-8, huruf besar). Harus identik karena hasilnya ikut tersimpan di URL lagu.
 */
internal fun uriEncode(s: String): String {
    val sb = StringBuilder(s.length + 8)
    for (byte in s.toByteArray(Charsets.UTF_8)) {
        val c = byte.toInt() and 0xFF
        val allowed = c < 0x80 && (
                c in 'a'.code..'z'.code ||
                        c in 'A'.code..'Z'.code ||
                        c in '0'.code..'9'.code ||
                        c.toChar() in "_-!.~'()*"
                )
        if (allowed) {
            sb.append(c.toChar())
        } else {
            sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
        }
    }
    return sb.toString()
}

/** GET sederhana yang mengembalikan isi respons sebagai teks UTF-8. Panggil dari Dispatchers.IO. */
internal fun httpGetString(
    url: String,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    userAgent: String = "SonoraMusic/1.0"
): String {
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.setRequestProperty("User-Agent", userAgent)
        val code = conn.responseCode
        if (code !in 200..299) throw IOException("HTTP $code")
        return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    } finally {
        conn.disconnect()
    }
}

// ---- Pembantu JSON yang meniru perilaku opt* milik org.json (kunci tak ada atau null -> bawaan) ----

private fun JsonObject.prim(key: String): JsonPrimitive? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

internal fun parseJsonObject(text: String): JsonObject = StoreJson.parseToJsonElement(text).jsonObject

internal fun JsonObject.jStr(key: String): String = prim(key)?.content.orEmpty()

internal fun JsonObject.jStrOrNull(key: String): String? = prim(key)?.content

internal fun JsonObject.jLong(key: String, default: Long = 0L): Long =
    prim(key)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() } ?: default

internal fun JsonObject.jInt(key: String, default: Int = 0): Int =
    prim(key)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() } ?: default

internal fun JsonObject.jDouble(key: String, default: Double): Double =
    prim(key)?.doubleOrNull ?: default

internal fun JsonObject.jObj(key: String): JsonObject? = this[key] as? JsonObject

/** Skema URL ("sonora", "https", ...) atau null kalau tidak ada. Pengganti Uri.scheme. */
internal fun urlScheme(url: String): String? {
    val i = url.indexOf(':')
    if (i <= 0) return null
    val scheme = url.substring(0, i)
    return scheme.takeIf { s -> s.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' } }
}

/**
 * Nilai parameter query pertama bernama [name], sudah di-decode. Pengganti Uri.getQueryParameter.
 * Cocok dengan [uriEncode] (spasi jadi %20, "+" asli jadi %2B).
 */
internal fun urlQueryParam(url: String, name: String): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    for (pair in query.split('&')) {
        if (pair.substringBefore('=') == name) {
            val value = pair.substringAfter('=', "")
            return runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
        }
    }
    return null
}