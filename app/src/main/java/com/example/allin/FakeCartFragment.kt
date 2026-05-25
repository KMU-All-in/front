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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.activity_fake_cart, container, false)
        repository = FakeCartRepository()
        initViews(view)
        setupListeners(view)
        observeCartItems()
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
        
        etEditUrl = view.findViewById(R.id.etEditUrl)
        etEditName = view.findViewById(R.id.etEditName)
        etEditPrice = view.findViewById(R.id.etEditPrice)
        
        spExpiry = view.findViewById(R.id.spExpiry)
        btnSubmit = view.findViewById(R.id.btnSubmit)
        btnSubmitEdit = view.findViewById(R.id.btnSubmitEdit)

        etNewReason = view.findViewById(R.id.etNewReason)
        btnSubmitReason = view.findViewById(R.id.btnSubmitReason)

        val expiryOptions = arrayOf("1일", "3일", "7일(권장)", "14일", "30일")
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

        dimView.setOnClickListener { }
    }

    private fun selectTab(index: Int) {
        currentTabIndex = index
        val activeBg = Color.BLACK
        val inactiveBg = Color.parseColor("#F2F4F8")
        val activeTextColor = Color.WHITE
        val inactiveTextColor = Color.parseColor("#666666")

        tabUrl.backgroundTintList = ColorStateList.valueOf(if (index == 0) activeBg else inactiveBg)
        tabUrl.setTextColor(if (index == 0) activeTextColor else inactiveTextColor)
        tabPhoto.backgroundTintList = ColorStateList.valueOf(if (index == 1) activeBg else inactiveBg)
        tabPhoto.setTextColor(if (index == 1) activeTextColor else inactiveTextColor)
        tabManual.backgroundTintList = ColorStateList.valueOf(if (index == 2) activeBg else inactiveBg)
        tabManual.setTextColor(if (index == 2) activeTextColor else inactiveTextColor)

        // URL 입력은 이제 항상 보이므로 생략
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
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        for (product in products) {
            val expiryCal = Calendar.getInstance().apply {
                timeInMillis = product.addedTime
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, product.expiryDays)
            }
            
            val diffMillis = expiryCal.timeInMillis - today.timeInMillis
            val diffInDays = TimeUnit.MILLISECONDS.toDays(diffMillis)
            val dDayText = if (diffInDays <= 0L) "D-Day" else "D-$diffInDays"

            val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_cart_product, cartItemsContainer, false)
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

            // [추가] 리스트 아이템 터치 시 URL 이동/복사 로직
            itemView.setOnClickListener {
                val isDDay = diffInDays <= 0
                val reasonCount = product.reasons.size
                val hasEnoughReasons = reasonCount >= 5

                if ((isDDay || hasEnoughReasons) && product.url.isNotEmpty()) {
                    try {
                        val url = if (!product.url.startsWith("http")) "https://${product.url}" else product.url
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        // 이동 실패 시 클립보드 복사
                        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Product URL", product.url)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "브라우저 연결이 어려워 주소를 복사했습니다.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (product.url.isEmpty()) {
                        Toast.makeText(requireContext(), "연결할 주소가 저장되어 있지 않습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        val message = "숙고가 부족합니다! (남은 기간: ${if(diffInDays > 0) diffInDays else 0}일 또는 남은 이유: ${5 - reasonCount}개)"
                        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            itemView.findViewById<ImageButton>(R.id.btnOptions)?.setOnClickListener { v ->
                val popup = PopupMenu(requireContext(), v)
                popup.menu.add("수정")
                popup.menu.add("삭제")
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.title) {
                        "수정" -> showEditProductPopup(product)
                        "삭제" -> {
                            val dialog = AlertDialog.Builder(requireContext())
                                .setTitle("상품 삭제")
                                .setMessage("이 상품을 삭제하시겠습니까?")
                                .setPositiveButton("삭제") { _, _ ->
                                    viewLifecycleOwner.lifecycleScope.launch { repository.delete(product) }
                                }
                                .setNegativeButton("취소", null)
                                .create()
                            dialog.setOnShowListener {
                                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#212121"))
                                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#212121"))
                            }
                            dialog.show()
                        }
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
        dimView.bringToFront()
        cardEditProduct.bringToFront()
        
        etEditUrl.setText(product.url)
        etEditName.setText(product.name)
        etEditPrice.setText(product.price.toString())
        Glide.with(this).load(product.imageUrl).into(ivEditPhotoPreview)
    }

    private fun saveEditedProduct() {
        val product = editingProduct ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val updatedProduct = product.copy(
                // 사용자가 확인/수정하지 못하도록 기존 URL을 그대로 유지
                url = product.url,
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
        dimView.bringToFront()
        cardAddReason.bringToFront()
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
            var name = etManualName.text.toString().trim()
            var price = etManualPrice.text.toString().toIntOrNull() ?: 0
            val url = etUrlInput.text.toString().trim()
            var imageUrl = ""
            val expiryDays = when(spExpiry.selectedItemPosition) {
                0 -> 1; 1 -> 3; 2 -> 7; 3 -> 14; 4 -> 30; else -> 7
            }

            if (url.isNotEmpty()) {
                try {
                    val parsed = ProductParser.parse(url)
                    name = if (name.isEmpty()) (parsed.name ?: "") else name
                    price = if (price == 0) (parsed.price ?: 0) else price
                    imageUrl = parsed.imageUrl ?: ""
                } catch (e: Exception) {
                    Log.e("FakeCart", "Parsing error", e)
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
                addedTime = System.currentTimeMillis()
            )
            repository.insert(product)
            withContext(Dispatchers.Main) { 
                Toast.makeText(requireContext(), "장바구니에 추가되었습니다.", Toast.LENGTH_SHORT).show()
                hidePopups() 
            }
        }
    }

    private fun showAddProductPopup() {
        editingProduct = null
        clearInputs()
        dimView.visibility = View.VISIBLE
        cardAddProduct.visibility = View.VISIBLE
        dimView.bringToFront()
        cardAddProduct.bringToFront()
    }

    private fun hidePopups() {
        dimView.visibility = View.GONE
        cardAddProduct.visibility = View.GONE
        cardEditProduct.visibility = View.GONE
        cardAddReason.visibility = View.GONE
        clearInputs()
    }

    private fun clearInputs() {
        etUrlInput.setText("")
        etManualName.setText("")
        etManualPrice.setText("")
        etNewReason.setText("")
        ivPhotoPreview.setImageResource(android.R.drawable.ic_menu_camera)
        selectedImageUri = null
    }

    private fun processOcr(uri: Uri) {
        val image = InputImage.fromFilePath(requireContext(), uri)
        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        recognizer.process(image).addOnSuccessListener { visionText -> parseOcrResult(visionText.text) }
    }

    private fun parseOcrResult(text: String) {
        if (cardEditProduct.visibility == View.VISIBLE) return
        etManualName.setText(text.split("\n").firstOrNull() ?: "")
        selectTab(2)
    }
}
