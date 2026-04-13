package com.example.allin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.allin.data.Payment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_setup)

        // 몰입 모드(Immersive Mode) 설정: 상단바 및 하단 내비게이션 바 숨기기
        hideSystemBars()

        initViews()
        updateUI()
        setupNavigation()
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // 시스템 바를 숨기고, 스와이프 시에만 잠시 나타나게 설정 (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
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
            startActivity(Intent(this, ReportActivity::class.java))
        }
        findViewById<CardView>(R.id.btnMonthlyReport).setOnClickListener {
            startActivity(Intent(this, ReportActivity::class.java))
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
        val amountStr = etInputAmount.text.toString()
        if (amountStr.isEmpty()) return

        val sharedPref = getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        val isAddPlan = tvPopupTitle.text == "이번 주 계획 추가"
        val amount = amountStr.toInt()

        if (isAddPlan) {
            sharedPref.edit().putInt("TOTAL_BUDGET", amount).apply()
        } else {
            val itemName = etInputName.text.toString().ifEmpty { "지출" }
            val category = spCategory.selectedItem.toString()
            
            val paymentsJson = sharedPref.getString("PAYMENTS_LIST", null)
            val type = object : TypeToken<MutableList<Payment>>() {}.type
            val payments: MutableList<Payment> = if (paymentsJson != null) {
                Gson().fromJson(paymentsJson, type)
            } else {
                mutableListOf()
            }
            
            payments.add(Payment(UUID.randomUUID().toString(), amount, category, System.currentTimeMillis(), itemName))
            
            sharedPref.edit().apply {
                putString("PAYMENTS_LIST", Gson().toJson(payments))
                putInt("USED_AMOUNT", payments.sumOf { it.amount })
                apply()
            }
        }
        updateUI()
        hidePopup()
    }

    private fun deletePlan() {
        getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE).edit().clear().apply()
        updateUI()
    }

    private fun updateUI() {
        val sharedPref = getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        val totalBudget = sharedPref.getInt("TOTAL_BUDGET", 0)
        val usedAmount = sharedPref.getInt("USED_AMOUNT", 0)

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

            val percent = (usedAmount.toFloat() / totalBudget.toFloat() * 100)
            findViewById<TextView>(R.id.tvUsagePercentVal).text = String.format("%.1f%%", percent)
            findViewById<ProgressBar>(R.id.pbBudgetUsage).progress = percent.toInt()

            renderCategoryList()
        }
    }

    private fun renderCategoryList() {
        llCategoryList.removeAllViews()
        val sharedPref = getSharedPreferences("BudgetPrefs", Context.MODE_PRIVATE)
        val paymentsJson = sharedPref.getString("PAYMENTS_LIST", null)
        val type = object : TypeToken<List<Payment>>() {}.type
        val payments: List<Payment> = if (paymentsJson != null) Gson().fromJson(paymentsJson, type) else emptyList()

        categories.forEach { category ->
            val catUsed = payments.filter { it.category == category }.sumOf { it.amount }
            val catGoal = 50000 

            val itemView = LayoutInflater.from(this).inflate(R.layout.item_budget_category, llCategoryList, false)
            itemView.findViewById<TextView>(R.id.tvCatName).text = category
            itemView.findViewById<TextView>(R.id.tvCatDetail).text = "${DecimalFormat("#,###").format(catUsed)} / ${DecimalFormat("#,###").format(catGoal)} 원"
            
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
        }
        findViewById<LinearLayout>(R.id.navFakeCart).setOnClickListener {
            val intent = Intent(this, FakeCartActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            startActivity(intent)
        }
    }

    // 화면 포커스가 다시 돌아왔을 때(드래그 후 등) 다시 시스템 바 숨기기
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }
}
