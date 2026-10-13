package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firestore.model.FirestoreProduct
import com.example.ui.components.BarcodeCanvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    product: FirestoreProduct?,
    onSaveProduct: (FirestoreProduct) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenScannerForSku: () -> Unit,
    onGenerateNewBarcode: (category: String) -> String,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var sku by remember { mutableStateOf(product?.sku ?: onGenerateNewBarcode("General")) }
    var category by remember { mutableStateOf(product?.category ?: "General") }
    var costPriceText by remember { mutableStateOf(product?.let { String.format("%.2f", it.costPrice) } ?: "") }
    var sellingPriceText by remember { mutableStateOf(product?.let { String.format("%.2f", it.sellingPrice) } ?: "") }
    var currentStockText by remember { mutableStateOf(product?.currentStock?.toString() ?: "10") }
    var minStockAlertText by remember { mutableStateOf(product?.minStockAlert?.toString() ?: "5") }
    var unit by remember { mutableStateOf(product?.unit ?: "pcs") }
    var shelfLocation by remember { mutableStateOf(product?.shelfLocation ?: "") }
    var supplierName by remember { mutableStateOf(product?.supplierName ?: "") }
    var notes by remember { mutableStateOf(product?.notes ?: "") }

    var nameError by remember { mutableStateOf(false) }
    var skuError by remember { mutableStateOf(false) }

    val categoryPresets = listOf("General", "Beverages", "Snacks", "Groceries", "Electronics", "Personal Care", "Stationery", "Hardware")
    val unitPresets = listOf("pcs", "pack", "box", "bottle", "kg", "can", "meter")

    val cost = costPriceText.toDoubleOrNull() ?: 0.0
    val selling = sellingPriceText.toDoubleOrNull() ?: 0.0
    val profitMargin = if (selling > 0) ((selling - cost) / selling) * 100 else 0.0

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (product == null) "New Product" else "Edit Product", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                nameError = true
                                return@Button
                            }
                            if (sku.isBlank()) {
                                skuError = true
                                return@Button
                            }
                            val savedProduct = FirestoreProduct(
                                id = product?.id ?: "",
                                name = name.trim(),
                                sku = sku.trim(),
                                category = category.trim().ifBlank { "General" },
                                costPrice = cost,
                                sellingPrice = selling,
                                currentStock = currentStockText.toIntOrNull() ?: 0,
                                minStockAlert = minStockAlertText.toIntOrNull() ?: 5,
                                unit = unit.trim().ifBlank { "pcs" },
                                shelfLocation = shelfLocation.trim(),
                                supplierName = supplierName.trim(),
                                notes = notes.trim(),
                                createdAt = product?.createdAt
                            )
                            onSaveProduct(savedProduct)
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("save_product_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Basic Info Section
            Text("Product Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = false
                },
                label = { Text("Product Name *") },
                isError = nameError,
                supportingText = if (nameError) { { Text("Product name is required") } } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("product_name_input")
            )

            // Category Selection Chips
            Column {
                Text("Category", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoryPresets.forEach { preset ->
                        val isSelected = category.equals(preset, ignoreCase = true)
                        Surface(
                            onClick = { category = preset },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = preset,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Barcode & SKU Management
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Barcode & Identification",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = sku,
                        onValueChange = {
                            sku = it
                            skuError = false
                        },
                        label = { Text("Barcode / SKU Number *") },
                        isError = skuError,
                        trailingIcon = {
                            IconButton(onClick = onOpenScannerForSku) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan with Camera",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_sku_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { sku = onGenerateNewBarcode(category) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("generate_barcode_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-Generate", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onOpenScannerForSku,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Code", fontSize = 12.sp)
                        }
                    }

                    if (sku.isNotBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Live Barcode Preview:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        BarcodeCanvas(
                            barcodeText = sku,
                            barHeight = 60.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Pricing Section
            Text("Pricing & Margins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = costPriceText,
                    onValueChange = { costPriceText = it },
                    label = { Text("Cost Price ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cost_price_input")
                )

                OutlinedTextField(
                    value = sellingPriceText,
                    onValueChange = { sellingPriceText = it },
                    label = { Text("Selling Price ($)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("selling_price_input")
                )
            }

            // Profit margin badge preview
            if (selling > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimated Profit Margin:",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format("%.1f", profitMargin)}% ($${String.format("%.2f", selling - cost)} per unit)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Stock & Inventory Tracking Section
            Text("Stock & Inventory Levels", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = currentStockText,
                    onValueChange = { currentStockText = it.filter { c -> c.isDigit() } },
                    label = { Text("Current Stock") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("current_stock_input")
                )

                OutlinedTextField(
                    value = minStockAlertText,
                    onValueChange = { minStockAlertText = it.filter { c -> c.isDigit() } },
                    label = { Text("Min Alert Level") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("min_stock_input")
                )
            }

            // Unit Presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                unitPresets.forEach { presetUnit ->
                    val isSelected = unit.equals(presetUnit, ignoreCase = true)
                    Surface(
                        onClick = { unit = presetUnit },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = presetUnit,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Location & Supplier Details
            Text("Location & Supplier (Optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = shelfLocation,
                onValueChange = { shelfLocation = it },
                label = { Text("Shelf / Aisle Location (e.g. Shelf A-3)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = supplierName,
                onValueChange = { supplierName = it },
                label = { Text("Supplier Name / Distributor") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Item Notes / Specs") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
