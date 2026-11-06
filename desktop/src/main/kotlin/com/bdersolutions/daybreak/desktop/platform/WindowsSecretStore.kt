package com.bdersolutions.daybreak.desktop.platform

import com.bdersolutions.daybreak.platform.SecretStore
import com.sun.jna.platform.win32.Crypt32Util
import java.io.File

/**
 * Keeps the WebUntis mobile key with Windows DPAPI. The data is encrypted for the signed-in
 * Windows user, so another user account (or a copy of the file) cannot read it.
 */
class WindowsSecretStore(private val directory: File) : SecretStore {
    private val file: File get() = File(directory, FILE_NAME)

    override fun getStoredSecret(): String? = try {
        if (file.exists()) {
            String(Crypt32Util.cryptUnprotectData(file.readBytes()), Charsets.UTF_8)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    override fun storeSecret(secret: String): Boolean = try {
        writeAtomically(file, Crypt32Util.cryptProtectData(secret.toByteArray(Charsets.UTF_8)))
    } catch (_: Exception) {
        false
    }

    override fun clear() {
        file.delete()
        // Files from the earlier AES store are removed too.
        File(directory, "secret.bin").delete()
        File(directory, "secret.key").delete()
    }

    private companion object {
        const val FILE_NAME = "secret.dpapi"
    }
}
