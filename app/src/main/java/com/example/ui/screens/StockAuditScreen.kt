package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.firestore.model.FirestoreStockMovement
import com.example.ui.viewmodel.ShopUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StockAuditScreen(
    state: ShopUiState,
    modifier: Modifier = Modifier
) {
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }

    val filteredMovements = state.recentMovements.filter {
        when (selectedTypeFilter) {
            "ALL" -> true
            "RESTOCK" -> it.type == "RESTOCK" || it.type == "INITIAL_STOCK"
            "SALE" -> it.type == "SALE"
            "ADJUSTMENT" -> it.type == "ADJUSTMENT"
            "DAMAGE_LOSS" -> it.type == "DAMAGE_LOSS"
            else -> true
        }
    }

    val totalRestocked = state.recentMovements.filter { it.quantityChange > 0 }.sumOf { it.quantityChange }
    val totalSold = state.recentMovements.filter { it.type == "SALE" }.sumOf { kotlin.math.abs(it.quantityChange) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Stock Movement Summary Metrics
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Total Restocked", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("+$totalRestocked units", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Total Sold", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("-$totalSold units", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "ALL" to "All Logs",
                "RESTOCK" to "Restocks",
                "SALE" to "Sales Deductions",
                "ADJUSTMENT" to "Adjustments",
                "DAMAGE_LOSS" to "Damages & Loss"
            ).forEach { (type, label) ->
                val isSelected = selectedTypeFilter == type
                Surface(
                    onClick = { selectedTypeFilter = type },
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

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredMovements.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No stock movements recorded yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMovements, key = { it.id }) { movement ->
                    StockMovementRow(movement = movement, dateFormatter = dateFormatter)
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun StockMovementRow(
    movement: FirestoreStockMovement,
    dateFormatter: SimpleDateFormat
) {
    val isPositive = movement.quantityChange > 0
    val badgeColor = when (movement.type) {
        "RESTOCK", "INITIAL_STOCK" -> Color(0xFF16A34A)
        "SALE" -> MaterialTheme.colorScheme.primary
        "DAMAGE_LOSS" -> Color(0xFFDC2626)
        else -> Color(0xFFD97706)
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("movement_card_${movement.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(badgeColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPositive) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = movement.productName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${movement.type} • ${movement.reason.ifBlank { "Inventory change" }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val timestampMs = movement.timestamp?.toDate()?.time ?: System.currentTimeMillis()
                    Text(
                        text = dateFormatter.format(Date(timestampMs)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isPositive) "+${movement.quantityChange}" else "${movement.quantityChange}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = badgeColor
                )
                Text(
                    text = "${movement.previousStock} → ${movement.newStock}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
