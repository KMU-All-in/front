package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.DecimalFormat
import java.util.*
import android.util.Log

class BudgetFragment : Fragment() {

    private lateinit var cardAddPlan: CardView
    private lateinit var dimView: View
    private lateinit var tvPopupTitle: TextView
    private lateinit var tvLabel1: TextView
    private lateinit var tvLabel2: TextView
    private lateinit var tvLabel3: TextView
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_budget_setup, container, false)

        initViews(view)
        observeData()

        return view
    }

    private fun initViews(view: View) {
        cardAddPlan = view.findViewById(R.id.cardAddPlan)
        dimView = view.findViewById(R.id.dimView)
        tvPopupTitle = view.findViewById(R.id.tvPopupTitle)
        tvLabel1 = view.findViewById(R.id.tvLabel1)
        tvLabel2 = view.findViewById(R.id.tvLabel2)
        tvLabel3 = view.findViewById(R.id.tvLabel3)
        etInputAmount = view.findViewById(R.id.etInputAmount)
        etInputName = view.findViewById(R.id.etInputName)
        spCategory = view.findViewById(R.id.spCategory)
        btnSaveBudget = view.findViewById(R.id.btnSaveBudget)

        cardNoPlan = view.findViewById(R.id.cardNoPlan)
        cardBudgetOverview = view.findViewById(R.id.cardBudgetOverview)
        tvCategoryLabel = view.findViewById(R.id.tvCategoryLabel)
        llCategoryList = view.findViewById(R.id.llCategoryList)
        btnOpenAddPlanBottom = view.findViewById(R.id.btnOpenAddPlanBottom)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        spCategory.adapter = adapter

        view.findViewById<ImageView>(R.id.btnCloseCard).setOnClickListener { hidePopup() }
        view.findViewById<LinearLayout>(R.id.btnViewReport).setOnClickListener {
            startActivity(Intent(requireContext(), ReportActivity::class.java))
        }
        view.findViewById<CardView>(R.id.btnMonthlyReport).setOnClickListener {
            startActivity(Intent(requireContext(), ReportActivity::class.java))
        }
        view.findViewById<Button>(R.id.btnOpenAddPlanEmpty).setOnClickListener { showAddPlanPopup() }
        btnOpenAddPlanBottom.setOnClickListener { showAddPlanPopup() }
        view.findViewById<ImageView>(R.id.btnEditBudget).setOnClickListener { showAddPlanPopup() }
        view.findViewById<ImageView>(R.id.btnDeleteBudget).setOnClickListener { deletePlan() }

        btnSaveBudget.setOnClickListener { saveData() }

        // ❌ 네비게이션 리스너는 MainActivity에서 관리하므로 삭제

        dimView.setOnClickListener { }
    }

    private fun showAddPlanPopup() {
        tvPopupTitle.text = "이번 주 계획 추가"
        tvLabel1.text = "이번 주 총 예산"
        etInputAmount.hint = "예: 200000"
        tvLabel2.visibility = View.GONE
        etInputName.visibility = View.GONE
        tvLabel3.visibility = View.GONE
        spCategory.visibility = View.GONE
        dimView.visibility = View.VISIBLE
        cardAddPlan.visibility = View.VISIBLE
        dimView.bringToFront()
        cardAddPlan.bringToFront()
    }

    private fun showAddExpensePopup(category: String) {
        tvPopupTitle.text = "지출 추가"
        tvLabel1.text = "지출 금액"
        etInputAmount.hint = "금액을 입력하세요"
        tvLabel2.visibility = View.VISIBLE
        etInputName.visibility = View.VISIBLE
        tvLabel3.visibility = View.VISIBLE
        spCategory.visibility = View.VISIBLE

        val adapter = spCategory.adapter as ArrayAdapter<String>
        val pos = adapter.getPosition(category)
        if (pos >= 0) spCategory.setSelection(pos)

        dimView.visibility = View.VISIBLE
        cardAddPlan.visibility = View.VISIBLE

        dimView.bringToFront()
        cardAddPlan.bringToFront()
    }

    private fun hidePopup() {
        dimView.visibility = View.GONE
        cardAddPlan.visibility = View.GONE
        etInputAmount.text.clear()
        etInputName.text.clear()
    }

    private fun saveData() {
        val currentUser = auth.currentUser ?: return
        val amountStr = etInputAmount.text.toString().trim()
        if (amountStr.isEmpty()) return

        val amount = amountStr.toLongOrNull()
        if (amount == null) {
            Toast.makeText(requireContext(), "올바른 숫자만 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (tvPopupTitle.text == "이번 주 계획 추가") {
            db.collection("users").document(currentUser.uid)
                .collection("reports")
                .orderBy("start_date", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { snapshots ->
                    if (!snapshots.isEmpty) {
                        snapshots.documents[0].reference.update("budget_usage", amount)
                            .addOnSuccessListener {
                                savePlanToLocal(amount)
                            }
                    } else {
                        val newReport = hashMapOf(
                            "budget_usage" to amount,
                            "total_spent" to 0L,
                            "start_date" to Timestamp.now(),
                            "end_date" to Timestamp(Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000)),
                            "report_type" to "weekly"
                        )
                        db.collection("users").document(currentUser.uid).collection("reports").add(newReport)
                            .addOnSuccessListener {
                                savePlanToLocal(amount)
                            }
                    }
                }
        } else {
            val storeName = etInputName.text.toString().ifEmpty { "지출" }
            val category = spCategory.selectedItem?.toString() ?: "기타"

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
                    // 🌟 [안전빵 추가] 주간 계획 리포트 문서가 진짜로 존재할 때만 업데이트를 실행합니다!
                    if (!reports.isEmpty) {
                        reports.documents[0].reference.update("total_spent", total)
                        Log.d("BudgetFragment", "총 지출액 업데이트 완료: ${total}원")
                    } else {
                        // 계획서가 없다면 튕기지 않고 로그만 찍고 안전하게 넘어갑니다.
                        Log.d("BudgetFragment", "서버에 주간 계획서가 없어 지출 합산 업데이트를 건너뜁니다.")
                    }
                }
                .addOnFailureListener {
                    Log.e("BudgetFragment", "리포트 조회 실패", it)
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

        val sharedPref = requireContext().getSharedPreferences("AppLockPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putBoolean("has_weekly_plan", false)
            putInt("weekly_budget", 0)
            commit() // 즉시 물리 반영
        }
        Toast.makeText(requireContext(), "계획이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun observeData() {
        val currentUser = auth.currentUser ?: return

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

        db.collection("users").document(currentUser.uid)
            .collection("transactions")
            .addSnapshotListener { snapshots, _ ->
                val transactions = snapshots?.documents ?: emptyList()
                renderCategoryList(transactions)
            }
    }

    private fun updateUI(totalBudget: Long, usedAmount: Long) {
        val view = view ?: return
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
            view.findViewById<TextView>(R.id.tvTotalBudgetVal).text = "${dec.format(totalBudget)}원"
            view.findViewById<TextView>(R.id.tvUsedAmountVal).text = "${dec.format(usedAmount)}원"
            view.findViewById<TextView>(R.id.tvRemainingAmountVal).text = "${dec.format(totalBudget - usedAmount)}원"

            val percent = if (totalBudget > 0) (usedAmount.toFloat() / totalBudget.toFloat() * 100) else 0f
            view.findViewById<TextView>(R.id.tvUsagePercentVal).text = String.format("%.1f%%", percent)
            view.findViewById<ProgressBar>(R.id.pbBudgetUsage).progress = percent.toInt()
        }
    }

    private fun renderCategoryList(transactions: List<com.google.firebase.firestore.DocumentSnapshot>) {
        val context = context ?: return
        
        llCategoryList.removeAllViews()
        val dec = DecimalFormat("#,###")

        categories.forEach { category ->
            val catUsed = transactions.filter { it.getString("category") == category }
                .sumOf { it.getLong("amount") ?: 0L }
            val catGoal = 50000 // 예시 목표액

            val itemView = LayoutInflater.from(requireContext()).inflate(R.layout.item_budget_category, llCategoryList, false)
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

    private fun savePlanToLocal(amount: Long) {
        val sharedPref = requireContext().getSharedPreferences("AppLockPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().apply {
            putBoolean("has_weekly_plan", true)
            putInt("weekly_budget", amount.toInt())
            commit() // 즉시 물리 파일에 저장하여 서비스가 바로 읽을 수 있게 함
        }
        android.util.Log.d("BudgetFragment", "로컬 주머니(AppLockPrefs)에 주간 계획 true 동기화 완료!")
    }
}