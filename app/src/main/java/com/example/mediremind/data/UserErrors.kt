package com.example.mediremind.data

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

fun Throwable.userMessage(): String {
    if (this is TimeoutCancellationException) return "Kết nối quá lâu. Kiểm tra mạng rồi thử lại."
    if (this is CancellationException) throw this
    return when (this) {
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Firebase từ chối truy cập. Kiểm tra quyền truy cập hoặc ghép đôi lại rồi thử lại."
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> "Không kết nối được Firebase. Kiểm tra mạng rồi thử lại."
            else -> "Không thể đồng bộ dữ liệu. Vui lòng thử lại."
        }
        is IOException -> "Không có kết nối mạng ổn định. Kiểm tra Wi-Fi hoặc dữ liệu di động rồi thử lại."
        else -> "Không thể hoàn tất thao tác. Vui lòng thử lại."
    }
}
