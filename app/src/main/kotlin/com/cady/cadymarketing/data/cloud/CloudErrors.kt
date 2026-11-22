package com.cady.cadymarketing.data.cloud

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException

/** Firebase is not set up in this build (no google-services.json), so there is nothing to read from. */
class CloudUnavailableException(message: String) : Exception(message)

/** The server did not answer in time (Firestore holds a write while the device is offline, so a limit is set on every one). */
class CloudTimeoutException : Exception("انتهت مهلة الاتصال بالخادم")

/** Turns a cloud failure into a sentence that says what happened and what to do. */
object CloudErrors {
    fun message(error: Throwable): String = when {
        error is CloudUnavailableException -> error.message ?: "Firebase غير مربوط"
        error is CloudTimeoutException -> "انتهت مهلة الاتصال بالخادم. تحقق من الإنترنت ثم أعد المحاولة."
        error is FirebaseNetworkException -> "لا يوجد اتصال بالإنترنت. تحقق من الشبكة ثم حاول مرة أخرى."
        error is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "لا تملك صلاحية على هذه البيانات. راجع قواعد Firestore أو حسابك."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                "تعذّر الوصول إلى الخادم. تحقق من الإنترنت ثم أعد المحاولة."
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> "انتهت جلسة الدخول. سجّل الدخول من جديد."
            else -> "تعذّر الاتصال بالخادم (${error.code.name})."
        }
        else -> "تعذّر الاتصال بالخادم."
    }
}
