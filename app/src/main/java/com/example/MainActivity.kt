package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firestore.model.FirestoreCustomer
import com.example.data.firestore.model.FirestoreOrder
import com.example.data.firestore.model.FirestoreProduct
import com.example.ui.components.AdjustStockDialog
import com.example.ui.components.BarcodeScannerModal
import com.example.ui.components.ProductBarcodeModal
import com.example.ui.components.RestockDialog
import com.example.ui.screens.AddEditProductScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.NewOrderScreen
import com.example.ui.screens.OrderReceiptScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.StockAuditScreen
import com.example.ui.screens.TeamAccessScreen
import com.example.ui.screens.performSignOut
import com.example.ui.theme.ShopTrackTheme
import com.example.ui.viewmodel.ShopViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.collectLatest

enum class ShopScreen {
    DASHBOARD,
    INVENTORY,
    ORDERS,
    CUSTOMERS,
    AUDIT,
    TEAM_ACCESS,
    ADD_EDIT_PRODUCT,
    NEW_ORDER,
    ORDER_RECEIPT
}

class MainActivity : ComponentActivity() {
    private val viewModel: ShopViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShopTrackTheme {
                val auth = remember { FirebaseAuth.getInstance() }
                var currentUser by remember { mutableStateOf(auth.currentUser) }

                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                        val user = firebaseAuth.currentUser
                        currentUser = user
                        if (user != null) {
                            viewModel.initializeUserSession(user)
                        }
                    }
                    auth.addAuthStateListener(listener)
                    onDispose {
                        auth.removeAuthStateListener(listener)
                    }
                }

                if (currentUser == null) {
                    AuthScreen(
                        onAuthSuccess = { user ->
                            currentUser = user
                            viewModel.initializeUserSession(user)
                        }
                    )
                } else {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: ShopViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var currentScreen by remember { mutableStateOf(ShopScreen.DASHBOARD) }
    var editingProduct by remember { mutableStateOf<FirestoreProduct?>(null) }
    var activeReceiptOrder by remember { mutableStateOf<FirestoreOrder?>(null) }
    var pendingScannedSkuForNewProduct by remember { mutableStateOf<String?>(null) }

    // Listen for snackbar notifications
    LaunchedEffect(Unit) {
        viewModel.messageEvents.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Handle back button on sub-screens
    BackHandler(
        enabled = currentScreen in listOf(
            ShopScreen.ADD_EDIT_PRODUCT,
            ShopScreen.NEW_ORDER,
            ShopScreen.ORDER_RECEIPT,
            ShopScreen.TEAM_ACCESS
        )
    ) {
        when (currentScreen) {
            ShopScreen.ORDER_RECEIPT -> currentScreen = ShopScreen.ORDERS
            ShopScreen.ADD_EDIT_PRODUCT -> currentScreen = ShopScreen.INVENTORY
            ShopScreen.NEW_ORDER -> currentScreen = ShopScreen.DASHBOARD
            ShopScreen.TEAM_ACCESS -> currentScreen = ShopScreen.DASHBOARD
            else -> currentScreen = ShopScreen.DASHBOARD
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (currentScreen in listOf(
                    ShopScreen.DASHBOARD,
                    ShopScreen.INVENTORY,
                    ShopScreen.ORDERS,
                    ShopScreen.CUSTOMERS,
                    ShopScreen.AUDIT,
                    ShopScreen.TEAM_ACCESS
                )
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when (currentScreen) {
                                    ShopScreen.DASHBOARD -> "ShopTrack"
                                    ShopScreen.INVENTORY -> "Inventory & Stock"
                                    ShopScreen.ORDERS -> "Customer Orders"
                                    ShopScreen.CUSTOMERS -> "Customer Directory"
                                    ShopScreen.AUDIT -> "Stock Movement Audit"
                                    ShopScreen.TEAM_ACCESS -> "Team & User Access"
                                    else -> "ShopTrack"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                            val userRoleInfo = state.currentUser?.let { "• ${it.displayName} (${it.role})" } ?: ""
                            Text(
                                text = "Online Cloud POS $userRoleInfo",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.openScanner() },
                            modifier = Modifier.testTag("appbar_scanner_button")
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = "Scan Barcode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                currentScreen = if (currentScreen == ShopScreen.TEAM_ACCESS) ShopScreen.DASHBOARD else ShopScreen.TEAM_ACCESS
                            },
                            modifier = Modifier.testTag("appbar_team_button")
                        ) {
                            Icon(
                                if (state.isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.People,
                                contentDescription = "User Access & Roles",
                                tint = if (state.isAdmin) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (currentScreen in listOf(
                    ShopScreen.DASHBOARD,
                    ShopScreen.INVENTORY,
                    ShopScreen.ORDERS,
                    ShopScreen.CUSTOMERS,
                    ShopScreen.AUDIT
                )
            ) {
                NavigationBar(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("main_navigation_bar"),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        selected = currentScreen == ShopScreen.DASHBOARD,
                        onClick = { currentScreen = ShopScreen.DASHBOARD },
                        icon = { Icon(Icons.Default.Store, contentDescription = "Overview") },
                        label = { Text("Overview") },
                        modifier = Modifier.testTag("nav_dashboard")
                    )
                    NavigationBarItem(
                        selected = currentScreen == ShopScreen.INVENTORY,
                        onClick = { currentScreen = ShopScreen.INVENTORY },
                        icon = {
                            val lowStockCount = state.products.count { it.currentStock <= it.minStockAlert }
                            if (lowStockCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = Color(0xFFDC2626)) {
                                            Text("$lowStockCount", color = Color.White)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Inventory, contentDescription = "Inventory")
                                }
                            } else {
                                Icon(Icons.Default.Inventory, contentDescription = "Inventory")
                            }
                        },
                        label = { Text("Inventory") },
                        modifier = Modifier.testTag("nav_inventory")
                    )
                    NavigationBarItem(
                        selected = currentScreen == ShopScreen.ORDERS,
                        onClick = { currentScreen = ShopScreen.ORDERS },
                        icon = { Icon(Icons.Default.PointOfSale, contentDescription = "Orders") },
                        label = { Text("Orders") },
                        modifier = Modifier.testTag("nav_orders")
                    )
                    NavigationBarItem(
                        selected = currentScreen == ShopScreen.CUSTOMERS,
                        onClick = { currentScreen = ShopScreen.CUSTOMERS },
                        icon = { Icon(Icons.Default.People, contentDescription = "Customers") },
                        label = { Text("Customers") },
                        modifier = Modifier.testTag("nav_customers")
                    )
                    NavigationBarItem(
                        selected = currentScreen == ShopScreen.AUDIT,
                        onClick = { currentScreen = ShopScreen.AUDIT },
                        icon = { Icon(Icons.Default.History, contentDescription = "Audit") },
                        label = { Text("Audit") },
                        modifier = Modifier.testTag("nav_audit")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                ShopScreen.DASHBOARD -> {
                    DashboardScreen(
                        state = state,
                        onNavigateToInventory = { currentScreen = ShopScreen.INVENTORY },
                        onNavigateToNewOrder = { currentScreen = ShopScreen.NEW_ORDER },
                        onNavigateToOrders = { currentScreen = ShopScreen.ORDERS },
                        onNavigateToAddProduct = {
                            editingProduct = null
                            pendingScannedSkuForNewProduct = null
                            currentScreen = ShopScreen.ADD_EDIT_PRODUCT
                        },
                        onOpenScanner = { viewModel.openScanner() },
                        onShowBarcode = { product -> viewModel.showBarcode(product) },
                        onRestockProduct = { product -> viewModel.openRestockDialog(product) }
                    )
                }

                ShopScreen.INVENTORY -> {
                    InventoryScreen(
                        state = state,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onCategorySelect = { viewModel.setSelectedCategory(it) },
                        onStockFilterSelect = { viewModel.setSelectedStockFilter(it) },
                        onAddNewProduct = {
                            editingProduct = null
                            pendingScannedSkuForNewProduct = null
                            currentScreen = ShopScreen.ADD_EDIT_PRODUCT
                        },
                        onEditProduct = { product ->
                            editingProduct = product
                            currentScreen = ShopScreen.ADD_EDIT_PRODUCT
                        },
                        onDeleteProduct = { product -> viewModel.deleteProduct(product.id) },
                        onShowBarcode = { product -> viewModel.showBarcode(product) },
                        onRestockProduct = { product -> viewModel.openRestockDialog(product) },
                        onAdjustStockProduct = { product -> viewModel.openAdjustDialog(product) },
                        onOpenScanner = { viewModel.openScanner() }
                    )
                }

                ShopScreen.ADD_EDIT_PRODUCT -> {
                    AddEditProductScreen(
                        product = editingProduct,
                        onSaveProduct = { saved ->
                            viewModel.saveProduct(saved)
                            currentScreen = ShopScreen.INVENTORY
                        },
                        onNavigateBack = { currentScreen = ShopScreen.INVENTORY },
                        onOpenScannerForSku = { viewModel.openScanner() },
                        onGenerateNewBarcode = { category ->
                            viewModel.generateNewBarcode("890")
                        }
                    )
                }

                ShopScreen.NEW_ORDER -> {
                    NewOrderScreen(
                        state = state,
                        onAddToCart = { product -> viewModel.addToCart(product) },
                        onUpdateQuantity = { id, delta -> viewModel.updateCartQuantity(id, delta) },
                        onRemoveFromCart = { id -> viewModel.removeFromCart(id) },
                        onClearCart = { viewModel.clearCart() },
                        onSelectCustomer = { customer -> viewModel.setCustomerForOrder(customer) },
                        onPaymentMethodChange = { method -> viewModel.setPaymentMethod(method) },
                        onPaymentStatusChange = { status -> viewModel.setPaymentStatus(status) },
                        onDiscountChange = { discount -> viewModel.setDiscount(discount) },
                        onCompleteOrder = { walkInName, walkInPhone, onSuccess ->
                            viewModel.completeOrder(walkInName, walkInPhone, onSuccess)
                        },
                        onOpenScanner = { viewModel.openScanner() },
                        onOrderFinished = { order ->
                            activeReceiptOrder = order
                            currentScreen = ShopScreen.ORDER_RECEIPT
                        }
                    )
                }

                ShopScreen.ORDER_RECEIPT -> {
                    activeReceiptOrder?.let { receipt ->
                        OrderReceiptScreen(
                            order = receipt,
                            onNavigateBack = { currentScreen = ShopScreen.ORDERS }
                        )
                    } ?: run {
                        currentScreen = ShopScreen.ORDERS
                    }
                }

                ShopScreen.ORDERS -> {
                    OrdersScreen(
                        state = state,
                        onSelectOrder = { order ->
                            activeReceiptOrder = order
                            currentScreen = ShopScreen.ORDER_RECEIPT
                        },
                        onNavigateToNewOrder = { currentScreen = ShopScreen.NEW_ORDER }
                    )
                }

                ShopScreen.CUSTOMERS -> {
                    CustomersScreen(
                        state = state,
                        onSaveCustomer = { viewModel.saveCustomer(it) },
                        onRecordPayment = { customer, amount -> viewModel.recordDuePayment(customer, amount) },
                        onNewOrderForCustomer = { customer ->
                            viewModel.setCustomerForOrder(customer)
                            currentScreen = ShopScreen.NEW_ORDER
                        }
                    )
                }

                ShopScreen.AUDIT -> {
                    StockAuditScreen(state = state)
                }

                ShopScreen.TEAM_ACCESS -> {
                    TeamAccessScreen(
                        currentUser = state.currentUser,
                        teamMembers = state.teamMembers,
                        onUpdateRole = { targetUid, targetEmail, newRole ->
                            viewModel.updateUserRole(targetUid, targetEmail, newRole)
                        },
                        onSignOutClicked = {
                            val credentialManager = CredentialManager.create(context)
                            performSignOut(context, credentialManager, scope) {
                                currentScreen = ShopScreen.DASHBOARD
                            }
                        }
                    )
                }
            }
        }
    }

    // Barcode Scanner Modal Sheet
    if (state.isScannerActive) {
        BarcodeScannerModal(
            products = state.products,
            scannedProduct = state.scannedProduct,
            onBarcodeScanned = { rawBarcode ->
                viewModel.handleScannedBarcode(rawBarcode) { notFoundSku ->
                    pendingScannedSkuForNewProduct = notFoundSku
                }
            },
            onAddToCart = { product ->
                viewModel.addToCart(product)
                Toast.makeText(context, "Added '${product.name}' to cart!", Toast.LENGTH_SHORT).show()
                if (currentScreen != ShopScreen.NEW_ORDER) {
                    currentScreen = ShopScreen.NEW_ORDER
                }
            },
            onRestockProduct = { product ->
                viewModel.openRestockDialog(product)
            },
            onDismiss = { viewModel.closeScanner() }
        )
    }

    // Product Barcode Label Dialog
    state.activeBarcodeProduct?.let { barcodeProduct ->
        ProductBarcodeModal(
            product = barcodeProduct,
            onDismiss = { viewModel.showBarcode(null) }
        )
    }

    // Restock Dialog
    state.activeRestockProduct?.let { restockProduct ->
        RestockDialog(
            product = restockProduct,
            onDismiss = { viewModel.openRestockDialog(null) },
            onConfirm = { addedQty, supplier, notes ->
                viewModel.restockProduct(restockProduct, addedQty, supplier, notes)
            }
        )
    }

    // Adjust Stock Dialog
    state.activeAdjustProduct?.let { adjustProduct ->
        AdjustStockDialog(
            product = adjustProduct,
            onDismiss = { viewModel.openAdjustDialog(null) },
            onConfirm = { newStock, reason, type ->
                viewModel.adjustStock(adjustProduct, newStock, reason, type)
            }
        )
    }
}
