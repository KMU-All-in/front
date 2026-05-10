package com.example.allin

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

        setupRecyclerView()
        setupSortSpinner()
        observePayments()
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
        // strings.xml의 sort_options 사용
        val adapterSort = ArrayAdapter.createFromResource(
            this,
            R.array.sort_options,
            android.R.layout.simple_spinner_item
        )
        adapterSort.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSort.adapter = adapterSort
        
        // 정렬 변경 시 리스트 갱신을 위해 관찰
        // 간단한 구현을 위해 여기서는 리스너 없이 observePayments에서 처리하거나 
        // 선택 시점에 다시 fetch 할 수 있습니다. 
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
}
