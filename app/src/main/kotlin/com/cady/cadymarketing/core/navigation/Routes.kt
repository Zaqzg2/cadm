package com.cady.cadymarketing.core.navigation

import java.net.URLEncoder

/** Every destination in the app. Plain string routes — no extra serialization plugin needed. */
object Routes {
    // Tabs of the bottom bar.
    const val HOME = "home"
    const val CAMPAIGNS = "campaigns"
    const val CALENDAR = "calendar"
    const val MORE = "more"

    // Full-screen workspace: hides the bottom bar and closes with the X button.
    const val STUDIO = "studio"

    // Screens reached from "More".
    const val PRODUCTS = "products"
    const val IDENTITY = "identity"
    const val TEMPLATES = "templates"
    const val LIBRARY = "library"
    const val AUDIENCE = "audience"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    // A campaign's own page.
    const val ARG_ID = "id"
    const val CAMPAIGN_DETAIL = "campaign/{id}"

    // What the Studio can be asked to start on (all optional, see studio()).
    const val ARG_CONTENT_ID = "contentId"
    const val ARG_CAMPAIGN_ID = "campaignId"
    const val ARG_DATE = "date"
    const val ARG_PRODUCT_ID = "productId"
    const val ARG_IDEA = "idea"
    const val STUDIO_PATTERN =
        "studio?contentId={contentId}&campaignId={campaignId}&date={date}&productId={productId}&idea={idea}"

    /** The bottom bar is visible only on these routes. */
    val tabRoutes: Set<String> = setOf(HOME, CAMPAIGNS, CALENDAR, MORE)

    fun campaign(id: String): String = "campaign/" + encode(id)

    /**
     * The Studio, optionally started on something: a saved post to edit, a campaign to link, a day to schedule for,
     * a product to write about, or the idea of the day.
     */
    fun studio(
        contentId: String? = null,
        campaignId: String? = null,
        date: String? = null,
        productId: String? = null,
        ideaOfDay: Boolean = false,
    ): String {
        val parts = ArrayList<String>()
        if (contentId != null) parts.add("$ARG_CONTENT_ID=" + encode(contentId))
        if (campaignId != null) parts.add("$ARG_CAMPAIGN_ID=" + encode(campaignId))
        if (date != null) parts.add("$ARG_DATE=" + encode(date))
        if (productId != null) parts.add("$ARG_PRODUCT_ID=" + encode(productId))
        if (ideaOfDay) parts.add("$ARG_IDEA=1")
        return if (parts.isEmpty()) STUDIO else STUDIO + "?" + parts.joinToString("&")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
