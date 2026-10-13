package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firestore.model.FirestoreCustomer
import com.example.data.firestore.model.FirestoreOrder
import com.example.data.firestore.model.FirestoreProduct
import com.example.data.firestore.model.FirestoreStockMovement
import com.example.data.firestore.model.FirestoreUser
import com.example.data.firestore.repository.OnlineShopRepository
import com.example.data.util.BarcodeGenerator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StockFilter {
    ALL, IN_STOCK, LOW_STOCK, OUT_OF_STOCK
}

data class OnlineCartItem(
    val product: FirestoreProduct,
    val quantity: Int
) {
    val totalPrice: Double
        get() = product.sellingPrice * quantity
}

data class ShopUiState(
    val currentUser: FirestoreUser? = null,
    val teamMembers: List<FirestoreUser> = emptyList(),
    val products: List<FirestoreProduct> = emptyList(),
    val filteredProducts: List<FirestoreProduct> = emptyList(),
    val lowStockProducts: List<FirestoreProduct> = emptyList(),
    val categories: List<String> = emptyList(),
    val selectedCategory: String = "All",
    val selectedStockFilter: StockFilter = StockFilter.ALL,
    val searchQuery: String = "",
    val customers: List<FirestoreCustomer> = emptyList(),
    val orders: List<FirestoreOrder> = emptyList(),
    val recentMovements: List<FirestoreStockMovement> = emptyList(),
    val cartItems: List<OnlineCartItem> = emptyList(),
    val selectedCustomer: FirestoreCustomer? = null,
    val paymentMethod: String = "CASH",
    val paymentStatus: String = "PAID",
    val discountAmount: Double = 0.0,
    val orderNotes: String = "",
    val isLoading: Boolean = false,
    val scannedProduct: FirestoreProduct? = null,
    val isScannerActive: Boolean = false,
    val activeBarcodeProduct: FirestoreProduct? = null,
    val activeRestockProduct: FirestoreProduct? = null,
    val activeAdjustProduct: FirestoreProduct? = null,
    val createdOrder: FirestoreOrder? = null
) {
    val isAdmin: Boolean
        get() = currentUser?.isAdmin == true
}

class ShopViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OnlineShopRepository(application)
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(ShopUiState())
    val uiState: StateFlow<ShopUiState> = _uiState.asStateFlow()

    private val _messageEvents = MutableSharedFlow<String>()
    val messageEvents: SharedFlow<String> = _messageEvents.asSharedFlow()

    init {
        // If already logged in, init immediately
        auth.currentUser?.let { user ->
            initializeUserSession(user)
        }
    }

    fun initializeUserSession(firebaseUser: FirebaseUser) {
        viewModelScope.launch {
            try {
                val profile = repository.syncCurrentUserProfile(firebaseUser)
                _uiState.update { it.copy(currentUser = profile) }
                repository.seedInitialOnlineDataIfEmpty()

                // Start observing realtime streams
                launch {
                    repository.observeCurrentUserProfile().collect { user ->
                        if (user != null) {
                            _uiState.update { it.copy(currentUser = user) }
                        }
                    }
                }

                launch {
                    repository.observeAllUsers().collect { team ->
                        _uiState.update { it.copy(teamMembers = team) }
                    }
                }

                launch {
                    repository.observeProducts().collect { products ->
                        val categories = listOf("All") + products.map { it.category }.distinct().sorted()
                        val lowStock = products.filter { it.isLowStock || it.isOutOfStock }
                        _uiState.update { current ->
                            current.copy(
                                products = products,
                                filteredProducts = applyFilters(products, current.selectedCategory, current.selectedStockFilter, current.searchQuery),
                                lowStockProducts = lowStock,
                                categories = categories
                            )
                        }
                    }
                }

                launch {
                    repository.observeOrders().collect { orders ->
                        _uiState.update { it.copy(orders = orders) }
                    }
                }

                launch {
                    repository.observeCustomers().collect { customers ->
                        _uiState.update { it.copy(customers = customers) }
                    }
                }

                launch {
                    repository.observeStockMovements().collect { movements ->
                        _uiState.update { it.copy(recentMovements = movements) }
                    }
                }

                _messageEvents.emit("Welcome, ${profile.displayName} (${profile.role})")
            } catch (e: Exception) {
                _messageEvents.emit("Error initializing session: ${e.message}")
            }
        }
    }

    private fun applyFilters(
        products: List<FirestoreProduct>,
        category: String,
        filter: StockFilter,
        query: String
    ): List<FirestoreProduct> {
        return products.filter { product ->
            val matchesCategory = (category == "All" || product.category.equals(category, ignoreCase = true))
            val matchesQuery = query.isBlank() ||
                    product.name.contains(query, ignoreCase = true) ||
                    product.sku.contains(query, ignoreCase = true) ||
                    product.shelfLocation.contains(query, ignoreCase = true)
            val matchesStock = when (filter) {
                StockFilter.ALL -> true
                StockFilter.IN_STOCK -> product.currentStock > 0
                StockFilter.LOW_STOCK -> product.isLowStock
                StockFilter.OUT_OF_STOCK -> product.isOutOfStock
            }
            matchesCategory && matchesQuery && matchesStock
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            current.copy(
                searchQuery = query,
                filteredProducts = applyFilters(current.products, current.selectedCategory, current.selectedStockFilter, query)
            )
        }
    }

    fun setSelectedCategory(category: String) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = category,
                filteredProducts = applyFilters(current.products, category, current.selectedStockFilter, current.searchQuery)
            )
        }
    }

    fun setSelectedStockFilter(filter: StockFilter) {
        _uiState.update { current ->
            current.copy(
                selectedStockFilter = filter,
                filteredProducts = applyFilters(current.products, current.selectedCategory, filter, current.searchQuery)
            )
        }
    }

    // Role Management (Admin only)
    fun updateUserRole(targetUserId: String, targetEmail: String, newRole: String) {
        if (!_uiState.value.isAdmin) {
            viewModelScope.launch { _messageEvents.emit("Only Admins can change user roles.") }
            return
        }
        viewModelScope.launch {
            try {
                repository.updateUserRole(targetUserId, targetEmail, newRole)
                _messageEvents.emit("Updated $targetEmail to $newRole")
            } catch (e: Exception) {
                _messageEvents.emit("Failed to update role: ${e.message}")
            }
        }
    }

    // Product actions
    fun saveProduct(product: FirestoreProduct) {
        viewModelScope.launch {
            try {
                repository.saveProduct(product)
                _messageEvents.emit("Product '${product.name}' saved to cloud.")
            } catch (e: Exception) {
                _messageEvents.emit("Error saving product: ${e.message}")
            }
        }
    }

    fun deleteProduct(productId: String) {
        if (!_uiState.value.isAdmin) {
            viewModelScope.launch { _messageEvents.emit("Only Admins can delete products from the shop.") }
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteProduct(productId)
                _messageEvents.emit("Product removed from online inventory.")
            } catch (e: Exception) {
                _messageEvents.emit("Error deleting product: ${e.message}")
            }
        }
    }

    fun restockProduct(product: FirestoreProduct, addedQty: Int, supplier: String, notes: String) {
        viewModelScope.launch {
            try {
                repository.restockProduct(product, addedQty, supplier, notes)
                _uiState.update { it.copy(activeRestockProduct = null) }
                _messageEvents.emit("Restocked +$addedQty units in cloud inventory.")
            } catch (e: Exception) {
                _messageEvents.emit("Error restocking: ${e.message}")
            }
        }
    }

    fun adjustStock(product: FirestoreProduct, newStock: Int, reason: String, movementType: String) {
        viewModelScope.launch {
            try {
                repository.adjustStock(product, newStock, reason, movementType)
                _uiState.update { it.copy(activeAdjustProduct = null) }
                _messageEvents.emit("Stock adjusted to $newStock units.")
            } catch (e: Exception) {
                _messageEvents.emit("Error adjusting stock: ${e.message}")
            }
        }
    }

    fun showBarcode(product: FirestoreProduct?) {
        _uiState.update { it.copy(activeBarcodeProduct = product) }
    }

    fun openRestockDialog(product: FirestoreProduct?) {
        _uiState.update { it.copy(activeRestockProduct = product) }
    }

    fun openAdjustDialog(product: FirestoreProduct?) {
        _uiState.update { it.copy(activeAdjustProduct = product) }
    }

    // Barcode Scanning Actions
    fun openScanner() {
        _uiState.update { it.copy(isScannerActive = true, scannedProduct = null) }
    }

    fun closeScanner() {
        _uiState.update { it.copy(isScannerActive = false, scannedProduct = null) }
    }

    fun handleScannedBarcode(rawBarcode: String, onProductNotFound: ((String) -> Unit)? = null) {
        val trimmed = rawBarcode.trim()
        val foundProduct = _uiState.value.products.firstOrNull {
            it.sku.equals(trimmed, ignoreCase = true)
        }
        if (foundProduct != null) {
            _uiState.update { it.copy(scannedProduct = foundProduct) }
            viewModelScope.launch {
                _messageEvents.emit("Found: ${foundProduct.name} (${foundProduct.currentStock} in stock)")
            }
        } else {
            viewModelScope.launch {
                _messageEvents.emit("No product with barcode: $trimmed")
            }
            onProductNotFound?.invoke(trimmed)
        }
    }

    // Cart and Order actions
    fun addToCart(product: FirestoreProduct, quantity: Int = 1) {
        _uiState.update { current ->
            val existingIndex = current.cartItems.indexOfFirst { it.product.id == product.id }
            val updated = current.cartItems.toMutableList()
            if (existingIndex >= 0) {
                val currentItem = updated[existingIndex]
                val newQty = (currentItem.quantity + quantity).coerceAtMost(product.currentStock.coerceAtLeast(0))
                if (newQty > currentItem.quantity) {
                    updated[existingIndex] = currentItem.copy(quantity = newQty)
                }
            } else {
                if (product.currentStock > 0) {
                    updated.add(OnlineCartItem(product = product, quantity = quantity.coerceAtMost(product.currentStock)))
                }
            }
            current.copy(cartItems = updated)
        }
    }

    fun updateCartQuantity(productId: String, delta: Int) {
        _uiState.update { current ->
            val updated = current.cartItems.mapNotNull { item ->
                if (item.product.id == productId) {
                    val newQty = item.quantity + delta
                    if (newQty <= 0) {
                        null
                    } else {
                        item.copy(quantity = newQty.coerceAtMost(item.product.currentStock))
                    }
                } else {
                    item
                }
            }
            current.copy(cartItems = updated)
        }
    }

    fun removeFromCart(productId: String) {
        _uiState.update { current ->
            current.copy(cartItems = current.cartItems.filterNot { it.product.id == productId })
        }
    }

    fun clearCart() {
        _uiState.update { current ->
            current.copy(
                cartItems = emptyList(),
                selectedCustomer = null,
                discountAmount = 0.0,
                orderNotes = ""
            )
        }
    }

    fun setCustomerForOrder(customer: FirestoreCustomer?) {
        _uiState.update { it.copy(selectedCustomer = customer) }
    }

    fun setPaymentMethod(method: String) {
        _uiState.update { it.copy(paymentMethod = method) }
    }

    fun setPaymentStatus(status: String) {
        _uiState.update { it.copy(paymentStatus = status) }
    }

    fun setDiscount(amount: Double) {
        _uiState.update { it.copy(discountAmount = amount) }
    }

    fun setOrderNotes(notes: String) {
        _uiState.update { it.copy(orderNotes = notes) }
    }

    fun completeOrder(
        walkInName: String = "",
        walkInPhone: String = "",
        onSuccess: (FirestoreOrder) -> Unit
    ) {
        val state = _uiState.value
        if (state.cartItems.isEmpty()) return

        viewModelScope.launch {
            try {
                val subtotal = state.cartItems.sumOf { it.totalPrice }
                val total = (subtotal - state.discountAmount).coerceAtLeast(0.0)
                val orderNumber = "ORD-${System.currentTimeMillis() % 1000000}"

                val customerName = state.selectedCustomer?.name ?: walkInName.ifBlank { "Walk-in Customer" }
                val customerPhone = state.selectedCustomer?.phone ?: walkInPhone

                val itemsPayload = state.cartItems.map {
                    mapOf(
                        "productId" to it.product.id,
                        "productName" to it.product.name,
                        "productSku" to it.product.sku,
                        "quantity" to it.quantity,
                        "unitPrice" to it.product.sellingPrice,
                        "totalPrice" to it.totalPrice
                    )
                }

                val order = FirestoreOrder(
                    orderNumber = orderNumber,
                    customerId = state.selectedCustomer?.id ?: "",
                    customerName = customerName,
                    customerPhone = customerPhone,
                    subtotal = subtotal,
                    discount = state.discountAmount,
                    tax = 0.0,
                    totalAmount = total,
                    paymentMethod = state.paymentMethod,
                    paymentStatus = state.paymentStatus,
                    orderStatus = "COMPLETED",
                    notes = state.orderNotes,
                    items = itemsPayload
                )

                val orderId = repository.createOrder(order, itemsPayload)
                val created = order.copy(id = orderId)
                _uiState.update {
                    it.copy(
                        createdOrder = created,
                        cartItems = emptyList(),
                        selectedCustomer = null,
                        discountAmount = 0.0,
                        orderNotes = ""
                    )
                }
                _messageEvents.emit("Order $orderNumber completed & synced online!")
                onSuccess(created)
            } catch (e: Exception) {
                _messageEvents.emit("Failed to create order: ${e.message}")
            }
        }
    }

    fun setCreatedOrder(order: FirestoreOrder?) {
        _uiState.update { it.copy(createdOrder = order) }
    }

    fun deleteOrder(orderId: String) {
        if (!_uiState.value.isAdmin) {
            viewModelScope.launch { _messageEvents.emit("Only Admins can cancel or delete orders.") }
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteOrder(orderId)
                _messageEvents.emit("Order deleted.")
            } catch (e: Exception) {
                _messageEvents.emit("Error deleting order: ${e.message}")
            }
        }
    }

    // Customer Actions
    fun saveCustomer(customer: FirestoreCustomer) {
        viewModelScope.launch {
            try {
                repository.saveCustomer(customer)
                _messageEvents.emit("Customer '${customer.name}' saved to cloud.")
            } catch (e: Exception) {
                _messageEvents.emit("Error saving customer: ${e.message}")
            }
        }
    }

    fun recordDuePayment(customer: FirestoreCustomer, amountPaid: Double) {
        viewModelScope.launch {
            try {
                repository.recordDuePayment(customer, amountPaid)
                _messageEvents.emit("Recorded payment of $${String.format("%.2f", amountPaid)}.")
            } catch (e: Exception) {
                _messageEvents.emit("Error recording payment: ${e.message}")
            }
        }
    }

    fun deleteCustomer(customerId: String) {
        if (!_uiState.value.isAdmin) {
            viewModelScope.launch { _messageEvents.emit("Only Admins can remove customer profiles.") }
            return
        }
        viewModelScope.launch {
            try {
                repository.deleteCustomer(customerId)
                _messageEvents.emit("Customer removed.")
            } catch (e: Exception) {
                _messageEvents.emit("Error removing customer: ${e.message}")
            }
        }
    }

    fun generateNewBarcode(prefix: String = "890"): String {
        return BarcodeGenerator.generateEan13(prefix)
    }
}
