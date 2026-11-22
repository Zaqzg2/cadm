package com.cady.cadymarketing

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.brand.BrandRepository
import com.cady.cadymarketing.domain.engine.BrandRules
import com.cady.cadymarketing.domain.model.Brand
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Holds what the whole window needs: the colour the app takes from the saved brand identity. */
@HiltViewModel
class MainViewModel @Inject constructor(
    brandRepository: BrandRepository,
) : ViewModel() {

    /** The brand's main colour, or null while it is still the original one (the hand-tuned red theme then applies). */
    val brandColor: StateFlow<Color?> = brandRepository.observeBrand()
        .map { brand -> colorOf(brand) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun colorOf(brand: Brand): Color? {
        if (brand.primaryHex.equals(Brand.DEFAULT_PRIMARY_HEX, ignoreCase = true)) return null
        val argb = BrandRules.argb(brand.primaryHex) ?: return null
        return Color(argb)
    }
}
