package com.example.allin.data

import com.example.allin.data.FakeProduct

object PurchaseTracker {
    var lastOpenedProduct: FakeProduct? = null
    var lastOpenedTime: Long = 0
}
