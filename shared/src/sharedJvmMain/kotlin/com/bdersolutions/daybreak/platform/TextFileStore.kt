package com.bdersolutions.daybreak.platform

/**
 * One UTF-8 text file in the app's private storage. [write] must replace the file atomically,
 * so a crash never leaves half a file behind.
 */
interface TextFileStore {
    fun read(): String?
    fun write(text: String): Boolean
    fun delete()
}
