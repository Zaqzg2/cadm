package com.cady.cadymarketing.data.cloud

/** A document as one readable line ("id ← field=value · field=value"), for the diagnostics dialogs. */
fun RawDoc.describe(maxLength: Int = 600): String =
    (id + " ← " + data.entries.joinToString(" · ") { (key, value) -> key + "=" + (value?.toString() ?: "null").take(40) })
        .take(maxLength)
