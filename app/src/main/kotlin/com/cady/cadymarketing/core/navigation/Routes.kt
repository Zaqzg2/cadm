package com.cady.cadymarketing.core.navigation

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

    /** The bottom bar is visible only on these routes. */
    val tabRoutes: Set<String> = setOf(HOME, CAMPAIGNS, CALENDAR, MORE)
}
