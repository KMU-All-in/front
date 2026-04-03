package com.example.allin

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.util.*

class FakeCartActivity : AppCompatActivity() {

    private lateinit var cartItemsContainer: LinearLayout
    private lateinit var cardAddProduct: CardView
    private lateinit var dimView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_cart)

        cartItemsContainer = findViewById(R.id.cartItemsContainer)
        cardAddProduct = findViewById(R.id.cardAddProduct)
        dimView = findViewById(R.id.dimView)

        val btnAddProduct = findViewById<Button>(R.id.btnAddProduct)
        val btnCloseCard = findViewById<ImageView>(R.id.btnCloseCard)
        val btnFetchInfo = findViewById<Button>(R.id.btnFetchInfo)
        val spExpireDays = findViewById<Spinner>(R.id.spExpireDays)

        // 만료일 선택 스피너 (7일 고정 또는 선택 가능)
        val days = arrayOf("7일", "14일", "30일")
        spExpireDays.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, days)

        // 상품 추가 팝업 열기
        btnAddProduct.setOnClickListener {
            dimView.visibility = View.VISIBLE
            cardAddProduct.visibility = View.VISIBLE
        }

        // 팝업 닫기
        btnCloseCard.setOnClickListener {
            dimView.visibility = View.GONE
            cardAddProduct.visibility = View.GONE
        }

        // 정보 가져오기 버튼 (여기서는 예시로 '스니커즈' 자동 추가)
        btnFetchInfo.setOnClickListener {
            addProductToCart("새로운 상품", "패션/의류", 125000)
            dimView.visibility = View.GONE
            cardAddProduct.visibility = View.GONE
            Toast.makeText(this, "상품이 추가되었습니다.", Toast.LENGTH_SHORT).show()
        }

        loadCartItems()
    }

    private fun addProductToCart(name: String, category: String, price: Int) {
        val sharedPref = getSharedPreferences("CartPrefs", Context.MODE_PRIVATE)
        val cartArray = JSONArray(sharedPref.getString("CART_ITEMS", "[]"))
        
        val newItem = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("name", name)
            put("category", category)
            put("price", price)
            put("addedTime", System.currentTimeMillis())
        }
        
        cartArray.put(newItem)
        sharedPref.edit().putString("CART_ITEMS", cartArray.toString()).apply()
        loadCartItems()
    }

    private fun loadCartItems() {
        cartItemsContainer.removeAllViews()
        val sharedPref = getSharedPreferences("CartPrefs", Context.MODE_PRIVATE)
        val cartArray = JSONArray(sharedPref.getString("CART_ITEMS", "[]"))
        val newCartArray = JSONArray()

        val dec = DecimalFormat("#,###")
        val currentTime = System.currentTimeMillis()
        val sevenDaysInMillis = 7 * 24 * 60 * 60 * 1000L

        for (i in 0 until cartArray.length()) {
            val item = cartArray.getJSONObject(i)
            val addedTime = item.getLong("addedTime")
            
            if (currentTime - addedTime < sevenDaysInMillis) {
                newCartArray.put(item)
                val itemView = LayoutInflater.from(this).inflate(R.layout.item_cart_product, null)
                
                itemView.findViewById<TextView>(R.id.tvProductName).text = item.getString("name")
                itemView.findViewById<TextView>(R.id.tvProductCategory).text = item.getString("category")
                itemView.findViewById<TextView>(R.id.tvProductPrice).text = "${dec.format(item.getInt("price"))}원"
                
                val remainingDays = 7 - ((currentTime - addedTime) / (24 * 60 * 60 * 1000L)).toInt()
                itemView.findViewById<TextView>(R.id.tvRemainingTime).text = "${remainingDays}일 후 삭제"

                itemView.findViewById<Button>(R.id.btnCompletePayment).setOnClickListener {
                    removeProduct(item.getString("id"))
                }

                cartItemsContainer.addView(itemView)
            }
        }
        sharedPref.edit().putString("CART_ITEMS", newCartArray.toString()).apply()
    }

    private fun removeProduct(id: String) {
        val sharedPref = getSharedPreferences("CartPrefs", Context.MODE_PRIVATE)
        val cartArray = JSONArray(sharedPref.getString("CART_ITEMS", "[]"))
        val newCartArray = JSONArray()
        for (i in 0 until cartArray.length()) {
            val item = cartArray.getJSONObject(i)
            if (item.getString("id") != id) newCartArray.put(item)
        }
        sharedPref.edit().putString("CART_ITEMS", newCartArray.toString()).apply()
        loadCartItems()
        Toast.makeText(this, "충동구매를 참으셨군요!", Toast.LENGTH_SHORT).show()
    }
}
