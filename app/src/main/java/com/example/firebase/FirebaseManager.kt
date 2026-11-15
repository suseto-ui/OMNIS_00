package com.example.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object FirebaseManager {
    @Volatile
    private var db: FirebaseFirestore? = null

    fun getFirestore(context: Context): FirebaseFirestore {
        return db ?: synchronized(this) {
            db ?: run {
                val databaseId = context.getString(R.string.firestore_database_id)
                FirebaseFirestore.getInstance(databaseId).also { db = it }
            }
        }
    }

    fun getAuth(): FirebaseAuth {
        return Firebase.auth
    }
}
