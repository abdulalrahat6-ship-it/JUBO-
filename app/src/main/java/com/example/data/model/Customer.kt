package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String,
    val email: String = "",
    val address: String = "",
    val totalOrdersCount: Int = 0,
    val totalSpent: Double = 0.0,
    val outstandingDue: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)
