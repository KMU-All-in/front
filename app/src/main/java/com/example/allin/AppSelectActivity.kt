package com.example.allin

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
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
    
    private val shoppingKeywords = listOf("쿠팡", "무신사", "당근", "11번가", "G마켓", "옥션", "쇼핑", "store", "market", "shop", "coupang", "musinsa", "zigzag", "ably")

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
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val appInfos = mutableListOf<AppInfo>()

            for (app in packages) {
                // 시스템 앱 제외하고 런처 아이콘이 있는 앱만 포함
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    val name = app.loadLabel(pm).toString()
                    val isShopping = shoppingKeywords.any { name.lowercase().contains(it) || app.packageName.lowercase().contains(it) }
                    
                    appInfos.add(AppInfo(
                        name = name,
                        packageName = app.packageName,
                        icon = app.loadIcon(pm),
                        isShopping = isShopping
                    ))
                }
            }

            // 쇼핑 앱 우선순위 및 이름순 정렬
            appInfos.sortWith(compareByDescending<AppInfo> { it.isShopping }.thenBy { it.name })

            withContext(Dispatchers.Main) {
                loadingProgress.visibility = View.GONE
                rvAppList.adapter = AppAdapter(appInfos)
            }
        }
    }

    private fun saveSelectedApps() {
        val currentUser = auth.currentUser ?: return
        val selectedPackages = (rvAppList.adapter as AppAdapter).getSelectedPackages()
        
        if (selectedPackages.isEmpty()) {
            Toast.makeText(this, "잠글 앱을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // Firestore에 잠금 앱 리스트 저장
        db.collection("users").document(currentUser.uid)
            .update("locked_apps", selectedPackages)
            .addOnSuccessListener {
                Toast.makeText(this, "잠금 앱 리스트가 업데이트되었습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                // 문가 없는 경우 생성
                db.collection("users").document(currentUser.uid)
                    .set(mapOf("locked_apps" to selectedPackages), com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                        finish()
                    }
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
                notifyItemChanged(position)
            }
            
            // 쇼핑 앱인 경우 강조 (선택 사항)
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
