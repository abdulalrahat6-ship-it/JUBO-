package com.example

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.firestore.model.FirestoreCustomer
import com.example.data.firestore.model.FirestoreProduct
import com.example.data.firestore.repository.OnlineShopRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun testCreateAndReadProductWhenAuthenticated() = runBlocking {
    val uid = signInTestUser("staff@shop.com")
    val repository = OnlineShopRepository(firestore, auth)

    val product = FirestoreProduct(
      name = "Wireless Mouse",
      sku = "8901234567890",
      category = "Electronics",
      costPrice = 12.0,
      sellingPrice = 25.0,
      currentStock = 15,
      minStockAlert = 5,
      unit = "pcs"
    )

    val docId = repository.saveProduct(product)
    assertNotNull(docId)

    val fetched = repository.getProductById(docId)
    assertNotNull(fetched)
    assertEquals("Wireless Mouse", fetched?.name)
    assertEquals(15, fetched?.currentStock)
  }

  @Test
  fun testCreateCustomerWhenAuthenticated() = runBlocking {
    signInTestUser("staff2@shop.com")
    val repository = OnlineShopRepository(firestore, auth)

    val customer = FirestoreCustomer(
      name = "John Doe",
      phone = "+1234567890",
      email = "john@example.com"
    )

    val docId = repository.saveCustomer(customer)
    assertNotNull(docId)
    assertTrue(docId.isNotEmpty())
  }
}
