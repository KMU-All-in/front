package com.example.allin

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.allin.data.FakeCartRepository
import com.example.allin.data.FakeProduct
import com.google.firebase.auth.FirebaseAuth
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
    private lateinit var cardAddReason: CardView
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

    private lateinit var etNewReason: EditText
    private lateinit var btnSubmitReason: Button
    private var selectedProductForReason: FakeProduct? = null

    private lateinit var repository: FakeCartRepository
    private var currentTabIndex = 0
    
    private var editingProduct: FakeProduct? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_cart)

        hideSystemBars()

        if (FirebaseAuth.getInstance().currentUser == null) {
            Toast.makeText(this, "로그인이 필요한 서비스입니다.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, AllInActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            finish()
            return
        }

        try {
            repository = FakeCartRepository()

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

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun initViews(): Boolean {
        return try {
            cartItemsContainer = findViewById(R.id.cartItemsContainer)
            cardAddProduct = findViewById(R.id.cardAddProduct)
            cardAddReason = findViewById(R.id.cardAddReason)
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

            etNewReason = findViewById(R.id.etNewReason)
            btnSubmitReason = findViewById(R.id.btnSubmitReason)

            val expiryOptions = arrayOf("1일", "3일", "7일(권장)", "14일", "30일")
            spExpiry.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, expiryOptions)
            spExpiry.setSelection(2) 
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.btnAddProduct)?.setOnClickListener { showAddProductPopup() }
        findViewById<ImageView>(R.id.btnCloseCard)?.setOnClickListener { hidePopups() }
        findViewById<ImageView>(R.id.btnCloseReason)?.setOnClickListener { hidePopups() }

        tabUrl.setOnClickListener { selectTab(0) }
        tabPhoto.setOnClickListener { selectTab(1) }
        tabManual.setOnClickListener { selectTab(2) }

        btnSubmit.setOnClickListener { validateAndSaveProduct() }
        btnSubmitReason.setOnClickListener { saveNewReason() }

        layoutPhotoInput.setOnClickListener {
            showErrorDialog("카테고리 분류 오류 가능성", "카테고리 분류 오류 가능성이 있습니다. 직접 재분류하시겠습니까?") {
                selectTab(2)
            }
        }

        // 하단 네비게이션 애니메이션 추가
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        findViewById<LinearLayout>(R.id.navBudget).setOnClickListener {
            val intent = Intent(this, BudgetSetupActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun selectTab(index: Int) {
        currentTabIndex = index
        val activeBg = Color.BLACK
        val inactiveBg = Color.parseColor("#F2F4F8")
        val activeText = Color.WHITE
        val inactiveText = Color.parseColor("#666666")

        tabUrl.backgroundTintList = ColorStateList.valueOf(if (index == 0) activeBg else inactiveBg)
        tabUrl.setTextColor(if (index == 0) activeText else inactiveText)
        tabPhoto.backgroundTintList = ColorStateList.valueOf(if (index == 1) activeBg else inactiveBg)
        tabPhoto.setTextColor(if (index == 1) activeText else inactiveText)
        tabManual.backgroundTintList = ColorStateList.valueOf(if (index == 2) activeBg else inactiveBg)
        tabManual.setTextColor(if (index == 2) activeText else inactiveText)

        layoutUrlInput.visibility = if (index == 0) View.VISIBLE else View.GONE
        layoutPhotoInput.visibility = if (index == 1) View.VISIBLE else View.GONE
        layoutManualInput.visibility = if (index == 2) View.VISIBLE else View.GONE

        btnSubmit.text = if (editingProduct != null) "수정 완료" else when(index) {
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
                else product.reasons.joinToString("\n") { "• $it" }
                itemView.findViewById<TextView>(R.id.tvReasonsSummary)?.text = reasonsText

                itemView.findViewById<Button>(R.id.btnAddReason)?.setOnClickListener { showAddReasonPopup(product) }
                
                itemView.findViewById<ImageButton>(R.id.btnOptions)?.setOnClickListener { view ->
                    val popup = PopupMenu(this, view)
                    popup.menu.add("수정")
                    popup.menu.add("기간 연장")
                    popup.menu.add("삭제")
                    popup.setOnMenuItemClickListener { item ->
                        when (item.title) {
                            "수정" -> showEditProductPopup(product)
                            "기간 연장" -> showExtendPeriodDialog(product)
                            "삭제" -> showDeleteConfirmDialog(product)
                        }
                        true
                    }
                    popup.show()
                }
                
                cartItemsContainer.addView(itemView)
            } catch (e: Exception) {
                Log.e("FakeCartActivity", "Error rendering item", e)
            }
        }
    }

    private fun showExtendPeriodDialog(product: FakeProduct) {
        val options = arrayOf("1일 연장", "3일 연장", "7일 연장", "14일 연장")
        AlertDialog.Builder(this)
            .setTitle("숙고 기간 연장")
            .setItems(options) { _, which ->
                val addedDays = when(which) {
                    0 -> 1; 1 -> 3; 2 -> 7; else -> 14
                }
                lifecycleScope.launch {
                    val updatedProduct = product.copy(expiryDays = product.expiryDays + addedDays)
                    repository.insert(updatedProduct)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FakeCartActivity, "${addedDays}일 연장되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }.show()
    }

    private fun showDeleteConfirmDialog(product: FakeProduct) {
        AlertDialog.Builder(this)
            .setTitle("상품 삭제")
            .setMessage("'${product.name}'을(를) 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch { repository.delete(product) }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditProductPopup(product: FakeProduct) {
        editingProduct = product
        dimView.visibility = View.VISIBLE
        cardAddProduct.visibility = View.VISIBLE
        
        if (product.url.isNotEmpty()) {
            selectTab(0)
            etUrlInput.setText(product.url)
        } else {
            selectTab(2)
            etManualName.setText(product.name)
            etManualPrice.setText(product.price.toString())
        }
        
        val expiryIndex = when(product.expiryDays) {
            1 -> 0; 3 -> 1; 14 -> 3; 30 -> 4; else -> 2
        }
        spExpiry.setSelection(expiryIndex)
        btnSubmit.text = "수정 완료"
    }

    private fun showAddReasonPopup(product: FakeProduct) {
        selectedProductForReason = product
        dimView.visibility = View.VISIBLE
        cardAddReason.visibility = View.VISIBLE
        etNewReason.setText("")
    }

    private fun saveNewReason() {
        val reason = etNewReason.text.toString().trim()
        val product = selectedProductForReason
        if (reason.isNotEmpty() && product != null) {
            lifecycleScope.launch {
                repository.insert(product.copy(reasons = product.reasons + reason))
                withContext(Dispatchers.Main) {
                    hidePopups()
                    Toast.makeText(this@FakeCartActivity, "이유가 등록되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
                    btnSubmit.text = if (editingProduct != null) "수정 중..." else "분석 중..."
                    btnSubmit.isEnabled = false
                    
                    try {
                        name = withContext(Dispatchers.IO) {
                            val doc = Jsoup.connect(url).timeout(5000).get()
                            doc.title().split(":")[0].trim()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            btnSubmit.isEnabled = true
                            btnSubmit.text = if (editingProduct != null) "수정 완료" else "URL에서 상품 정보 가져오기"
                            showErrorDialog(
                                "상품 정보를 불러올 수 없습니다.",
                                "네트워크 오류 또는 지원하지 않는 URL입니다. 직접 입력하시겠습니까?"
                            ) {
                                selectTab(2)
                            }
                        }
                        return@launch
                    }
                    btnSubmit.isEnabled = true
                }
                2 -> {
                    name = etManualName.text.toString().trim()
                    val priceStr = etManualPrice.text.toString().trim()
                    if (name.isEmpty() || priceStr.isEmpty()) return@launch
                    price = priceStr.toIntOrNull() ?: 0
                }
                else -> {
                    Toast.makeText(this@FakeCartActivity, "준비 중인 기능입니다.", Toast.LENGTH_SHORT).show()
                    return@launch 
                }
            }

            val product = editingProduct?.copy(
                name = name,
                price = price,
                url = url,
                expiryDays = expiryDays
            ) ?: FakeProduct(
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
                hidePopups()
                val msg = if (editingProduct != null) "수정되었습니다." else "장바구니에 추가되었습니다."
                Toast.makeText(this@FakeCartActivity, msg, Toast.LENGTH_SHORT).show()
                editingProduct = null
            }
        }
    }

    private fun showErrorDialog(title: String, message: String, onPositive: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("직접 입력/재분류") { _, _ -> onPositive() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showAddProductPopup() {
        editingProduct = null
        dimView.visibility = View.VISIBLE
        cardAddProduct.visibility = View.VISIBLE
        etUrlInput.setText("")
        etManualName.setText("")
        etManualPrice.setText("")
        btnSubmit.text = "장바구니에 담기"
    }

    private fun hidePopups() {
        dimView.visibility = View.GONE
        cardAddProduct.visibility = View.GONE
        cardAddReason.visibility = View.GONE
        etUrlInput.setText(""); etManualName.setText(""); etManualPrice.setText("")
        editingProduct = null
        selectTab(currentTabIndex)
    }
}
