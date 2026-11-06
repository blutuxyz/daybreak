package com.bdersolutions.daybreak.platform

/**
 * Small preferences store. Android backs it with SharedPreferences, the desktop app with a
 * properties file in the user's profile. Writes are chained like SharedPreferences.Editor.
 */
interface KeyValueStore {
    fun getString(key: String, default: String? = null): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun edit(): KeyValueEditor
}

interface KeyValueEditor {
    fun putString(key: String, value: String?): KeyValueEditor
    fun putBoolean(key: String, value: Boolean): KeyValueEditor
    fun putInt(key: String, value: Int): KeyValueEditor
    fun putLong(key: String, value: Long): KeyValueEditor
    fun remove(key: String): KeyValueEditor
    fun clear(): KeyValueEditor

    /** Applies the changes; the caller does not wait for the write to finish. */
    fun apply()

    /** Applies the changes and reports whether they were written. */
    fun commit(): Boolean
}
