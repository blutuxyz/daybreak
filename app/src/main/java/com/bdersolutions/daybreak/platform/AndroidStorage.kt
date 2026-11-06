package com.bdersolutions.daybreak.platform

import android.content.SharedPreferences
import android.util.AtomicFile
import java.io.File

/** [KeyValueStore] over the app's SharedPreferences. */
class SharedPreferencesStore(private val preferences: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, default: String?): String? = preferences.getString(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = preferences.getBoolean(key, default)
    override fun getInt(key: String, default: Int): Int = preferences.getInt(key, default)
    override fun getLong(key: String, default: Long): Long = preferences.getLong(key, default)
    override fun edit(): KeyValueEditor = Editor(preferences.edit())

    private class Editor(private val editor: SharedPreferences.Editor) : KeyValueEditor {
        override fun putString(key: String, value: String?): KeyValueEditor {
            editor.putString(key, value)
            return this
        }

        override fun putBoolean(key: String, value: Boolean): KeyValueEditor {
            editor.putBoolean(key, value)
            return this
        }

        override fun putInt(key: String, value: Int): KeyValueEditor {
            editor.putInt(key, value)
            return this
        }

        override fun putLong(key: String, value: Long): KeyValueEditor {
            editor.putLong(key, value)
            return this
        }

        override fun remove(key: String): KeyValueEditor {
            editor.remove(key)
            return this
        }

        override fun clear(): KeyValueEditor {
            editor.clear()
            return this
        }

        override fun apply() = editor.apply()
        override fun commit(): Boolean = editor.commit()
    }
}

/** [TextFileStore] over [AtomicFile], so a crash never leaves a half-written file. */
class AtomicTextFile(private val file: File) : TextFileStore {
    private val atomic = AtomicFile(file)

    override fun read(): String? = try {
        if (!file.exists()) null else atomic.readFully().toString(Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    override fun write(text: String): Boolean = try {
        val stream = atomic.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            atomic.failWrite(stream)
            throw error
        }
        true
    } catch (_: Exception) {
        false
    }

    override fun delete() {
        atomic.delete()
    }
}
