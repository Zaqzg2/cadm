package com.cady.cadymarketing.feature.studio

import androidx.lifecycle.SavedStateHandle
import com.cady.cadymarketing.core.navigation.Routes

/**
 * What the screen that opened the Studio asked it to start on. Everything is optional: the Studio tab itself asks
 * for nothing and opens on the first product.
 */
data class StudioLaunch(
    val contentId: String? = null,
    val campaignId: String? = null,
    val date: String? = null,
    val productId: String? = null,
    val ideaOfDay: Boolean = false,
) {
    val isEmpty: Boolean
        get() = contentId == null && campaignId == null && date == null && productId == null && !ideaOfDay

    companion object {
        fun from(handle: SavedStateHandle): StudioLaunch = StudioLaunch(
            contentId = handle.get<String>(Routes.ARG_CONTENT_ID),
            campaignId = handle.get<String>(Routes.ARG_CAMPAIGN_ID),
            date = handle.get<String>(Routes.ARG_DATE),
            productId = handle.get<String>(Routes.ARG_PRODUCT_ID),
            ideaOfDay = handle.get<String>(Routes.ARG_IDEA) == "1",
        )
    }
}
