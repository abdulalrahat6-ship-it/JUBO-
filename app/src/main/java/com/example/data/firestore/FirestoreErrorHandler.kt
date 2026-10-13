package com.example.data.firestore

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import org.json.JSONObject

enum class OperationType {
    CREATE, READ, UPDATE, DELETE, LIST
}

object FirestoreErrorHandler {
    fun handleError(exception: Exception, operationType: OperationType, path: String) {
        val errorCode = if (exception is FirebaseFirestoreException) exception.code.name else "UNKNOWN"
        val jsonLog = JSONObject().apply {
            put("event", "firestore_error")
            put("operation", operationType.name)
            put("path", path)
            put("code", errorCode)
            put("message", exception.localizedMessage ?: "Unknown Firestore error")
        }
        Log.e("FirestoreError", jsonLog.toString(), exception)
    }
}
