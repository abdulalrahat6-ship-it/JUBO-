package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Product
import com.example.data.util.BarcodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("ShopTrack", appName)
  }

  @Test
  fun `test barcode generation length and encoding`() {
    val ean = BarcodeGenerator.generateEan13("890")
    assertEquals(13, ean.length)
    assertTrue(ean.startsWith("890"))

    val barSequence = BarcodeGenerator.encodeToBarSequence(ean)
    assertTrue(barSequence.isNotEmpty())
  }

  @Test
  fun `test product stock alerts and margins`() {
    val lowStockProduct = Product(
      name = "Test Item",
      sku = "12345678",
      category = "General",
      costPrice = 5.0,
      sellingPrice = 10.0,
      currentStock = 3,
      minStockAlert = 5
    )
    assertTrue(lowStockProduct.isLowStock)
    assertEquals(50.0, lowStockProduct.profitMargin, 0.01)
    assertEquals(15.0, lowStockProduct.inventoryCostValue, 0.01)
    assertEquals(30.0, lowStockProduct.inventoryRetailValue, 0.01)
  }
}
