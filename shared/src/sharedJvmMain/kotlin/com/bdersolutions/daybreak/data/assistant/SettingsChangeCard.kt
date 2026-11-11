package com.bdersolutions.daybreak.data.assistant

import com.bdersolutions.daybreak.data.settings.SettingChange

/**
 * The settings a change_settings call changed, shown in the chat with an Undo. [undone] is set once the student
 * has put them back; it is saved with the chat.
 */
data class SettingsChangeCard(
    val callId: String,
    val changes: List<SettingChange>,
    val undone: Boolean = false,
) : AssistantCard
