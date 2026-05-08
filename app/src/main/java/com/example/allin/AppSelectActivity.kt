package com.example.allin

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
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
    
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val shoppingKeywords = listOf(
        "쿠팡", "무신사", "당근", "11번가", "G마켓", "옥션", "쇼핑", "store", "market", "shop", 
        "coupang", "musinsa", "zigzag", "ably", "브랜디", "에이블리", "지그재그", "티몬", "위메프", 
        "네이버페이", "번개장터", "중고나라", "아이디어스", "컬리", "kurly"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_select)

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
        val currentUser = auth.currentUser
        
        if (currentUser == null) {
            loadingProgress.visibility = View.GONE
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("AppSelectActivity", "Firestore 로드 시도 - UID: ${currentUser.uid}")
        
        db.collection("users").document(currentUser.uid).get()
            .addOnCompleteListener { task ->
                val currentLockedApps = if (task.isSuccessful) {
                    val apps = task.result?.get("locked_apps") as? List<String> ?: emptyList()
                    Log.d("AppSelectActivity", "기존 데이터 로드 성공: $apps")
                    apps
                } else {
                    val e = task.exception as? FirebaseFirestoreException
                    val errorCode = e?.code?.name ?: "UNKNOWN"
                    Log.e("AppSelectActivity", "로드 실패: $errorCode", task.exception)
                    Toast.makeText(this, "데이터 로드 실패: [$errorCode] ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                    emptyList()
                }
                
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val pm = packageManager
                        val packages = pm.getInstalledPackages(0)
                        val appInfos = mutableListOf<AppInfo>()

                        for (pkg in packages) {
                            val app = pkg.applicationInfo ?: continue
                            val pkgName = pkg.packageName ?: continue
                            
                            if (pm.getLaunchIntentForPackage(pkgName) != null && pkgName != packageName) {
                                val name = app.loadLabel(pm).toString()
                                val isShopping = shoppingKeywords.any { 
                                    name.lowercase().contains(it) || pkgName.lowercase().contains(it) 
                                }
                                
                                appInfos.add(AppInfo(
                                    name = name,
                                    packageName = pkgName,
                                    icon = app.loadIcon(pm),
                                    isShopping = isShopping,
                                    isSelected = currentLockedApps.contains(pkgName)
                                ))
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
    }

    private fun saveSelectedApps() {
        val currentUser = auth.currentUser ?: return
        val adapter = rvAppList.adapter as? AppAdapter ?: return
        val selectedPackages = adapter.getSelectedPackages()
        
        if (selectedPackages.isEmpty()) {
            Toast.makeText(this, "잠글 앱을 하나 이상 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        loadingProgress.visibility = View.VISIBLE
        btnDone.isEnabled = false

        val data = mapOf("locked_apps" to selectedPackages)
        Log.d("AppSelectActivity", "저장 시도 - 데이터: $data")

        db.collection("users").document(currentUser.uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                loadingProgress.visibility = View.GONE
                Toast.makeText(this, "잠금 앱 리스트가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                loadingProgress.visibility = View.GONE
                btnDone.isEnabled = true
                val firestoreException = e as? FirebaseFirestoreException
                val errorCode = firestoreException?.code?.name ?: "ERROR"
                Log.e("AppSelectActivity", "저장 실패: $errorCode", e)
                Toast.makeText(this, "저장 실패: [$errorCode] ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    inner class AppAdapter(private val apps: List<AppInfo>) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {
        
        fun getSelectedPackages() = apps.filter { it.isSelected }.map { it.packageName }

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
