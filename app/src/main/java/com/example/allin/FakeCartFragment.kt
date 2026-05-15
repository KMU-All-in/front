package com.example.allin

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.allin.data.FakeCartRepository
import com.example.allin.data.FakeProduct
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

class FakeCartFragment : Fragment() {

    // 뷰 변수들
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

    // 사진 선택 런처 (Fragment에서는 registerForActivityResult를 바로 사용 가능)
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

    // Fragment는 onCreateView에서 레이아웃을 생성합니다.
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 기존 activity_fake_cart.xml을 그대로 사용합니다.
        val view = inflater.inflate(R.layout.activity_fake_cart, container, false)

        repository = FakeCartRepository()
        initViews(view)
        setupListeners(view)
        observeCartItems()

        // 초기 탭 설정
        view.post { selectTab(0) }

        return view
    }

    private fun initViews(view: View) {
        cartItemsContainer = view.findViewById(R.id.cartItemsContainer)
        cardAddProduct = view.findViewById(R.id.cardAddProduct)
        cardEditProduct = view.findViewById(R.id.cardEditProduct)
        cardAddReason = view.findViewById(R.id.cardAddReason)
        dimView = view.findViewById(R.id.dimView)
        ivPhotoPreview = view.findViewById(R.id.ivPhotoPreview)
        ivEditPhotoPreview = view.findViewById(R.id.ivEditPhotoPreview)

        tabUrl = view.findViewById(R.id.tabUrl)
        tabPhoto = view.findViewById(R.id.tabPhoto)
        tabManual = view.findViewById(R.id.tabManual)

        layoutUrlInput = view.findViewById(R.id.layoutUrlInput)
        layoutPhotoInput = view.findViewById(R.id.layoutPhotoInput)
        layoutManualInput = view.findViewById(R.id.layoutManualInput)

        etUrlInput = view.findViewById(R.id.etUrlInput)
        etManualName = view.findViewById(R.id.etManualName)
        etManualPrice = view.findViewById(R.id.etManualPrice)
        etEditName = view.findViewById(R.id.etEditName)
        etEditPrice = view.findViewById(R.id.etEditPrice)
        spExpiry = view.findViewById(R.id.spExpiry)
        btnSubmit = view.findViewById(R.id.btnSubmit)
        btnSubmitEdit = view.findViewById(R.id.btnSubmitEdit)

        etNewReason = view.findViewById(R.id.etNewReason)
        btnSubmitReason = view.findViewById(R.id.btnSubmitReason)

        val expiryOptions = arrayOf("1일", "3일", "7일(권장)", "14일", "30일")
        // Activity 대신 requireContext() 사용
        spExpiry.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, expiryOptions)
        spExpiry.setSelection(2)
    }

    private fun setupListeners(view: View) {
        view.findViewById<Button>(R.id.btnAddProduct)?.setOnClickListener { showAddProductPopup() }
        view.findViewById<ImageView>(R.id.btnCloseCard)?.setOnClickListener { hidePopups() }
        view.findViewById<ImageView>(R.id.btnCloseEdit)?.setOnClickListener { hidePopups() }
        view.findViewById<ImageView>(R.id.btnCloseReason)?.setOnClickListener { hidePopups() }

        tabUrl.setOnClickListener { selectTab(0) }
        tabPhoto.setOnClickListener { selectTab(1) }
        tabManual.setOnClickListener { selectTab(2) }

        layoutPhotoInput.setOnClickListener { pickImageLauncher.launch("image/*") }
        view.findViewById<View>(R.id.layoutEditPhoto)?.setOnClickListener { pickImageLauncher.launch("image/*") }

        btnSubmit.setOnClickListener { validateAndSaveProduct() }
        btnSubmitEdit.setOnClickListener { saveEditedProduct() }
        btnSubmitReason.setOnClickListener { saveNewReason() }

        // [중요] 네비게이션 버튼들은 이제 MainActivity에서 관리하므로 여기서는 삭제하거나 주석 처리합니다.
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
        viewLifecycleOwner.lifecycleScope.launch {
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
                viewLifecycleOwner.lifecycleScope.launch { repository.delete(product) }
                continue
            }

            val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_cart_product, cartItemsContainer, false)
            // ... (renderCartItems 내부 로직은 기존과 동일하며 Context만 requireContext()로 수정)

            itemView.findViewById<TextView>(R.id.tvRemainingDays)?.text = if (TimeUnit.MILLISECONDS.toDays(expiryTime - now) <= 0L) "D-Day" else "D-${TimeUnit.MILLISECONDS.toDays(expiryTime - now)}"
            itemView.findViewById<TextView>(R.id.tvProductName)?.text = product.name
            itemView.findViewById<TextView>(R.id.tvProductPrice)?.text = "${dec.format(product.price)}원"

            val ivProduct = itemView.findViewById<ImageView>(R.id.ivProduct)
            if (product.imageUrl.isNotEmpty()) {
                Glide.with(this).load(product.imageUrl).placeholder(android.R.drawable.ic_menu_gallery).into(ivProduct)
            }

            itemView.findViewById<Button>(R.id.btnAddReason)?.setOnClickListener { showAddReasonPopup(product) }
            itemView.findViewById<ImageButton>(R.id.btnOptions)?.setOnClickListener { v ->
                val popup = PopupMenu(requireContext(), v)
                popup.menu.add("수정")
                popup.menu.add("삭제")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.title) {
                        "수정" -> showEditProductPopup(product)
                        "삭제" -> viewLifecycleOwner.lifecycleScope.launch { repository.delete(product) }
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
        Glide.with(this).load(product.imageUrl).into(ivEditPhotoPreview)
    }

    private fun saveEditedProduct() {
        val product = editingProduct ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val updatedProduct = product.copy(
                name = etEditName.text.toString().trim().ifEmpty { product.name },
                price = etEditPrice.text.toString().toIntOrNull() ?: 0,
                imageUrl = if (selectedImageUri != null) selectedImageUri.toString() else product.imageUrl
            )
            repository.insert(updatedProduct)
            withContext(Dispatchers.Main) {
                hidePopups()
                Toast.makeText(requireContext(), "수정되었습니다.", Toast.LENGTH_SHORT).show()
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
            viewLifecycleOwner.lifecycleScope.launch {
                repository.insert(product.copy(reasons = product.reasons + reason))
                withContext(Dispatchers.Main) { hidePopups() }
            }
        }
    }

    private fun validateAndSaveProduct() {
        viewLifecycleOwner.lifecycleScope.launch {
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
                        val priceStr = doc.select("meta[property=product:price:amount]").attr("content")
                        if (priceStr.isNotEmpty()) price = priceStr.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                    }
                } catch (e: Exception) { Log.e("FakeCart", "Jsoup error", e) }
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
        dimView.visibility = View.VISIBLE
        cardAddProduct.visibility = View.VISIBLE
    }

    private fun hidePopups() {
        dimView.visibility = View.GONE
        cardAddProduct.visibility = View.GONE
        cardEditProduct.visibility = View.GONE
        cardAddReason.visibility = View.GONE
    }

    private fun processOcr(uri: Uri) {
        val image = InputImage.fromFilePath(requireContext(), uri)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        recognizer.process(image).addOnSuccessListener { visionText -> parseOcrResult(visionText.text) }
    }

    private fun parseOcrResult(text: String) {
        // 기존 parseOcrResult 로직과 동일
        if (cardEditProduct.visibility == View.VISIBLE) return
        etManualName.setText(text.split("\n").firstOrNull() ?: "")
        selectTab(2)
    }
}