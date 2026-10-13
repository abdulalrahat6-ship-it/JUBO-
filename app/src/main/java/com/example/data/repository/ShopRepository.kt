package com.example.data.repository

import com.example.data.dao.CustomerDao
import com.example.data.dao.OrderDao
import com.example.data.dao.ProductDao
import com.example.data.dao.StockMovementDao
import com.example.data.model.Customer
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.OrderWithItems
import com.example.data.model.Product
import com.example.data.model.StockMovement
import com.example.data.util.BarcodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ShopRepository(
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao,
    private val customerDao: CustomerDao,
    private val orderDao: OrderDao
) {
    // Products
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()
    val lowStockProducts: Flow<List<Product>> = productDao.getLowStockProducts()

    fun getProductById(id: Long): Flow<Product?> = productDao.getProductById(id)
    suspend fun getProductByIdSync(id: Long): Product? = productDao.getProductByIdSync(id)
    fun getProductBySku(sku: String): Flow<Product?> = productDao.getProductBySku(sku)
    suspend fun getProductBySkuSync(sku: String): Product? = productDao.getProductBySkuSync(sku)

    suspend fun saveProduct(product: Product): Long = withContext(Dispatchers.IO) {
        if (product.id == 0L) {
            val newId = productDao.insertProduct(product)
            stockMovementDao.insertMovement(
                StockMovement(
                    productId = newId,
                    productName = product.name,
                    productSku = product.sku,
                    quantityChange = product.currentStock,
                    previousStock = 0,
                    newStock = product.currentStock,
                    type = "INITIAL_STOCK",
                    reason = "Initial inventory registration"
                )
            )
            newId
        } else {
            val existing = productDao.getProductByIdSync(product.id)
            if (existing != null && existing.currentStock != product.currentStock) {
                val diff = product.currentStock - existing.currentStock
                stockMovementDao.insertMovement(
                    StockMovement(
                        productId = product.id,
                        productName = product.name,
                        productSku = product.sku,
                        quantityChange = diff,
                        previousStock = existing.currentStock,
                        newStock = product.currentStock,
                        type = "ADJUSTMENT",
                        reason = "Manual stock update in product details"
                    )
                )
            }
            productDao.updateProduct(product.copy(updatedAt = System.currentTimeMillis()))
            product.id
        }
    }

    suspend fun deleteProduct(product: Product) = withContext(Dispatchers.IO) {
        productDao.deleteProduct(product)
    }

    suspend fun restockProduct(productId: Long, addedQuantity: Int, supplier: String, notes: String) =
        withContext(Dispatchers.IO) {
            val product = productDao.getProductByIdSync(productId) ?: return@withContext
            val newStock = product.currentStock + addedQuantity
            val now = System.currentTimeMillis()
            productDao.updateStock(productId, newStock, now)
            stockMovementDao.insertMovement(
                StockMovement(
                    productId = productId,
                    productName = product.name,
                    productSku = product.sku,
                    quantityChange = addedQuantity,
                    previousStock = product.currentStock,
                    newStock = newStock,
                    type = "RESTOCK",
                    reason = if (notes.isNotBlank()) notes else "Restocked inventory",
                    referenceId = supplier
                )
            )
        }

    suspend fun adjustStock(productId: Long, newStock: Int, reason: String, movementType: String) =
        withContext(Dispatchers.IO) {
            val product = productDao.getProductByIdSync(productId) ?: return@withContext
            val diff = newStock - product.currentStock
            val now = System.currentTimeMillis()
            productDao.updateStock(productId, newStock, now)
            stockMovementDao.insertMovement(
                StockMovement(
                    productId = productId,
                    productName = product.name,
                    productSku = product.sku,
                    quantityChange = diff,
                    previousStock = product.currentStock,
                    newStock = newStock,
                    type = movementType,
                    reason = reason
                )
            )
        }

    // Stock Movements
    val allStockMovements: Flow<List<StockMovement>> = stockMovementDao.getAllMovements()
    fun getRecentStockMovements(limit: Int = 20): Flow<List<StockMovement>> = stockMovementDao.getRecentMovements(limit)
    fun getMovementsForProduct(productId: Long): Flow<List<StockMovement>> = stockMovementDao.getMovementsForProduct(productId)

    // Customers
    val allCustomers: Flow<List<Customer>> = customerDao.getAllCustomers()
    fun getCustomerById(id: Long): Flow<Customer?> = customerDao.getCustomerById(id)
    suspend fun saveCustomer(customer: Customer): Long = withContext(Dispatchers.IO) {
        if (customer.id == 0L) {
            customerDao.insertCustomer(customer)
        } else {
            customerDao.updateCustomer(customer)
            customer.id
        }
    }
    suspend fun deleteCustomer(customer: Customer) = withContext(Dispatchers.IO) {
        customerDao.deleteCustomer(customer)
    }
    suspend fun updateCustomerDue(customerId: Long, newDue: Double) = withContext(Dispatchers.IO) {
        customerDao.updateDue(customerId, newDue)
    }

    // Orders
    val allOrders: Flow<List<Order>> = orderDao.getAllOrders()
    val allOrdersWithItems: Flow<List<OrderWithItems>> = orderDao.getAllOrdersWithItems()
    fun getOrderWithItems(orderId: Long): Flow<OrderWithItems?> = orderDao.getOrderWithItems(orderId)
    fun getOrdersForCustomer(customerId: Long): Flow<List<OrderWithItems>> = orderDao.getOrdersForCustomer(customerId)

    suspend fun createOrder(
        order: Order,
        items: List<OrderItem>
    ): Long = withContext(Dispatchers.IO) {
        val orderId = orderDao.insertOrder(order)
        val itemsWithOrderId = items.map { it.copy(orderId = orderId) }
        orderDao.insertOrderItems(itemsWithOrderId)

        // Deduct inventory and record stock movements
        val now = System.currentTimeMillis()
        for (item in itemsWithOrderId) {
            val product = productDao.getProductByIdSync(item.productId)
            if (product != null) {
                val newStock = product.currentStock - item.quantity
                productDao.updateStock(product.id, newStock, now)
                stockMovementDao.insertMovement(
                    StockMovement(
                        productId = product.id,
                        productName = product.name,
                        productSku = product.sku,
                        quantityChange = -item.quantity,
                        previousStock = product.currentStock,
                        newStock = newStock,
                        type = "SALE",
                        reason = "Customer purchase",
                        referenceId = order.orderNumber
                    )
                )
            }
        }

        // Update customer statistics if associated
        if (order.customerId != null && order.customerId > 0) {
            val dueAddition = if (order.paymentStatus == "DUE") order.totalAmount else 0.0
            customerDao.recordCustomerOrder(order.customerId, order.totalAmount, dueAddition)
        }

        orderId
    }

    suspend fun updateOrderStatus(order: Order, newStatus: String, newPaymentStatus: String) =
        withContext(Dispatchers.IO) {
            orderDao.updateOrder(order.copy(orderStatus = newStatus, paymentStatus = newPaymentStatus))
        }

    suspend fun deleteOrder(order: Order) = withContext(Dispatchers.IO) {
        orderDao.deleteOrder(order)
    }

    // Prepopulate rich default data on first launch
    suspend fun prepopulateIfEmpty() = withContext(Dispatchers.IO) {
        if (productDao.getCount() == 0) {
            val initialProducts = listOf(
                Product(
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
                Product(
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
                Product(
                    name = "Artisan Dark Chocolate 70%",
                    sku = "890100345678",
                    category = "Snacks",
                    costPrice = 1.80,
                    sellingPrice = 3.99,
                    currentStock = 4,
                    minStockAlert = 10, // Triggers Low Stock alert!
                    unit = "bar",
                    shelfLocation = "Checkout Counter A",
                    supplierName = "ChocoCraft",
                    notes = "Single-origin cocoa bar (100g)"
                ),
                Product(
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
                Product(
                    name = "Almond Butter Creamy 350g",
                    sku = "890100567890",
                    category = "Groceries",
                    costPrice = 4.50,
                    sellingPrice = 8.99,
                    currentStock = 2,
                    minStockAlert = 5, // Triggers Low Stock alert!
                    unit = "jar",
                    shelfLocation = "Aisle 2 - Spreads",
                    supplierName = "NutriSpread",
                    notes = "No added sugar, high protein"
                ),
                Product(
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
                Product(
                    name = "USB-C Fast Charging Cable 2m",
                    sku = "890100789012",
                    category = "Electronics",
                    costPrice = 2.40,
                    sellingPrice = 9.99,
                    currentStock = 0, // Triggers Out of Stock alert!
                    minStockAlert = 8,
                    unit = "pcs",
                    shelfLocation = "Tech Display Stand",
                    supplierName = "Apex Gadgets",
                    notes = "Braided 60W power delivery"
                ),
                Product(
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
                ),
                Product(
                    name = "Hardcover Dotted Journal A5",
                    sku = "890100901234",
                    category = "Stationery",
                    costPrice = 3.80,
                    sellingPrice = 8.50,
                    currentStock = 19,
                    minStockAlert = 5,
                    unit = "pcs",
                    shelfLocation = "Stationery Shelf B",
                    supplierName = "PaperWorks Studio",
                    notes = "160gsm bleed-proof paper"
                )
            )

            productDao.insertAll(initialProducts)

            val initialCustomers = listOf(
                Customer(
                    name = "Sarah Jenkins",
                    phone = "+1 (555) 234-5678",
                    email = "sarah.j@example.com",
                    address = "42 Maple Street, Suite 3",
                    totalOrdersCount = 3,
                    totalSpent = 68.45,
                    outstandingDue = 0.0
                ),
                Customer(
                    name = "Michael Vance",
                    phone = "+1 (555) 876-5432",
                    email = "m.vance@example.com",
                    address = "108 Oakwood Ave",
                    totalOrdersCount = 2,
                    totalSpent = 44.98,
                    outstandingDue = 15.00
                ),
                Customer(
                    name = "Elena Rostova",
                    phone = "+1 (555) 345-9876",
                    email = "elena.r@example.com",
                    address = "77 Willow Creek Dr",
                    totalOrdersCount = 5,
                    totalSpent = 129.50,
                    outstandingDue = 0.0
                )
            )

            customerDao.insertAll(initialCustomers)

            // Create 2 starter completed sample orders
            val sampleOrder1 = Order(
                orderNumber = "ORD-202610-101",
                customerId = 1L,
                customerName = "Sarah Jenkins",
                customerPhone = "+1 (555) 234-5678",
                subtotal = 30.48,
                discount = 2.00,
                tax = 2.00,
                totalAmount = 30.48,
                paymentMethod = "CASH",
                paymentStatus = "PAID",
                orderStatus = "COMPLETED",
                notes = "Walk-in pickup",
                timestamp = System.currentTimeMillis() - 3600000 * 4
            )
            val oId1 = orderDao.insertOrder(sampleOrder1)
            orderDao.insertOrderItems(
                listOf(
                    OrderItem(
                        orderId = oId1,
                        productId = 1L,
                        productName = "Premium Colombian Coffee Beans",
                        productSku = "890100123456",
                        quantity = 1,
                        unitPrice = 14.99,
                        totalPrice = 14.99
                    ),
                    OrderItem(
                        orderId = oId1,
                        productId = 4L,
                        productName = "Extra Virgin Olive Oil 1L",
                        productSku = "890100456789",
                        quantity = 1,
                        unitPrice = 16.50,
                        totalPrice = 16.50
                    )
                )
            )

            val sampleOrder2 = Order(
                orderNumber = "ORD-202610-102",
                customerId = 2L,
                customerName = "Michael Vance",
                customerPhone = "+1 (555) 876-5432",
                subtotal = 27.98,
                discount = 0.0,
                tax = 2.00,
                totalAmount = 29.98,
                paymentMethod = "CARD",
                paymentStatus = "PAID",
                orderStatus = "COMPLETED",
                notes = "Contactless tap",
                timestamp = System.currentTimeMillis() - 3600000 * 24
            )
            val oId2 = orderDao.insertOrder(sampleOrder2)
            orderDao.insertOrderItems(
                listOf(
                    OrderItem(
                        orderId = oId2,
                        productId = 6L,
                        productName = "Wireless Optical Mouse",
                        productSku = "890100678901",
                        quantity = 1,
                        unitPrice = 18.99,
                        totalPrice = 18.99
                    ),
                    OrderItem(
                        orderId = oId2,
                        productId = 8L,
                        productName = "Eco Bamboo Toothbrush (4-Pack)",
                        productSku = "890100890123",
                        quantity = 1,
                        unitPrice = 5.49,
                        totalPrice = 5.49
                    )
                )
            )
        }
    }
}
