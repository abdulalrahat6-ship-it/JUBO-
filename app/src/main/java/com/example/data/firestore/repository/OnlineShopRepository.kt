package com.example.data.firestore.repository

import android.content.Context
import com.example.R
import com.example.data.firestore.FirestoreErrorHandler
import com.example.data.firestore.OperationType
import com.example.data.firestore.model.FirestoreCustomer
import com.example.data.firestore.model.FirestoreOrder
import com.example.data.firestore.model.FirestoreProduct
import com.example.data.firestore.model.FirestoreStockMovement
import com.example.data.firestore.model.FirestoreUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class OnlineShopRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing online shop.")
    }

    // 1. Current User Profile & Role Synchronization
    suspend fun syncCurrentUserProfile(firebaseUser: FirebaseUser): FirestoreUser = withContext(Dispatchers.IO) {
        val uid = firebaseUser.uid
        val email = firebaseUser.email ?: ""
        val userDoc = db.collection("users").document(uid).get().await()

        val isBootstrapAdmin = email.equals("abdulalrahat6@gmail.com", ignoreCase = true)

        if (userDoc.exists()) {
            val existing = userDoc.toObject(FirestoreUser::class.java)
            if (existing != null) {
                if (isBootstrapAdmin && !existing.isAdmin) {
                    // Elevate bootstrap admin
                    db.collection("users").document(uid).update("role", "ADMIN").await()
                    db.collection("admins").document(uid).set(
                        mapOf(
                            "userId" to uid,
                            "email" to email,
                            "assignedBy" to "bootstrap",
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    return@withContext existing.copy(role = "ADMIN")
                }
                return@withContext existing
            }
        }

        // Check if there are any users yet; if first user or bootstrap admin -> ADMIN, else STAFF
        val role = if (isBootstrapAdmin) "ADMIN" else "STAFF"

        val newUser = FirestoreUser(
            id = uid,
            userId = uid,
            email = email,
            displayName = firebaseUser.displayName ?: email.substringBefore("@"),
            role = role
        )

        val payload = mapOf(
            "userId" to uid,
            "email" to email,
            "displayName" to newUser.displayName,
            "role" to role,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        db.collection("users").document(uid).set(payload).await()

        if (role == "ADMIN") {
            db.collection("admins").document(uid).set(
                mapOf(
                    "userId" to uid,
                    "email" to email,
                    "assignedBy" to "bootstrap",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        newUser
    }

    fun observeCurrentUserProfile(): Flow<FirestoreUser?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val registration = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    FirestoreErrorHandler.handleError(error, OperationType.READ, "users/$uid")
                    return@addSnapshotListener
                }
                val user = snapshot?.toObject(FirestoreUser::class.java)
                trySend(user)
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    fun observeAllUsers(): Flow<List<FirestoreUser>> = callbackFlow {
        val registration = db.collection("users").orderBy("displayName")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    FirestoreErrorHandler.handleError(error, OperationType.LIST, "users")
                    return@addSnapshotListener
                }
                val users = snapshot?.documents?.mapNotNull { it.toObject(FirestoreUser::class.java) } ?: emptyList()
                trySend(users)
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    suspend fun updateUserRole(targetUserId: String, targetEmail: String, newRole: String) = withContext(Dispatchers.IO) {
        val adminUid = requireUserId()
        db.collection("users").document(targetUserId).update(
            mapOf(
                "role" to newRole,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        if (newRole == "ADMIN") {
            db.collection("admins").document(targetUserId).set(
                mapOf(
                    "userId" to targetUserId,
                    "email" to targetEmail,
                    "assignedBy" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            db.collection("admins").document(targetUserId).delete().await()
        }
    }

    // 2. Realtime Products Catalog
    fun observeProducts(): Flow<List<FirestoreProduct>> = callbackFlow {
        val registration = db.collection("products")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    FirestoreErrorHandler.handleError(error, OperationType.LIST, "products")
                    return@addSnapshotListener
                }
                val products = snapshot?.documents?.mapNotNull { it.toObject(FirestoreProduct::class.java) } ?: emptyList()
                trySend(products)
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    suspend fun saveProduct(product: FirestoreProduct) = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val docRef = if (product.id.isNotBlank()) db.collection("products").document(product.id) else db.collection("products").document()
        val isNew = product.id.isBlank()

        val payload = mutableMapOf<String, Any>(
            "name" to product.name,
            "sku" to product.sku,
            "category" to product.category,
            "costPrice" to product.costPrice,
            "sellingPrice" to product.sellingPrice,
            "currentStock" to product.currentStock,
            "minStockAlert" to product.minStockAlert,
            "unit" to product.unit,
            "shelfLocation" to product.shelfLocation,
            "supplierName" to product.supplierName,
            "notes" to product.notes,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        if (isNew) {
            payload["createdAt"] = FieldValue.serverTimestamp()
        }

        docRef.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()

        if (isNew && product.currentStock > 0) {
            recordStockMovement(
                productId = docRef.id,
                productName = product.name,
                productSku = product.sku,
                quantityChange = product.currentStock,
                previousStock = 0,
                newStock = product.currentStock,
                type = "INITIAL_STOCK",
                reason = "Online product creation"
            )
        }
    }

    suspend fun deleteProduct(productId: String) = withContext(Dispatchers.IO) {
        db.collection("products").document(productId).delete().await()
    }

    suspend fun restockProduct(
        product: FirestoreProduct,
        addedQty: Int,
        supplier: String,
        notes: String
    ) = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val newStock = product.currentStock + addedQty
        db.collection("products").document(product.id).update(
            mapOf(
                "currentStock" to newStock,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        recordStockMovement(
            productId = product.id,
            productName = product.name,
            productSku = product.sku,
            quantityChange = addedQty,
            previousStock = product.currentStock,
            newStock = newStock,
            type = "RESTOCK",
            reason = notes.ifBlank { "Restocked inventory" },
            referenceId = supplier
        )
    }

    suspend fun adjustStock(
        product: FirestoreProduct,
        newStock: Int,
        reason: String,
        movementType: String
    ) = withContext(Dispatchers.IO) {
        val uid = requireUserId()
        val diff = newStock - product.currentStock
        db.collection("products").document(product.id).update(
            mapOf(
                "currentStock" to newStock,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        recordStockMovement(
            productId = product.id,
            productName = product.name,
            productSku = product.sku,
            quantityChange = diff,
            previousStock = product.currentStock,
            newStock = newStock,
            type = movementType,
            reason = reason
        )
    }

    // 3. Realtime Stock Movements (Audit Trail)
    fun observeStockMovements(limit: Long = 50): Flow<List<FirestoreStockMovement>> = callbackFlow {
        val registration = db.collection("stock_movements")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    FirestoreErrorHandler.handleError(error, OperationType.LIST, "stock_movements")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(FirestoreStockMovement::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    private suspend fun recordStockMovement(
        productId: String,
        productName: String,
        productSku: String,
        quantityChange: Int,
        previousStock: Int,
        newStock: Int,
        type: String,
        reason: String = "",
        referenceId: String = ""
    ) {
        val user = auth.currentUser ?: return
        val payload = mapOf(
            "productId" to productId,
            "productName" to productName,
            "productSku" to productSku,
            "quantityChange" to quantityChange,
            "previousStock" to previousStock,
            "newStock" to newStock,
            "type" to type,
            "reason" to reason,
            "referenceId" to referenceId,
            "performedBy" to user.uid,
            "performedByName" to (user.displayName ?: user.email ?: "Staff"),
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("stock_movements").add(payload).await()
    }

    // 4. Realtime Orders
    fun observeOrders(): Flow<List<FirestoreOrder>> = callbackFlow {
        val registration = db.collection("orders")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    FirestoreErrorHandler.handleError(error, OperationType.LIST, "orders")
                    return@addSnapshotListener
                }
                val orders = snapshot?.documents?.mapNotNull { it.toObject(FirestoreOrder::class.java) } ?: emptyList()
                trySend(orders)
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    suspend fun createOrder(
        order: FirestoreOrder,
        items: List<Map<String, Any>>
    ): String = withContext(Dispatchers.IO) {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated")
        val orderDoc = db.collection("orders").document()

        val payload = mapOf(
            "orderNumber" to order.orderNumber,
            "customerId" to order.customerId,
            "customerName" to order.customerName,
            "customerPhone" to order.customerPhone,
            "subtotal" to order.subtotal,
            "discount" to order.discount,
            "tax" to order.tax,
            "totalAmount" to order.totalAmount,
            "paymentMethod" to order.paymentMethod,
            "paymentStatus" to order.paymentStatus,
            "orderStatus" to order.orderStatus,
            "cashierId" to user.uid,
            "cashierName" to (user.displayName ?: user.email ?: "Cashier"),
            "notes" to order.notes,
            "items" to items,
            "timestamp" to FieldValue.serverTimestamp()
        )

        orderDoc.set(payload).await()

        // Atomically update product inventory counts and log movements
        for (item in items) {
            val productId = item["productId"] as? String ?: continue
            val qty = (item["quantity"] as? Number)?.toInt() ?: 1
            val pName = item["productName"] as? String ?: "Item"
            val pSku = item["productSku"] as? String ?: ""

            try {
                val pDoc = db.collection("products").document(productId).get().await()
                val current = (pDoc.getLong("currentStock") ?: 0L).toInt()
                val newQty = current - qty
                db.collection("products").document(productId).update(
                    mapOf(
                        "currentStock" to newQty,
                        "updatedBy" to user.uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                recordStockMovement(
                    productId = productId,
                    productName = pName,
                    productSku = pSku,
                    quantityChange = -qty,
                    previousStock = current,
                    newStock = newQty,
                    type = "SALE",
                    reason = "Customer sale",
                    referenceId = order.orderNumber
                )
            } catch (e: Exception) {
                FirestoreErrorHandler.handleError(e, OperationType.UPDATE, "products/$productId")
            }
        }

        // Update customer statistics if bound
        if (order.customerId.isNotBlank()) {
            try {
                val cDoc = db.collection("customers").document(order.customerId).get().await()
                if (cDoc.exists()) {
                    val prevOrders = (cDoc.getLong("totalOrdersCount") ?: 0L).toInt()
                    val prevSpent = cDoc.getDouble("totalSpent") ?: 0.0
                    val prevDue = cDoc.getDouble("outstandingDue") ?: 0.0
                    val addedDue = if (order.paymentStatus == "DUE") order.totalAmount else 0.0

                    db.collection("customers").document(order.customerId).update(
                        mapOf(
                            "totalOrdersCount" to prevOrders + 1,
                            "totalSpent" to prevSpent + order.totalAmount,
                            "outstandingDue" to prevDue + addedDue
                        )
                    ).await()
                }
            } catch (e: Exception) {
                FirestoreErrorHandler.handleError(e, OperationType.UPDATE, "customers/${order.customerId}")
            }
        }

        orderDoc.id
    }

    suspend fun deleteOrder(orderId: String) = withContext(Dispatchers.IO) {
        db.collection("orders").document(orderId).delete().await()
    }

    // 5. Customers
    fun observeCustomers(): Flow<List<FirestoreCustomer>> = callbackFlow {
        val registration = db.collection("customers")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    FirestoreErrorHandler.handleError(error, OperationType.LIST, "customers")
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { it.toObject(FirestoreCustomer::class.java) } ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }.flowOn(Dispatchers.IO)

    suspend fun saveCustomer(customer: FirestoreCustomer) = withContext(Dispatchers.IO) {
        val docRef = if (customer.id.isNotBlank()) db.collection("customers").document(customer.id) else db.collection("customers").document()
        val isNew = customer.id.isBlank()

        val payload = mutableMapOf<String, Any>(
            "name" to customer.name,
            "phone" to customer.phone,
            "email" to customer.email,
            "address" to customer.address,
            "totalOrdersCount" to customer.totalOrdersCount,
            "totalSpent" to customer.totalSpent,
            "outstandingDue" to customer.outstandingDue
        )
        if (isNew) {
            payload["createdAt"] = FieldValue.serverTimestamp()
        }
        docRef.set(payload, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    suspend fun recordDuePayment(customer: FirestoreCustomer, amount: Double) = withContext(Dispatchers.IO) {
        val newDue = (customer.outstandingDue - amount).coerceAtLeast(0.0)
        db.collection("customers").document(customer.id).update(
            "outstandingDue", newDue
        ).await()
    }

    suspend fun deleteCustomer(customerId: String) = withContext(Dispatchers.IO) {
        db.collection("customers").document(customerId).delete().await()
    }

    // Seed initial products in Cloud Firestore if database is empty
    suspend fun seedInitialOnlineDataIfEmpty() = withContext(Dispatchers.IO) {
        try {
            val existing = db.collection("products").limit(1).get().await()
            if (existing.isEmpty) {
                val seed = listOf(
                    FirestoreProduct(
                        name = "Premium Colombian Coffee Beans",
                        sku = "890100123456",
                        category = "Beverages",
                        costPrice = 8.50,
                        sellingPrice = 14.99,
                        currentStock = 35,
                        minStockAlert = 8,
                        unit = "pack",
                        shelfLocation = "Aisle 1 - Top Shelf",
                        supplierName = "Andean Imports Ltd",
                        notes = "100% Arabica roasted whole bean (500g)"
                    ),
                    FirestoreProduct(
                        name = "Organic Green Tea Leaves",
                        sku = "890100234567",
                        category = "Beverages",
                        costPrice = 3.20,
                        sellingPrice = 6.49,
                        currentStock = 18,
                        minStockAlert = 5,
                        unit = "box",
                        shelfLocation = "Aisle 1 - Middle",
                        supplierName = "Zen Harvest Co",
                        notes = "100 pyramid tea bags"
                    ),
                    FirestoreProduct(
                        name = "Artisan Dark Chocolate 70%",
                        sku = "890100345678",
                        category = "Snacks",
                        costPrice = 1.80,
                        sellingPrice = 3.99,
                        currentStock = 4,
                        minStockAlert = 10,
                        unit = "bar",
                        shelfLocation = "Checkout Counter A",
                        supplierName = "ChocoCraft",
                        notes = "Single-origin cocoa bar (100g)"
                    ),
                    FirestoreProduct(
                        name = "Extra Virgin Olive Oil 1L",
                        sku = "890100456789",
                        category = "Groceries",
                        costPrice = 9.00,
                        sellingPrice = 16.50,
                        currentStock = 22,
                        minStockAlert = 6,
                        unit = "bottle",
                        shelfLocation = "Aisle 3 - Oil & Vinegar",
                        supplierName = "Mediterranean Sun",
                        notes = "Cold pressed glass bottle"
                    ),
                    FirestoreProduct(
                        name = "Almond Butter Creamy 350g",
                        sku = "890100567890",
                        category = "Groceries",
                        costPrice = 4.50,
                        sellingPrice = 8.99,
                        currentStock = 2,
                        minStockAlert = 5,
                        unit = "jar",
                        shelfLocation = "Aisle 2 - Spreads",
                        supplierName = "NutriSpread",
                        notes = "No added sugar, high protein"
                    ),
                    FirestoreProduct(
                        name = "Wireless Optical Mouse",
                        sku = "890100678901",
                        category = "Electronics",
                        costPrice = 7.00,
                        sellingPrice = 18.99,
                        currentStock = 14,
                        minStockAlert = 3,
                        unit = "pcs",
                        shelfLocation = "Tech Display Stand",
                        supplierName = "Apex Gadgets",
                        notes = "2.4GHz with nano receiver"
                    ),
                    FirestoreProduct(
                        name = "USB-C Fast Charging Cable 2m",
                        sku = "890100789012",
                        category = "Electronics",
                        costPrice = 2.40,
                        sellingPrice = 9.99,
                        currentStock = 0,
                        minStockAlert = 8,
                        unit = "pcs",
                        shelfLocation = "Tech Display Stand",
                        supplierName = "Apex Gadgets",
                        notes = "Braided 60W power delivery"
                    ),
                    FirestoreProduct(
                        name = "Eco Bamboo Toothbrush (4-Pack)",
                        sku = "890100890123",
                        category = "Personal Care",
                        costPrice = 2.00,
                        sellingPrice = 5.49,
                        currentStock = 28,
                        minStockAlert = 6,
                        unit = "pack",
                        shelfLocation = "Aisle 4 - Hygiene",
                        supplierName = "Green Earth Co",
                        notes = "Biodegradable charcoal bristles"
                    )
                )

                for (p in seed) {
                    saveProduct(p)
                }

                val customers = listOf(
                    FirestoreCustomer(
                        name = "Sarah Jenkins",
                        phone = "+1 (555) 234-5678",
                        email = "sarah.j@example.com",
                        address = "42 Maple Street, Suite 3",
                        totalOrdersCount = 2,
                        totalSpent = 45.47,
                        outstandingDue = 0.0
                    ),
                    FirestoreCustomer(
                        name = "Michael Vance",
                        phone = "+1 (555) 876-5432",
                        email = "m.vance@example.com",
                        address = "108 Oakwood Ave",
                        totalOrdersCount = 1,
                        totalSpent = 24.48,
                        outstandingDue = 15.00
                    )
                )

                for (c in customers) {
                    saveCustomer(c)
                }
            }
        } catch (e: Exception) {
            FirestoreErrorHandler.handleError(e, OperationType.CREATE, "products/seed")
        }
    }
}
