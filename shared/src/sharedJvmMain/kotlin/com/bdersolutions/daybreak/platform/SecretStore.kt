package com.bdersolutions.daybreak.platform

/** Holds the WebUntis mobile key. Android encrypts it with the Keystore; desktop uses [DesktopSecretStore]. */
interface SecretStore {
    fun getStoredSecret(): String?
    fun storeSecret(secret: String): Boolean
    fun clear()
}
