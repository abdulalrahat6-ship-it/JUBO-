package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    fun getCustomerById(id: Long): Flow<Customer?>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerByIdSync(id: Long): Customer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<Customer>)

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    @Query("UPDATE customers SET totalOrdersCount = totalOrdersCount + 1, totalSpent = totalSpent + :orderAmount, outstandingDue = outstandingDue + :dueChange WHERE id = :customerId")
    suspend fun recordCustomerOrder(customerId: Long, orderAmount: Double, dueChange: Double)

    @Query("UPDATE customers SET outstandingDue = :newDue WHERE id = :customerId")
    suspend fun updateDue(customerId: Long, newDue: Double)

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun getCount(): Int
}
