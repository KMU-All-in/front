package com.example.allin

import android.graphics.Color
import android.os.Bundle
import android.util.Log
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
import org.jsoup.Jsoup
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.TimeUnit

class FakeCartActivity : AppCompatActivity() {

    private lateinit var cartItemsContainer: LinearLayout
    private lateinit var cardAddProduct: CardView
    private lateinit var dimView: View

    private lateinit var tabUrl: Button
    private lateinit var tabPhoto: Button
    private lateinit var tabManual: Button

    private lateinit var layoutUrlInput: LinearLayout
    private lateinit var layoutPhotoInput: LinearLayout
    private lateinit var layoutManualInput: LinearLayout

    private lateinit var etUrlInput: EditText
    private lateinit var etManualName: EditText
    private lateinit var etManualPrice: EditText
    private lateinit var spExpiry: Spinner
    private lateinit var btnSubmit: Button

    private lateinit var repository: FakeCartRepository
    private var currentTabIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_cart)

        try {
            val database = FakeProductDatabase.getDatabase(this)
            repository = FakeCartRepository(database.fakeProductDao())

            if (!initViews()) return
            setupListeners()
            observeCartItems()
            
            dimView.post { selectTab(0) }
        } catch (e: Exception) {
            Log.e("FakeCartActivity", "Error in onCreate", e)
            Toast.makeText(this, "화면 로드 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initViews(): Boolean {
        return try {
            cartItemsContainer = findViewById(R.id.cartItemsContainer)
            cardAddProduct = findViewById(R.id.cardAddProduct)
            dimView = findViewById(R.id.dimView)

            tabUrl = findViewById(R.id.tabUrl)
            tabPhoto = findViewById(R.id.tabPhoto)
            tabManual = findViewById(R.id.tabManual)

            layoutUrlInput = findViewById(R.id.layoutUrlInput)
            layoutPhotoInput = findViewById(R.id.layoutPhotoInput)
            layoutManualInput = findViewById(R.id.layoutManualInput)

            etUrlInput = findViewById(R.id.etUrlInput)
            etManualName = findViewById(R.id.etManualName)
            etManualPrice = findViewById(R.id.etManualPrice)
            spExpiry = findViewById(R.id.spExpiry)
            btnSubmit = findViewById(R.id.btnSubmit)

            val expiryOptions = arrayOf("1일", "3일", "7일(권장)", "14일", "30일")
            spExpiry.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, expiryOptions)
            spExpiry.setSelection(2) 
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setupListeners() {
        findViewById<ImageView>(R.id.btnBack)?.setOnClickListener { finish() }
        findViewById<Button>(R.id.btnAddProduct)?.setOnClickListener { showAddProductPopup() }
        findViewById<ImageView>(R.id.btnCloseCard)?.setOnClickListener { hideAddProductPopup() }

        tabUrl.setOnClickListener { selectTab(0) }
        tabPhoto.setOnClickListener { selectTab(1) }
        tabManual.setOnClickListener { selectTab(2) }

        btnSubmit.setOnClickListener { validateAndSaveProduct() }
    }

    private fun selectTab(index: Int) {
        currentTabIndex = index
        // [수정] 보라색 대신 검정색 배경으로 변경
        val activeBg = Color.BLACK 
        val inactiveBg = Color.parseColor("#F2F4F8")
        val activeText = Color.WHITE
        val inactiveText = Color.parseColor("#666666")

        tabUrl.setBackgroundColor(if (index == 0) activeBg else inactiveBg)
        tabUrl.setTextColor(if (index == 0) activeText else inactiveText)
        tabPhoto.setBackgroundColor(if (index == 1) activeBg else inactiveBg)
        tabPhoto.setTextColor(if (index == 1) activeText else inactiveText)
        tabManual.setBackgroundColor(if (index == 2) activeBg else inactiveBg)
        tabManual.setTextColor(if (index == 2) activeText else inactiveText)

        layoutUrlInput.visibility = if (index == 0) View.VISIBLE else View.GONE
        layoutPhotoInput.visibility = if (index == 1) View.VISIBLE else View.GONE
        layoutManualInput.visibility = if (index == 2) View.VISIBLE else View.GONE

        btnSubmit.text = when(index) {
            0 -> "URL에서 상품 정보 가져오기"
            1 -> "사진 선택하기"
            else -> "장바구니에 담기"
        }
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
        val now = System.currentTimeMillis()

        for (product in products) {
            try {
                val itemView = LayoutInflater.from(this).inflate(R.layout.item_cart_product, cartItemsContainer, false)
                
                val diffInMillis = (product.addedTime + TimeUnit.DAYS.toMillis(product.expiryDays.toLong())) - now
                val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)
                val dDayText = if (diffInDays <= 0) "D-Day" else "D-$diffInDays"
                
                itemView.findViewById<TextView>(R.id.tvRemainingDays)?.text = dDayText
                itemView.findViewById<TextView>(R.id.tvProductName)?.text = product.name
                itemView.findViewById<TextView>(R.id.tvProductPrice)?.text = "${dec.format(product.price)}원"
                
                val tvUrl = itemView.findViewById<TextView>(R.id.tvProductUrl)
                tvUrl?.text = if(product.url.isNullOrEmpty()) "직접 입력됨" else product.url

                val reasonsText = if (product.reasons.isEmpty()) "아직 작성된 이유가 없습니다."
                else product.reasons.joinToString("   ") { "• $it" }
                itemView.findViewById<TextView>(R.id.tvReasonsSummary)?.text = reasonsText

                itemView.findViewById<Button>(R.id.btnAddReason)?.setOnClickListener { showAddReasonDialog(product) }
                itemView.findViewById<Button>(R.id.btnDelete)?.setOnClickListener {
                    lifecycleScope.launch { repository.delete(product) }
                }
                cartItemsContainer.addView(itemView)
            } catch (e: Exception) {
                Log.e("FakeCartActivity", "Error rendering item", e)
            }
        }
    }

    private fun showAddReasonDialog(product: FakeProduct) {
        val etReason = EditText(this).apply {
            hint = "사야 하는 이유를 입력하세요"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }

        AlertDialog.Builder(this)
            .setTitle("이유 추가")
            .setView(etReason)
            .setPositiveButton("추가") { _, _ ->
                val newReason = etReason.text.toString().trim()
                if (newReason.isNotEmpty()) {
                    lifecycleScope.launch {
                        repository.insert(product.copy(reasons = product.reasons + newReason))
                    }
                }
            }
            .setNegativeButton("취소", null).show()
    }

    private fun validateAndSaveProduct() {
        lifecycleScope.launch {
            var name = ""
            var price = 0
            var url = ""
            val expiryDays = when(spExpiry.selectedItemPosition) {
                0 -> 1; 1 -> 3; 3 -> 14; 4 -> 30; else -> 7
            }

            when(currentTabIndex) {
                0 -> {
                    url = etUrlInput.text.toString().trim()
                    if (url.isEmpty()) return@launch
                    
                    btnSubmit.text = "분석 중..."
                    btnSubmit.isEnabled = false
                    
                    name = withContext(Dispatchers.IO) {
                        try {
                            val doc = Jsoup.connect(url)
                                .timeout(5000)
                                .get()
                            doc.title().split(":")[0].trim()
                        } catch (e: Exception) {
                            "분석된 상품"
                        }
                    }
                    btnSubmit.isEnabled = true
                }
                2 -> {
                    name = etManualName.text.toString().trim()
                    val priceStr = etManualPrice.text.toString().trim()
                    if (name.isEmpty() || priceStr.isEmpty()) return@launch
                    price = priceStr.toIntOrNull() ?: 0
                }
                else -> return@launch 
            }

            val product = FakeProduct(
                id = UUID.randomUUID().toString(),
                name = name,
                category = "기타",
                price = price,
                url = url,
                imageUrl = "",
                expiryDays = expiryDays,
                addedTime = System.currentTimeMillis(),
                reasons = emptyList()
            )
            repository.insert(product)
            withContext(Dispatchers.Main) { 
                hideAddProductPopup()
                Toast.makeText(this@FakeCartActivity, "장바구니에 추가되었습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddProductPopup() {
        dimView.visibility = View.VISIBLE
        cardAddProduct.visibility = View.VISIBLE
    }

    private fun hideAddProductPopup() {
        dimView.visibility = View.GONE
        cardAddProduct.visibility = View.GONE
        etUrlInput.setText(""); etManualName.setText(""); etManualPrice.setText("")
        selectTab(currentTabIndex) // 버튼 텍스트 복구
    }
}
