package com.example.allin.data

import kotlinx.coroutines.flow.Flow

class FakeCartRepository(private val dao: FakeProductDao) {

    val allProducts: Flow<List<FakeProduct>> = dao.getAllProducts()

    suspend fun insert(product: FakeProduct) {
        dao.insertProduct(product)
    }

    suspend fun delete(product: FakeProduct) {
        dao.deleteProduct(product)
    }
}
