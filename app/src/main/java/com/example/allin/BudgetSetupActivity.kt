package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.DecimalFormat
import java.util.*

class BudgetSetupActivity : AppCompatActivity() {

    private lateinit var cardAddPlan: CardView
    private lateinit var dimView: View
    private lateinit var tvPopupTitle: TextView
    private lateinit var tvLabel1: TextView
    private lateinit var tvLabel2: TextView
    private lateinit var etInputAmount: EditText
    private lateinit var etInputName: EditText
    private lateinit var spCategory: Spinner
    private lateinit var btnSaveBudget: Button

    private lateinit var cardNoPlan: CardView
    private lateinit var cardBudgetOverview: CardView
    private lateinit var tvCategoryLabel: TextView
    private lateinit var llCategoryList: LinearLayout
    private lateinit var btnOpenAddPlanBottom: Button

    private val categories = arrayOf("패션/의류", "뷰티/화장품", "전자기기", "도서/문구", "식품/음료", "생활용품", "스포츠/레저", "기타")
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_setup)

        hideSystemBars()
        initViews()
        setupNavigation()
        observeData()
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun initViews() {
        cardAddPlan = findViewById(R.id.cardAddPlan)
        dimView = findViewById(R.id.dimView)
        tvPopupTitle = findViewById(R.id.tvPopupTitle)
        tvLabel1 = findViewById(R.id.tvLabel1)
        tvLabel2 = findViewById(R.id.tvLabel2)
        etInputAmount = findViewById(R.id.etInputAmount)
        etInputName = findViewById(R.id.etInputName)
        spCategory = findViewById(R.id.spCategory)
        btnSaveBudget = findViewById(R.id.btnSaveBudget)

        cardNoPlan = findViewById(R.id.cardNoPlan)
        cardBudgetOverview = findViewById(R.id.cardBudgetOverview)
        tvCategoryLabel = findViewById(R.id.tvCategoryLabel)
        llCategoryList = findViewById(R.id.llCategoryList)
        btnOpenAddPlanBottom = findViewById(R.id.btnOpenAddPlanBottom)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        spCategory.adapter = adapter

        findViewById<ImageView>(R.id.btnCloseCard).setOnClickListener { hidePopup() }
        findViewById<LinearLayout>(R.id.btnViewReport).setOnClickListener {
            val intent = Intent(this, ReportActivity::class.java)
            startActivity(intent)
        }
        findViewById<CardView>(R.id.btnMonthlyReport).setOnClickListener {
            val intent = Intent(this, ReportActivity::class.java)
            startActivity(intent)
        }
        findViewById<Button>(R.id.btnOpenAddPlanEmpty).setOnClickListener { showAddPlanPopup() }
        btnOpenAddPlanBottom.setOnClickListener { showAddPlanPopup() }
        findViewById<ImageView>(R.id.btnEditBudget).setOnClickListener { showAddPlanPopup() }
        findViewById<ImageView>(R.id.btnDeleteBudget).setOnClickListener { deletePlan() }

        btnSaveBudget.setOnClickListener { saveData() }
    }

    private fun showAddPlanPopup() {
        tvPopupTitle.text = "이번 주 계획 추가"
        tvLabel1.text = "이번 주 총 예산"
        etInputAmount.hint = "예: 200000"
        tvLabel2.visibility = View.GONE
        etInputName.visibility = View.GONE
        dimView.visibility = View.VISIBLE
        cardAddPlan.visibility = View.VISIBLE
    }

    private fun showAddExpensePopup(category: String) {
        tvPopupTitle.text = "지출 추가"
        tvLabel1.text = "지출 금액"
        etInputAmount.hint = "금액을 입력하세요"
        tvLabel2.visibility = View.VISIBLE
        etInputName.visibility = View.VISIBLE
        tvLabel2.text = "항목 명"
        etInputName.hint = "예: 점심 식사"
        
        val adapter = spCategory.adapter as ArrayAdapter<String>
        val pos = adapter.getPosition(category)
        if (pos >= 0) spCategory.setSelection(pos)

        dimView.visibility = View.VISIBLE
        cardAddPlan.visibility = View.VISIBLE
    }

    private fun hidePopup() {
        dimView.visibility = View.GONE
        cardAddPlan.visibility = View.GONE
        etInputAmount.text.clear()
        etInputName.text.clear()
    }

    private fun saveData() {
        val currentUser = auth.currentUser ?: return
        val amountStr = etInputAmount.text.toString()
        if (amountStr.isEmpty()) return
        val amount = amountStr.toLong()

        if (tvPopupTitle.text == "이번 주 계획 추가") {
            // 예산 설정 (가장 최근 리포트 문서 업데이트 또는 생성)
            db.collection("users").document(currentUser.uid)
                .collection("reports")
                .orderBy("start_date", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshots ->
                    if (!snapshots.isEmpty) {
                        snapshots.documents[0].reference.update("budget_usage", amount)
                    } else {
                        // 리포트가 없는 경우 새로 생성 (기본 예시 날짜)
                        val newReport = hashMapOf(
                            "budget_usage" to amount,
                            "total_spent" to 0,
                            "start_date" to Timestamp.now(),
                            "end_date" to Timestamp(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000)),
                            "report_type" to "weekly"
                        )
                        db.collection("users").document(currentUser.uid).collection("reports").add(newReport)
                    }
                }
        } else {
            // 지출 추가 (transactions 컬렉션에 추가)
            val storeName = etInputName.text.toString().ifEmpty { "지출" }
            val category = spCategory.selectedItem.toString()
            
            val transaction = hashMapOf(
                "amount" to amount,
                "category" to category,
                "store_name" to storeName,
                "transaction_date" to Timestamp.now(),
                "payment_method" to "기타"
            )
            
            db.collection("users").document(currentUser.uid).collection("transactions")
                .add(transaction)
                .addOnSuccessListener {
                    // 지출 추가 후 해당 리포트의 total_spent도 업데이트하는 로직이 필요할 수 있습니다.
                    updateTotalSpent(currentUser.uid)
                }
        }
        hidePopup()
    }

    private fun updateTotalSpent(uid: String) {
        db.collection("users").document(uid).collection("transactions").get().addOnSuccessListener { snapshots ->
            val total = snapshots.documents.sumOf { it.getLong("amount") ?: 0L }
            db.collection("users").document(uid).collection("reports")
                .orderBy("start_date", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { reports ->
                    if (!reports.isEmpty) {
                        reports.documents[0].reference.update("total_spent", total)
                    }
                }
        }
    }

    private fun deletePlan() {
        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid).collection("reports").get().addOnSuccessListener { 
            for (doc in it) doc.reference.delete()
        }
        db.collection("users").document(currentUser.uid).collection("transactions").get().addOnSuccessListener { 
            for (doc in it) doc.reference.delete()
        }
    }

    private fun observeData() {
        val currentUser = auth.currentUser ?: return
        
        // 리포트 데이터 감시 (예산 및 총 소비액)
        db.collection("users").document(currentUser.uid)
            .collection("reports")
            .orderBy("start_date", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshots, _ ->
                if (snapshots != null && !snapshots.isEmpty) {
                    val doc = snapshots.documents[0]
                    val budget = doc.getLong("budget_usage") ?: 0L
                    val spent = doc.getLong("total_spent") ?: 0L
                    updateUI(budget, spent)
                } else {
                    updateUI(0, 0)
                }
            }

        // 지출 내역 감시 (카테고리별 리스트)
        db.collection("users").document(currentUser.uid)
            .collection("transactions")
            .addSnapshotListener { snapshots, _ ->
                val transactions = snapshots?.documents ?: emptyList()
                renderCategoryList(transactions)
            }
    }

    private fun updateUI(totalBudget: Long, usedAmount: Long) {
        if (totalBudget <= 0) {
            cardNoPlan.visibility = View.VISIBLE
            cardBudgetOverview.visibility = View.GONE
            tvCategoryLabel.visibility = View.GONE
            llCategoryList.visibility = View.GONE
            btnOpenAddPlanBottom.visibility = View.GONE
        } else {
            cardNoPlan.visibility = View.GONE
            cardBudgetOverview.visibility = View.VISIBLE
            tvCategoryLabel.visibility = View.VISIBLE
            llCategoryList.visibility = View.VISIBLE
            btnOpenAddPlanBottom.visibility = View.VISIBLE
            btnOpenAddPlanBottom.text = "계획 수정하기"

            val dec = DecimalFormat("#,###")
            findViewById<TextView>(R.id.tvTotalBudgetVal).text = "${dec.format(totalBudget)}원"
            findViewById<TextView>(R.id.tvUsedAmountVal).text = "${dec.format(usedAmount)}원"
            findViewById<TextView>(R.id.tvRemainingAmountVal).text = "${dec.format(totalBudget - usedAmount)}원"

            val percent = if (totalBudget > 0) (usedAmount.toFloat() / totalBudget.toFloat() * 100) else 0f
            findViewById<TextView>(R.id.tvUsagePercentVal).text = String.format("%.1f%%", percent)
            findViewById<ProgressBar>(R.id.pbBudgetUsage).progress = percent.toInt()
        }
    }

    private fun renderCategoryList(transactions: List<com.google.firebase.firestore.DocumentSnapshot>) {
        llCategoryList.removeAllViews()
        val dec = DecimalFormat("#,###")

        categories.forEach { category ->
            val catUsed = transactions.filter { it.getString("category") == category }
                                     .sumOf { it.getLong("amount") ?: 0L }
            val catGoal = 50000 // 예시 목표액

            val itemView = LayoutInflater.from(this).inflate(R.layout.item_budget_category, llCategoryList, false)
            itemView.findViewById<TextView>(R.id.tvCatName).text = category
            itemView.findViewById<TextView>(R.id.tvCatDetail).text = "${dec.format(catUsed)} / ${dec.format(catGoal)} 원"
            
            val pb = itemView.findViewById<ProgressBar>(R.id.pbCatUsage)
            pb.progress = if (catGoal > 0) (catUsed.toFloat() / catGoal * 100).toInt() else 0
            
            itemView.findViewById<CardView>(R.id.btnAddExpense).setOnClickListener {
                showAddExpensePopup(category)
            }
            llCategoryList.addView(itemView)
        }
    }

    private fun setupNavigation() {
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        findViewById<LinearLayout>(R.id.navFakeCart).setOnClickListener {
            val intent = Intent(this, FakeCartActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
}
