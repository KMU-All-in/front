package com.example.allin

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allin.data.AppDatabase
import com.example.allin.data.Payment
import com.example.allin.data.PaymentRepository
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup

class PaymentHistoryActivity : AppCompatActivity() {

    private lateinit var repository: PaymentRepository
    private lateinit var adapter: PaymentAdapter
    private lateinit var rvPayments: RecyclerView
    private lateinit var spinnerSort: Spinner
    private lateinit var spinnerCategoryFilter: Spinner
    private var paymentsJob: Job? = null
    
    // 카테고리 목록 정의
    private val categories = arrayOf("식품/음료", "패션/의류", "뷰티/화장품", "전자기기", "도서/문구", "생활용품", "스포츠/레저", "기타")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_history)
        hideSystemBars()

        val dao = AppDatabase.getDatabase(this).paymentDao()
        repository = PaymentRepository(dao)

        setupToolbar()
        setupRecyclerView()
        setupSortSpinner()
        setupCategoryFilter()
        setupAddButton()

        // [추가] 화면 진입 시 Firestore에서 기존 데이터 동기화
        lifecycleScope.launch {
            repository.fetchPaymentsFromFirestore()
            observePayments()
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar) ?: return
        setSupportActionBar(toolbar)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        toolbar.setOnLongClickListener {
            showClearAllConfirmDialog()
            true
        }
    }

    private fun setupRecyclerView() {
        rvPayments = findViewById(R.id.rvPayments)
        adapter = PaymentAdapter(
            onEdit = { payment -> showEditDialog(payment) },
            onDelete = { payment -> showDeleteConfirmDialog(payment) }
        )
        rvPayments.layoutManager = LinearLayoutManager(this)
        rvPayments.adapter = adapter
    }

    private fun setupSortSpinner() {
        spinnerSort = findViewById(R.id.spinnerSort)
        val adapterSort = ArrayAdapter.createFromResource(
            this,
            R.array.sort_options,
            android.R.layout.simple_spinner_item
        )
        adapterSort.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSort.adapter = adapterSort
        
        spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                observePayments() 
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun setupCategoryFilter() {
        spinnerCategoryFilter = findViewById(R.id.spinnerCategoryFilter)
        val filterItems = arrayOf("전체 카테고리") + categories
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, filterItems)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategoryFilter.adapter = categoryAdapter

        spinnerCategoryFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                observePayments()
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun setupAddButton() {
        findViewById<FloatingActionButton>(R.id.fabAdd)?.setOnClickListener {
            showAddDialog()
        }
    }

    private fun observePayments() {
        if (!::spinnerSort.isInitialized || !::spinnerCategoryFilter.isInitialized) return

        paymentsJob?.cancel()
        paymentsJob = lifecycleScope.launch {
            repository.allPayments.collectLatest { payments ->
                val selectedCategory = spinnerCategoryFilter.selectedItem?.toString()
                val filteredList = if (selectedCategory == null || selectedCategory == "전체 카테고리") {
                    payments
                } else {
                    payments.filter { it.category == selectedCategory }
                }

                val sortedList = when (spinnerSort.selectedItemPosition) {
                    1 -> filteredList.sortedByDescending { it.amount }
                    2 -> filteredList.sortedBy { it.amount }
                    else -> filteredList.sortedByDescending { it.date }
                }
                adapter.submitList(sortedList)
            }
        }
    }

    private fun showAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_payment, null)

        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvDialogSubtitle)
        val etStore = view.findViewById<EditText>(R.id.etStoreName)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val spCategory = view.findViewById<Spinner>(R.id.spCategory)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitPayment)

        tvTitle.text = "지출 내역 추가"
        tvSubtitle.text = "새로운 지출 정보를 입력해주세요."
        btnSubmit.text = "추가"

        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spCategory.adapter = categoryAdapter
        spCategory.setSelection(categories.indexOf("기타").takeIf { it >= 0 } ?: 0)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val width = (resources.displayMetrics.widthPixels * 0.86).toInt()
            dialog.window?.setLayout(
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSubmit.setOnClickListener {
            val store = etStore.text.toString().trim()
            val amount = etAmount.text.toString().replace(",", "").toIntOrNull() ?: 0
            val category = spCategory.selectedItem.toString()

            if (store.isNotEmpty() && amount > 0) {
                lifecycleScope.launch {
                    repository.insert(
                        Payment(
                            storeName = store,
                            amount = amount,
                            category = category,
                            date = System.currentTimeMillis(),
                            itemName = "직접 입력"
                        ),
                        this@PaymentHistoryActivity
                    )

                    Toast.makeText(this@PaymentHistoryActivity, "추가되었습니다.", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(this, "상점명과 금액을 정확히 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showEditDialog(payment: Payment) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_payment, null)

        val tvTitle = view.findViewById<TextView>(R.id.tvDialogTitle)
        val tvSubtitle = view.findViewById<TextView>(R.id.tvDialogSubtitle)
        val etStore = view.findViewById<EditText>(R.id.etStoreName)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        val spCategory = view.findViewById<Spinner>(R.id.spCategory)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitPayment)

        tvTitle.text = "지출 내역 수정"
        tvSubtitle.text = "지출 정보를 수정해주세요."
        btnSubmit.text = "완료"

        etStore.setText(payment.storeName)
        etAmount.setText(payment.amount.toString())

        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spCategory.adapter = categoryAdapter

        val categoryIndex = categories.indexOf(payment.category)
        if (categoryIndex >= 0) {
            spCategory.setSelection(categoryIndex)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val width = (resources.displayMetrics.widthPixels * 0.86).toInt()
            dialog.window?.setLayout(
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSubmit.setOnClickListener {
            val newStore = etStore.text.toString().trim()
            val newAmount = etAmount.text.toString().toIntOrNull() ?: payment.amount
            val newCategory = spCategory.selectedItem.toString()

            if (newStore.isEmpty() || newAmount <= 0) {
                Toast.makeText(this, "상점명과 금액을 정확히 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updatedPayment = payment.copy(
                storeName = newStore,
                amount = newAmount,
                category = newCategory
            )

            lifecycleScope.launch {
                repository.update(payment, updatedPayment, this@PaymentHistoryActivity)
                Toast.makeText(this@PaymentHistoryActivity, "수정되었습니다.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showDeleteConfirmDialog(payment: Payment) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("결제 내역 삭제")
            .setMessage("이 결제 내역을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    repository.delete(payment, this@PaymentHistoryActivity)
                    Toast.makeText(this@PaymentHistoryActivity, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#F44336"))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#8B94A8"))
        }

        dialog.show()
    }

    private fun showClearAllConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("전체 내역 초기화")
            .setMessage("테스트를 위해 모든 내역을 삭제하고 0원으로 만드시겠습니까?")
            .setPositiveButton("모두 삭제") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteAll()
                    Toast.makeText(this@PaymentHistoryActivity, "모든 내역이 초기화되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}
