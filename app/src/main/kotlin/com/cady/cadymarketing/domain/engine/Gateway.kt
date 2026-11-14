package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Product

/** The Apps Script gateway the AI requests go through. Both values are kept on the device only. */
data class GatewayConfig(val url: String = "", val token: String = "") {
    val isConfigured: Boolean get() = url.isNotBlank()
}

data class GatewayResponse(val model: String?, val text: String?, val image: String?)

/** A gateway failure. [code] is one of GatewayMessages.byCode (or "x"), [message] is ready to show. */
class GatewayException(val code: String, message: String, val raw: String? = null) : Exception(message)

/** Error codes and their messages: the same as the original tool, so a failure reads the same. */
object GatewayMessages {
    val byCode: Map<String, String> = mapOf(
        "busy" to "الذكاء مشغول الآن (ضغط على خوادم Google)، جرّب بعد دقيقة",
        "noquota" to "هذا النموذج غير متاح في الباقة المجانية",
        "quota" to "بلغت حد الطلبات المؤقت، جرّب بعد قليل",
        "auth" to "رمز البوابة أو مفتاح Gemini غير صحيح",
        "nokey" to "لم يُضبط GEMINI_KEY في خصائص السكربت",
        "gone" to "لا يوجد نموذج متاح الآن، حدّث كود البوابة",
        "html" to "رابط البوابة لا يرد كما يجب: انشره بصلاحية Anyone وتأكد أنه ينتهي بـ /exec",
        "net" to "تعذّر الوصول للبوابة، تحقق من الإنترنت",
        "time" to "انتهت المهلة، أعد المحاولة",
        "empty" to "لم يرجع الذكاء نصًا، أعد المحاولة",
        "blocked" to "رفض الذكاء هذا الطلب، أعد المحاولة أو غيّر الصنف",
        "bad" to "طلب غير صالح",
    )

    private val busy = Regex("high demand|UNAVAILABLE|overloaded", RegexOption.IGNORE_CASE)
    private val noQuota = Regex("limit: 0")
    private val quota = Regex("quota|RESOURCE_EXHAUSTED|rate limit", RegexOption.IGNORE_CASE)
    private val auth = Regex("unauthorized|API key|PERMISSION_DENIED", RegexOption.IGNORE_CASE)
    private val gone = Regex("no longer available|NOT_FOUND", RegexOption.IGNORE_CASE)

    /** The gateway's own code when it is a known one, otherwise a guess from the raw error text ("x" when unknown). */
    fun codeFor(code: String?, rawMessage: String?): String {
        if (code != null && byCode.containsKey(code)) return code
        val message = rawMessage.orEmpty()
        return when {
            busy.containsMatchIn(message) -> "busy"
            noQuota.containsMatchIn(message) -> "noquota"
            quota.containsMatchIn(message) -> "quota"
            auth.containsMatchIn(message) -> "auth"
            gone.containsMatchIn(message) -> "gone"
            else -> "x"
        }
    }
}

/** Turns the AI's answer into a list of texts, whatever shape it came back in. */
object GatewayParsing {
    private val fence = Regex("```json|```")
    private val arrayInside = Regex("\\[[\\s\\S]*\\]")
    private val blankLines = Regex("\\n{2,}")

    fun list(raw: String?): List<String> {
        val text = raw.orEmpty().replace(fence, "").trim()
        var parsed: Any? = null
        var ok = false
        try {
            parsed = MiniJson.parse(text)
            ok = true
        } catch (e: MiniJson.ParseException) {
            val inside = arrayInside.find(text)
            if (inside != null) {
                try {
                    parsed = MiniJson.parse(inside.value)
                    ok = true
                } catch (e2: MiniJson.ParseException) {
                    ok = false
                }
            }
        }

        val value: Any? = parsed
        val items: List<Any?> = when {
            !ok -> text.split(blankLines).filter { it.trim().length > 15 }
            value is List<*> -> value
            value is Map<*, *> -> value.values.firstOrNull { it is List<*> } as? List<*> ?: emptyList<Any?>()
            else -> emptyList()
        }
        return items
            .map { item -> textOf(item).trim() }
            .filter { it.isNotEmpty() }
            .take(8)
    }

    private fun textOf(item: Any?): String {
        if (item is String) return item
        if (item is Map<*, *>) {
            for (key in listOf("text", "post", "نص", "النص", "منشور")) {
                val value = item[key]
                if (value is String && value.isNotEmpty()) return value
            }
        }
        return ""
    }
}

/** The prompts sent to the AI. Same wording as the original tool. */
object GatewayPrompts {
    const val CONNECTION_TEST = "اكتب كلمة واحدة فقط: جاهز"

    fun ideas(product: Product, brand: Brand, angle: String): String =
        "اقترح 8 أفكار محتوى تسويقي قصيرة بالعربية بزاوية «$angle» لمنتج «${product.name}» (${product.unit}) " +
            "من علامة «${brand.name}». كل فكرة جملة افتتاحية جاهزة للنشر بلا أي رقم أو سعر أو ادعاء. " +
            "ممنوع: ${brand.bannedPhrases}. أعد مصفوفة JSON من نصوص فقط."

    fun posts(product: Product, brand: Brand, tone: String): String {
        val facts = StringBuilder()
        facts.append("السعر ").append(ArabicText.formatNumber(product.effectivePrice)).append(" ريال")
        if (product.hasOffer) facts.append(" بدل ").append(ArabicText.formatNumber(product.price))
        if (product.offerEnd.isNotEmpty()) facts.append("، ينتهي العرض ").append(product.offerEnd)
        if (product.features.isNotEmpty()) facts.append("، المزايا: ").append(product.features.joinToString("، "))
        return "اكتب 4 منشورات تسويقية قصيرة بالعربية بنبرة «$tone» (${brand.tone}) لمنتج «${product.name}» (${product.unit}). " +
            "الحقائق المسموحة فقط: $facts. " +
            "ممنوع اختراع أي رقم أو ادعاء أو مقارنة بمنافس، وممنوع هذه العبارات: ${brand.bannedPhrases}. " +
            "اكتب {الرابط} حرفيًا في آخر كل منشور. نوّع الافتتاحيات والأسلوب ولا تكرر الجمل. " +
            "أعد مصفوفة JSON فقط من أربعة نصوص."
    }
}
