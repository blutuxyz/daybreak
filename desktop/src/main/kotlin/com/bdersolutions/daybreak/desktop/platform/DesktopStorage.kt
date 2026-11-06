package com.bdersolutions.daybreak.desktop.platform

import com.bdersolutions.daybreak.platform.KeyValueEditor
import com.bdersolutions.daybreak.platform.KeyValueStore
import com.bdersolutions.daybreak.platform.SecretStore
import com.bdersolutions.daybreak.platform.TextFileStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Writes [bytes] to [target] through a temporary file, so a crash never leaves half a file. */
internal fun writeAtomically(target: File, bytes: ByteArray): Boolean = try {
    target.parentFile?.mkdirs()
    val temp = File(target.parentFile, "${target.name}.tmp")
    temp.writeBytes(bytes)
    Files.move(
        temp.toPath(),
        target.toPath(),
        StandardCopyOption.REPLACE_EXISTING,
        StandardCopyOption.ATOMIC_MOVE,
    )
    true
} catch (_: Exception) {
    false
}

/** [KeyValueStore] kept in a properties file in the user's app-data folder. */
class PropertiesStore(private val file: File) : KeyValueStore {
    private val lock = Any()
    private val values: MutableMap<String, String> = load()

    private fun load(): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        if (!file.exists()) return map
        runCatching {
            val properties = Properties()
            file.inputStream().use { properties.load(it) }
            properties.forEach { (key, value) -> map[key.toString()] = value.toString() }
        }
        return map
    }

    override fun getString(key: String, default: String?): String? = synchronized(lock) { values[key] ?: default }

    override fun getBoolean(key: String, default: Boolean): Boolean =
        synchronized(lock) { values[key]?.toBooleanStrictOrNull() ?: default }

    override fun getInt(key: String, default: Int): Int =
        synchronized(lock) { values[key]?.toIntOrNull() ?: default }

    override fun getLong(key: String, default: Long): Long =
        synchronized(lock) { values[key]?.toLongOrNull() ?: default }

    override fun edit(): KeyValueEditor = Editor()

    private inner class Editor : KeyValueEditor {
        private val changes = mutableListOf<(MutableMap<String, String>) -> Unit>()

        override fun putString(key: String, value: String?): KeyValueEditor = change { map ->
            if (value == null) map.remove(key) else map[key] = value
        }

        override fun putBoolean(key: String, value: Boolean): KeyValueEditor = change { it[key] = value.toString() }
        override fun putInt(key: String, value: Int): KeyValueEditor = change { it[key] = value.toString() }
        override fun putLong(key: String, value: Long): KeyValueEditor = change { it[key] = value.toString() }
        override fun remove(key: String): KeyValueEditor = change { it.remove(key) }
        override fun clear(): KeyValueEditor = change { it.clear() }

        private fun change(block: (MutableMap<String, String>) -> Unit): KeyValueEditor {
            changes += block
            return this
        }

        override fun apply() {
            commit()
        }

        override fun commit(): Boolean = synchronized(lock) {
            val next = values.toMutableMap()
            changes.forEach { it(next) }
            val properties = Properties()
            next.forEach { (key, value) -> properties.setProperty(key, value) }
            val buffer = ByteArrayOutputStream()
            properties.store(buffer, null)
            val written = writeAtomically(file, buffer.toByteArray())
            if (written) {
                values.clear()
                values.putAll(next)
            }
            written
        }
    }
}

/** [TextFileStore] for one UTF-8 file, replaced atomically. */
class DesktopTextFile(private val file: File) : TextFileStore {
    override fun read(): String? = try {
        if (file.exists()) file.readText(Charsets.UTF_8) else null
    } catch (_: Exception) {
        null
    }

    override fun write(text: String): Boolean = writeAtomically(file, text.toByteArray(Charsets.UTF_8))

    override fun delete() {
        file.delete()
    }
}

/**
 * Keeps the WebUntis mobile key encrypted with AES-GCM. The key lives in its own file next to the
 * ciphertext, in the user's app-data folder. That only stops casual reading; it is no stronger than
 * the plain session file the desktop app used before. Windows DPAPI would be the next step.
 */
class DesktopSecretStore(private val directory: File) : SecretStore {
    private val keyFile: File get() = File(directory, "secret.key")
    private val cipherFile: File get() = File(directory, "secret.bin")

    @Synchronized
    override fun getStoredSecret(): String? = try {
        if (!cipherFile.exists()) {
            null
        } else {
            val bytes = Base64.getDecoder().decode(cipherFile.readText(Charsets.UTF_8).trim())
            val key = key(create = false) ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_BYTES)))
            String(cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)), Charsets.UTF_8)
        }
    } catch (_: Exception) {
        null
    }

    @Synchronized
    override fun storeSecret(secret: String): Boolean = try {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = key(create = true) ?: return false
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val payload = Base64.getEncoder().encodeToString(iv + encrypted)
        writeAtomically(cipherFile, payload.toByteArray(Charsets.UTF_8))
    } catch (_: Exception) {
        false
    }

    @Synchronized
    override fun clear() {
        cipherFile.delete()
        keyFile.delete()
    }

    private fun key(create: Boolean): SecretKey? {
        if (keyFile.exists()) {
            val raw = Base64.getDecoder().decode(keyFile.readText(Charsets.UTF_8).trim())
            return SecretKeySpec(raw, "AES")
        }
        if (!create) return null
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        keyFile.parentFile?.mkdirs()
        keyFile.writeText(Base64.getEncoder().encodeToString(key.encoded), Charsets.UTF_8)
        return key
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
