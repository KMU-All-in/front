package com.example.allin

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.allin.data.AppDatabase
import com.example.allin.data.LockedAppRepository
import com.example.allin.worker.AppMonitorService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class AppLockActivity : AppCompatActivity() {

    private lateinit var swMainLock: SwitchCompat
    private lateinit var rvLockedApps: RecyclerView
    private lateinit var btnAddApp: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnMoreOptions: ImageButton
    private lateinit var btnChangePassword: LinearLayout
    private lateinit var tvLockStatus: TextView

    private lateinit var adapter: LockedAppAdapter
    private lateinit var repository: LockedAppRepository
    private lateinit var sharedPref: SharedPreferences
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "is_budget_exceeded") {
            runOnUiThread { 
                checkBudgetAndForceLock() 
                observeLockedApps() 
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock)
        hideSystemBars()

        sharedPref = getSharedPreferences("AppLockPrefs", Context.MODE_PRIVATE)
        sharedPref.registerOnSharedPreferenceChangeListener(prefListener)

        val dao = AppDatabase.getDatabase(applicationContext).lockedAppDao()
        repository = LockedAppRepository(dao)

        initViews()
        setupRecyclerView()
        setupListeners()
        checkPermissions()
        observeLockedApps()

        repository.syncFromFirestore(packageManager)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        checkBudgetAndForceLock()
    }

    private fun checkBudgetAndForceLock() {
        val isBudgetExceeded = sharedPref.getBoolean("is_budget_exceeded", false)

        if (isBudgetExceeded) {
            swMainLock.setOnCheckedChangeListener(null)
            swMainLock.isChecked = true
            swMainLock.isEnabled = false 
            setupMainLockListener()
            
            tvLockStatus.text = "예산 초과로 잠금 해제 불가"
            tvLockStatus.setTextColor(Color.parseColor("#F04452"))
            
            if (!isServiceRunning(AppMonitorService::class.java)) startLockService()
        } else {
            swMainLock.isEnabled = true
            swMainLock.isChecked = isServiceRunning(AppMonitorService::class.java)
            updateLockStatusText(swMainLock.isChecked)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun initViews() {
        swMainLock = findViewById(R.id.swMainLock)
        rvLockedApps = findViewById(R.id.rvLockedApps)
        btnAddApp = findViewById(R.id.btnAddApp)
        btnBack = findViewById(R.id.btnBack)
        btnMoreOptions = findViewById(R.id.btnMoreOptions)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        tvLockStatus = findViewById(R.id.tvLockStatus)
    }

    private fun setupRecyclerView() {
        adapter = LockedAppAdapter(
            emptyList(),
            onToggle = { packageName, isActive ->
                if (sharedPref.getBoolean("is_budget_exceeded", false)) {
                    Toast.makeText(this, "예산 초과 상태에서는 끌 수 없습니다.", Toast.LENGTH_SHORT).show()
                    adapter.notifyDataSetChanged()
                    return@LockedAppAdapter
                }
                lifecycleScope.launch { repository.updateLockedAppStatus(packageName, isActive) }
            },
            onMoreClick = { view, app -> showLockedAppOptions(view, app) }
        )
        rvLockedApps.layoutManager = LinearLayoutManager(this)
        rvLockedApps.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        setupMainLockListener()
        btnAddApp.setOnClickListener { startActivity(Intent(this, AppSelectActivity::class.java)) }
        btnMoreOptions.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add("잠금 리스트 전체 삭제")
            popup.setOnMenuItemClickListener { showDeleteAllConfirmDialog(); true }
            popup.show()
        }
        btnChangePassword.setOnClickListener { showPasswordChangeDialog() }
    }

    private fun setupMainLockListener() {
        swMainLock.setOnCheckedChangeListener { _, isChecked ->
            if (sharedPref.getBoolean("is_budget_exceeded", false)) {
                swMainLock.isChecked = true
                return@setOnCheckedChangeListener
            }
            updateLockStatusText(isChecked)
            if (isChecked) {
                if (hasUsageStatsPermission()) startLockService()
                else { swMainLock.isChecked = false; requestUsageStatsPermission() }
            } else stopLockService()
        }
    }

    private fun updateLockStatusText(isLocked: Boolean) {
        if (isLocked) {
            tvLockStatus.text = "잠금 기능 사용 중"
            tvLockStatus.setTextColor(Color.parseColor("#4358F6"))
        } else {
            tvLockStatus.text = "잠금 기능 해제"
            tvLockStatus.setTextColor(Color.parseColor("#4C4C4C"))
        }
    }

    private fun observeLockedApps() {
        lifecycleScope.launch {
            repository.allLockedApps.collect { dbApps ->
                val isExceeded = sharedPref.getBoolean("is_budget_exceeded", false)
                val pm = packageManager
                val uiModels = dbApps.mapNotNull { dbApp ->
                    try {
                        val appInfo = pm.getApplicationInfo(dbApp.packageName, 0)
                        // com.example.allin.LockedApp (UI 모델) 사용
                        com.example.allin.LockedApp(
                            packageName = dbApp.packageName,
                            name = appInfo.loadLabel(pm).toString(),
                            icon = appInfo.loadIcon(pm),
                            isActive = if (isExceeded) true else dbApp.isActive
                        )
                    } catch (e: Exception) { null }
                }
                adapter.updateData(uiModels)
            }
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
    }

    private fun startLockService() {
        val intent = Intent(this, AppMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun stopLockService() = stopService(Intent(this, AppMonitorService::class.java))

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() = startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    override fun onDestroy() {
        sharedPref.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDestroy()
    }

    private fun checkPermissions() {
        if (!hasUsageStatsPermission()) {
            AlertDialog.Builder(this)
                .setTitle("권한 필요")
                .setMessage("앱 잠금을 위해 '사용 정보 접근 권한'이 필요합니다.")
                .setPositiveButton("이동") { _, _ -> requestUsageStatsPermission() }
                .show()
        }
    }

    private fun showDeleteAllConfirmDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("전체 삭제")
            .setMessage("잠금 리스트를 모두 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> lifecycleScope.launch { repository.updateAllLockedApps(emptyList()) } }
            .setNegativeButton("취소", null)
            .create()
        dialog.show()
    }

    private fun showPasswordChangeDialog() {
        val currentUser = auth.currentUser ?: return
        val et = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; hint = "숫자 4자리" }
        AlertDialog.Builder(this)
            .setTitle("새 비밀번호 설정")
            .setView(et)
            .setPositiveButton("변경") { _, _ ->
                val pin = et.text.toString()
                if (pin.length == 4) {
                    getSharedPreferences("LockPrefs", Context.MODE_PRIVATE).edit().putString("LOCK_PIN", pin).apply()
                    db.collection("users").document(currentUser.uid).update("lock_pin", pin)
                        .addOnSuccessListener { Toast.makeText(this, "비밀번호가 변경되었습니다.", Toast.LENGTH_SHORT).show() }
                } else Toast.makeText(this, "4자리 숫자를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showLockedAppOptions(anchor: android.view.View, app: com.example.allin.LockedApp) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("삭제")
        popup.setOnMenuItemClickListener { menuItem ->
            if (menuItem.title == "삭제") showRemoveLockedAppDialog(app)
            true
        }
        popup.show()
    }

    private fun showRemoveLockedAppDialog(app: com.example.allin.LockedApp) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("앱 삭제")
            .setMessage("${app.name} 앱을 잠금 리스트에서 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> lifecycleScope.launch { repository.removeLockedApp(app.packageName) } }
            .setNegativeButton("취소", null)
            .create()
        dialog.show()
    }
}
