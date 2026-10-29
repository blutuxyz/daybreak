package com.bdersolutions.daybreak.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bdersolutions.daybreak.AppContainer
import com.bdersolutions.daybreak.data.debug.RawLogEntry
import com.bdersolutions.daybreak.data.debug.RawLogStore
import kotlinx.coroutines.flow.StateFlow

class DebugViewModel(
    private val rawLogStore: RawLogStore,
) : ViewModel() {
    val entries: StateFlow<List<RawLogEntry>> = rawLogStore.entries

    fun clear() {
        rawLogStore.clear()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DebugViewModel::class.java))
            return DebugViewModel(container.rawLogStore) as T
        }
    }
}
