package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Product

internal object TestFixtures {
    val brand = Brand()

    val offer = Product("i1", "سائل جلي كادي — ليمون", "كرتونة (4 جراكن × 5 لتر)", 7000L, 6800L, "")
    val offerWithEnd = offer.copy(offerEnd = "2099-12-31")
    val expiredOffer = offer.copy(offerEnd = "2020-01-01")
    val plain = Product(
        id = "i2",
        name = "منتج تجريبي",
        unit = "جركن (5 لتر)",
        price = 1800L,
        features = listOf("عبوة عملية سهلة الحمل", "مناسب للاستخدام المنزلي", "خلطة بـ 99 مكوّنًا", "يقتل الجراثيم"),
    )
    val plainWithEnd = plain.copy(offerEnd = "2099-12-31")

    /** Every product whose generated text must pass the checker. */
    val passable = listOf(offer, offerWithEnd, plain, plainWithEnd)
}
