package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_movements")
data class StockMovement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val productName: String,
    val productSku: String,
    val quantityChange: Int, // Positive for addition, negative for deduction
    val previousStock: Int,
    val newStock: Int,
    val type: String, // "RESTOCK", "SALE", "ADJUSTMENT", "DAMAGE_LOSS", "RETURN"
    val reason: String = "",
    val referenceId: String = "", // e.g. Order # or Supplier invoice #
    val timestamp: Long = System.currentTimeMillis()
)
