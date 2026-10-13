package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderNumber: String, // e.g. "ORD-10024"
    val customerId: Long? = null,
    val customerName: String,
    val customerPhone: String = "",
    val subtotal: Double,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val totalAmount: Double,
    val paymentMethod: String = "CASH", // "CASH", "CARD", "MOBILE_PAY", "CREDIT_DUE"
    val paymentStatus: String = "PAID", // "PAID", "PARTIALLY_PAID", "DUE"
    val orderStatus: String = "COMPLETED", // "COMPLETED", "PENDING", "CANCELLED"
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
