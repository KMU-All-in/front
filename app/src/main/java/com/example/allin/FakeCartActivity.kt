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
import androidx.work.*
import com.example.allin.data.FakeCartRepository
import com.example.allin.data.FakeProduct
import com.example.allin.worker.FakeCartWorker
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import com.bumptech.glide.Glide
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
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
            scheduleExpiryCheck()
            dimView.post { selectTab(0) }
        } catch (e: Exception) {
            Log.e("FakeCartActivity", "Error in onCreate", e)
            finish()
        }
    }

    private fun scheduleExpiryCheck() {
        val workRequest = PeriodicWorkRequestBuilder<FakeCartWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("FakeCartExpiryWork", ExistingPeriodicWorkPolicy.KEEP, workRequest)
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
                renderCartItems(products)
            }
        }
    }

    private fun renderCartItems(products: List<FakeProduct>) {
        cartItemsContainer.removeAllViews()
        val dec = DecimalFormat("#,###")
        val now = System.currentTimeMillis()

        for (product in products) {
            val expiryTime = product.addedTime + TimeUnit.DAYS.toMillis(product.expiryDays.toLong())
            if (now >= expiryTime) {
                lifecycleScope.launch { repository.delete(product) }
                continue
            }

            val itemView = LayoutInflater.from(this).inflate(R.layout.item_cart_product, cartItemsContainer, false)
            val diffInMillis = expiryTime - now
            val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)
            val dDayText = if (diffInDays <= 0L) "D-Day" else "D-$diffInDays"
            
            itemView.findViewById<TextView>(R.id.tvRemainingDays)?.text = dDayText
            itemView.findViewById<TextView>(R.id.tvProductName)?.text = product.name
            itemView.findViewById<TextView>(R.id.tvProductPrice)?.text = "${dec.format(product.price)}원"
            
            val ivProduct = itemView.findViewById<ImageView>(R.id.ivProduct)
            if (product.imageUrl.isNotEmpty()) {
                Glide.with(this).load(product.imageUrl).placeholder(android.R.drawable.ic_menu_gallery).error(android.R.drawable.ic_menu_report_image).into(ivProduct)
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
        } else {
            ivEditPhotoPreview.setImageResource(android.R.drawable.ic_menu_camera)
        }
    }

    private fun saveEditedProduct() {
        val product = editingProduct ?: return
        val newName = etEditName.text.toString().trim()
        val newPrice = etEditPrice.text.toString().toIntOrNull() ?: 0
        lifecycleScope.launch {
            val updatedProduct = product.copy(
                name = newName.ifEmpty { product.name },
                price = newPrice,
                imageUrl = if (selectedImageUri != null) selectedImageUri.toString() else product.imageUrl
            )
            repository.insert(updatedProduct)
            withContext(Dispatchers.Main) {
                hidePopups()
                Toast.makeText(this@FakeCartActivity, "정보가 수정되었습니다.", Toast.LENGTH_SHORT).show()
            }
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
            var name = etManualName.text.toString()
            var price = etManualPrice.text.toString().toIntOrNull() ?: 0
            val url = etUrlInput.text.toString()
            var imageUrl = ""
            val expiryDays = when(spExpiry.selectedItemPosition) {
                0 -> 1; 1 -> 3; 3 -> 14; 4 -> 30; else -> 7
            }

            if (currentTabIndex == 0 && url.isNotEmpty()) {
                try {
                    withContext(Dispatchers.IO) {
                        val doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(5000).get()
                        val ogTitle = doc.select("meta[property=og:title]").attr("content")
                        val ogImgElement = doc.select("meta[property=og:image]").first()
                        imageUrl = ogImgElement?.absUrl("content") ?: ""
                        name = if (ogTitle.isNotEmpty()) ogTitle else doc.title().split(":")[0].trim()
                        var priceStr = doc.select("meta[property=product:price:amount]").attr("content")
                        if (priceStr.isEmpty()) priceStr = doc.select("meta[name=twitter:data1]").attr("content")
                        if (priceStr.isNotEmpty()) price = priceStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                        
                        var successCount = 0
                        if (name.isNotEmpty() && name != "상품명 없음") successCount++
                        if (price > 0) successCount++
                        if (imageUrl.isNotEmpty()) successCount++

                        if (successCount < 2) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@FakeCartActivity, "정보가 부족합니다. 사진 인식을 이용해 주세요!", Toast.LENGTH_LONG).show()
                                selectTab(1)
                            }
                            return@withContext 
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FakeCart", "Jsoup error", e)
                }
            }

            val product = FakeProduct(
                id = UUID.randomUUID().toString(),
                name = name.ifEmpty { "상품명 없음" },
                category = "기타",
                price = price,
                url = url,
                imageUrl = imageUrl,
                expiryDays = expiryDays,
                addedTime = System.currentTimeMillis(),
                reasons = emptyList()
            )
            repository.insert(product)
            withContext(Dispatchers.Main) { hidePopups() }
        }
    }

    private fun showAddProductPopup() {
        editingProduct = null 
        btnSubmit.text = "장바구니에 담기"
        etManualName.setText("")
        etManualPrice.setText("")
        etUrlInput.setText("")
        spExpiry.setSelection(2)
        ivPhotoPreview.setImageResource(android.R.drawable.ic_menu_camera)
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
        } catch (e: Exception) {
            Log.e("FakeCartOCR", "OCR error", e)
        }
    }

    private fun parseOcrResult(text: String) {
        val lines = text.split("\n")
        var foundName = ""
        var foundPrice = 0
        val priceRegex = Regex("([0-9,]{2,10})")
        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.contains("원") || cleanLine.contains(",")) {
                val match = priceRegex.find(cleanLine)
                if (match != null) {
                    val priceVal = match.groupValues[1].replace(",", "").toIntOrNull() ?: 0
                    if (priceVal > 100) foundPrice = priceVal
                }
            }
            if (foundName.isEmpty() && cleanLine.length > 5 && !cleanLine.contains("원")) foundName = cleanLine
        }

        // [수정] 수정 모드일 때는 이름과 가격을 건드리지 않고 리턴합니다.
        if (cardEditProduct.visibility == View.VISIBLE) {
            Toast.makeText(this, "사진이 변경되었습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 추가 모드일 때만 자동 입력을 수행합니다.
        etManualName.setText(foundName)
        etManualPrice.setText(if (foundPrice > 0) foundPrice.toString() else "")
        selectTab(2)
    }
}
