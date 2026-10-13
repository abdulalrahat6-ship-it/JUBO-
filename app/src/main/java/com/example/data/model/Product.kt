package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sku: String, // Barcode / SKU
    val category: String,
    val costPrice: Double,
    val sellingPrice: Double,
    val currentStock: Int,
    val minStockAlert: Int = 5,
    val unit: String = "pcs",
    val shelfLocation: String = "",
    val supplierName: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isLowStock: Boolean
        get() = currentStock in 1..minStockAlert

    val isOutOfStock: Boolean
        get() = currentStock <= 0

    val profitMargin: Double
        get() = if (sellingPrice > 0) ((sellingPrice - costPrice) / sellingPrice) * 100 else 0.0

    val inventoryCostValue: Double
        get() = costPrice * currentStock.coerceAtLeast(0)

    val inventoryRetailValue: Double
        get() = sellingPrice * currentStock.coerceAtLeast(0)
}
