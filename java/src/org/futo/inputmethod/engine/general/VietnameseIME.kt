package org.futo.inputmethod.engine.general

import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore

object VietnameseIMESettings {
    val DeleteWholeCharOnBackspace = SettingsKey(
        booleanPreferencesKey("ime_vi_delete_whole_char_on_backspace"),
        false
    )

    val menu = UserSettingsMenu(
        title = R.string.vietnamese_settings_title,
        navPath = "ime/vi", registerNavPath = true,
        settings = listOf(
            userSettingToggleDataStore(
                title = R.string.vietnamese_settings_toggle_delete_whole_char,
                subtitle = R.string.vietnamese_settings_toggle_delete_whole_char_subtitle,
                setting = DeleteWholeCharOnBackspace
            )
        )
    )
}
