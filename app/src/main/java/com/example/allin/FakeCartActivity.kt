package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.allin.data.FakeCartRepository
import com.example.allin.data.FakeProduct
import com.example.allin.data.FakeProductDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.util.*

class FakeCartActivity : AppCompatActivity() {

    private lateinit var cartItemsContainer: LinearLayout
    private lateinit var cardAddProduct: CardView
    private lateinit var dimView: View
    private lateinit var etProductName: EditText
    private lateinit var etProductPrice: EditText
    private lateinit var spProductCategory: Spinner
    private lateinit var btnSubmitProduct: Button

    private lateinit var repository: FakeCartRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_cart)

        val database = FakeProductDatabase.getDatabase(this)
        repository = FakeCartRepository(database.fakeProductDao())

        initViews()
        setupListeners()
        observeCartItems()
    }

    private fun initViews() {
        cartItemsContainer = findViewById(R.id.cartItemsContainer)
        cardAddProduct = findViewById(R.id.cardAddProduct)
        dimView = findViewById(R.id.dimView)
        etProductName = findViewById(R.id.etProductName)
        etProductPrice = findViewById(R.id.etProductPrice)
        spProductCategory = findViewById(R.id.spProductCategory)
        btnSubmitProduct = findViewById(R.id.btnSubmitProduct)

        val categories = arrayOf("패션/의류", "가전/디지털", "뷰티", "도서", "기타")
        spProductCategory.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.btnAddProduct).setOnClickListener { 
            showAddProductPopup() 
        }
        findViewById<ImageView>(R.id.btnCloseCard).setOnClickListener { hideAddProductPopup() }
        btnSubmitProduct.setOnClickListener { validateAndSaveProduct() }
    }

    private fun observeCartItems() {
        lifecycleScope.launch {
            repository.allProducts.collect { products ->
                renderCartItems(products)
            }
        }
    }

    private fun renderCartItems(products: List<FakeProduct>) {
        cartItemsContainer.removeAllViews()
        val dec = DecimalFormat("#,###")
        for (product in products) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_cart_product, null)
            itemView.findViewById<TextView>(R.id.tvProductName).text = product.name
            itemView.findViewById<TextView>(R.id.tvProductPrice).text = "${dec.format(product.price)}원"
            
            itemView.setOnClickListener {
                showReasonManagementDialog(product)
            }
            
            // 삭제 버튼 (결제 완료 버튼 재활용)
            itemView.findViewById<Button>(R.id.btnCompletePayment).setOnClickListener {
                lifecycleScope.launch {
                    repository.delete(product)
                }
            }

            cartItemsContainer.addView(itemView)
        }
    }

    private fun showReasonManagementDialog(product: FakeProduct) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_manage_reasons, null)
        val tvName = dialogView.findViewById<TextView>(R.id.tvDialogProductName)
        val llReasons = dialogView.findViewById<LinearLayout>(R.id.llReasonsContainer)
        val etNewReason = dialogView.findViewById<EditText>(R.id.etNewReason)
        val btnAddReason = dialogView.findViewById<Button>(R.id.btnAddReason)

        tvName.text = product.name

        product.reasons.forEach { reason ->
            val tv = TextView(this)
            tv.text = "• $reason"
            tv.setPadding(0, 8, 0, 8)
            llReasons.addView(tv)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("닫기", null)
            .create()

        btnAddReason.setOnClickListener {
            val newReason = etNewReason.text.toString().trim()
            if (newReason.isNotEmpty()) {
                val updatedReasons = product.reasons.toMutableList()
                updatedReasons.add(newReason)
                
                lifecycleScope.launch {
                    val updatedProduct = product.copy(reasons = updatedReasons)
                    repository.insert(updatedProduct)
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        Toast.makeText(this@FakeCartActivity, "이유가 추가되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun validateAndSaveProduct() {
        val name = etProductName.text.toString().trim()
        val priceStr = etProductPrice.text.toString().trim()
        if (name.isEmpty() || priceStr.isEmpty()) return

        lifecycleScope.launch {
            val product = FakeProduct(
                id = UUID.randomUUID().toString(),
                name = name,
                category = spProductCategory.selectedItem.toString(),
                price = priceStr.toIntOrNull() ?: 0,
                reasons = emptyList()
            )
            repository.insert(product)
            withContext(Dispatchers.Main) { hideAddProductPopup() }
        }
    }

    private fun showAddProductPopup() {
        dimView.visibility = View.VISIBLE
        cardAddProduct.visibility = View.VISIBLE
    }

    private fun hideAddProductPopup() {
        dimView.visibility = View.GONE
        cardAddProduct.visibility = View.GONE
        etProductName.setText(""); etProductPrice.setText("")
    }
}
