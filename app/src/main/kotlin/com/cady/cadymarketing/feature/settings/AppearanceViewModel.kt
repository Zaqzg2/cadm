package com.cady.cadymarketing.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.domain.model.AppStyle
import com.cady.cadymarketing.domain.model.Appearance
import com.cady.cadymarketing.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings → المظهر. Saving is all it does: the whole window watches the same saved values
 * (see `MainViewModel`), so a choice shows up everywhere at once.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val settings: SettingsStore,
) : ViewModel() {

    /** What is saved; null for the instant before it has been read. */
    val appearance: StateFlow<Appearance?> = combine<AppStyle, ThemeMode, Appearance?>(
        settings.appStyle,
        settings.themeMode,
    ) { style, mode -> Appearance(style, mode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setStyle(style: AppStyle) {
        viewModelScope.launch { settings.setAppStyle(style) }
    }

    fun setMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }
}
