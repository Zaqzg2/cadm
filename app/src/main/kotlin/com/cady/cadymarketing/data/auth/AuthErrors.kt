package com.cady.cadymarketing.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

/** Turns a Firebase failure into a sentence that says what happened and what to do. */
object AuthErrors {
    fun message(error: Throwable): String = when (error) {
        is FirebaseNetworkException ->
            "لا يوجد اتصال بالإنترنت. تحقق من الشبكة ثم حاول مرة أخرى."
        is FirebaseTooManyRequestsException ->
            "محاولات كثيرة. انتظر قليلًا ثم حاول مرة أخرى."
        is FirebaseAuthInvalidUserException ->
            "هذا الحساب غير موجود أو معطّل. راجع مدير النظام."
        is FirebaseAuthInvalidCredentialsException ->
            "البريد الإلكتروني أو كلمة المرور غير صحيحة."
        else ->
            "تعذّر تسجيل الدخول. حاول مرة أخرى."
    }
}
