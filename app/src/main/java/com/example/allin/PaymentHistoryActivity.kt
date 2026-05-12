package com.example.allin

import android.os.Bundle
import android.view.LayoutInflater
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PaymentHistoryActivity : AppCompatActivity() {

    private lateinit var repository: PaymentRepository
    private lateinit var adapter: PaymentAdapter
    private lateinit var rvPayments: RecyclerView
    private lateinit var spinnerSort: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_history)

        val dao = AppDatabase.getDatabase(this).paymentDao()
        repository = PaymentRepository(dao)

        setupToolbar()
        setupRecyclerView()
        setupSortSpinner()
        observePayments()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar) ?: return
        setSupportActionBar(toolbar)
        
        // 뒤로가기 버튼 활성화
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // 전체 삭제 기능 (툴바 제목 길게 누르기) - 테스트용 데이터 초기화
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
            override fun onItemSelected(p0: AdapterView<*>?, p1: android.view.View?, p2: Int, p3: Long) {
                observePayments() 
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun observePayments() {
        lifecycleScope.launch {
            repository.allPayments.collectLatest { payments ->
                val sortedList = when (spinnerSort.selectedItemPosition) {
                    1 -> payments.sortedByDescending { it.amount }
                    2 -> payments.sortedBy { it.amount }
                    else -> payments.sortedByDescending { it.date }
                }
                adapter.submitList(sortedList)
            }
        }
    }

    private fun showEditDialog(payment: Payment) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_payment, null)
        val etStore = view.findViewById<EditText>(R.id.etStoreName)
        val etAmount = view.findViewById<EditText>(R.id.etAmount)
        
        etStore.setText(payment.storeName)
        etAmount.setText(payment.amount.toString())

        AlertDialog.Builder(this)
            .setTitle("결제 내역 수정")
            .setView(view)
            .setPositiveButton("완료") { _, _ ->
                val newStore = etStore.text.toString()
                val newAmount = etAmount.text.toString().toIntOrNull() ?: payment.amount
                
                lifecycleScope.launch {
                    repository.update(payment.copy(storeName = newStore, amount = newAmount))
                    Toast.makeText(this@PaymentHistoryActivity, "수정되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showDeleteConfirmDialog(payment: Payment) {
        AlertDialog.Builder(this)
            .setTitle("결제 내역 삭제")
            .setMessage("이 결제 내용을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    repository.delete(payment)
                    Toast.makeText(this@PaymentHistoryActivity, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
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
}
