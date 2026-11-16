package com.cady.cadymarketing.data.cloud

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException

/** Firebase is not set up in this build (no google-services.json), so there is nothing to read from. */
class CloudUnavailableException(message: String) : Exception(message)

/** Turns a cloud failure into a sentence that says what happened and what to do. */
object CloudErrors {
    fun message(error: Throwable): String = when {
        error is CloudUnavailableException -> error.message ?: "Firebase غير مربوط"
        error is FirebaseNetworkException -> "لا يوجد اتصال بالإنترنت. تحقق من الشبكة ثم حاول مرة أخرى."
        error is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "لا تملك صلاحية قراءة هذه البيانات. راجع قواعد Firestore أو حسابك."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "تعذّر الوصول إلى الخادم. تحقق من الإنترنت ثم أعد المحاولة."
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> "انتهت جلسة الدخول. سجّل الدخول من جديد."
            else -> "تعذّر الاتصال بالخادم (${error.code.name})."
        }
        else -> "تعذّر الاتصال بالخادم."
    }
}
