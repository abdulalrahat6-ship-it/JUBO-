package com.example.data.firestore.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole {
    ADMIN,
    STAFF
}

@IgnoreExtraProperties
data class FirestoreUser(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "STAFF",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isAdmin: Boolean
        get() = role.equals("ADMIN", ignoreCase = true)
}

@IgnoreExtraProperties
data class FirestoreProduct(
    @DocumentId val id: String = "",
    val name: String = "",
    val sku: String = "",
    val category: String = "General",
    val costPrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val currentStock: Int = 0,
    val minStockAlert: Int = 5,
    val unit: String = "pcs",
    val shelfLocation: String = "",
    val supplierName: String = "",
    val notes: String = "",
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
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

@IgnoreExtraProperties
data class FirestoreStockMovement(
    @DocumentId val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val productSku: String = "",
    val quantityChange: Int = 0,
    val previousStock: Int = 0,
    val newStock: Int = 0,
    val type: String = "ADJUSTMENT",
    val reason: String = "",
    val referenceId: String = "",
    val performedBy: String = "",
    val performedByName: String = "",
    @ServerTimestamp val timestamp: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreCustomer(
    @DocumentId val id: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val totalOrdersCount: Int = 0,
    val totalSpent: Double = 0.0,
    val outstandingDue: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreOrder(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val customerId: String = "",
    val customerName: String = "Walk-in Customer",
    val customerPhone: String = "",
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paymentMethod: String = "CASH",
    val paymentStatus: String = "PAID",
    val orderStatus: String = "COMPLETED",
    val cashierId: String = "",
    val cashierName: String = "",
    val notes: String = "",
    val items: List<Map<String, Any>> = emptyList(),
    @ServerTimestamp val timestamp: Timestamp? = null
)
