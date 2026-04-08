package com.example.allin.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FakeProductDao {
    @Query("SELECT * FROM fake_products ORDER BY addedTime DESC")
    fun getAllProducts(): Flow<List<FakeProduct>>

    @Query("SELECT * FROM fake_products WHERE id = :id")
    suspend fun getProductById(id: String): FakeProduct?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: FakeProduct)

    @Delete
    suspend fun deleteProduct(product: FakeProduct)
}
