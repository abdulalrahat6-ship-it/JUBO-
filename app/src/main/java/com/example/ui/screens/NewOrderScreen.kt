package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firestore.model.FirestoreCustomer
import com.example.data.firestore.model.FirestoreOrder
import com.example.data.firestore.model.FirestoreProduct
import com.example.ui.viewmodel.OnlineCartItem
import com.example.ui.viewmodel.ShopUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(
    state: ShopUiState,
    onAddToCart: (FirestoreProduct) -> Unit,
    onUpdateQuantity: (productId: String, delta: Int) -> Unit,
    onRemoveFromCart: (productId: String) -> Unit,
    onClearCart: () -> Unit,
    onSelectCustomer: (FirestoreCustomer?) -> Unit,
    onPaymentMethodChange: (String) -> Unit,
    onPaymentStatusChange: (String) -> Unit,
    onDiscountChange: (Double) -> Unit,
    onCompleteOrder: (walkInName: String, walkInPhone: String, onSuccess: (FirestoreOrder) -> Unit) -> Unit,
    onOpenScanner: () -> Unit,
    onOrderFinished: (FirestoreOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var itemSearchQuery by remember { mutableStateOf("") }
    var walkInCustomerName by remember { mutableStateOf("") }
    var walkInCustomerPhone by remember { mutableStateOf("") }
    var discountText by remember { mutableStateOf(if (state.discountAmount > 0) state.discountAmount.toString() else "") }
    var showCustomerPicker by remember { mutableStateOf(false) }

    val subtotal = state.cartItems.sumOf { it.totalPrice }
    val discount = discountText.toDoubleOrNull() ?: 0.0
    val totalPayable = (subtotal - discount).coerceAtLeast(0.0)

    val quickAddCatalog = state.products.filter { product ->
        (itemSearchQuery.isBlank() || product.name.contains(itemSearchQuery, ignoreCase = true) || product.sku.contains(itemSearchQuery, ignoreCase = true)) &&
                product.currentStock > 0
    }.take(6)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("New Customer Order", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenScanner, modifier = Modifier.testTag("pos_scan_barcode_btn")) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Item Barcode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (state.cartItems.isNotEmpty()) {
                        IconButton(onClick = onClearCart) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Cart")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Customer Selection Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Customer Information",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Box {
                                OutlinedButton(
                                    onClick = { showCustomerPicker = true },
                                    modifier = Modifier.testTag("select_customer_dropdown_btn")
                                ) {
                                    Text(
                                        text = state.selectedCustomer?.name ?: "Select Saved",
                                        fontSize = 12.sp
                                    )
                                }
                                DropdownMenu(
                                    expanded = showCustomerPicker,
                                    onDismissRequest = { showCustomerPicker = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Walk-in / Guest Customer") },
                                        onClick = {
                                            onSelectCustomer(null)
                                            showCustomerPicker = false
                                        }
                                    )
                                    state.customers.forEach { customer ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(customer.name, fontWeight = FontWeight.Bold)
                                                    Text(customer.phone, fontSize = 11.sp, color = Color.Gray)
                                                }
                                            },
                                            onClick = {
                                                onSelectCustomer(customer)
                                                showCustomerPicker = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (state.selectedCustomer == null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = walkInCustomerName,
                                    onValueChange = { walkInCustomerName = it },
                                    placeholder = { Text("Walk-in Name") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("walk_in_name_input")
                                )
                                OutlinedTextField(
                                    value = walkInCustomerPhone,
                                    onValueChange = { walkInCustomerPhone = it },
                                    placeholder = { Text("Phone") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("walk_in_phone_input")
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Selected: ${state.selectedCustomer.name} (${state.selectedCustomer.phone})",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Quick Product Search & Scanner Callout
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = itemSearchQuery,
                        onValueChange = { itemSearchQuery = it },
                        placeholder = { Text("Search catalog to add...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("order_item_search")
                    )
                    Button(
                        onClick = onOpenScanner,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier.height(56.dp).testTag("scanner_order_btn")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan", color = Color.White)
                    }
                }
            }

            // Quick Add Catalog Suggestions
            if (quickAddCatalog.isNotEmpty() && itemSearchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "Matching Products",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(quickAddCatalog) { product ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    "$${String.format("%.2f", product.sellingPrice)} • In Stock: ${product.currentStock} ${product.unit}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onAddToCart(product) },
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Add", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Active Cart Items Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Order Cart (${state.cartItems.sumOf { it.quantity }} items)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (state.cartItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.AddShoppingCart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Cart is empty",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Scan barcode or search products above to add items",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                items(state.cartItems) { item ->
                    CartItemRow(
                        cartItem = item,
                        onQuantityChange = { delta -> onUpdateQuantity(item.product.id, delta) },
                        onRemove = { onRemoveFromCart(item.product.id) }
                    )
                }
            }

            // Payment and Checkout Summary Card
            if (state.cartItems.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Payment & Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                            Spacer(modifier = Modifier.height(12.dp))

                            // Payment Method selector
                            Text("Payment Method", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("CASH", "CARD", "MOBILE_PAY", "CREDIT_DUE").forEach { method ->
                                    val isSelected = state.paymentMethod == method
                                    val label = when (method) {
                                        "CASH" -> "Cash"
                                        "CARD" -> "Card / POS"
                                        "MOBILE_PAY" -> "Mobile / UPI"
                                        "CREDIT_DUE" -> "Credit (Due)"
                                        else -> method
                                    }
                                    Surface(
                                        onClick = { onPaymentMethodChange(method) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Payment status
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("PAID", "DUE").forEach { status ->
                                    val isSelected = state.paymentStatus == status
                                    Surface(
                                        onClick = { onPaymentStatusChange(status) },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) (if (status == "PAID") Color(0xFF16A34A) else Color(0xFFDC2626)) else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (status == "PAID") "Paid in Full" else "Mark as Due",
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Discount input
                            OutlinedTextField(
                                value = discountText,
                                onValueChange = {
                                    discountText = it
                                    onDiscountChange(it.toDoubleOrNull() ?: 0.0)
                                },
                                label = { Text("Discount ($)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("order_discount_input")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Summary totals
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$${String.format("%.2f", subtotal)}", fontWeight = FontWeight.SemiBold)
                            }

                            if (discount > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Discount", color = Color(0xFFDC2626))
                                    Text("-$${String.format("%.2f", discount)}", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total Amount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    "$${String.format("%.2f", totalPayable)}",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    onCompleteOrder(walkInCustomerName, walkInCustomerPhone) { completedOrder ->
                                        onOrderFinished(completedOrder)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("complete_order_button")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Complete Order & Generate Receipt",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun CartItemRow(
    cartItem: OnlineCartItem,
    onQuantityChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cartItem.product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$${String.format("%.2f", cartItem.product.sellingPrice)} each • SKU: ${cartItem.product.sku}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$${String.format("%.2f", cartItem.totalPrice)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onQuantityChange(-1) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }

                Text(
                    text = "${cartItem.quantity}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                IconButton(
                    onClick = { onQuantityChange(1) },
                    enabled = cartItem.quantity < cartItem.product.currentStock,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
