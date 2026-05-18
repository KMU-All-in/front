package com.example.allin

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.bumptech.glide.Glide
import com.example.allin.data.FakeCartRepository
import com.example.allin.data.FakeProduct
import com.example.allin.worker.FakeCartWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
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
    private lateinit var cardEditProduct: CardView
    private lateinit var cardAddReason: CardView
    private lateinit var dimView: View
    private lateinit var ivPhotoPreview: ImageView
    private lateinit var ivEditPhotoPreview: ImageView

    private lateinit var tabUrl: Button
    private lateinit var tabPhoto: Button
    private lateinit var tabManual: Button

    private lateinit var layoutUrlInput: LinearLayout
    private lateinit var layoutPhotoInput: LinearLayout
    private lateinit var layoutManualInput: LinearLayout

    private lateinit var etUrlInput: EditText
    private lateinit var etManualName: EditText
    private lateinit var etManualPrice: EditText
    private lateinit var etEditName: EditText
    private lateinit var etEditPrice: EditText
    private lateinit var spExpiry: Spinner
    private lateinit var btnSubmit: Button
    private lateinit var btnSubmitEdit: Button

    private lateinit var etNewReason: EditText
    private lateinit var btnSubmitReason: Button
    private var selectedProductForReason: FakeProduct? = null

    private lateinit var repository: FakeCartRepository
    private var currentTabIndex = 0
    private var selectedImageUri: Uri? = null
    private var editingProduct: FakeProduct? = null
    
    // 만료 팝업을 한 번만 띄우기 위한 플래그
    private var isExpiredCheckDone = false

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) scheduleExpiryCheck()
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            if (cardEditProduct.visibility == View.VISIBLE) {
                ivEditPhotoPreview.setImageURI(it)
            } else {
                ivPhotoPreview.setImageURI(it)
            }
            processOcr(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fake_cart)
        hideSystemBars()

        if (FirebaseAuth.getInstance().currentUser == null) {
            Toast.makeText(this, "로그인이 필요한 서비스입니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            repository = FakeCartRepository()
            if (!initViews()) return
            setupListeners()
            observeCartItems()
            checkNotificationPermission()
            handleIntent(intent)
            dimView.post { selectTab(0) }
        } catch (e: Exception) {
            Log.e("FakeCartActivity", "Error in onCreate", e)
            finish()
        }
        setupBackPress()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (sharedText != null) {
                etUrlInput.setText(sharedText)
                selectTab(0)
                showAddProductPopup()
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                scheduleExpiryCheck()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            scheduleExpiryCheck()
        }
    }

    private fun scheduleExpiryCheck() {
        val workRequest = PeriodicWorkRequestBuilder<FakeCartWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "FakeCartExpiryWork", 
            ExistingPeriodicWorkPolicy.UPDATE, 
            workRequest
        )
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun initViews(): Boolean {
        cartItemsContainer = findViewById(R.id.cartItemsContainer)
        cardAddProduct = findViewById(R.id.cardAddProduct)
        cardEditProduct = findViewById(R.id.cardEditProduct) 
        cardAddReason = findViewById(R.id.cardAddReason)
        dimView = findViewById(R.id.dimView)
        ivPhotoPreview = findViewById(R.id.ivPhotoPreview)
        ivEditPhotoPreview = findViewById(R.id.ivEditPhotoPreview) 

        tabUrl = findViewById(R.id.tabUrl)
        tabPhoto = findViewById(R.id.tabPhoto)
        tabManual = findViewById(R.id.tabManual)

        layoutUrlInput = findViewById(R.id.layoutUrlInput)
        layoutPhotoInput = findViewById(R.id.layoutPhotoInput)
        layoutManualInput = findViewById(R.id.layoutManualInput)

        etUrlInput = findViewById(R.id.etUrlInput)
        etManualName = findViewById(R.id.etManualName)
        etManualPrice = findViewById(R.id.etManualPrice)
        etEditName = findViewById(R.id.etEditName) 
        etEditPrice = findViewById(R.id.etEditPrice) 
        spExpiry = findViewById(R.id.spExpiry)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnSubmitEdit = findViewById(R.id.btnSubmitEdit) 

        etNewReason = findViewById(R.id.etNewReason)
        btnSubmitReason = findViewById(R.id.btnSubmitReason)

        val expiryOptions = arrayOf("1일", "3일", "7일(권장)", "14일", "30일")
        spExpiry.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, expiryOptions)
        spExpiry.setSelection(2) 
        return true
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.btnAddProduct)?.setOnClickListener { showAddProductPopup() }
        findViewById<ImageView>(R.id.btnCloseCard)?.setOnClickListener { hidePopups() }
        findViewById<ImageView>(R.id.btnCloseEdit)?.setOnClickListener { hidePopups() } 
        findViewById<ImageView>(R.id.btnCloseReason)?.setOnClickListener { hidePopups() }

        tabUrl.setOnClickListener { selectTab(0) }
        tabPhoto.setOnClickListener { selectTab(1) }
        tabManual.setOnClickListener { selectTab(2) }

        layoutPhotoInput.setOnClickListener { pickImageLauncher.launch("image/*") }
        findViewById<View>(R.id.layoutEditPhoto)?.setOnClickListener { pickImageLauncher.launch("image/*") } 

        btnSubmit.setOnClickListener { validateAndSaveProduct() }
        btnSubmitEdit.setOnClickListener { saveEditedProduct() } 
        btnSubmitReason.setOnClickListener { saveNewReason() }

        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
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
    }

    private fun observeCartItems() {
        lifecycleScope.launch {
            repository.allProducts.collect { products ->
                // [추가] 만료 상품 체크 로직
                if (!isExpiredCheckDone) {
                    checkAndShowExpiredDialog(products)
                    isExpiredCheckDone = true
                }
                renderCartItems(products)
            }
        }
    }

    // 만료된 상품이 있는지 확인하고 팝업 띄우기
    private fun checkAndShowExpiredDialog(products: List<FakeProduct>) {
        val now = System.currentTimeMillis()
        val expiredProducts = products.filter { 
            val expiryTime = it.addedTime + TimeUnit.DAYS.toMillis(it.expiryDays.toLong())
            now >= expiryTime
        }

        if (expiredProducts.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("숙고 기간 만료 안내")
                .setMessage("숙고 기간이 지난 상품이 ${expiredProducts.size}건 있습니다. 삭제하시겠습니까?")
                .setPositiveButton("삭제") { _, _ ->
                    lifecycleScope.launch {
                        expiredProducts.forEach { repository.delete(it) }
                        Toast.makeText(this@FakeCartActivity, "만료된 상품이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("유지", null)
                .show()
        }
    }

    private fun renderCartItems(products: List<FakeProduct>) {
        cartItemsContainer.removeAllViews()
        val dec = DecimalFormat("#,###")
        val now = System.currentTimeMillis()

        for (product in products) {
            val expiryTime = product.addedTime + TimeUnit.DAYS.toMillis(product.expiryDays.toLong())
            
            // [수정] 자동 삭제 로직을 제거했습니다. (사용자가 팝업에서 선택함)

            val itemView = LayoutInflater.from(this).inflate(R.layout.item_cart_product, cartItemsContainer, false)
            val diffInDays = TimeUnit.MILLISECONDS.toDays(expiryTime - now)
            val dDayText = if (diffInDays <= 0L) "D-Day" else "D-$diffInDays"
            
            itemView.findViewById<TextView>(R.id.tvRemainingDays)?.text = dDayText
            itemView.findViewById<TextView>(R.id.tvProductName)?.text = product.name
            itemView.findViewById<TextView>(R.id.tvProductPrice)?.text = "${dec.format(product.price)}원"
            
            val ivProduct = itemView.findViewById<ImageView>(R.id.ivProduct)
            if (product.imageUrl.isNotEmpty()) {
                Glide.with(this).load(product.imageUrl).placeholder(android.R.drawable.ic_menu_gallery).into(ivProduct)
            }
            
            val reasonsText = if (product.reasons.isEmpty()) "아직 작성된 이유가 없습니다."
            else product.reasons.joinToString("\n") { "• $it" }
            itemView.findViewById<TextView>(R.id.tvReasonsSummary)?.text = reasonsText

            itemView.findViewById<Button>(R.id.btnAddReason)?.setOnClickListener { showAddReasonPopup(product) }
            itemView.findViewById<ImageButton>(R.id.btnOptions)?.setOnClickListener { view ->
                val popup = PopupMenu(this, view)
                popup.menu.add("수정")
                popup.menu.add("삭제")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.title) {
                        "수정" -> showEditProductPopup(product)
                        "삭제" -> lifecycleScope.launch { repository.delete(product) }
                    }
                    true
                }
                popup.show()
            }
            cartItemsContainer.addView(itemView)
        }
    }

    private fun showEditProductPopup(product: FakeProduct) {
        editingProduct = product
        dimView.visibility = View.VISIBLE
        cardEditProduct.visibility = View.VISIBLE
        etEditName.setText(product.name)
        etEditPrice.setText(product.price.toString())
        if (product.imageUrl.isNotEmpty()) {
            Glide.with(this).load(product.imageUrl).into(ivEditPhotoPreview)
        }
    }

    private fun saveEditedProduct() {
        val product = editingProduct ?: return
        lifecycleScope.launch {
            val updatedProduct = product.copy(
                name = etEditName.text.toString().trim().ifEmpty { product.name },
                price = etEditPrice.text.toString().toIntOrNull() ?: product.price,
                imageUrl = selectedImageUri?.toString() ?: product.imageUrl
            )
            repository.insert(updatedProduct)
            withContext(Dispatchers.Main) { hidePopups() }
        }
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
            var name = etManualName.text.toString().trim()
            var price = etManualPrice.text.toString().toIntOrNull() ?: 0
            val url = etUrlInput.text.toString()
            val expiryDays = when(spExpiry.selectedItemPosition) {
                0 -> 1; 1 -> 3; 3 -> 14; 4 -> 30; else -> 7
            }

            val product = FakeProduct(
                id = UUID.randomUUID().toString(),
                name = name.ifEmpty { "상품명 없음" },
                price = price,
                url = url,
                expiryDays = expiryDays,
                addedTime = System.currentTimeMillis()
            )
            repository.insert(product)
            withContext(Dispatchers.Main) { hidePopups() }
        }
    }

    private fun showAddProductPopup() {
        editingProduct = null 
        dimView.visibility = View.VISIBLE
        cardAddProduct.visibility = View.VISIBLE
    }

    private fun hidePopups() {
        editingProduct = null
        selectedImageUri = null
        dimView.visibility = View.GONE
        cardAddProduct.visibility = View.GONE
        cardEditProduct.visibility = View.GONE 
        cardAddReason.visibility = View.GONE
    }

    private fun processOcr(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            recognizer.process(image).addOnSuccessListener { visionText -> parseOcrResult(visionText.text) }
        } catch (e: Exception) { }
    }

    private fun parseOcrResult(text: String) {
        if (cardEditProduct.visibility == View.VISIBLE) return
        etManualName.setText(text.split("\n").firstOrNull() ?: "")
        selectTab(2)
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@FakeCartActivity, HomeActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT) })
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                finish()
            }
        })
    }
}
