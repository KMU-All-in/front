package com.example.allin

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allin.data.AppDatabase
import com.example.allin.data.LockedApp
import com.example.allin.data.LockedAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isSelected: Boolean = false,
    val isShopping: Boolean = false
)

class AppSelectActivity : AppCompatActivity() {

    private lateinit var rvAppList: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var btnDone: Button
    
    private lateinit var repository: LockedAppRepository
    
    // 쇼핑 앱 판단 키워드
    private val shoppingKeywords = listOf(
        "쿠팡", "무신사", "당근", "11번가", "G마켓", "옥션", "쇼핑", "store", "market", "shop", 
        "coupang", "musinsa", "zigzag", "ably", "브랜디", "에이블리", "지그재그", "티몬", "위메프", 
        "네이버페이", "번개장터", "중고나라", "아이디어스", "컬리", "kurly", "인터파크", "롯데", "lotte", 
        "ssg", "hmall", "mustit", "머스트잇", "kream", "크림", "trenbe", "트렌비"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_select)

        val dao = AppDatabase.getDatabase(applicationContext).lockedAppDao()
        repository = LockedAppRepository(dao)

        rvAppList = findViewById(R.id.rvAppList)
        loadingProgress = findViewById(R.id.loadingProgress)
        btnDone = findViewById(R.id.btnDone)

        rvAppList.layoutManager = LinearLayoutManager(this)
        
        loadInstalledApps()

        btnDone.setOnClickListener {
            saveSelectedApps()
        }
    }

    private fun loadInstalledApps() {
        loadingProgress.visibility = View.VISIBLE
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentLockedPackages = repository.allLockedApps.first().map { it.packageName }
                val pm = packageManager
                val packages = pm.getInstalledPackages(0)
                val appInfos = mutableListOf<AppInfo>()

                for (pkg in packages) {
                    val app = pkg.applicationInfo ?: continue
                    val pkgName = pkg.packageName
                    
                    if (pm.getLaunchIntentForPackage(pkgName) != null && pkgName != packageName) {
                        val name = app.loadLabel(pm).toString()
                        
                        // 시스템 카테고리가 '쇼핑'(4)인 경우 체크
                        val isShoppingCategory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            app.category == 4 // 4는 ApplicationInfo.CATEGORY_SHOPPING의 값입니다.
                        } else false

                        // 키워드 매칭
                        val hasShoppingKeyword = shoppingKeywords.any { 
                            name.lowercase().contains(it) || pkgName.lowercase().contains(it) 
                        }

                        // 쇼핑 앱이거나, 이미 잠금 설정된 앱만 추가
                        if (isShoppingCategory || hasShoppingKeyword || currentLockedPackages.contains(pkgName)) {
                            appInfos.add(AppInfo(
                                name = name,
                                packageName = pkgName,
                                icon = app.loadIcon(pm),
                                isShopping = isShoppingCategory || hasShoppingKeyword,
                                isSelected = currentLockedPackages.contains(pkgName)
                            ))
                        }
                    }
                }

                appInfos.sortWith(compareByDescending<AppInfo> { it.isShopping }.thenBy { it.name })

                withContext(Dispatchers.Main) {
                    loadingProgress.visibility = View.GONE
                    rvAppList.adapter = AppAdapter(appInfos)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingProgress.visibility = View.GONE
                    Toast.makeText(this@AppSelectActivity, "목록 로딩 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveSelectedApps() {
        val adapter = rvAppList.adapter as? AppAdapter ?: return
        val selectedApps = adapter.getSelectedApps()
        
        loadingProgress.visibility = View.VISIBLE
        btnDone.isEnabled = false

        lifecycleScope.launch {
            try {
                val existingStates = repository.allLockedApps.first()
                    .associate { it.packageName to it.isActive }

                repository.updateAllLockedApps(
                    selectedApps.map {
                        LockedApp(
                            packageName = it.packageName,
                            appName = it.name,
                            isActive = existingStates[it.packageName] ?: true
                        )
                    }
                )
                withContext(Dispatchers.Main) {
                    loadingProgress.visibility = View.GONE
                    Toast.makeText(this@AppSelectActivity, "저장 완료!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingProgress.visibility = View.GONE
                    btnDone.isEnabled = true
                    Toast.makeText(this@AppSelectActivity, "저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    inner class AppAdapter(private val apps: List<AppInfo>) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {
        fun getSelectedApps() = apps.filter { it.isSelected }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_select, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.ivIcon.setImageDrawable(app.icon)
            holder.tvName.text = app.name
            holder.tvPackage.text = app.packageName
            holder.cbSelect.isChecked = app.isSelected
            holder.itemView.setOnClickListener {
                app.isSelected = !app.isSelected
                holder.cbSelect.isChecked = app.isSelected
            }
            if (app.isShopping) {
                holder.tvName.setTextColor(resources.getColor(R.color.purple_500, null))
            } else {
                holder.tvName.setTextColor(resources.getColor(android.R.color.black, null))
            }
        }
        override fun getItemCount() = apps.size
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
            val tvName: TextView = view.findViewById(R.id.tvAppName)
            val tvPackage: TextView = view.findViewById(R.id.tvPackageName)
            val cbSelect: CheckBox = view.findViewById(R.id.cbSelect)
        }
    }
}
