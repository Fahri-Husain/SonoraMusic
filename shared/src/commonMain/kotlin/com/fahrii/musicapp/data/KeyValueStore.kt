package com.fahrii.musicapp.data

import java.io.File

/**
 * Penyimpanan kunci-nilai sederhana untuk pengaturan. Android membungkus SharedPreferences,
 * desktop memakai [FileKeyValueStore]. Setiap put langsung dicatat (tidak perlu commit).
 */
interface KeyValueStore {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun getString(key: String): String?

    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
    fun putString(key: String, value: String)
}

/** Implementasi berbasis satu file JSON ({"kunci": "nilai"}), untuk desktop. */
class FileKeyValueStore(private val file: File) : KeyValueStore {

    private val lock = Any()
    private val map: MutableMap<String, String> = load()

    override fun getBoolean(key: String, default: Boolean): Boolean =
        synchronized(lock) { map[key]?.toBooleanStrictOrNull() } ?: default

    override fun getInt(key: String, default: Int): Int =
        synchronized(lock) { map[key]?.toIntOrNull() } ?: default

    override fun getString(key: String): String? = synchronized(lock) { map[key] }

    override fun putBoolean(key: String, value: Boolean) = put(key, value.toString())
    override fun putInt(key: String, value: Int) = put(key, value.toString())
    override fun putString(key: String, value: String) = put(key, value)

    private fun put(key: String, value: String) = synchronized(lock) {
        map[key] = value
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(StoreJson.encodeToString<Map<String, String>>(map))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
        Unit
    }

    private fun load(): MutableMap<String, String> = try {
        StoreJson.decodeFromString<Map<String, String>>(file.readText()).toMutableMap()
    } catch (e: Exception) {
        mutableMapOf()
    }
}