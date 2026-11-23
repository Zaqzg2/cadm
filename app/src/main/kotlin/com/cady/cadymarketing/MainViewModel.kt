package com.cady.cadymarketing

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.brand.BrandRepository
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.domain.engine.BrandRules
import com.cady.cadymarketing.domain.model.AppStyle
import com.cady.cadymarketing.domain.model.Appearance
import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Holds what the whole window needs: the brand colour and the look picked in Settings → المظهر. */
@HiltViewModel
class MainViewModel @Inject constructor(
    brandRepository: BrandRepository,
    settingsStore: SettingsStore,
) : ViewModel() {

    /** The brand's main colour, or null while it is still the original one (the hand-tuned red theme then applies). */
    val brandColor: StateFlow<Color?> = brandRepository.observeBrand()
        .map { brand -> colorOf(brand) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The style and the light/dark mode saved in Settings → المظهر. Null until the saved choice has
     * been read (a few milliseconds), so the window can wait for it instead of opening in the
     * classic look and then jumping to the chosen one.
     */
    val appearance: StateFlow<Appearance?> = combine<AppStyle, ThemeMode, Appearance?>(
        settingsStore.appStyle,
        settingsStore.themeMode,
    ) { style, mode -> Appearance(style, mode) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun colorOf(brand: Brand): Color? {
        if (brand.primaryHex.equals(Brand.DEFAULT_PRIMARY_HEX, ignoreCase = true)) return null
        val argb = BrandRules.argb(brand.primaryHex) ?: return null
        return Color(argb)
    }
}
