package com.bdersolutions.daybreak.data.assistant

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The saved assistant chats, only on this device: `files/assistant_chats/<id>.json` plus `index.json` for
 * the history list. Files are written whole and renamed into place, so a crash never leaves half a chat.
 * The folder is outside backups (like all app files here) and is deleted on logout.
 */
class ChatStore(context: Context) {
    private val dir = File(context.filesDir, DIR_NAME)
    private val index = File(dir, "index.json")
    private val mutex = Mutex()
    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    private var loaded = false

    /** The history, newest first. */
    val chats: StateFlow<List<ChatSummary>> = _chats.asStateFlow()

    suspend fun load() = locked {
        if (!loaded) {
            _chats.value = ChatArchive.decodeIndex(index.takeIf { it.exists() }?.readText())
            loaded = true
        }
    }

    suspend fun save(chat: SavedChat) = locked {
        dir.mkdirs()
        writeAtomic(fileFor(chat.id), ChatArchive.encode(chat))
        val summaries = listOf(ChatSummary(chat.id, chat.title, chat.updatedAt)) + _chats.value.filterNot { it.id == chat.id }
        _chats.value = summaries.sortedByDescending { it.updatedAt }
        writeAtomic(index, ChatArchive.encodeIndex(_chats.value))
    }

    suspend fun open(id: Long): SavedChat? = locked {
        ChatArchive.decode(fileFor(id).takeIf { it.exists() }?.readText())
    }

    /** Gives a saved chat the student's own name, which nothing replaces later. */
    suspend fun rename(id: Long, title: String) {
        val chat = open(id) ?: return
        save(chat.copy(title = title, titleSource = TitleSource.MANUAL, updatedAt = chat.updatedAt))
    }

    suspend fun delete(id: Long) = locked {
        fileFor(id).delete()
        _chats.value = _chats.value.filterNot { it.id == id }
        if (dir.exists()) writeAtomic(index, ChatArchive.encodeIndex(_chats.value))
    }

    /** Deletes every chat (logout). */
    suspend fun clear() = locked {
        dir.deleteRecursively()
        _chats.value = emptyList()
    }

    private fun fileFor(id: Long) = File(dir, "$id.json")

    private fun writeAtomic(target: File, text: String) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    private suspend fun <T> locked(block: () -> T): T = withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private companion object {
        const val DIR_NAME = "assistant_chats"
    }
}
