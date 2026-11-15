package com.example.ui.user

data class UserBadge(
    val id: String,
    val name: String,
    val category: String,
    val description: String,
    val icon: String,
    val cryptographicProof: String,
    val earned: Boolean,
    val earnedTimestamp: Long? = null
)
