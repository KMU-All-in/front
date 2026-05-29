package com.example.allin

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
    private lateinit var layoutPhotoPreview: View
    private lateinit var layoutUrlInput: LinearLayout
    private lateinit var layoutPhotoInput: LinearLayout
    private lateinit var layoutManualInput: LinearLayout
    private lateinit var etUrlInput: EditText
    private lateinit var etManualName: EditText
    private lateinit var etManualPrice: EditText
    private lateinit var etEditUrl: EditText
    private lateinit var etEditName: EditText
    private lateinit var etEditPrice: EditText
    private lateinit var spExpiry: Spinner
    private lateinit var btnSubmit: Button
    private lateinit var btnSubmitEdit: Button
    private lateinit var btnAddProduct: Button
    private lateinit var btnStartSelection: Button
    private lateinit var btnCancelSelection: Button
    private lateinit var btnDeleteSelected: Button
    private lateinit var cbSelectAllProducts: CheckBox
    private lateinit var selectionBar: View
    private lateinit var etNewReason: EditText
    private lateinit var btnSubmitReason: Button

    private var selectedProductForReason: FakeProduct? = null
    private lateinit var repository: FakeCartRepository
    private var currentTabIndex = 0
    private var selectedImageUri: Uri? = null
    private var editingProduct: FakeProduct? = null
    private var latestProducts: List<FakeProduct> = emptyList()
    private val selectedProductIds = mutableSetOf<String>()
    private var selectionMode = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            if (cardEditProduct.visibility == View.VISIBLE) {
                clearImageTint(ivEditPhotoPreview)
                ivEditPhotoPreview.setImageURI(it)
            } else {
                clearImageTint(ivPhotoPreview)
                ivPhotoPreview.setImageURI(it)
            }
            processOcr(it)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.activity_fake_cart, container, false)
        repository = FakeCartRepository()
        initViews(view)
        setupListeners(view)
        observeCartItems()
        view.post { 
            selectTab(0)
            checkArgumentsForSharedData()
        }
        return view
    }

    private fun checkArgumentsForSharedData() {
        arguments?.let {
            if (it.getBoolean("SHOW_ADD_POPUP", false)) {
                val name = it.getString("EXTRA_NAME", "")
                val price = it.getInt("EXTRA_PRICE", 0)
                val url = it.getString("EXTRA_URL", "")
                val sharedText = it.getString("EXTRA_SHARED_TEXT", name)

                showAddProductPopup()
                etUrlInput.setText(url)
                etManualName.setText(name)
                if (price > 0) etManualPrice.setText(price.toString())
                
                selectTab(0) 
                
                if (url.isNotEmpty()) {
                    validateAndSaveProduct(autoSave = false, sharedText = sharedText) 
                } else {
                    Log.d("SHARE_DEBUG", "No URL extracted from shared text: $sharedText")
                }
                arguments?.clear()
            }
        }
    }

    private fun initViews(view: View) {
        cartItemsContainer = view.findViewById(R.id.cartItemsContainer)
        cardAddProduct = view.findViewById(R.id.cardAddProduct)
        cardEditProduct = view.findViewById(R.id.cardEditProduct)
        cardAddReason = view.findViewById(R.id.cardAddReason)
        dimView = view.findViewById(R.id.dimView)
        ivPhotoPreview = view.findViewById(R.id.ivPhotoPreview)
        ivEditPhotoPreview = view.findViewById(R.id.ivEditPhotoPreview)
        layoutPhotoPreview = view.findViewById(R.id.layoutPhotoPreview)

        tabUrl = view.findViewById(R.id.tabUrl); tabPhoto = view.findViewById(R.id.tabPhoto); tabManual = view.findViewById(R.id.tabManual)
        layoutUrlInput = view.findViewById(R.id.layoutUrlInput); layoutPhotoInput = view.findViewById(R.id.layoutPhotoInput); layoutManualInput = view.findViewById(R.id.layoutManualInput)

        etUrlInput = view.findViewById(R.id.etUrlInput)
        etManualName = view.findViewById(R.id.etManualName); etManualPrice = view.findViewById(R.id.etManualPrice)
        etEditUrl = view.findViewById(R.id.etEditUrl); etEditName = view.findViewById(R.id.etEditName); etEditPrice = view.findViewById(R.id.etEditPrice)
        spExpiry = view.findViewById(R.id.spExpiry)
        btnSubmit = view.findViewById(R.id.btnSubmit); btnSubmitEdit = view.findViewById(R.id.btnSubmitEdit)
        btnAddProduct = view.findViewById(R.id.btnAddProduct)
        btnStartSelection = view.findViewById(R.id.btnStartSelection)
        btnCancelSelection = view.findViewById(R.id.btnCancelSelection)
        btnDeleteSelected = view.findViewById(R.id.btnDeleteSelected)
        cbSelectAllProducts = view.findViewById(R.id.cbSelectAllProducts)
        selectionBar = view.findViewById(R.id.selectionBar)
        etNewReason = view.findViewById(R.id.etNewReason); btnSubmitReason = view.findViewById(R.id.btnSubmitReason)

        val expiryOptions = arrayOf("1일", "3일", "7일(권장)", "14일", "30일")
        spExpiry.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, expiryOptions)
        spExpiry.setSelection(2)
    }

    private fun setupListeners(view: View) {
        btnAddProduct.setOnClickListener { showAddProductPopup() }
        btnStartSelection.setOnClickListener { enterSelectionMode() }
        btnCancelSelection.setOnClickListener { exitSelectionMode() }
        cbSelectAllProducts.setOnClickListener { toggleSelectAll(cbSelectAllProducts.isChecked) }
        btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }
        view.findViewById<ImageView>(R.id.btnCloseCard)?.setOnClickListener { hidePopups() }
        view.findViewById<ImageView>(R.id.btnCloseEdit)?.setOnClickListener { hidePopups() }
        view.findViewById<ImageView>(R.id.btnCloseReason)?.setOnClickListener { hidePopups() }

        tabUrl.setOnClickListener { selectTab(0) }; tabPhoto.setOnClickListener { selectTab(1) }; tabManual.setOnClickListener { selectTab(2) }

        layoutPhotoPreview.setOnClickListener { pickImageLauncher.launch("image/*") }
        ivPhotoPreview.setOnClickListener { pickImageLauncher.launch("image/*") }
        layoutPhotoInput.setOnClickListener { pickImageLauncher.launch("image/*") }
        view.findViewById<View>(R.id.layoutEditPhoto)?.setOnClickListener { pickImageLauncher.launch("image/*") }

        btnSubmit.setOnClickListener { validateAndSaveProduct(autoSave = true) }
        btnSubmitEdit.setOnClickListener { saveEditedProduct() }
        btnSubmitReason.setOnClickListener { saveNewReason() }
    }

    private fun selectTab(index: Int) {
        currentTabIndex = index
        val activeBg = Color.BLACK; val inactiveBg = Color.parseColor("#F2F4F8")
        val activeTextColor = Color.WHITE; val inactiveTextColor = Color.parseColor("#666666")

        tabUrl.backgroundTintList = ColorStateList.valueOf(if (index == 0) activeBg else inactiveBg); tabUrl.setTextColor(if (index == 0) activeTextColor else inactiveTextColor)
        tabPhoto.backgroundTintList = ColorStateList.valueOf(if (index == 1) activeBg else inactiveBg); tabPhoto.setTextColor(if (index == 1) activeTextColor else inactiveTextColor)
        tabManual.backgroundTintList = ColorStateList.valueOf(if (index == 2) activeBg else inactiveBg); tabManual.setTextColor(if (index == 2) activeTextColor else inactiveTextColor)

        layoutUrlInput.visibility = View.VISIBLE
        layoutPhotoInput.visibility = if (index == 1) View.VISIBLE else View.GONE
        layoutManualInput.visibility = if (index == 0 || index == 2) View.VISIBLE else View.GONE
    }

    private fun observeCartItems() {
        viewLifecycleOwner.lifecycleScope.launch { repository.allProducts.collect { renderCartItems(it) } }
    }

    private fun renderCartItems(products: List<FakeProduct>) {
        latestProducts = products
        selectedProductIds.retainAll(products.map { it.id }.toSet())
        updateSelectionActions()
        cartItemsContainer.removeAllViews()
        val dec = DecimalFormat("#,###")
        val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }

        for (product in products) {
            val expiryCal = Calendar.getInstance().apply { timeInMillis = product.addedTime; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_YEAR, product.expiryDays) }
            val diffInDays = TimeUnit.MILLISECONDS.toDays(expiryCal.timeInMillis - today.timeInMillis)
            val dDayText = if (diffInDays <= 0L) "D-Day" else "D-$diffInDays"

            val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_cart_product, cartItemsContainer, false)
            val isSelected = selectedProductIds.contains(product.id)
            (itemView as? CardView)?.setCardBackgroundColor(if (isSelected) Color.parseColor("#EEF2FF") else Color.WHITE)
            itemView.findViewById<TextView>(R.id.tvRemainingDays)?.text = dDayText
            itemView.findViewById<TextView>(R.id.tvProductName)?.text = product.name
            itemView.findViewById<TextView>(R.id.tvProductPrice)?.text = "${dec.format(product.price)}원"
            itemView.findViewById<TextView>(R.id.tvProductUrl)?.text = product.url.ifEmpty { "URL 없음" }
            val ivProduct = itemView.findViewById<ImageView>(R.id.ivProduct)
            if (product.imageUrl.isNotEmpty()) Glide.with(this).load(product.imageUrl).placeholder(android.R.drawable.ic_menu_gallery).into(ivProduct)

            val reasonsText = if (product.reasons.isEmpty()) "아직 작성된 이유가 없습니다."
            else product.reasons.joinToString("\n") { "• $it" }
            itemView.findViewById<TextView>(R.id.tvReasonsSummary)?.text = reasonsText

            val cbSelect = itemView.findViewById<CheckBox>(R.id.cbSelectProduct)
            cbSelect.visibility = if (selectionMode) View.VISIBLE else View.GONE
            cbSelect.setOnCheckedChangeListener(null)
            cbSelect.isChecked = isSelected
            cbSelect.setOnCheckedChangeListener { _, _ -> toggleProductSelection(product.id) }

            itemView.setOnClickListener {
                openProductIfAllowed(product, diffInDays)
            }
            itemView.findViewById<TextView>(R.id.tvProductName)?.setOnClickListener {
                openProductIfAllowed(product, diffInDays)
            }

            itemView.findViewById<Button>(R.id.btnAddReason)?.apply {
                visibility = View.VISIBLE
                setOnClickListener { showAddReasonPopup(product) }
            }
            itemView.findViewById<ImageButton>(R.id.btnOptions)?.apply {
                visibility = View.VISIBLE
                setOnClickListener { v ->
                val popup = PopupMenu(requireContext(), v)
                popup.menu.add("수정"); popup.menu.add("삭제")
                popup.setOnMenuItemClickListener { menuItem ->
                    if (menuItem.title == "수정") showEditProductPopup(product)
                    else if (menuItem.title == "삭제") {
                        AlertDialog.Builder(requireContext()).setTitle("상품 삭제").setMessage("이 상품을 삭제하시겠습니까?").setPositiveButton("삭제") { _, _ -> viewLifecycleOwner.lifecycleScope.launch { repository.delete(product) } }.setNegativeButton("취소", null).show()
                    }
                    true
                }
                popup.show()
                }
            }
            cartItemsContainer.addView(itemView)
        }
    }

    private fun openProductIfAllowed(product: FakeProduct, diffInDays: Long) {
        val isDDay = diffInDays <= 0
        val reasonCount = product.reasons.size
        val hasEnoughReasons = reasonCount >= 5

        if ((isDDay || hasEnoughReasons) && product.url.isNotEmpty()) {
            try {
                val url = if (!product.url.startsWith("http")) "https://${product.url}" else product.url
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Product URL", product.url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "브라우저 연결이 어려워 주소를 복사했습니다.", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (product.url.isEmpty()) {
                Toast.makeText(requireContext(), "연결할 주소가 저장되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "숙고 기간이 지나지 않았습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleProductSelection(productId: String) {
        if (!selectionMode) return
        if (selectedProductIds.contains(productId)) selectedProductIds.remove(productId)
        else selectedProductIds.add(productId)
        renderCartItems(latestProducts)
    }

    private fun enterSelectionMode() {
        selectionMode = true
        renderCartItems(latestProducts)
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedProductIds.clear()
        renderCartItems(latestProducts)
    }

    private fun toggleSelectAll(selectAll: Boolean) {
        selectedProductIds.clear()
        if (selectAll) selectedProductIds.addAll(latestProducts.map { it.id })
        renderCartItems(latestProducts)
    }

    private fun updateSelectionActions() {
        selectionBar.visibility = if (selectionMode) View.VISIBLE else View.GONE
        btnStartSelection.visibility = if (selectionMode) View.GONE else View.VISIBLE
        btnAddProduct.visibility = if (selectionMode) View.GONE else View.VISIBLE
        cbSelectAllProducts.setOnCheckedChangeListener(null)
        cbSelectAllProducts.isChecked = latestProducts.isNotEmpty() && selectedProductIds.size == latestProducts.size
        cbSelectAllProducts.setOnClickListener { toggleSelectAll(cbSelectAllProducts.isChecked) }
        btnDeleteSelected.isEnabled = selectedProductIds.isNotEmpty()
        btnDeleteSelected.alpha = if (selectedProductIds.isNotEmpty()) 1f else 0.45f
        btnDeleteSelected.text = if (selectedProductIds.isEmpty()) "선택한 상품 삭제" else "선택한 상품 삭제 ${selectedProductIds.size}"
    }

    private fun confirmDeleteSelected() {
        val selectedProducts = latestProducts.filter { selectedProductIds.contains(it.id) }
        if (selectedProducts.isEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle("선택 상품 삭제")
            .setMessage("${selectedProducts.size}개 상품을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    selectedProducts.forEach { repository.delete(it) }
                    selectedProductIds.clear()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "선택한 상품을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                        updateSelectionActions()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showEditProductPopup(product: FakeProduct) {
        editingProduct = product; dimView.visibility = View.VISIBLE; cardEditProduct.visibility = View.VISIBLE
        etEditName.setText(product.name); etEditPrice.setText(product.price.toString())
        Glide.with(this).load(product.imageUrl).into(ivEditPhotoPreview)
    }

    private fun saveEditedProduct() {
        val product = editingProduct ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            repository.insert(product.copy(name = etEditName.text.toString().trim(), price = etEditPrice.text.toString().toIntOrNull() ?: product.price, imageUrl = selectedImageUri?.toString() ?: product.imageUrl))
            withContext(Dispatchers.Main) { hidePopups() }
        }
    }

    private fun showAddReasonPopup(product: FakeProduct) {
        selectedProductForReason = product; dimView.visibility = View.VISIBLE; cardAddReason.visibility = View.VISIBLE
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

    private fun validateAndSaveProduct(autoSave: Boolean, sharedText: String = etManualName.text.toString().trim()) {
        viewLifecycleOwner.lifecycleScope.launch {
            var name = etManualName.text.toString().trim()
            var price = etManualPrice.text.toString().toIntOrNull() ?: 0
            var url = etUrlInput.text.toString().trim()
            var imageUrl = ""
            val expiryDays = when(spExpiry.selectedItemPosition) { 0 -> 1; 1 -> 3; 2 -> 7; 3 -> 14; 4 -> 30; else -> 7 }

            if (url.isNotEmpty()) {
                try {
                    Log.d("SHARE_DEBUG", "Parsing product url=$url sharedText=$sharedText")
                    val parsed = ProductParser.parse(url, sharedText)
                    name = if (name.isEmpty() || name == "상품 정보 분석 중..." || name == "상품 정보를 가져오는 중...") (parsed.name ?: name) else name
                    price = if (price == 0) (parsed.price ?: 0) else price
                    imageUrl = parsed.imageUrl ?: ""
                    url = parsed.resolvedUrl?.takeIf { it.isNotEmpty() } ?: url
                    Log.d("SHARE_DEBUG", "Parsed result name=$name price=$price imageUrl=$imageUrl resolvedUrl=$url")
                    
                    withContext(Dispatchers.Main) {
                        etManualName.setText(name)
                        if (price > 0) etManualPrice.setText(price.toString())
                        if (imageUrl.isNotEmpty()) {
                            ivPhotoPreview.visibility = View.VISIBLE
                            clearImageTint(ivPhotoPreview)
                            Glide.with(this@FakeCartFragment).load(imageUrl).into(ivPhotoPreview)
                        }
                    }
                } catch (e: Exception) { Log.e("FakeCart", "Jsoup error", e) }
            }

            if (autoSave) {
                if (url.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "상품 URL이 없어 사진/가격을 가져올 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
                repository.insert(FakeProduct(id = UUID.randomUUID().toString(), name = name.ifEmpty { "상품명 없음" }, category = "기타", price = price, url = url, imageUrl = imageUrl, expiryDays = expiryDays, addedTime = System.currentTimeMillis()))
                withContext(Dispatchers.Main) { Toast.makeText(requireContext(), "장바구니에 추가되었습니다.", Toast.LENGTH_SHORT).show(); hidePopups() }
            }
        }
    }

    private fun showAddProductPopup() { editingProduct = null; clearInputs(); dimView.visibility = View.VISIBLE; cardAddProduct.visibility = View.VISIBLE }
    private fun hidePopups() { dimView.visibility = View.GONE; cardAddProduct.visibility = View.GONE; cardEditProduct.visibility = View.GONE; cardAddReason.visibility = View.GONE; clearInputs() }
    private fun clearInputs() { etUrlInput.setText(""); etManualName.setText(""); etManualPrice.setText(""); etNewReason.setText(""); restoreImageTint(ivPhotoPreview); ivPhotoPreview.setImageResource(android.R.drawable.ic_menu_camera); selectedImageUri = null }

    private fun clearImageTint(imageView: ImageView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) imageView.imageTintList = null
    }

    private fun restoreImageTint(imageView: ImageView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) imageView.imageTintList = ColorStateList.valueOf(Color.parseColor("#CCCCCC"))
    }

    private fun processOcr(uri: Uri) {
        val image = InputImage.fromFilePath(requireContext(), uri)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        recognizer.process(image).addOnSuccessListener { visionText -> 
            if (cardEditProduct.visibility != View.VISIBLE) { etManualName.setText(visionText.text.split("\n").firstOrNull() ?: ""); selectTab(2) }
        }
    }
}
